package main

import (
	"bufio"
	"encoding/json"
	"fmt"
	"os"
	"os/exec"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"
)

// Instance = un serveur de jeu géré (persisté sur disque).
type Instance struct {
	ID        string            `json:"id"`
	Name      string            `json:"name"`
	PresetID  string            `json:"preset"`
	Vars      map[string]string `json:"vars"`      // port, maxram, gslt…
	Autostart bool              `json:"autostart"` // relancé au boot / après crash
}

const (
	StStopped    = "stopped"
	StInstalling = "installing"
	StRunning    = "running"
	StCrashed    = "crashed"
)

type proc struct {
	mu        sync.Mutex
	status    string
	cmd       *exec.Cmd
	pid       int
	startedAt time.Time
	restarts  int
	stopReq   bool // arrêt volontaire → pas de relance auto
	ring      []string
	subs      map[chan string]struct{}
}

func newProc() *proc { return &proc{status: StStopped, subs: map[chan string]struct{}{}} }

func (p *proc) push(line string) {
	p.mu.Lock()
	p.ring = append(p.ring, line)
	if len(p.ring) > 400 {
		p.ring = p.ring[len(p.ring)-400:]
	}
	for ch := range p.subs {
		select {
		case ch <- line:
		default:
		}
	}
	p.mu.Unlock()
}

func (p *proc) snapshot() (string, []string, int, int) {
	p.mu.Lock()
	defer p.mu.Unlock()
	out := make([]string, len(p.ring))
	copy(out, p.ring)
	return p.status, out, p.pid, p.restarts
}

func (p *proc) subscribe() chan string {
	ch := make(chan string, 256)
	p.mu.Lock()
	for _, l := range p.ring { // rejoue l'historique
		select {
		case ch <- l:
		default:
		}
	}
	p.subs[ch] = struct{}{}
	p.mu.Unlock()
	return ch
}
func (p *proc) unsubscribe(ch chan string) {
	p.mu.Lock()
	delete(p.subs, ch)
	p.mu.Unlock()
	close(ch)
}

// Supervisor gère le cycle de vie de tous les serveurs.
type Supervisor struct {
	mu       sync.Mutex
	dataFile string // instances.json
	srvDir   string // racine des fichiers de jeu (/srv/gamehost)
	insts    map[string]*Instance
	procs    map[string]*proc
	restart  bool // relance auto après crash (désactivable en test)
}

func NewSupervisor(dataFile, srvDir string) *Supervisor {
	return &Supervisor{
		dataFile: dataFile, srvDir: srvDir,
		insts: map[string]*Instance{}, procs: map[string]*proc{}, restart: true,
	}
}

func (s *Supervisor) load() error {
	b, err := os.ReadFile(s.dataFile)
	if err != nil {
		if os.IsNotExist(err) {
			return nil
		}
		return err
	}
	var list []*Instance
	if err := json.Unmarshal(b, &list); err != nil {
		return err
	}
	s.mu.Lock()
	for _, in := range list {
		s.insts[in.ID] = in
		s.procs[in.ID] = newProc()
	}
	s.mu.Unlock()
	return nil
}

func (s *Supervisor) save() error {
	s.mu.Lock()
	list := make([]*Instance, 0, len(s.insts))
	for _, in := range s.insts {
		list = append(list, in)
	}
	s.mu.Unlock()
	sort.Slice(list, func(i, j int) bool { return list[i].ID < list[j].ID })
	b, _ := json.MarshalIndent(list, "", "  ")
	tmp := s.dataFile + ".tmp"
	if err := os.WriteFile(tmp, b, 0644); err != nil {
		return err
	}
	return os.Rename(tmp, s.dataFile)
}

func (s *Supervisor) dir(id string) string { return filepath.Join(s.srvDir, id) }

func (s *Supervisor) Create(name, presetID string, vars map[string]string, autostart bool) (*Instance, error) {
	if presetByID(presetID) == nil {
		return nil, fmt.Errorf("preset inconnu: %s", presetID)
	}
	if strings.TrimSpace(name) == "" {
		return nil, fmt.Errorf("nom requis")
	}
	id := slug(name)
	s.mu.Lock()
	if _, ok := s.insts[id]; ok {
		s.mu.Unlock()
		return nil, fmt.Errorf("un serveur nommé « %s » existe déjà", name)
	}
	in := &Instance{ID: id, Name: name, PresetID: presetID, Vars: vars, Autostart: autostart}
	if in.Vars == nil {
		in.Vars = map[string]string{}
	}
	s.insts[id] = in
	s.procs[id] = newProc()
	s.mu.Unlock()
	os.MkdirAll(s.dir(id), 0755)
	return in, s.save()
}

func (s *Supervisor) Delete(id string) error {
	s.Stop(id)
	s.mu.Lock()
	delete(s.insts, id)
	delete(s.procs, id)
	s.mu.Unlock()
	os.RemoveAll(s.dir(id))
	return s.save()
}

func (s *Supervisor) get(id string) (*Instance, *proc, error) {
	s.mu.Lock()
	in, p := s.insts[id], s.procs[id]
	s.mu.Unlock()
	if in == nil || p == nil {
		return nil, nil, fmt.Errorf("serveur introuvable")
	}
	return in, p, nil
}

// expand remplace les placeholders dans un argv.
func (s *Supervisor) expand(argv []string, in *Instance) []string {
	rep := map[string]string{
		"{dir}":    s.dir(in.ID),
		"{port}":   def(in.Vars["port"], "27015"),
		"{maxram}": def(in.Vars["maxram"], "2048"),
		"{gslt}":   in.Vars["gslt"],
	}
	out := make([]string, len(argv))
	for i, a := range argv {
		for k, v := range rep {
			a = strings.ReplaceAll(a, k, v)
		}
		out[i] = a
	}
	return out
}

// runOnce lance une commande et pousse sa sortie dans le ring ; bloquant.
func (s *Supervisor) runOnce(p *proc, argv []string, dir string) error {
	cmd := exec.Command(argv[0], argv[1:]...)
	cmd.Dir = dir
	cmd.SysProcAttr = &syscall.SysProcAttr{Setpgid: true} // groupe → kill propre
	stdout, _ := cmd.StdoutPipe()
	cmd.Stderr = cmd.Stdout
	if err := cmd.Start(); err != nil {
		return err
	}
	p.mu.Lock()
	p.cmd = cmd
	p.pid = cmd.Process.Pid
	p.startedAt = time.Now()
	p.mu.Unlock()
	sc := bufio.NewScanner(stdout)
	sc.Buffer(make([]byte, 64*1024), 1024*1024)
	for sc.Scan() {
		p.push(sc.Text())
	}
	return cmd.Wait()
}

func (s *Supervisor) Install(id string) error {
	in, p, err := s.get(id)
	if err != nil {
		return err
	}
	pre := presetByID(in.PresetID)
	if len(pre.InstallCmd) == 0 {
		return nil
	}
	p.mu.Lock()
	if p.status == StInstalling || p.status == StRunning {
		p.mu.Unlock()
		return fmt.Errorf("déjà en cours")
	}
	p.status = StInstalling
	p.stopReq = false
	p.mu.Unlock()
	os.MkdirAll(s.dir(id), 0755)
	go func() {
		p.push("=== Installation : " + pre.Name + " ===")
		err := s.runOnce(p, s.expand(pre.InstallCmd, in), s.dir(id))
		if err != nil {
			p.push("!! Échec installation : " + err.Error())
		} else {
			p.push("=== Installation terminée ===")
		}
		p.mu.Lock()
		p.status = StStopped
		p.pid = 0
		p.mu.Unlock()
	}()
	return nil
}

func (s *Supervisor) Start(id string) error {
	in, p, err := s.get(id)
	if err != nil {
		return err
	}
	pre := presetByID(in.PresetID)
	p.mu.Lock()
	if p.status == StRunning || p.status == StInstalling {
		p.mu.Unlock()
		return fmt.Errorf("déjà actif")
	}
	p.status = StRunning
	p.stopReq = false
	p.mu.Unlock()
	go func() {
		for {
			p.push("=== Démarrage " + pre.Name + " ===")
			err := s.runOnce(p, s.expand(pre.RunCmd, in), s.dir(id))
			if err != nil {
				p.push("!! Processus arrêté : " + err.Error())
			}
			p.mu.Lock()
			stop := p.stopReq
			p.pid = 0
			if stop || !s.restart {
				p.status = StStopped
				p.mu.Unlock()
				return
			}
			p.restarts++
			p.status = StCrashed
			rc := p.restarts
			p.mu.Unlock()
			p.push(fmt.Sprintf("=== Crash — relance auto (#%d) dans %.0f s ===", rc, restartDelay.Seconds()))
			time.Sleep(restartDelay)
			p.mu.Lock()
			if p.stopReq { // arrêté pendant l'attente
				p.status = StStopped
				p.mu.Unlock()
				return
			}
			p.status = StRunning
			p.mu.Unlock()
		}
	}()
	return nil
}

var restartDelay = 3 * time.Second

func (s *Supervisor) Stop(id string) error {
	_, p, err := s.get(id)
	if err != nil {
		return err
	}
	p.mu.Lock()
	p.stopReq = true
	cmd := p.cmd
	pid := p.pid
	p.mu.Unlock()
	if cmd != nil && pid > 0 {
		syscall.Kill(-pid, syscall.SIGTERM) // tout le groupe
		go func() {
			time.Sleep(8 * time.Second)
			p.mu.Lock()
			still := p.pid == pid && p.status != StStopped
			p.mu.Unlock()
			if still {
				syscall.Kill(-pid, syscall.SIGKILL)
			}
		}()
	} else {
		p.mu.Lock()
		p.status = StStopped
		p.mu.Unlock()
	}
	return nil
}

func (s *Supervisor) Restart(id string) error {
	s.Stop(id)
	time.Sleep(200 * time.Millisecond)
	// attend l'arrêt effectif (max ~9 s)
	for i := 0; i < 90; i++ {
		_, p, err := s.get(id)
		if err != nil {
			return err
		}
		st, _, _, _ := p.snapshot()
		if st == StStopped || st == StCrashed {
			break
		}
		time.Sleep(100 * time.Millisecond)
	}
	return s.Start(id)
}

// Stat lit CPU/RAM depuis /proc pour un pid (Linux). 0 si indisponible.
func procRAMkB(pid int) int {
	b, err := os.ReadFile(fmt.Sprintf("/proc/%d/status", pid))
	if err != nil {
		return 0
	}
	for _, l := range strings.Split(string(b), "\n") {
		if strings.HasPrefix(l, "VmRSS:") {
			f := strings.Fields(l)
			if len(f) >= 2 {
				n, _ := strconv.Atoi(f[1])
				return n
			}
		}
	}
	return 0
}

func def(v, d string) string {
	if strings.TrimSpace(v) == "" {
		return d
	}
	return v
}

func slug(s string) string {
	s = strings.ToLower(strings.TrimSpace(s))
	var b strings.Builder
	for _, r := range s {
		switch {
		case r >= 'a' && r <= 'z', r >= '0' && r <= '9':
			b.WriteRune(r)
		case r == ' ' || r == '-' || r == '_':
			b.WriteByte('-')
		}
	}
	out := strings.Trim(b.String(), "-")
	if out == "" {
		out = "srv"
	}
	if len(out) > 32 {
		out = out[:32]
	}
	return out
}
