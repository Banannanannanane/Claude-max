extends Node
## Bruitages : un petit pool de lecteurs par son, avec anti-rafale.
## Plus les boucles : musique, ambiance (vent et oiseaux) et pluie, en fondu.

const SOUNDS := ["needle", "hay", "prick", "cash", "buy", "cast", "win", "click", "step", "golden", "recycle"]
const VOLUME := {"needle": -10.0, "hay": -2.0, "prick": -4.0, "cash": -6.0, "buy": -4.0, "cast": -4.0, "win": -3.0, "click": -8.0,
	"step": -17.0, "golden": -1.0, "recycle": -2.0}
const MUSIC_DB := -15.0
const AMBIANCE_DB := -19.0
const RAIN_DB := -12.0

var _players := {}
var _last := {}
var _music: AudioStreamPlayer
var _ambiance: AudioStreamPlayer
var _rain: AudioStreamPlayer
var _rain_target := 0.0
var _rain_level := 0.0


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	for s in SOUNDS:
		var stream: AudioStream = load("res://audio/%s.wav" % s)
		var pool: Array = []
		for i in (4 if s == "needle" or s == "step" else 2):
			var p := AudioStreamPlayer.new()
			p.stream = stream
			p.volume_db = VOLUME.get(s, -6.0)
			add_child(p)
			pool.append(p)
		_players[s] = pool
	_music = _loop("music", MUSIC_DB)
	_ambiance = _loop("ambiance", AMBIANCE_DB)
	_rain = _loop("rain", -80.0)
	_apply()


func _loop(name: String, db: float) -> AudioStreamPlayer:
	var p := AudioStreamPlayer.new()
	p.stream = load("res://audio/%s.wav" % name)
	p.volume_db = db
	add_child(p)
	return p


## Applique les réglages son / musique (à rappeler après un changement).
func _apply() -> void:
	var sound: bool = Game.settings.get("sound", true)
	var music: bool = Game.settings.get("music", true)
	_set_playing(_music, sound and music)
	_set_playing(_ambiance, sound)
	_set_playing(_rain, sound and _rain_level > 0.01)


func _set_playing(p: AudioStreamPlayer, on: bool) -> void:
	if on and not p.playing:
		p.play()
	elif not on and p.playing:
		p.stop()


func refresh() -> void:
	_apply()


## Intensité de la pluie (0 à 1), atteinte en fondu.
func set_rain(amount: float) -> void:
	_rain_target = clampf(amount, 0.0, 1.0)


func _process(delta: float) -> void:
	_rain_level = move_toward(_rain_level, _rain_target, delta * 0.25)
	_rain.volume_db = linear_to_db(maxf(_rain_level, 0.0001)) + RAIN_DB
	var want: bool = Game.settings.get("sound", true) and _rain_level > 0.01
	if want != _rain.playing:
		_set_playing(_rain, want)


func play(name: String, pitch := 1.0) -> void:
	if not Game.settings.get("sound", true) or not _players.has(name):
		return
	var now := Time.get_ticks_msec()
	if now - int(_last.get(name, -1000)) < 45:
		return
	_last[name] = now
	for p: AudioStreamPlayer in _players[name]:
		if not p.playing:
			p.pitch_scale = pitch
			p.play()
			return
	var first: AudioStreamPlayer = _players[name][0]
	first.pitch_scale = pitch
	first.play()
