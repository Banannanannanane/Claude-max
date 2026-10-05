class_name UI
## Thème et petits constructeurs d'interface.

const GOLD := Color(0.95, 0.78, 0.25)
const MUTED := Color(0.66, 0.7, 0.77)
const GOOD := Color(0.4, 0.85, 0.55)
const BAD := Color(1.0, 0.45, 0.42)
const BLUE := Color(0.5, 0.75, 1.0)

static var _theme: Theme


static func _sb(bg: Color, radius := 12, border := Color(0, 0, 0, 0), margin := 12) -> StyleBoxFlat:
	var s := StyleBoxFlat.new()
	s.bg_color = bg
	s.set_corner_radius_all(radius)
	if border.a > 0.0:
		s.border_color = border
		s.set_border_width_all(2)
	s.content_margin_left = margin
	s.content_margin_right = margin
	s.content_margin_top = margin * 0.6
	s.content_margin_bottom = margin * 0.6
	return s


static func _button_styles(t: Theme, type: String, bg: Color, fg: Color) -> void:
	t.set_stylebox("normal", type, _sb(bg, 12, Color(0, 0, 0, 0), 16))
	t.set_stylebox("hover", type, _sb(bg.lightened(0.12), 12, Color(0, 0, 0, 0), 16))
	t.set_stylebox("pressed", type, _sb(bg.darkened(0.2), 12, Color(0, 0, 0, 0), 16))
	t.set_stylebox("focus", type, StyleBoxEmpty.new())
	t.set_stylebox("disabled", type, _sb(Color(0.22, 0.23, 0.26, 0.9), 12, Color(0, 0, 0, 0), 16))
	t.set_color("font_color", type, fg)
	t.set_color("font_hover_color", type, fg)
	t.set_color("font_pressed_color", type, fg)
	t.set_color("font_focus_color", type, fg)
	t.set_color("font_disabled_color", type, Color(0.5, 0.52, 0.56))


static func theme() -> Theme:
	if _theme:
		return _theme
	var t := Theme.new()
	t.default_font_size = 22
	t.set_stylebox("panel", "PanelContainer", _sb(Color(0.11, 0.12, 0.15, 0.96), 18, Color(0.25, 0.27, 0.32), 18))
	t.set_type_variation("Card", "PanelContainer")
	t.set_stylebox("panel", "Card", _sb(Color(0.17, 0.19, 0.23, 1.0), 14, Color(0.27, 0.3, 0.35), 14))
	t.set_type_variation("Hud", "PanelContainer")
	t.set_stylebox("panel", "Hud", _sb(Color(0.06, 0.07, 0.09, 0.62), 14, Color(0, 0, 0, 0), 12))
	_button_styles(t, "Button", Color(0.27, 0.3, 0.36), Color(0.95, 0.96, 0.98))
	t.set_type_variation("GoldButton", "Button")
	_button_styles(t, "GoldButton", Color(0.9, 0.7, 0.18), Color(0.16, 0.11, 0.0))
	t.set_type_variation("BlueButton", "Button")
	_button_styles(t, "BlueButton", Color(0.25, 0.5, 0.85), Color.WHITE)
	t.set_type_variation("RedButton", "Button")
	_button_styles(t, "RedButton", Color(0.75, 0.25, 0.2), Color.WHITE)
	t.set_color("font_color", "Label", Color(0.93, 0.94, 0.96))
	t.set_color("font_outline_color", "Label", Color(0, 0, 0, 0.8))
	t.set_stylebox("background", "ProgressBar", _sb(Color(0.05, 0.05, 0.07, 0.9), 6, Color(0, 0, 0, 0), 0))
	t.set_stylebox("fill", "ProgressBar", _sb(Color(0.4, 0.8, 0.5), 6, Color(0, 0, 0, 0), 0))
	t.set_type_variation("HayBar", "ProgressBar")
	t.set_stylebox("fill", "HayBar", _sb(GOLD, 6, Color(0, 0, 0, 0), 0))
	t.set_type_variation("StorageBar", "ProgressBar")
	t.set_stylebox("fill", "StorageBar", _sb(BLUE, 6, Color(0, 0, 0, 0), 0))
	t.set_stylebox("slider", "HSlider", _sb(Color(0.2, 0.22, 0.26), 6, Color(0, 0, 0, 0), 4))
	t.set_constant("separation", "VBoxContainer", 10)
	t.set_constant("separation", "HBoxContainer", 10)
	_theme = t
	return t


static func label(text: String, size := 22, color := Color(0.93, 0.94, 0.96), wrap := false) -> Label:
	var l := Label.new()
	l.text = text
	l.add_theme_font_size_override("font_size", size)
	l.add_theme_color_override("font_color", color)
	if wrap:
		l.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
		l.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return l


static func button(text: String, cb: Callable, variation := "") -> Button:
	var b := Button.new()
	b.text = text
	if variation != "":
		b.theme_type_variation = variation
	b.mouse_filter = Control.MOUSE_FILTER_PASS # laisse défiler les listes au doigt
	b.focus_mode = Control.FOCUS_NONE
	b.custom_minimum_size = Vector2(0, 56)
	b.pressed.connect(cb)
	return b


static func card() -> PanelContainer:
	var c := PanelContainer.new()
	c.theme_type_variation = "Card"
	return c


static func spacer() -> Control:
	var c := Control.new()
	c.size_flags_horizontal = Control.SIZE_EXPAND_FILL
	return c


static func bar(variation := "") -> ProgressBar:
	var p := ProgressBar.new()
	p.show_percentage = false
	p.custom_minimum_size = Vector2(0, 14)
	if variation != "":
		p.theme_type_variation = variation
	return p


## Texture de bouton rond (dégradé radial à bord net) pour les boutons tactiles.
static func circle_texture(radius: int, color: Color) -> GradientTexture2D:
	var g := Gradient.new()
	g.offsets = PackedFloat32Array([0.0, 0.82, 0.9, 0.96, 1.0])
	g.colors = PackedColorArray([color.lightened(0.1), color, color.lightened(0.35), Color(color, 0.0), Color(color, 0.0)])
	var t := GradientTexture2D.new()
	t.gradient = g
	t.fill = GradientTexture2D.FILL_RADIAL
	t.fill_from = Vector2(0.5, 0.5)
	t.fill_to = Vector2(0.5, 0.0)
	t.width = radius * 2
	t.height = radius * 2
	return t
