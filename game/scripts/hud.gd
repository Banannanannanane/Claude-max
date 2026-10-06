extends CanvasLayer
## Interface en jeu : infos, viseur, joystick, boutons tactiles, notifications et fenêtres.

var player: Node
var main: Node
var root: Control
var sprint_held := false
var action_held := false

var _money: Label
var _hay: Label
var _hand: Label
var _pile: Label
var _store: Label
var _contract: Label
var _quest: Label
var _place_label: Label
var _stamina: ProgressBar
var _hay_bar: ProgressBar
var _prompt: Label
var _cross: Control
var _toasts: VBoxContainer
var _flash: ColorRect
var _big: Label
var _panel: Control
var _fps: Label
var _power: Label
var _level: Label
var _event: Label
var _xp_bar: ProgressBar
var _det_bar: ProgressBar
var _det_label: Label
var _det_signal := 0.0
var _beep_t := 0.0

var _joy_base: TextureRect
var _joy_knob: TextureRect
var _joy_index := -1
var _joy_center := Vector2.ZERO
var _joy_vec := Vector2.ZERO
var _look_index := -1
var _jump := false

var _btn_action: TouchScreenButton
var _btn_sprint: TouchScreenButton
var _btn_jump: TouchScreenButton
var _btn_place: TouchScreenButton
var _btn_rotate: TouchScreenButton
var _btn_cancel: TouchScreenButton
var _action_label: Label
var _buttons: Array = [] # [TouchScreenButton, rayon]
var _menu: HBoxContainer
var _info: PanelContainer
var _fold: Button
var _stamina_label: Label

const JOY_R := 90.0


func _ready() -> void:
	layer = 10
	root = Control.new()
	root.set_anchors_preset(Control.PRESET_FULL_RECT)
	root.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.theme = UI.theme()
	add_child(root)
	# vignette : bords de l'image légèrement assombris
	var vg := TextureRect.new()
	var g := Gradient.new()
	g.set_offset(0, 0.55)
	g.set_color(0, Color(0, 0, 0, 0))
	g.set_offset(1, 1.0)
	g.set_color(1, Color(0, 0, 0, 0.38))
	var gt := GradientTexture2D.new()
	gt.gradient = g
	gt.fill = GradientTexture2D.FILL_RADIAL
	gt.fill_from = Vector2(0.5, 0.5)
	gt.fill_to = Vector2(1.05, 0.5)
	gt.width = 256
	gt.height = 256
	vg.texture = gt
	vg.stretch_mode = TextureRect.STRETCH_SCALE
	vg.set_anchors_preset(Control.PRESET_FULL_RECT)
	vg.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(vg)
	_build_info()
	_build_menu()
	_build_center()
	_build_touch()
	_toasts = VBoxContainer.new()
	_toasts.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_toasts.position = Vector2(-280, 96)
	_toasts.custom_minimum_size = Vector2(560, 0)
	_toasts.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_toasts.alignment = BoxContainer.ALIGNMENT_BEGIN
	root.add_child(_toasts)
	_flash = ColorRect.new()
	_flash.set_anchors_preset(Control.PRESET_FULL_RECT)
	_flash.color = Color(1, 0.85, 0.3, 0)
	_flash.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(_flash)
	_big = UI.label("", 52, UI.GOLD)
	_big.set_anchors_preset(Control.PRESET_CENTER)
	_big.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_big.add_theme_constant_override("outline_size", 14)
	_big.position = Vector2(-400, -170)
	_big.custom_minimum_size = Vector2(800, 0)
	_big.modulate.a = 0
	_big.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(_big)
	_fps = UI.label("", 16, UI.MUTED)
	_fps.set_anchors_preset(Control.PRESET_CENTER_TOP)
	_fps.position = Vector2(-60, 4)
	_fps.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(_fps)
	Game.toast.connect(toast)
	Game.hay_found.connect(_on_hay)
	Game.golden_found.connect(_on_golden)
	Game.changed.connect(_refresh)
	get_viewport().size_changed.connect(_layout)
	_layout()
	_refresh()


# ============================================================ construction de l'interface
func _build_info() -> void:
	var p := PanelContainer.new()
	p.theme_type_variation = "Hud"
	p.position = Vector2(16, 12)
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(p)
	_info = p
	var v := VBoxContainer.new()
	v.add_theme_constant_override("separation", 3)
	v.custom_minimum_size = Vector2(330, 0)
	v.mouse_filter = Control.MOUSE_FILTER_IGNORE
	p.add_child(v)
	var top := HBoxContainer.new()
	top.mouse_filter = Control.MOUSE_FILTER_IGNORE
	v.add_child(top)
	_money = UI.label("", 30, UI.GOLD)
	_money.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	top.add_child(_money)
	# replie / déplie le panneau (les lignes secondaires disparaissent)
	_fold = UI.button("–", _toggle_fold)
	_fold.mouse_filter = Control.MOUSE_FILTER_STOP
	_fold.custom_minimum_size = Vector2(52, 44)
	_fold.add_theme_font_size_override("font_size", 24)
	top.add_child(_fold)
	_level = UI.label("", 17, UI.BLUE)
	v.add_child(_level)
	_xp_bar = UI.bar()
	_xp_bar.max_value = 1.0
	_xp_bar.step = 0.001
	_xp_bar.custom_minimum_size = Vector2(0, 8)
	v.add_child(_xp_bar)
	_hay = UI.label("", 21)
	v.add_child(_hay)
	_hay_bar = UI.bar("HayBar")
	_hay_bar.max_value = Data.HAY_PER_PILE
	v.add_child(_hay_bar)
	_pile = UI.label("", 18, UI.MUTED)
	v.add_child(_pile)
	_hand = UI.label("", 21)
	v.add_child(_hand)
	_store = UI.label("", 18, UI.MUTED)
	v.add_child(_store)
	_event = UI.label("", 17, Color(1, 0.6, 0.3), true)
	_event.custom_minimum_size = Vector2(330, 0)
	v.add_child(_event)
	_power = UI.label("", 17, UI.MUTED)
	v.add_child(_power)
	_quest = UI.label("", 17, UI.GOLD, true)
	_quest.custom_minimum_size = Vector2(330, 0)
	v.add_child(_quest)
	_contract = UI.label("", 17, UI.BLUE, true)
	_contract.custom_minimum_size = Vector2(330, 0)
	v.add_child(_contract)
	_det_label = UI.label("Détecteur de foin", 16, UI.MUTED)
	v.add_child(_det_label)
	_det_bar = UI.bar("HayBar")
	_det_bar.max_value = 1.0
	_det_bar.step = 0.01
	_det_bar.custom_minimum_size = Vector2(0, 8)
	v.add_child(_det_bar)
	# endurance : n'apparaît que lorsqu'elle n'est pas pleine
	_stamina_label = UI.label("Endurance", 16, UI.MUTED)
	v.add_child(_stamina_label)
	_stamina = UI.bar()
	v.add_child(_stamina)
	_apply_fold()


func _toggle_fold() -> void:
	Game.settings["hud_compact"] = not bool(Game.settings.get("hud_compact", false))
	Game.save_device()
	Sfx.play("click")
	_apply_fold()


## Panneau replié : argent, foin, main, objectif et alertes seulement.
func _apply_fold() -> void:
	var compact := bool(Game.settings.get("hud_compact", false))
	_fold.text = "+" if compact else "–"
	for c: Control in [_level, _xp_bar, _pile, _store]:
		c.visible = not compact
	_info.reset_size()


func _build_menu() -> void:
	_menu = HBoxContainer.new()
	_menu.set_anchors_preset(Control.PRESET_TOP_RIGHT)
	_menu.add_theme_constant_override("separation", 8)
	root.add_child(_menu)
	for e in [["Boutique", "boutique", "GoldButton"], ["Arbre", "arbre", "GoldButton"], ["Construire", "construire", "BlueButton"], ["Usine", "stock", ""], ["Carte", "carte", ""], ["Menu", "reglages", ""]]:
		var b := UI.button(e[0], open_panel.bind(e[1]), e[2])
		b.mouse_filter = Control.MOUSE_FILTER_STOP
		b.custom_minimum_size = Vector2(0, 64)
		b.add_theme_font_size_override("font_size", 22)
		_menu.add_child(b)


func _build_center() -> void:
	_cross = Control.new()
	_cross.set_anchors_preset(Control.PRESET_CENTER)
	_cross.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_cross.draw.connect(func() -> void:
		_cross.draw_circle(Vector2.ZERO, 5.0, Color(1, 1, 1, 0.9))
		_cross.draw_arc(Vector2.ZERO, 11.0, 0, TAU, 24, Color(0, 0, 0, 0.6), 2.0)
		# le détecteur s'emballe : cercle doré qui se resserre
		if _det_signal > 0.05:
			var r := lerpf(34.0, 14.0, _det_signal)
			_cross.draw_arc(Vector2.ZERO, r, 0, TAU, 32, Color(1, 0.8, 0.2, 0.35 + 0.6 * _det_signal), 3.0))
	root.add_child(_cross)
	_prompt = UI.label("", 24)
	_prompt.set_anchors_preset(Control.PRESET_CENTER)
	_prompt.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	_prompt.add_theme_constant_override("outline_size", 10)
	_prompt.position = Vector2(-400, 30)
	_prompt.custom_minimum_size = Vector2(800, 0)
	_prompt.mouse_filter = Control.MOUSE_FILTER_IGNORE
	root.add_child(_prompt)


func _touch_button(text: String, radius: float, color: Color, font := 24) -> TouchScreenButton:
	var b := TouchScreenButton.new()
	b.texture_normal = UI.circle_texture(int(radius), Color(color, 0.55))
	b.texture_pressed = UI.circle_texture(int(radius), Color(color.lightened(0.3), 0.8))
	var sh := CircleShape2D.new()
	sh.radius = radius
	b.shape = sh
	b.shape_centered = true
	b.passby_press = false
	root.add_child(b)
	var l := UI.label(text, font)
	l.add_theme_constant_override("outline_size", 8)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	l.vertical_alignment = VERTICAL_ALIGNMENT_CENTER
	l.size = Vector2(radius * 2, radius * 2)
	b.add_child(l)
	_buttons.append([b, radius])
	return b


func _build_touch() -> void:
	_joy_base = TextureRect.new()
	_joy_base.texture = UI.circle_texture(int(JOY_R), Color(1, 1, 1, 0.18))
	_joy_base.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_joy_base.size = Vector2(JOY_R * 2, JOY_R * 2)
	root.add_child(_joy_base)
	_joy_knob = TextureRect.new()
	_joy_knob.texture = UI.circle_texture(40, Color(1, 1, 1, 0.5))
	_joy_knob.mouse_filter = Control.MOUSE_FILTER_IGNORE
	_joy_knob.size = Vector2(80, 80)
	root.add_child(_joy_knob)

	_btn_action = _touch_button("", 95, Color(0.95, 0.7, 0.15), 26)
	_action_label = _btn_action.get_child(0)
	_btn_action.pressed.connect(func() -> void:
		action_held = true
		player.action_pressed())
	_btn_action.released.connect(func() -> void: action_held = false)
	_btn_sprint = _touch_button("Courir", 62, Color(0.3, 0.6, 1.0), 22)
	_btn_sprint.pressed.connect(func() -> void: sprint_held = true)
	_btn_sprint.released.connect(func() -> void: sprint_held = false)
	_btn_jump = _touch_button("Saut", 55, Color(0.5, 0.5, 0.55), 22)
	_btn_jump.pressed.connect(func() -> void: _jump = true)
	_btn_place = _touch_button("PLACER", 90, Color(0.3, 0.85, 0.4), 26)
	_place_label = _btn_place.get_child(0)
	_btn_place.pressed.connect(func() -> void: player.place_pressed())
	_btn_place.released.connect(func() -> void: player.place_released())
	_btn_rotate = _touch_button("Pivoter", 60, Color(0.3, 0.6, 1.0), 22)
	_btn_rotate.pressed.connect(func() -> void: player.rotate_build())
	_btn_cancel = _touch_button("Annuler", 55, Color(0.85, 0.3, 0.25), 22)
	_btn_cancel.pressed.connect(func() -> void: player.cancel_build())


func _layout() -> void:
	var s := root.get_viewport_rect().size
	_menu.position = Vector2(s.x - _menu.get_combined_minimum_size().x - 16, 12)
	_reset_joy()
	_btn_action.position = Vector2(s.x - 95 * 2 - 40, s.y - 95 * 2 - 40)
	_btn_sprint.position = Vector2(s.x - 62 * 2 - 270, s.y - 62 * 2 - 30)
	_btn_jump.position = Vector2(s.x - 55 * 2 - 70, s.y - 95 * 2 - 175)
	_btn_place.position = _btn_action.position + Vector2(5, 5)
	_btn_rotate.position = _btn_sprint.position
	_btn_cancel.position = _btn_jump.position


func _reset_joy() -> void:
	var s := root.get_viewport_rect().size
	_joy_center = Vector2(60 + JOY_R, s.y - 60 - JOY_R)
	_joy_vec = Vector2.ZERO
	_joy_base.position = _joy_center - Vector2(JOY_R, JOY_R)
	_joy_knob.position = _joy_center - Vector2(40, 40)
	_joy_base.modulate.a = 0.6
	_joy_knob.modulate.a = 0.6


# ============================================================ entrées tactiles
func _over_button(p: Vector2) -> bool:
	for e in _buttons:
		var b: TouchScreenButton = e[0]
		if b.visible and p.distance_to(b.position + Vector2(e[1], e[1])) <= e[1] + 6:
			return true
	return false


func _over_ui(p: Vector2) -> bool:
	if _menu.get_global_rect().has_point(p) or _fold.get_global_rect().grow(6).has_point(p):
		return true
	return _over_button(p)


func _input(event: InputEvent) -> void:
	if not visible or (_panel and is_instance_valid(_panel)):
		return
	if event is InputEventScreenTouch:
		var s := root.get_viewport_rect().size
		if event.pressed:
			if _over_ui(event.position):
				return
			if event.position.x < s.x * 0.42 and event.position.y > s.y * 0.35 and _joy_index < 0:
				_joy_index = event.index
				_joy_center = event.position
				_joy_base.position = _joy_center - Vector2(JOY_R, JOY_R)
				_joy_knob.position = _joy_center - Vector2(40, 40)
				_joy_base.modulate.a = 1.0
				_joy_knob.modulate.a = 1.0
			elif _look_index < 0:
				_look_index = event.index
		else:
			if event.index == _joy_index:
				_joy_index = -1
				_reset_joy()
			if event.index == _look_index:
				_look_index = -1
	elif event is InputEventScreenDrag:
		if event.index == _joy_index:
			var d: Vector2 = event.position - _joy_center
			_joy_vec = d.limit_length(JOY_R) / JOY_R
			_joy_knob.position = _joy_center + d.limit_length(JOY_R) - Vector2(40, 40)
		elif event.index == _look_index:
			player.look(event.relative)
	elif event is InputEventKey and event.pressed and not event.echo:
		if event.physical_keycode == KEY_E:
			action_held = true
			player.action_pressed()
		elif event.physical_keycode == KEY_R and player.build_type != "":
			player.rotate_build()
		elif event.physical_keycode == KEY_ENTER and player.build_type != "":
			player.place_pressed()
			player.place_released()
	elif event is InputEventKey and not event.pressed and event.physical_keycode == KEY_E:
		action_held = false


func move_vector() -> Vector2:
	return _joy_vec


func consume_jump() -> bool:
	var j := _jump
	_jump = false
	return j


# ============================================================ affichage
func _refresh() -> void:
	_money.text = Fmt.eur(Game.money)
	var lv := Game.level()
	_level.text = "Niveau %d%s" % [lv, "" if lv >= Data.MAX_LEVEL else " — %s / %s XP" % [Fmt.num(Game.xp), Fmt.num(Game.xp_for(lv + 1))]]
	_xp_bar.value = Game.level_progress()
	var ev := Game.event_id()
	_event.visible = ev != ""
	if ev != "":
		_event.text = "⚡ %s — encore %d s" % [Game.EVENTS[ev].name, int(Game.event_left())]
	_hay.text = "Foin trouvé : %d / %d" % [Game.pile_found, Data.HAY_PER_PILE]
	_hay_bar.value = Game.pile_found
	_pile.text = "%s · %s aiguilles" % [Data.PILES[Game.pile_size].name, Fmt.needles(Game.pile_n)]
	var held := Game.hand_n + Game.hand_h
	_hand.text = "Main : %s / %s" % [Fmt.needles(held), Fmt.needles(Game.hand_cap())]
	_hand.add_theme_color_override("font_color", UI.BAD if held >= Game.hand_cap() else Color(0.93, 0.94, 0.96))
	_store.text = "Revenus : %s / min" % Fmt.eur(float(Game.rates.income) * 60.0)
	_power.text = "Énergie : %s / %s kW%s" % [Fmt.num(Game.power_demand, 1), Fmt.num(Game.power_supply, 1), "  — manque de courant !" if Game.power_factor < 0.999 else ""]
	_power.add_theme_color_override("font_color", UI.BAD if Game.power_factor < 0.999 else UI.MUTED)
	var compact := bool(Game.settings.get("hud_compact", false))
	_power.visible = (Game.power_demand > 0.0 or Game.power_supply > Data.GRID_POWER) and (not compact or Game.power_factor < 0.999)
	if Game.prestige > 0:
		_store.text += "  ·  ★ %d jeton%s" % [Game.prestige, "s" if Game.prestige > 1 else ""]
	if Game.quest < Data.QUESTS.size():
		var q: Array = Data.QUESTS[Game.quest]
		var pr := Game.quest_progress(q[0])
		_quest.text = "Objectif : %s%s" % [q[1], "" if pr.y <= 1 else " (%s / %s)" % [Fmt.num(minf(pr.x, pr.y)), Fmt.num(pr.y)]]
	else:
		_quest.text = ""
	_quest.visible = _quest.text != ""
	_contract.visible = not Game.contract.is_empty()
	if Game.contract.is_empty():
		_contract.text = ""
	else:
		var c: Dictionary = Game.contract
		_contract.text = "Contrat : %d / %d %s (%s)" % [c.done, c.qty, Data.ITEMS[c.t].name.to_lower(), Fmt.duration(maxf(0.0, float(c.until) - float(Game.stats.time)))]


func _process(delta: float) -> void:
	if not player:
		return
	_stamina.max_value = Game.stamina_max()
	_stamina.value = player.stamina
	_stamina.modulate = Color(1, 0.5, 0.5) if player.is_exhausted() else Color.WHITE
	var tired: bool = player.stamina < Game.stamina_max() - 0.01 or player.is_exhausted()
	if tired != _stamina.visible:
		_stamina.visible = tired
		_stamina_label.visible = tired
		_info.reset_size()
	var building: bool = player.build_type != ""
	var free := not panel_open()
	for b in [_btn_action, _btn_sprint, _btn_jump]:
		b.visible = free and not building
	for b in [_btn_place, _btn_rotate, _btn_cancel]:
		b.visible = free and building
	_cross.visible = not building
	_update_detector(delta)
	_fps.visible = bool(Game.settings.get("fps", false))
	if _fps.visible:
		_fps.text = "%d i/s" % Engine.get_frames_per_second()
	_update_prompt()
	_flash.color.a = maxf(0.0, _flash.color.a - delta * 1.5)
	_big.modulate.a = maxf(0.0, _big.modulate.a - delta * 0.6)


## Détecteur de foin (comme le détecteur de métaux de Find the Needle) : distance entre
## l'endroit visé (ou soi-même) et le brin caché le plus proche ; bips de plus en plus rapides.
func _update_detector(delta: float) -> void:
	var origin: Vector3 = player.global_position + Vector3(0, 0.8, 0)
	if player.target.get("kind", "") == "pile":
		origin = player.target.get("point", origin)
	var near: Array = Game.nearest_hay(origin)
	var rng := Game.detector_range()
	var sig := clampf(1.0 - float(near[1]) / rng, 0.0, 1.0) if Game.pile_h > 0 else 0.0
	_det_signal = lerpf(_det_signal, sig, minf(1.0, delta * 8.0))
	_det_bar.value = _det_signal
	var show_bar := _det_signal > 0.02
	if show_bar != _det_bar.visible:
		_det_bar.visible = show_bar
		_info.reset_size()
	if sig <= 0.0:
		_det_label.text = "Détecteur de foin : rien à %s m" % Fmt.num(rng, 1)
	elif sig > 0.8:
		_det_label.text = "Détecteur de foin : ICI ! Creuse !"
	else:
		_det_label.text = "Détecteur de foin : à %s m" % Fmt.num(float(near[1]), 1)
	_det_label.add_theme_color_override("font_color", UI.GOLD if sig > 0.0 else UI.MUTED)
	_cross.queue_redraw()
	if sig > 0.0 and not panel_open():
		_beep_t -= delta
		if _beep_t <= 0.0:
			_beep_t = lerpf(1.1, 0.09, sig)
			Sfx.play("beep", 1.0 + sig * 0.5)
	else:
		_beep_t = 0.0


func _update_prompt() -> void:
	if player.build_type != "":
		var demo: bool = player.build_type == "__demolir"
		_place_label.text = "DÉMOLIR" if demo else "PLACER"
		if demo:
			_prompt.text = "Vise ce que tu veux démolir" if player.ghost_ok else player.ghost_why
		elif Game.is_belt(player.build_type):
			_prompt.text = "Garde PLACER appuyé en marchant pour poser une ligne de tapis (%s)" % Fmt.eur(Game.build_cost(player.build_type))
		else:
			_prompt.text = "%s — %s" % [Data.MACHINES[player.build_type].name, Fmt.eur(Game.build_cost(player.build_type)) if player.move_id < 0 else "déplacement"]
		return
	var t: Dictionary = player.target
	var kind: String = t.get("kind", "")
	var act := ""
	var txt := ""
	if kind == "pile":
		act = "Ramasser"
		if Game.hand_n + Game.hand_h >= Game.hand_cap():
			txt = "Main pleine !"
		elif Game.pile_items() <= 0:
			txt = "Le tas est vide"
		else:
			txt = "Ramasser des aiguilles (garde le bouton appuyé)"
	elif kind == "entity" and Game.entities.has(t.id) and Game.is_broken(t.id):
		act = "Réparer"
		txt = "%s EN PANNE — réparer : %s" % [Data.MACHINES[Game.entities[t.id].type].name, Fmt.eur(Game.repair_cost(Game.entities[t.id].type))]
	elif kind == "entity" and Game.entities.has(t.id):
		var e: Dictionary = Game.entities[t.id]
		match e.type:
			"tremie":
				act = "Verser"
				txt = "Trémie : %s / %s aiguilles" % [Fmt.needles(int(e.n) + int(e.h)), Fmt.needles(Game.tremie_cap())]
			"convoyeur", "express":
				act = "Poser"
				txt = "Convoyeur — poser une poignée dessus" if Game.hand_n + Game.hand_h > 0 else "Convoyeur"
			"bureau":
				act = "Commander"
				txt = "Bureau des commandes et des contrats"
			"trou":
				act = "Infos"
				txt = "Trou de vente — fais-y tomber tes produits par convoyeur"
			_:
				act = "Infos"
				txt = Data.MACHINES[e.type].name
	else:
		act = "Action"
	_prompt.text = txt
	_action_label.text = act


func toast(text: String, gold := false) -> void:
	var p := PanelContainer.new()
	p.theme_type_variation = "Hud"
	p.mouse_filter = Control.MOUSE_FILTER_IGNORE
	var l := UI.label(text, 19, UI.GOLD if gold else Color(0.95, 0.96, 0.98), true)
	l.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
	p.add_child(l)
	_toasts.add_child(p)
	while _toasts.get_child_count() > 3:
		var old := _toasts.get_child(0)
		_toasts.remove_child(old)
		old.queue_free()
	var tw := create_tween()
	tw.tween_interval(2.6)
	tw.tween_property(p, "modulate:a", 0.0, 0.4)
	tw.tween_callback(p.queue_free)


func _on_hay(count: int, by_hand: bool) -> void:
	_flash.color.a = 0.35 if by_hand else 0.15
	Game.vibrate(90 if by_hand else 40)
	_big.text = "BRIN DE FOIN !" if by_hand else "Foin détecté !"
	_big.modulate.a = 1.0
	if by_hand and main:
		main.hay_fx()


func _on_golden() -> void:
	_flash.color = Color(1, 0.8, 0.15, 0.55)
	Game.vibrate(200)
	_big.text = "BRIN DORÉ !"
	_big.modulate.a = 1.4
	if main:
		main.hay_fx()
		main.hay_fx()


func grab_fx(point: Vector3) -> void:
	if main:
		main.spark_fx(point)


# ============================================================ fenêtres
func open_panel(name: String) -> void:
	if player.build_type != "":
		player.cancel_build()
	var p: PanelBase
	match name:
		"boutique":
			p = Panels.ShopPanel.new()
		"arbre":
			p = Panels.TreePanel.new()
		"construire":
			p = Panels.BuildPanel.new()
		"sauvegardes":
			p = Panels.SavePanel.new()
		"succes":
			p = Panels.AchievementsPanel.new()
		"objectifs":
			p = Panels.QuestPanel.new()
		"commandes":
			p = Panels.OrderPanel.new()
		"stock":
			p = Panels.StockPanel.new()
		"carte":
			p = Panels.MapPanel.new()
		"reglages":
			p = Panels.SettingsPanel.new()
		_:
			return
	_show(p)


func open_entity(id: int) -> void:
	var p := Panels.EntityPanel.new()
	p.id = id
	_show(p)


func message(title: String, body: String) -> void:
	var p := Panels.MessagePanel.new()
	p.heading = title
	p.body = body
	_show(p)


func _show(p: PanelBase) -> void:
	close_panel()
	Sfx.play("click")
	p.hud = self
	_panel = p
	action_held = false
	sprint_held = false
	_joy_index = -1
	_look_index = -1
	_reset_joy()
	root.add_child(p)


func close_panel() -> void:
	if _panel and is_instance_valid(_panel):
		_panel.queue_free()
	_panel = null


## Bouton retour d'Android : ferme la fenêtre, annule la construction, ou propose de quitter.
func go_back() -> void:
	if panel_open():
		if _panel is Panels.QuitPanel:
			close_panel()
		else:
			(_panel as PanelBase).close()
		return
	if player.build_type != "":
		player.cancel_build()
		return
	_show(Panels.QuitPanel.new())


func panel_open() -> bool:
	return _panel != null and is_instance_valid(_panel)


func begin_build(type: String) -> void:
	close_panel()
	player.start_build(type)
	if type == "__demolir":
		toast("Démolition : vise un objet et touche DÉMOLIR (remboursé en partie).")
	elif Game.is_belt(type):
		toast("Regarde dans la direction du tapis et garde PLACER appuyé en avançant.")
	else:
		toast("Vise un emplacement libre et touche PLACER. Pivoter change le sens.")


func begin_move(id: int) -> void:
	close_panel()
	player.start_build(Game.entities[id].type, id)
	toast("Déplace la machine puis touche PLACER.")
