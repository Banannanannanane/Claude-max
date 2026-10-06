extends CanvasLayer
## Écran titre : caméra qui tourne autour du tas, logo et menu. Se ferme en fondu vers le jeu.

signal started

var main: Node
var _cam: Camera3D
var _angle := 2.3
var _root: Control
var _fade: ColorRect
var _closing := false


func _ready() -> void:
	layer = 30
	_cam = Camera3D.new()
	_cam.fov = 60.0
	main.add_child(_cam)
	_cam.make_current()
	main.player.process_mode = Node.PROCESS_MODE_DISABLED
	main.hud.visible = false

	_root = Control.new()
	_root.set_anchors_preset(Control.PRESET_FULL_RECT)
	_root.theme = UI.theme()
	add_child(_root)
	# voile sombre à gauche pour la lisibilité
	var shade := TextureRect.new()
	var g := Gradient.new()
	g.set_color(0, Color(0.03, 0.04, 0.06, 0.85))
	g.set_color(1, Color(0.03, 0.04, 0.06, 0.0))
	var gt := GradientTexture2D.new()
	gt.gradient = g
	gt.fill_from = Vector2(0, 0)
	gt.fill_to = Vector2(1, 0)
	shade.texture = gt
	shade.stretch_mode = TextureRect.STRETCH_SCALE
	shade.set_anchors_preset(Control.PRESET_LEFT_WIDE)
	shade.offset_right = 760
	shade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(shade)

	var box := VBoxContainer.new()
	box.position = Vector2(70, 70)
	box.add_theme_constant_override("separation", 14)
	_root.add_child(box)
	var logo := UI.label("TROUVE LE FOIN", 76, UI.GOLD)
	logo.add_theme_constant_override("outline_size", 18)
	logo.add_theme_color_override("font_outline_color", Color(0.12, 0.08, 0.0))
	box.add_child(logo)
	var sub := UI.label("Le foin se cache dans les aiguilles. À toi de le trouver.", 24, Color(0.9, 0.92, 0.95))
	sub.add_theme_constant_override("outline_size", 8)
	box.add_child(sub)
	var gap := Control.new()
	gap.custom_minimum_size = Vector2(0, 26)
	box.add_child(gap)
	var has_save := Game.last_save > 0
	_add_button(box, "Continuer" if has_save else "Jouer", "GoldButton", start.bind(""))
	_add_button(box, "Parties", "BlueButton", start.bind("sauvegardes"))
	_add_button(box, "Réglages", "", start.bind("reglages"))
	_add_button(box, "Quitter", "", func() -> void: get_tree().quit())
	if Game.prestige > 0 or float(Game.stats.get("time", 0.0)) > 0.0:
		var info := UI.label("%s · %s · %s de jeu%s" % [Data.PILES[Game.pile_size].name, Fmt.eur(Game.money), Fmt.duration(float(Game.stats.get("time", 0.0))),
			"  ·  ★ %d jeton%s" % [Game.prestige, "s" if Game.prestige > 1 else ""] if Game.prestige > 0 else ""], 19, UI.MUTED)
		info.add_theme_constant_override("outline_size", 6)
		box.add_child(info)
	var ver := UI.label("v" + str(ProjectSettings.get_setting("application/config/version", "")), 16, UI.MUTED)
	ver.set_anchors_preset(Control.PRESET_BOTTOM_RIGHT)
	ver.position = Vector2(-90, -36)
	_root.add_child(ver)

	_fade = ColorRect.new()
	_fade.set_anchors_preset(Control.PRESET_FULL_RECT)
	_fade.color = Color(0, 0, 0, 1)
	_fade.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_root.add_child(_fade)
	create_tween().tween_property(_fade, "color:a", 0.0, 0.8)


func _add_button(parent: Control, text: String, variation: String, cb: Callable) -> void:
	var b := UI.button(text, cb, variation)
	b.custom_minimum_size = Vector2(360, 66)
	b.size_flags_horizontal = Control.SIZE_SHRINK_BEGIN
	b.add_theme_font_size_override("font_size", 26)
	parent.add_child(b)


func _process(delta: float) -> void:
	if not is_instance_valid(_cam) or _cam.is_queued_for_deletion():
		return
	_angle += delta * 0.06
	var r := maxf(Game.pile_radius(), 2.5)
	var dist := r * 3.0 + 14.0
	var target := Data.PILE_POS + Vector3(0, r * 0.45, 0)
	_cam.global_position = Data.PILE_POS + Vector3(cos(_angle) * dist, r * 1.3 + 5.0, sin(_angle) * dist)
	_cam.look_at(target)


## Ferme l'écran titre en fondu ; panel : fenêtre à ouvrir ensuite ("" pour jouer directement).
func start(panel: String) -> void:
	if _closing:
		return
	_closing = true
	Sfx.play("click")
	var tw := create_tween()
	tw.tween_property(_fade, "color:a", 1.0, 0.35)
	tw.tween_callback(func() -> void:
		main.player.process_mode = Node.PROCESS_MODE_INHERIT
		main.player.cam.make_current()
		main.hud.visible = true
		_cam.queue_free()
		started.emit()
		if panel != "":
			main.hud.open_panel(panel)
		_root.mouse_filter = Control.MOUSE_FILTER_IGNORE
		for c in _root.get_children():
			if c != _fade:
				c.queue_free())
	tw.tween_property(_fade, "color:a", 0.0, 0.45)
	tw.tween_callback(queue_free)


func is_closing() -> bool:
	return _closing
