class_name Panels
## Toutes les fenêtres du jeu.


# ============================================================ boutique
class ShopPanel extends PanelBase:
	var _rows := {}

	func title() -> String:
		return "Boutique — améliorations"

	func sig() -> String:
		var s := ""
		for id in Data.UPGRADE_ORDER:
			s += "%s%d%s|" % [id, Game.lvl(id), Game.has_right(Data.UPGRADES[id].get("right", ""))]
		return s

	func build() -> void:
		_rows.clear()
		var cat := ""
		for id in Data.UPGRADE_ORDER:
			var u: Dictionary = Data.UPGRADES[id]
			if u.cat != cat:
				cat = u.cat
				section("Améliorations du joueur" if cat == "joueur" else "Améliorations des machines")
			var right := HBoxContainer.new()
			var b := UI.button("", _buy.bind(id), "GoldButton")
			b.custom_minimum_size = Vector2(190, 60)
			right.add_child(b)
			var r := row(u.name, u.desc, right)
			r["button"] = b
			_rows[id] = r

	func _buy(id: String) -> void:
		if not Game.buy_upgrade(id):
			Sfx.play("prick")

	func refresh() -> void:
		for id in _rows:
			var u: Dictionary = Data.UPGRADES[id]
			var r: Dictionary = _rows[id]
			var l := Game.lvl(id)
			var maxed := l >= int(u.max)
			var ok_right := Game.has_right(u.get("right", ""))
			r.title.text = "%s   niv. %d / %d" % [u.name, l, u.max]
			r.extra.text = _effect(id, l) + ("" if maxed else "  →  " + _effect(id, l + 1))
			var b: Button = r.button
			if not ok_right:
				b.text = "Droit requis"
				b.disabled = true
			elif maxed:
				b.text = "Maximum"
				b.disabled = true
			else:
				var c := Game.upgrade_cost(id)
				b.text = Fmt.eur(c)
				b.disabled = Game.money < c

	func _effect(id: String, l: int) -> String:
		match id:
			"main":
				return "%d aiguilles" % (15 + 10 * l)
			"poignee":
				return "%d par geste" % (3 + 2 * l)
			"endurance":
				return "%d d'endurance" % (100 + 25 * l)
			"recup":
				return "+%d /s" % (14 + 5 * l)
			"vitesse":
				return "%s m/s" % Fmt.num(4.2 + 0.45 * l, 1)
			"portee":
				return "%s m de portée" % Fmt.num(3.6 + 0.75 * l, 1)
			"oeil":
				return "%d %% de chances" % int(minf(0.9, 0.3 + 0.07 * l) * 100)
			"etageres":
				return "%d places / entrepôt" % (300 + 150 * l)
			"tri":
				return "%s aiguilles/s par table" % Fmt.num(2.0 + 1.5 * l, 1)
			"m_vendeur":
				return "+%d %% prix" % (5 * l)
			"m_verif":
				return "+%d %%" % (30 * l)
		return "+%d %%" % (25 * l)


# ============================================================ arbre de progression
class TreeCanvas extends Control:
	const CELL := Vector2(250, 128)
	const NODE := Vector2(220, 100)
	var buttons := {}

	func _draw() -> void:
		for id in Data.TREE:
			var n: Dictionary = Data.TREE[id]
			for r in n.req:
				var a := _center(Data.TREE[r].pos)
				var b := _center(n.pos)
				var col := UI.GOLD if Game.tree.has(r) else Color(0.4, 0.42, 0.47)
				draw_line(a + Vector2(NODE.x * 0.5, 0), b - Vector2(NODE.x * 0.5, 0), col, 4.0, true)

	func _center(p: Vector2) -> Vector2:
		return Vector2(20, 20) + p * CELL + NODE * 0.5


class TreePanel extends PanelBase:
	var canvas: TreeCanvas
	var detail_title: Label
	var detail_desc: Label
	var buy_btn: Button
	var selected := ""

	func title() -> String:
		return "Arbre de progression"

	func horizontal_mode() -> int:
		return ScrollContainer.SCROLL_MODE_AUTO

	func sig() -> String:
		var s := ""
		for id in Data.TREE:
			s += "%d" % Game.tree_state(id)
		return s

	func build() -> void:
		canvas = TreeCanvas.new()
		canvas.custom_minimum_size = Vector2(40 + 6 * TreeCanvas.CELL.x, 40 + 3 * TreeCanvas.CELL.y)
		content.add_child(canvas)
		for id in Data.TREE:
			var n: Dictionary = Data.TREE[id]
			var b := Button.new()
			b.mouse_filter = Control.MOUSE_FILTER_PASS
			b.focus_mode = Control.FOCUS_NONE
			b.position = Vector2(20, 20) + n.pos * TreeCanvas.CELL
			b.size = TreeCanvas.NODE
			b.autowrap_mode = TextServer.AUTOWRAP_WORD_SMART
			b.add_theme_font_size_override("font_size", 19)
			var st := Game.tree_state(id)
			b.theme_type_variation = ["", "GoldButton", "BlueButton"][st]
			b.text = n.name + ("\n" + Fmt.eur(n.cost) if st == 1 else ("\nAcquis" if st == 2 else "\nVerrouillé"))
			b.pressed.connect(_select.bind(id))
			canvas.add_child(b)
			canvas.buttons[id] = b
		canvas.queue_redraw()
		var c := UI.card()
		footer.add_child(c)
		var h := HBoxContainer.new()
		c.add_child(h)
		var v := VBoxContainer.new()
		v.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		h.add_child(v)
		detail_title = UI.label("", 24, UI.GOLD)
		v.add_child(detail_title)
		detail_desc = UI.label("", 18, UI.MUTED, true)
		v.add_child(detail_desc)
		buy_btn = UI.button("", _buy, "GoldButton")
		buy_btn.custom_minimum_size = Vector2(220, 60)
		h.add_child(buy_btn)
		if selected == "":
			for id in Data.TREE:
				if Game.tree_state(id) == 1:
					selected = id
					break
		if selected == "":
			selected = "licence"

	func _select(id: String) -> void:
		selected = id
		Sfx.play("click")
		refresh()

	func _buy() -> void:
		if not Game.buy_tree(selected):
			Sfx.play("prick")

	func refresh() -> void:
		if selected == "" or not detail_title:
			return
		var n: Dictionary = Data.TREE[selected]
		var st := Game.tree_state(selected)
		detail_title.text = n.name
		var req := []
		for r in n.req:
			req.append(Data.TREE[r].name)
		var extra := ""
		if req.size() > 0:
			extra += "\nNécessite : " + ", ".join(req)
		if int(n.get("piles", 0)) > 0:
			extra += "\nTas terminés nécessaires : %d (tu en as %d)" % [n.piles, Game.stats.piles]
		detail_desc.text = n.desc + extra
		if st == 2:
			buy_btn.text = "Acquis"
			buy_btn.disabled = true
		elif st == 0:
			buy_btn.text = "Verrouillé"
			buy_btn.disabled = true
		else:
			buy_btn.text = "Acheter " + Fmt.eur(n.cost)
			buy_btn.disabled = Game.money < n.cost
		for id in canvas.buttons:
			canvas.buttons[id].modulate = Color(1.2, 1.2, 1.2) if id == selected else Color.WHITE


# ============================================================ construction
class BuildPanel extends PanelBase:
	var _rows := {}

	func title() -> String:
		return "Construire"

	func sig() -> String:
		var s := ""
		for t in Data.BUILD_ORDER:
			s += "%s%d|" % [Game.has_right(Data.BUILDINGS[t].right), Game.count_type(t)]
		return s

	func build() -> void:
		_rows.clear()
		content.add_child(UI.label("Choisis un bâtiment, vise un emplacement libre puis touche PLACER. Les bras robots et pelleteuses doivent être près du tas.", 18, UI.MUTED, true))
		for t in Data.BUILD_ORDER:
			var info: Dictionary = Data.BUILDINGS[t]
			var b := UI.button("", _pick.bind(t), "GoldButton")
			b.custom_minimum_size = Vector2(220, 60)
			var r := row(info.name, info.desc, b)
			r["button"] = b
			_rows[t] = r

	func _pick(t: String) -> void:
		hud.begin_build(t)

	func refresh() -> void:
		for t in _rows:
			var info: Dictionary = Data.BUILDINGS[t]
			var r: Dictionary = _rows[t]
			var b: Button = r.button
			var owned := Game.count_type(t)
			r.extra.text = "Possédés : %d" % owned + _rate_text(t)
			if not Game.has_right(info.right):
				b.text = "Droit requis"
				b.disabled = true
				r.extra.text = "Achète « %s » dans l'arbre de progression." % Data.TREE[info.right].name
			else:
				var c := Game.building_cost(t)
				b.text = "Construire\n" + (Fmt.eur(c) if c > 0 else "gratuit")
				b.disabled = Game.money < c

	func _rate_text(t: String) -> String:
		match t:
			"bras", "pelle", "verif", "table":
				return " · %s aiguilles/s chacun" % Fmt.num(Game.rate_of(t), 1)
			"fonderie", "purif":
				return " · %s lingot/s chacun" % Fmt.num(Game.rate_of(t), 2)
			"entrepot":
				return " · %d places chacun" % (300 + 150 * Game.lvl("etageres"))
			"vendeur":
				return " · %s lots/s" % Fmt.num(Game.rate_of(t), 1)
		return ""


# ============================================================ vente
class SellPanel extends PanelBase:
	const KINDS := ["verified", "raw", "pure", "hay"]
	const NAMES := {"verified": "Aiguilles vérifiées", "raw": "Lingots bruts", "pure": "Lingots purs", "hay": "Brins de foin"}
	const DESCS := {
		"verified": "Garanties sans foin. Peu chères : fonds-les plutôt !",
		"raw": "Sortis de la fonderie (10 aiguilles chacun).",
		"pure": "Sortis du purificateur : le meilleur prix.",
		"hay": "Le trésor : chaque brin vaut une fortune.",
	}
	var _rows := {}
	var _warn: Label
	var _truck: Button

	func title() -> String:
		return "Comptoir de vente"

	func sig() -> String:
		return "%s%s" % [Game.has_right("r_vendeur"), Game.count_type("vendeur")]

	func build() -> void:
		_rows.clear()
		_warn = UI.label("", 18, UI.BAD, true)
		content.add_child(_warn)
		for k in KINDS:
			var h := HBoxContainer.new()
			var b10 := UI.button("Vendre 10", _sell.bind(k, 10))
			b10.custom_minimum_size = Vector2(150, 60)
			var ball := UI.button("Tout vendre", _sell.bind(k, 1 << 30), "GoldButton")
			ball.custom_minimum_size = Vector2(190, 60)
			h.add_child(b10)
			h.add_child(ball)
			var r := row(NAMES[k], DESCS[k], h)
			r["b10"] = b10
			r["ball"] = ball
			_rows[k] = r
		if Game.count_type("vendeur") > 0:
			section("Camion de vente")
			_truck = UI.button("", _toggle_truck)
			content.add_child(_truck)

	func _sell(k: String, q: int) -> void:
		var t := Game.sell(k, q)
		if t > 0.0:
			hud.toast("Vendu : +" + Fmt.eur(t), true)

	func _toggle_truck() -> void:
		Game.settings.truck_hay = not Game.settings.truck_hay
		refresh()

	func refresh() -> void:
		var hidden := Game.vrac_n + Game.vrac_h + Game.table_n + Game.table_h + Game.hand_n + Game.hand_h
		_warn.text = ("%d aiguilles ne sont pas encore vérifiées : invendables tant qu'on n'est pas sûr qu'il n'y a pas de foin dedans." % hidden) if hidden > 0 else ""
		for k in _rows:
			var r: Dictionary = _rows[k]
			var s := Game.stock_of(k)
			r.extra.text = "En stock : %s  ·  %s pièce" % [Fmt.num(s), Fmt.eur(Game.unit_price(k))]
			r.b10.disabled = s <= 0
			r.ball.disabled = s <= 0
		if _truck:
			_truck.text = "Le camion vend aussi le foin : " + ("OUI" if Game.settings.truck_hay else "NON")


# ============================================================ commandes de tas
class OrderPanel extends PanelBase:
	var _rows := {}
	var _status: Label

	func title() -> String:
		return "Bureau des commandes"

	func sig() -> String:
		var s := Game.pile_size + str(Game.pile_done)
		for id in Data.PILE_ORDER:
			s += str(Game.has_right(Data.PILES[id].right))
		return s

	func build() -> void:
		_rows.clear()
		_status = UI.label("", 20, UI.GOLD, true)
		content.add_child(_status)
		for id in Data.PILE_ORDER:
			var p: Dictionary = Data.PILES[id]
			var b := UI.button("", _order.bind(id), "GoldButton")
			b.custom_minimum_size = Vector2(230, 60)
			var r := row(p.name, "%s aiguilles · 22 brins de foin cachés · %s le brin" % [Fmt.num(p.needles), Fmt.eur(p.hay_value)], b)
			r["button"] = b
			_rows[id] = r

	func _order(id: String) -> void:
		if Game.order_pile(id):
			close()
		else:
			Sfx.play("prick")
			hud.toast(Game.can_order(id))

	func refresh() -> void:
		var p: Dictionary = Data.PILES[Game.pile_size]
		_status.text = "Tas actuel : %s — foin trouvé %d / 22 — %s aiguilles restantes%s" % [
			p.name, Game.pile_found, Fmt.num(Game.pile_n), "\nTas terminé : tu peux en commander un nouveau !" if Game.pile_done else ""]
		for id in _rows:
			var r: Dictionary = _rows[id]
			var b: Button = r.button
			var why := Game.can_order(id)
			r.extra.text = why if why != "" else "Prêt à être livré"
			if not Game.has_right(Data.PILES[id].right):
				b.text = "Droit requis"
				b.disabled = true
			else:
				var c := Game.order_cost(id)
				b.text = "Commander\n" + (Fmt.eur(c) if c > 0 else "gratuit")
				b.disabled = why != ""


# ============================================================ stock et production
class StockPanel extends PanelBase:
	var _lines: Label
	var _bar: ProgressBar
	var _cap: Label
	var _machines: Label

	func title() -> String:
		return "Stock et production"

	func build() -> void:
		_cap = UI.label("", 22)
		content.add_child(_cap)
		_bar = UI.bar("StorageBar")
		content.add_child(_bar)
		var c := UI.card()
		content.add_child(c)
		_lines = UI.label("", 21, Color(0.9, 0.92, 0.95), true)
		c.add_child(_lines)
		section("Machines")
		var c2 := UI.card()
		content.add_child(c2)
		_machines = UI.label("", 20, Color(0.9, 0.92, 0.95), true)
		c2.add_child(_machines)

	func refresh() -> void:
		_cap.text = "Entrepôts : %s / %s places" % [Fmt.num(Game.used()), Fmt.num(Game.capacity())]
		_bar.max_value = maxf(1, Game.capacity())
		_bar.value = Game.used()
		var p: Dictionary = Data.PILES[Game.pile_size]
		var hidden := 22 - Game.pile_found
		_lines.text = "\n".join([
			"Aiguilles en vrac (non vérifiées) : %s" % Fmt.num(Game.vrac_n + Game.vrac_h),
			"Sur les tables de tri : %s / %s" % [Fmt.num(Game.table_n + Game.table_h), Fmt.num(Game.table_cap())],
			"Dans ta main : %d / %d" % [Game.hand_n + Game.hand_h, Game.hand_cap()],
			"Aiguilles vérifiées : %s" % Fmt.num(Game.verified),
			"Lingots bruts : %s   ·   Lingots purs : %s" % [Fmt.num(Game.raw), Fmt.num(Game.pure)],
			"Brins de foin en stock : %d" % Game.hay_stock,
			"",
			"%s : %s aiguilles restantes, foin trouvé %d / 22" % [p.name, Fmt.num(Game.pile_n), Game.pile_found],
			("Encore %d brin(s) à trouver : dans le tas ou cachés dans des aiguilles non vérifiées." % hidden) if hidden > 0 else "Tous les brins sont trouvés !",
		])
		var lines := []
		for t in ["table", "verif", "bras", "pelle", "fonderie", "purif", "vendeur"]:
			var n := Game.count_type(t)
			if n == 0:
				continue
			var state := "en marche" if Game.is_active(t) else "à l'arrêt"
			if t == "bras" or t == "pelle":
				var far := 0
				for b in Game.buildings:
					if b.type == t and not Game.digger_in_range(b):
						far += 1
				if far > 0:
					state += " (%d trop loin du tas)" % far
			if Game.free_space() <= 0 and (t == "bras" or t == "pelle" or t == "table"):
				state += " — entrepôt plein !"
			lines.append("%s × %d : %s/s — %s" % [Data.BUILDINGS[t].name, n, Fmt.num(Game.rate_of(t) * n, 2), state])
		_machines.text = "\n".join(lines) if lines.size() > 0 else "Aucune machine pour l'instant. Achète des droits dans l'arbre, puis construis !"


# ============================================================ bâtiment
class BuildingPanel extends PanelBase:
	var index := -1
	var _info: Label

	func title() -> String:
		return Data.BUILDINGS[Game.buildings[index].type].name if index >= 0 and index < Game.buildings.size() else ""

	func sig() -> String:
		return str(Game.buildings.size())

	func build() -> void:
		if index < 0 or index >= Game.buildings.size():
			return
		var b: Dictionary = Game.buildings[index]
		var info: Dictionary = Data.BUILDINGS[b.type]
		content.add_child(UI.label(info.desc, 20, UI.MUTED, true))
		_info = UI.label("", 22, Color(0.9, 0.92, 0.95), true)
		content.add_child(_info)
		var h := HBoxContainer.new()
		content.add_child(h)
		h.add_child(UI.button("Déplacer", _move, "BlueButton"))
		if info.buildable:
			h.add_child(UI.button("Démolir (+%s)" % Fmt.eur(Game.building_cost(b.type) * 0.5), _demolish, "RedButton"))

	func _move() -> void:
		hud.begin_move(index)

	func _demolish() -> void:
		if Game.demolish(index):
			close()

	func refresh() -> void:
		if not _info or index >= Game.buildings.size():
			return
		var b: Dictionary = Game.buildings[index]
		var t: String = b.type
		var s := ""
		match t:
			"bras", "pelle":
				s = "Vitesse : %s aiguilles/s\n%s" % [Fmt.num(Game.rate_of(t), 1), "À portée du tas" if Game.digger_in_range(b) else "Trop loin du tas : déplace-le plus près !"]
			"verif", "table":
				s = "Vitesse : %s aiguilles/s" % Fmt.num(Game.rate_of(t), 1)
			"fonderie", "purif":
				s = "Vitesse : %s lingot/s" % Fmt.num(Game.rate_of(t), 2)
			"entrepot":
				s = "Stock total : %s / %s" % [Fmt.num(Game.used()), Fmt.num(Game.capacity())]
			"vendeur":
				s = "Vend %s lots/s" % Fmt.num(Game.rate_of(t), 1)
		s += "\nÉtat : " + ("en marche" if Game.is_active(t) else "en attente")
		_info.text = s


# ============================================================ réglages
class SettingsPanel extends PanelBase:
	var _sound: Button
	var _sens: HSlider
	var _confirm := false
	var _reset: Button

	func title() -> String:
		return "Réglages et aide"

	func build() -> void:
		var h := HBoxContainer.new()
		content.add_child(h)
		h.add_child(UI.label("Sensibilité du regard", 22))
		_sens = HSlider.new()
		_sens.min_value = 0.3
		_sens.max_value = 2.5
		_sens.step = 0.1
		_sens.value = float(Game.settings.sens)
		_sens.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		_sens.custom_minimum_size = Vector2(300, 50)
		_sens.value_changed.connect(func(v: float) -> void: Game.settings.sens = v)
		h.add_child(_sens)
		_sound = UI.button("", _toggle_sound)
		content.add_child(_sound)
		section("Comment jouer")
		var help := UI.card()
		content.add_child(help)
		help.add_child(UI.label("\n".join([
			"• C'est l'inverse du jeu classique : il faut trouver les 22 brins de FOIN cachés dans un tas d'AIGUILLES.",
			"• Joystick à gauche pour marcher, glisse à droite pour regarder. Vise le tas et garde ACTION appuyé pour ramasser.",
			"• Dépose tes aiguilles à la table de tri (elle repère le foin) ou à l'entrepôt.",
			"• Les aiguilles non vérifiées sont invendables : construis des vérificateurs !",
			"• Fonds les aiguilles en lingots (fonderie), purifie-les (purificateur), vends au comptoir ou par camion.",
			"• Achète les droits de construction dans l'Arbre, puis automatise avec des bras robots et des pelleteuses.",
			"• Termine un tas (22 brins) pour commander un tas plus grand au bureau des commandes.",
			"• Tes machines continuent de travailler quand le jeu est fermé (jusqu'à 8 h).",
		]), 19, Color(0.86, 0.88, 0.92), true))
		section("Partie")
		_reset = UI.button("Effacer la partie", _reset_game, "RedButton")
		content.add_child(_reset)
		content.add_child(UI.label("Trouve le Foin v1.0 — aucune donnée personnelle collectée, jeu 100 % hors ligne.", 17, UI.MUTED, true))

	func _toggle_sound() -> void:
		Game.settings.sound = not Game.settings.sound
		refresh()

	func _reset_game() -> void:
		if not _confirm:
			_confirm = true
			_reset.text = "Touche encore pour TOUT effacer"
			return
		Game.new_game()
		Game.save_game()
		close()

	func refresh() -> void:
		_sound.text = "Son : " + ("activé" if Game.settings.sound else "coupé")


# ============================================================ message simple
class MessagePanel extends PanelBase:
	var heading := ""
	var body := ""

	func title() -> String:
		return heading

	func build() -> void:
		var c := UI.card()
		content.add_child(c)
		c.add_child(UI.label(body, 22, Color(0.92, 0.93, 0.96), true))
		var b := UI.button("C'est parti !", close, "GoldButton")
		content.add_child(b)
