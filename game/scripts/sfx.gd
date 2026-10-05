extends Node
## Bruitages : un petit pool de lecteurs par son, avec anti-rafale.

const SOUNDS := ["needle", "hay", "prick", "cash", "buy", "cast", "win", "click"]
const VOLUME := {"needle": -10.0, "hay": -2.0, "prick": -4.0, "cash": -6.0, "buy": -4.0, "cast": -4.0, "win": -3.0, "click": -8.0}

var _players := {}
var _last := {}


func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	for s in SOUNDS:
		var stream: AudioStream = load("res://audio/%s.wav" % s)
		var pool: Array = []
		for i in (4 if s == "needle" else 2):
			var p := AudioStreamPlayer.new()
			p.stream = stream
			p.volume_db = VOLUME.get(s, -6.0)
			add_child(p)
			pool.append(p)
		_players[s] = pool


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
