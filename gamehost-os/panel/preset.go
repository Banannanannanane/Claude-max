package main

// Preset décrit un type de serveur de jeu installable.
// InstallCmd/RunCmd sont des gabarits argv ; les placeholders {dir} {port} {maxram}
// sont remplacés au lancement. Garder ça déclaratif rend l'ajout de jeux trivial.
type Preset struct {
	ID        string   `json:"id"`
	Name      string   `json:"name"`
	Icon      string   `json:"icon"`
	Kind      string   `json:"kind"`  // "steam" | "minecraft" | "custom"
	AppID     string   `json:"appid"` // appid SteamCMD (jeux Steam)
	Ports     []int    `json:"ports"`
	Proto     string   `json:"proto"`     // "udp" | "tcp" | "both"
	MinRAMMB  int      `json:"min_ram_mb"`
	InstallCmd []string `json:"install_cmd"` // argv ; vide = rien à installer
	RunCmd     []string `json:"run_cmd"`     // argv du serveur
	ConfigFile string   `json:"config_file"` // chemin relatif éditable (optionnel)
	Notes      string   `json:"notes"`
}

// steamInstall génère la commande SteamCMD standard (anonyme) pour un appid.
func steamInstall(appid string) []string {
	return []string{"/opt/gamehost/bin/steamcmd.sh",
		"+force_install_dir", "{dir}",
		"+login", "anonymous",
		"+app_update", appid, "validate",
		"+quit"}
}

// Presets par défaut fournis avec l'OS.
var Presets = []Preset{
	{
		ID: "cs2", Name: "Counter-Strike 2", Icon: "🔫", Kind: "steam", AppID: "730",
		Ports: []int{27015}, Proto: "both", MinRAMMB: 1500,
		InstallCmd: steamInstall("730"),
		RunCmd: []string{"{dir}/game/bin/linuxsteamrt64/cs2", "-dedicated",
			"-port", "{port}", "+map", "de_dust2", "+sv_setsteamaccount", "{gslt}"},
		Notes: "Un jeton GSLT (sv_setsteamaccount) est requis par Valve pour être listé publiquement.",
	},
	{
		ID: "rust", Name: "Rust", Icon: "🪓", Kind: "steam", AppID: "258550",
		Ports: []int{28015, 28016}, Proto: "both", MinRAMMB: 6000,
		InstallCmd: steamInstall("258550"),
		RunCmd: []string{"{dir}/RustDedicated", "-batchmode", "+server.port", "{port}",
			"+server.identity", "gamehost", "+server.hostname", "GameHost Rust",
			"+server.maxplayers", "50"},
		Notes: "Rust demande beaucoup de RAM (8 Go+ conseillé). Mods : Carbon/Oxide en option.",
	},
	{
		ID: "valheim", Name: "Valheim", Icon: "⚔️", Kind: "steam", AppID: "896660",
		Ports: []int{2456, 2457}, Proto: "udp", MinRAMMB: 2000,
		InstallCmd: steamInstall("896660"),
		RunCmd: []string{"{dir}/valheim_server.x86_64", "-nographics", "-batchmode",
			"-name", "GameHost", "-port", "{port}", "-world", "Dedicated",
			"-password", "changeme12"},
		Notes: "Le mot de passe doit faire 5 caractères minimum et différer du nom du serveur.",
	},
	{
		ID: "minecraft", Name: "Minecraft (Java)", Icon: "⛏️", Kind: "minecraft",
		Ports: []int{25565}, Proto: "tcp", MinRAMMB: 2000,
		InstallCmd: []string{"/opt/gamehost/bin/install-minecraft.sh", "{dir}"},
		RunCmd: []string{"java", "-Xmx{maxram}M", "-Xms1024M", "-jar", "{dir}/server.jar", "nogui"},
		ConfigFile: "server.properties",
		Notes: "Accepte l'EULA au premier lancement (eula.txt). Nécessite Java (fourni).",
	},
}

func presetByID(id string) *Preset {
	for i := range Presets {
		if Presets[i].ID == id {
			return &Presets[i]
		}
	}
	return nil
}
