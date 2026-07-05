package main

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"runtime"
	"strings"
	"testing"
	"time"
)

// preset de test : ne dépend d'aucun binaire de jeu.
func installTestPreset(t *testing.T) {
	t.Helper()
	// "install" écrit un fichier, "run" imprime des lignes puis boucle.
	Presets = append(Presets, Preset{
		ID: "echo", Name: "Echo Test", Icon: "🧪", Kind: "custom", Ports: []int{40000},
		InstallCmd: []string{"/bin/sh", "-c", "echo installed > {dir}/ok.txt; echo INSTALL_DONE"},
		RunCmd:     []string{"/bin/sh", "-c", "echo BOOT port={port}; i=0; while true; do echo tick $i; i=$((i+1)); sleep 0.2; done"},
		ConfigFile: "server.cfg",
	})
}

func newTestServer(t *testing.T) (*Server, func()) {
	t.Helper()
	dir := t.TempDir()
	sup := NewSupervisor(filepath.Join(dir, "instances.json"), filepath.Join(dir, "srv"))
	restartDelay = 150 * time.Millisecond
	srv := &Server{cfgFile: filepath.Join(dir, "config.json"), sup: sup, sess: map[string]time.Time{}, noAuth: true}
	return srv, func() {
		for id := range sup.insts {
			sup.Stop(id)
		}
	}
}

func do(t *testing.T, h http.Handler, method, path string, body any) (*httptest.ResponseRecorder, map[string]any) {
	t.Helper()
	var rd *bytes.Reader
	if body != nil {
		b, _ := json.Marshal(body)
		rd = bytes.NewReader(b)
	} else {
		rd = bytes.NewReader(nil)
	}
	req := httptest.NewRequest(method, path, rd)
	w := httptest.NewRecorder()
	h.ServeHTTP(w, req)
	var out map[string]any
	json.Unmarshal(w.Body.Bytes(), &out)
	return w, out
}

func TestSlug(t *testing.T) {
	cases := map[string]string{"Mon Serveur": "mon-serveur", "CS2 #1!": "cs2-1", "  ": "srv", "Éàü": "srv"}
	for in, want := range cases {
		if got := slug(in); got != want {
			t.Errorf("slug(%q)=%q want %q", in, got, want)
		}
	}
}

func TestAuthFlow(t *testing.T) {
	dir := t.TempDir()
	sup := NewSupervisor(filepath.Join(dir, "i.json"), filepath.Join(dir, "srv"))
	srv := &Server{cfgFile: filepath.Join(dir, "cfg.json"), sup: sup, sess: map[string]time.Time{}}
	h := srv.handler()

	// état initial : setup requis
	_, st := do(t, h, "GET", "/api/status", nil)
	if st["setup"] != false {
		t.Fatalf("setup devrait être false au départ")
	}
	// endpoint protégé refusé
	w, _ := do(t, h, "GET", "/api/state", nil)
	if w.Code != 401 {
		t.Fatalf("state sans auth: code %d", w.Code)
	}
	// mot de passe trop court
	w, _ = do(t, h, "POST", "/api/setup", map[string]string{"password": "abc"})
	if w.Code != 400 {
		t.Fatalf("mdp court accepté: %d", w.Code)
	}
	// setup OK → cookie
	w, _ = do(t, h, "POST", "/api/setup", map[string]string{"password": "secret123"})
	if w.Code != 200 {
		t.Fatalf("setup: %d", w.Code)
	}
	cookie := w.Result().Cookies()
	if len(cookie) == 0 || cookie[0].Value == "" {
		t.Fatalf("pas de cookie de session")
	}
	// setup déjà fait
	w, _ = do(t, h, "POST", "/api/setup", map[string]string{"password": "another"})
	if w.Code != 409 {
		t.Fatalf("second setup devrait être 409: %d", w.Code)
	}
	// mauvais mot de passe
	w, _ = do(t, h, "POST", "/api/login", map[string]string{"password": "wrong"})
	if w.Code != 401 {
		t.Fatalf("mauvais mdp accepté: %d", w.Code)
	}
	// bon mot de passe
	w, _ = do(t, h, "POST", "/api/login", map[string]string{"password": "secret123"})
	if w.Code != 200 {
		t.Fatalf("login: %d", w.Code)
	}
	// accès avec cookie
	req := httptest.NewRequest("GET", "/api/state", nil)
	req.AddCookie(w.Result().Cookies()[0])
	w2 := httptest.NewRecorder()
	h.ServeHTTP(w2, req)
	if w2.Code != 200 {
		t.Fatalf("state avec cookie: %d", w2.Code)
	}
	// le hash n'est pas le mot de passe en clair
	b, _ := os.ReadFile(srv.cfgFile)
	if strings.Contains(string(b), "secret123") {
		t.Fatalf("mot de passe stocké en clair !")
	}
}

func TestCreateAndLifecycle(t *testing.T) {
	if runtime.GOOS != "linux" {
		t.Skip("cycle de vie testé sous Linux")
	}
	installTestPreset(t)
	srv, cleanup := newTestServer(t)
	defer cleanup()
	h := srv.handler()

	// création
	w, out := do(t, h, "POST", "/api/create", map[string]any{"name": "Test 1", "preset": "echo", "vars": map[string]string{"port": "40001"}})
	if w.Code != 200 || out["id"] != "test-1" {
		t.Fatalf("create: %d %v", w.Code, out)
	}
	// doublon refusé
	w, _ = do(t, h, "POST", "/api/create", map[string]any{"name": "Test 1", "preset": "echo"})
	if w.Code != 400 {
		t.Fatalf("doublon accepté: %d", w.Code)
	}
	// preset inconnu
	w, _ = do(t, h, "POST", "/api/create", map[string]any{"name": "X", "preset": "nope"})
	if w.Code != 400 {
		t.Fatalf("preset inconnu accepté: %d", w.Code)
	}

	// install → attend le fichier ok.txt
	do(t, h, "POST", "/api/instance/test-1/install", nil)
	okFile := filepath.Join(srv.sup.dir("test-1"), "ok.txt")
	if !waitFor(2*time.Second, func() bool { _, e := os.Stat(okFile); return e == nil }) {
		t.Fatalf("install n'a pas produit ok.txt")
	}

	// start → statut running
	do(t, h, "POST", "/api/instance/test-1/start", nil)
	if !waitFor(2*time.Second, func() bool { return instStatus(srv, "test-1") == StRunning }) {
		t.Fatalf("serveur pas en marche")
	}
	// la console a des lignes
	_, p, _ := srv.sup.get("test-1")
	if !waitFor(2*time.Second, func() bool { _, lines, _, _ := p.snapshot(); return containsPrefix(lines, "tick ") }) {
		t.Fatalf("pas de sortie console")
	}
	// double start refusé
	if err := srv.sup.Start("test-1"); err == nil {
		t.Fatalf("double start accepté")
	}

	// stop → statut stopped, process tué
	pidBefore := instPID(srv, "test-1")
	do(t, h, "POST", "/api/instance/test-1/stop", nil)
	if !waitFor(3*time.Second, func() bool { return instStatus(srv, "test-1") == StStopped }) {
		t.Fatalf("serveur pas arrêté")
	}
	if pidBefore > 0 && processAlive(pidBefore) {
		t.Fatalf("process %d encore vivant après stop", pidBefore)
	}

	// state liste l'instance avec le bon statut
	_, out = do(t, h, "GET", "/api/state", nil)
	insts, _ := out["instances"].([]any)
	if len(insts) != 1 {
		t.Fatalf("state instances=%d", len(insts))
	}

	// delete
	do(t, h, "POST", "/api/instance/test-1/delete", nil)
	if _, _, err := srv.sup.get("test-1"); err == nil {
		t.Fatalf("instance pas supprimée")
	}
	if _, e := os.Stat(srv.sup.dir("test-1")); e == nil {
		t.Fatalf("dossier pas supprimé")
	}
}

func TestAutoRestart(t *testing.T) {
	if runtime.GOOS != "linux" {
		t.Skip()
	}
	dir := t.TempDir()
	sup := NewSupervisor(filepath.Join(dir, "i.json"), filepath.Join(dir, "srv"))
	restartDelay = 120 * time.Millisecond
	Presets = append(Presets, Preset{
		ID: "crashy", Name: "Crashy", Kind: "custom",
		RunCmd: []string{"/bin/sh", "-c", "echo up; sleep 0.2; exit 1"}, // meurt vite
	})
	srv := &Server{cfgFile: filepath.Join(dir, "c.json"), sup: sup, sess: map[string]time.Time{}, noAuth: true}
	sup.Create("boom", "crashy", nil, false)
	sup.Start("boom")
	// doit accumuler des relances puis, sur stop, ne plus repartir
	if !waitFor(3*time.Second, func() bool { _, p, _ := sup.get("boom"); _, _, _, r := p.snapshot(); return r >= 2 }) {
		t.Fatalf("pas de relance auto après crash")
	}
	sup.Stop("boom")
	if !waitFor(2*time.Second, func() bool { return instStatus(srv, "boom") == StStopped }) {
		t.Fatalf("stop pendant crash-loop n'a pas arrêté")
	}
	_, p, _ := sup.get("boom")
	_, _, _, r1 := p.snapshot()
	time.Sleep(400 * time.Millisecond)
	_, _, _, r2 := p.snapshot()
	if r2 != r1 {
		t.Fatalf("relance continue après stop (%d→%d)", r1, r2)
	}
}

func TestConfigEditor(t *testing.T) {
	installTestPreset(t)
	srv, cleanup := newTestServer(t)
	defer cleanup()
	h := srv.handler()
	do(t, h, "POST", "/api/create", map[string]any{"name": "cfg", "preset": "echo"})
	// écrit
	w, _ := do(t, h, "POST", "/api/instance/cfg/config", map[string]string{"content": "hostname=Test\nmax=10"})
	if w.Code != 200 {
		t.Fatalf("save config: %d", w.Code)
	}
	// relit
	_, out := do(t, h, "GET", "/api/instance/cfg/config", nil)
	if !strings.Contains(out["content"].(string), "hostname=Test") {
		t.Fatalf("config non relue: %v", out)
	}
	// pas d'évasion de chemin : le fichier est bien dans le dossier de l'instance
	p := filepath.Join(srv.sup.dir("cfg"), "server.cfg")
	if _, e := os.Stat(p); e != nil {
		t.Fatalf("fichier config absent: %v", e)
	}
}

func TestPersistence(t *testing.T) {
	dir := t.TempDir()
	data := filepath.Join(dir, "i.json")
	sup := NewSupervisor(data, filepath.Join(dir, "srv"))
	sup.Create("Alpha", "cs2", map[string]string{"port": "27020"}, true)
	sup.Create("Beta", "minecraft", nil, false)
	// recharge dans un nouveau superviseur
	sup2 := NewSupervisor(data, filepath.Join(dir, "srv"))
	if err := sup2.load(); err != nil {
		t.Fatalf("load: %v", err)
	}
	in, _, err := sup2.get("alpha")
	if err != nil || in.Vars["port"] != "27020" || !in.Autostart {
		t.Fatalf("instance alpha mal restaurée: %v %v", in, err)
	}
	if _, _, err := sup2.get("beta"); err != nil {
		t.Fatalf("instance beta absente")
	}
}

func TestExpandPlaceholders(t *testing.T) {
	sup := NewSupervisor("/tmp/x/i.json", "/srv/gamehost")
	in := &Instance{ID: "cs2-1", Vars: map[string]string{"port": "27016", "gslt": "ABC", "maxram": "4096"}}
	got := sup.expand([]string{"{dir}/cs2", "-port", "{port}", "+tok", "{gslt}", "-Xmx{maxram}M"}, in)
	want := []string{"/srv/gamehost/cs2-1/cs2", "-port", "27016", "+tok", "ABC", "-Xmx4096M"}
	for i := range want {
		if got[i] != want[i] {
			t.Fatalf("expand[%d]=%q want %q", i, got[i], want[i])
		}
	}
}

func TestPresetsValid(t *testing.T) {
	// chaque preset livré doit avoir une commande de lancement et un port
	for _, p := range Presets {
		if p.ID == "" || p.Name == "" {
			t.Errorf("preset sans id/nom: %+v", p)
		}
		if len(p.RunCmd) == 0 {
			t.Errorf("preset %s sans RunCmd", p.ID)
		}
		if p.Kind == "steam" && p.AppID == "" {
			t.Errorf("preset steam %s sans appid", p.ID)
		}
	}
	// les 4 jeux demandés sont présents
	for _, id := range []string{"cs2", "rust", "minecraft", "valheim"} {
		if presetByID(id) == nil {
			t.Errorf("preset manquant: %s", id)
		}
	}
}

// ---------- helpers ----------
func waitFor(d time.Duration, cond func() bool) bool {
	deadline := time.Now().Add(d)
	for time.Now().Before(deadline) {
		if cond() {
			return true
		}
		time.Sleep(20 * time.Millisecond)
	}
	return cond()
}
func instStatus(s *Server, id string) string { _, p, e := s.sup.get(id); if e != nil { return "?" }; st, _, _, _ := p.snapshot(); return st }
func instPID(s *Server, id string) int       { _, p, e := s.sup.get(id); if e != nil { return 0 }; _, _, pid, _ := p.snapshot(); return pid }
func containsPrefix(lines []string, pre string) bool {
	for _, l := range lines {
		if strings.HasPrefix(l, pre) {
			return true
		}
	}
	return false
}
func processAlive(pid int) bool {
	_, err := os.Stat("/proc/" + itoa(pid))
	return err == nil
}
func itoa(i int) string { b, _ := json.Marshal(i); return string(b) }
