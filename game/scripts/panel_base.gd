class_name PanelBase
extends Control
## Base des fenêtres plein écran : fond assombri, titre, bouton Fermer, liste défilante.
## Les sous-classes remplissent `content` dans build() et mettent à jour dans refresh().

signal closed

var hud: Node
var content: VBoxContainer
var footer: VBoxContainer
var scroll: ScrollContainer
var title_label: Label
var _sig := ""
var _acc := 0.0
var _box: PanelContainer
var _vbox: VBoxContainer


func _init() -> void:
	set_anchors_preset(Control.PRESET_FULL_RECT)
	mouse_filter = Control.MOUSE_FILTER_STOP


func _ready() -> void:
	theme = UI.theme()
	var dim := ColorRect.new()
	dim.color = Color(0, 0, 0, 0.55)
	dim.set_anchors_preset(Control.PRESET_FULL_RECT)
	add_child(dim)
	var box := PanelContainer.new()
	box.set_anchors_preset(Control.PRESET_FULL_RECT)
	box.offset_left = 40
	box.offset_right = -40
	box.offset_top = 24
	box.offset_bottom = -24
	add_child(box)
	_box = box
	var v := VBoxContainer.new()
	box.add_child(v)
	_vbox = v
	var head := HBoxContainer.new()
	v.add_child(head)
	title_label = UI.label(title(), 30, UI.GOLD)
	head.add_child(title_label)
	head.add_child(UI.spacer())
	var extra := header_extra()
	if extra:
		head.add_child(extra)
	head.add_child(UI.button("Fermer", close))
	scroll = ScrollContainer.new()
	scroll.size_flags_vertical = Control.SIZE_EXPAND_FILL
	scroll.horizontal_scroll_mode = horizontal_mode()
	v.add_child(scroll)
	content = VBoxContainer.new()
	content.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	scroll.add_child(content)
	footer = VBoxContainer.new()
	v.add_child(footer)
	_rebuild()
	Game.changed.connect(_on_changed)
	if fit_content():
		_fit_later()


## Fenêtre courte (message) : la boîte prend la hauteur de son contenu, centrée.
func fit_content() -> bool:
	return false


func _fit_later() -> void:
	# le texte ne connaît sa hauteur qu'une fois mis en page (et la fenêtre peut s'ouvrir
	# cachée, derrière l'écran titre) : on réajuste à chaque changement de taille
	content.resized.connect(_fit)
	scroll.resized.connect(_fit)


func _fit() -> void:
	if not is_inside_tree() or scroll.size.x <= 1.0:
		return
	var avail := get_viewport_rect().size.y - 48.0
	var head: Control = _vbox.get_child(0)
	var head_h: float = head.get_combined_minimum_size().y
	var want: float = head_h + content.get_combined_minimum_size().y + footer.get_combined_minimum_size().y + 60.0
	var h := minf(want, avail)
	var top := (avail - h) * 0.5 + 24.0
	if absf(_box.offset_top - top) > 1.0:
		_box.offset_top = top
		_box.offset_bottom = -top


func title() -> String:
	return ""


func header_extra() -> Control:
	return null


func horizontal_mode() -> int:
	return ScrollContainer.SCROLL_MODE_DISABLED


func sig() -> String:
	return ""


func build() -> void:
	pass


func refresh() -> void:
	pass


func _rebuild() -> void:
	for c in content.get_children():
		c.queue_free()
	for c in footer.get_children():
		c.queue_free()
	_sig = sig()
	build()
	refresh()


func _on_changed() -> void:
	if not is_inside_tree():
		return
	if sig() != _sig:
		_rebuild()
	else:
		refresh()


func close() -> void:
	Sfx.play("click")
	closed.emit()
	queue_free()


## Ligne type : titre + description à gauche, contrôle(s) à droite, dans une carte.
func row(title_text: String, desc: String, right: Control) -> Dictionary:
	var c := UI.card()
	content.add_child(c)
	var h := HBoxContainer.new()
	c.add_child(h)
	var left := VBoxContainer.new()
	left.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	left.add_theme_constant_override("separation", 2)
	h.add_child(left)
	var t := UI.label(title_text, 24)
	left.add_child(t)
	var d := UI.label(desc, 18, UI.MUTED, true)
	left.add_child(d)
	var e := UI.label("", 18, UI.BLUE, true)
	left.add_child(e)
	if right:
		h.add_child(right)
	return {"title": t, "desc": d, "extra": e, "card": c}


func section(text: String) -> void:
	var l := UI.label(text.to_upper(), 20, UI.MUTED)
	l.add_theme_constant_override("outline_size", 0)
	var m := MarginContainer.new()
	m.add_theme_constant_override("margin_top", 10)
	m.add_child(l)
	content.add_child(m)
