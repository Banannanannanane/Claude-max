// GameHost Panel — interface web de gestion de serveurs de jeux.
// Binaire unique, stdlib seule (aucune dépendance externe), supervise les
// serveurs comme processus enfants (overhead quasi nul, pas de conteneur).
package main

import (
	"crypto/rand"
	"crypto/sha256"
	"crypto/subtle"
	"embed"
	"encoding/hex"
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"net"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"time"
)

//go:embed web
var webFS embed.FS

type Config struct {
	Salt     string `json:"salt"`
	PassHash string `json:"pass_hash"` // hex(stretch(salt,pass))
}

type Server struct {
	cfgFile string
	cfg     Config
	cfgMu   sync.Mutex
	sup     *Supervisor
	sess    map[string]time.Time
	sessMu  sync.Mutex
	noAuth  bool // tests
}

func stretch(salt, pass string) string {
	h := sha256.Sum256([]byte(salt + ":" + pass))
	for i := 0; i < 100000; i++ {
		h = sha256.Sum256(h[:])
	}
	return hex.EncodeToString(h[:])
}

func randHex(n int) string {
	b := make([]byte, n)
	rand.Read(b)
	return hex.EncodeToString(b)
}

func (s *Server) loadCfg() {
	b, err := os.ReadFile(s.cfgFile)
	if err == nil {
		json.Unmarshal(b, &s.cfg)
	}
}
func (s *Server) saveCfg() error {
	b, _ := json.MarshalIndent(s.cfg, "", "  ")
	os.MkdirAll(filepath.Dir(s.cfgFile), 0755)
	return os.WriteFile(s.cfgFile, b, 0600)
}
func (s *Server) needsSetup() bool {
	s.cfgMu.Lock()
	defer s.cfgMu.Unlock()
	return s.cfg.PassHash == ""
}

func (s *Server) newSession() string {
	t := randHex(24)
	s.sessMu.Lock()
	s.sess[t] = time.Now().Add(7 * 24 * time.Hour)
	s.sessMu.Unlock()
	return t
}
func (s *Server) validSession(t string) bool {
	if t == "" {
		return false
	}
	s.sessMu.Lock()
	defer s.sessMu.Unlock()
	exp, ok := s.sess[t]
	if !ok || time.Now().After(exp) {
		delete(s.sess, t)
		return false
	}
	return true
}

func (s *Server) authed(r *http.Request) bool {
	if s.noAuth {
		return true
	}
	c, err := r.Cookie("gh_sess")
	if err != nil {
		return false
	}
	return s.validSession(c.Value)
}

func writeJSON(w http.ResponseWriter, code int, v any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(code)
	json.NewEncoder(w).Encode(v)
}
func fail(w http.ResponseWriter, code int, msg string) {
	writeJSON(w, code, map[string]string{"error": msg})
}

// ---------- host info ----------
func hostIPs() []string {
	var out []string
	ifs, _ := net.Interfaces()
	for _, ifc := range ifs {
		if ifc.Flags&net.FlagUp == 0 || ifc.Flags&net.FlagLoopback != 0 {
			continue
		}
		addrs, _ := ifc.Addrs()
		for _, a := range addrs {
			if ipn, ok := a.(*net.IPNet); ok && ipn.IP.To4() != nil {
				out = append(out, ipn.IP.String())
			}
		}
	}
	return out
}
func hostMem() (totalKB, availKB int) {
	b, _ := os.ReadFile("/proc/meminfo")
	for _, l := range strings.Split(string(b), "\n") {
		f := strings.Fields(l)
		if len(f) >= 2 {
			var v int
			fmt.Sscanf(f[1], "%d", &v)
			switch f[0] {
			case "MemTotal:":
				totalKB = v
			case "MemAvailable:":
				availKB = v
			}
		}
	}
	return
}

// ---------- handlers ----------
func (s *Server) handler() http.Handler {
	mux := http.NewServeMux()

	mux.HandleFunc("/api/status", func(w http.ResponseWriter, r *http.Request) {
		writeJSON(w, 200, map[string]any{"setup": !s.needsSetup(), "authed": s.authed(r)})
	})

	mux.HandleFunc("/api/setup", func(w http.ResponseWriter, r *http.Request) {
		if !s.needsSetup() {
			fail(w, 409, "déjà configuré")
			return
		}
		var body struct{ Password string }
		json.NewDecoder(r.Body).Decode(&body)
		if len(body.Password) < 6 {
			fail(w, 400, "mot de passe : 6 caractères minimum")
			return
		}
		s.cfgMu.Lock()
		s.cfg.Salt = randHex(16)
		s.cfg.PassHash = stretch(s.cfg.Salt, body.Password)
		err := s.saveCfg()
		s.cfgMu.Unlock()
		if err != nil {
			fail(w, 500, err.Error())
			return
		}
		setCookie(w, s.newSession())
		writeJSON(w, 200, map[string]bool{"ok": true})
	})

	mux.HandleFunc("/api/login", func(w http.ResponseWriter, r *http.Request) {
		var body struct{ Password string }
		json.NewDecoder(r.Body).Decode(&body)
		s.cfgMu.Lock()
		want := s.cfg.PassHash
		got := stretch(s.cfg.Salt, body.Password)
		s.cfgMu.Unlock()
		if want == "" || subtle.ConstantTimeCompare([]byte(want), []byte(got)) != 1 {
			fail(w, 401, "mot de passe incorrect")
			return
		}
		setCookie(w, s.newSession())
		writeJSON(w, 200, map[string]bool{"ok": true})
	})

	mux.HandleFunc("/api/logout", func(w http.ResponseWriter, r *http.Request) {
		if c, err := r.Cookie("gh_sess"); err == nil {
			s.sessMu.Lock()
			delete(s.sess, c.Value)
			s.sessMu.Unlock()
		}
		http.SetCookie(w, &http.Cookie{Name: "gh_sess", Value: "", Path: "/", MaxAge: -1})
		writeJSON(w, 200, map[string]bool{"ok": true})
	})

	mux.HandleFunc("/api/state", s.auth(s.state))
	mux.HandleFunc("/api/create", s.auth(s.create))
	mux.HandleFunc("/api/instance/", s.auth(s.instance)) // /api/instance/{id}/{action}

	// UI statique
	sub, _ := webFS.Open("web/index.html")
	_ = sub
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		p := r.URL.Path
		if p == "/" {
			p = "/index.html"
		}
		b, err := webFS.ReadFile("web" + p)
		if err != nil {
			b, _ = webFS.ReadFile("web/index.html") // SPA fallback
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
			w.Write(b)
			return
		}
		switch {
		case strings.HasSuffix(p, ".html"):
			w.Header().Set("Content-Type", "text/html; charset=utf-8")
		case strings.HasSuffix(p, ".js"):
			w.Header().Set("Content-Type", "text/javascript")
		case strings.HasSuffix(p, ".css"):
			w.Header().Set("Content-Type", "text/css")
		}
		w.Write(b)
	})
	return mux
}

func (s *Server) auth(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if !s.authed(r) {
			fail(w, 401, "non authentifié")
			return
		}
		h(w, r)
	}
}

func setCookie(w http.ResponseWriter, tok string) {
	http.SetCookie(w, &http.Cookie{
		Name: "gh_sess", Value: tok, Path: "/", HttpOnly: true,
		MaxAge: 7 * 24 * 3600, SameSite: http.SameSiteLaxMode,
	})
}

type instView struct {
	*Instance
	Status   string `json:"status"`
	PID      int    `json:"pid"`
	Restarts int    `json:"restarts"`
	RAMmb    int    `json:"ram_mb"`
	Preset   string `json:"preset_name"`
	Icon     string `json:"icon"`
}

func (s *Server) state(w http.ResponseWriter, r *http.Request) {
	s.sup.mu.Lock()
	ids := make([]string, 0, len(s.sup.insts))
	for id := range s.sup.insts {
		ids = append(ids, id)
	}
	s.sup.mu.Unlock()
	var views []instView
	for _, id := range ids {
		in, p, err := s.sup.get(id)
		if err != nil {
			continue
		}
		st, _, pid, rst := p.snapshot()
		ram := 0
		if pid > 0 {
			ram = procRAMkB(pid) / 1024
		}
		pre := presetByID(in.PresetID)
		icon, pname := "🎮", in.PresetID
		if pre != nil {
			icon, pname = pre.Icon, pre.Name
		}
		views = append(views, instView{Instance: in, Status: st, PID: pid, Restarts: rst, RAMmb: ram, Preset: pname, Icon: icon})
	}
	tot, avail := hostMem()
	host, _ := os.Hostname()
	writeJSON(w, 200, map[string]any{
		"presets":   Presets,
		"instances": views,
		"host": map[string]any{
			"hostname": host, "ips": hostIPs(),
			"ram_total_mb": tot / 1024, "ram_avail_mb": avail / 1024,
		},
	})
}

func (s *Server) create(w http.ResponseWriter, r *http.Request) {
	var body struct {
		Name, Preset string
		Vars         map[string]string
		Autostart    bool
	}
	if err := json.NewDecoder(r.Body).Decode(&body); err != nil {
		fail(w, 400, "requête invalide")
		return
	}
	in, err := s.sup.Create(body.Name, body.Preset, body.Vars, body.Autostart)
	if err != nil {
		fail(w, 400, err.Error())
		return
	}
	writeJSON(w, 200, in)
}

func (s *Server) instance(w http.ResponseWriter, r *http.Request) {
	parts := strings.Split(strings.TrimPrefix(r.URL.Path, "/api/instance/"), "/")
	if len(parts) < 2 {
		fail(w, 404, "route inconnue")
		return
	}
	id, action := parts[0], parts[1]
	if _, _, err := s.sup.get(id); err != nil {
		fail(w, 404, "serveur introuvable")
		return
	}
	switch action {
	case "console":
		s.console(w, r, id)
		return
	case "config":
		s.config(w, r, id)
		return
	}
	var err error
	switch action {
	case "install":
		err = s.sup.Install(id)
	case "start":
		err = s.sup.Start(id)
	case "stop":
		err = s.sup.Stop(id)
	case "restart":
		err = s.sup.Restart(id)
	case "delete":
		err = s.sup.Delete(id)
	default:
		fail(w, 404, "action inconnue")
		return
	}
	if err != nil {
		fail(w, 400, err.Error())
		return
	}
	writeJSON(w, 200, map[string]bool{"ok": true})
}

// SSE : flux console live.
func (s *Server) console(w http.ResponseWriter, r *http.Request, id string) {
	_, p, err := s.sup.get(id)
	if err != nil {
		fail(w, 404, "introuvable")
		return
	}
	fl, ok := w.(http.Flusher)
	if !ok {
		fail(w, 500, "streaming non supporté")
		return
	}
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Connection", "keep-alive")
	ch := p.subscribe()
	defer p.unsubscribe(ch)
	fl.Flush()
	ctx := r.Context()
	tick := time.NewTicker(15 * time.Second)
	defer tick.Stop()
	for {
		select {
		case <-ctx.Done():
			return
		case line := <-ch:
			fmt.Fprintf(w, "data: %s\n\n", strings.ReplaceAll(line, "\n", " "))
			fl.Flush()
		case <-tick.C:
			fmt.Fprint(w, ": ping\n\n")
			fl.Flush()
		}
	}
}

// Éditeur de fichier de configuration.
func (s *Server) config(w http.ResponseWriter, r *http.Request, id string) {
	in, _, _ := s.sup.get(id)
	pre := presetByID(in.PresetID)
	if pre == nil || pre.ConfigFile == "" {
		fail(w, 404, "pas de fichier de config pour ce jeu")
		return
	}
	path := filepath.Join(s.sup.dir(id), pre.ConfigFile)
	if !strings.HasPrefix(filepath.Clean(path), filepath.Clean(s.sup.dir(id))) {
		fail(w, 400, "chemin invalide")
		return
	}
	if r.Method == http.MethodPost {
		var body struct{ Content string }
		json.NewDecoder(r.Body).Decode(&body)
		os.MkdirAll(filepath.Dir(path), 0755)
		if err := os.WriteFile(path, []byte(body.Content), 0644); err != nil {
			fail(w, 500, err.Error())
			return
		}
		writeJSON(w, 200, map[string]bool{"ok": true})
		return
	}
	b, _ := os.ReadFile(path)
	writeJSON(w, 200, map[string]string{"file": pre.ConfigFile, "content": string(b)})
}

func main() {
	listen := flag.String("listen", ":8080", "adresse d'écoute")
	dataDir := flag.String("data", "/var/lib/gamehost", "dossier de données")
	srvDir := flag.String("srv", "/srv/gamehost", "dossier des fichiers de jeu")
	cfgFile := flag.String("config", "/etc/gamehost/config.json", "fichier de configuration")
	flag.Parse()
	os.MkdirAll(*dataDir, 0755)
	os.MkdirAll(*srvDir, 0755)

	sup := NewSupervisor(filepath.Join(*dataDir, "instances.json"), *srvDir)
	if err := sup.load(); err != nil {
		log.Printf("chargement instances: %v", err)
	}
	srv := &Server{cfgFile: *cfgFile, sup: sup, sess: map[string]time.Time{}}
	srv.loadCfg()

	// relance auto des serveurs marqués autostart
	for id, in := range sup.insts {
		if in.Autostart {
			log.Printf("autostart: %s", id)
			sup.Start(id)
		}
	}

	log.Printf("GameHost Panel sur %s (data=%s srv=%s)", *listen, *dataDir, *srvDir)
	log.Fatal(http.ListenAndServe(*listen, srv.handler()))
}
