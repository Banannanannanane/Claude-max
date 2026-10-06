class_name Panels
## Toutes les fenêtres du jeu.


# ============================================================ boutique
class ShopPanel extends PanelBase:
	var _rows := {}

	func title() -> String:
		return "Boutique — équipement"

	func build() -> void:
		_rows.clear()
		content.add_child(UI.label("Améliore ton personnage. Les machines s'améliorent dans l'Arbre.", 18, UI.MUTED, true))
		for id in Data.SHOP_ORDER:
			var u: Dictionary = Data.SHOP[id]
			var b := UI.button("", _buy.bind(id), "GoldButton")
			b.custom_minimum_size = Vector2(190, 60)
			var r := row(u.name, u.desc, b)
			r["button"] = b
			_rows[id] = r

	func _buy(id: String) -> void:
		if not Game.buy_shop(id):
			Sfx.play("prick")

	func refresh() -> void:
		for id in _rows:
			var u: Dictionary = Data.SHOP[id]
			var r: Dictionary = _rows[id]
			var l := Game.shop_lvl(id)
			var maxed := l >= int(u.max)
			r.title.text = "%s   niv. %d / %d" % [u.name, l, u.max]
			r.extra.text = _effect(id, l) + ("" if maxed else "  →  " + _effect(id, l + 1))
			var b: Button = r.button
			if maxed:
				b.text = "Maximum"
				b.disabled = true
			else:
				var c := Game.shop_cost(id)
				b.text = Fmt.eur(c)
				b.disabled = Game.money < c

	func _effect(id: String, l: int) -> String:
		match id:
			"main":
				return "%s aiguilles" % Fmt.needles(15 + 10 * l)
			"poignee":
				return "%s : %s aiguilles par geste" % [Game.tool_name(l), Fmt.needles(3 + 2 * l)]
			"detecteur":
				return "%s m de portée" % Fmt.num(3.0 + 1.5 * l, 1)
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
			"tremie_cap":
				return "%s aiguilles / trémie" % Fmt.needles(600 + 300 * l)
		return ""


# ============================================================ arbre technologique par étapes
class TreePanel extends PanelBase:
	var _nodes := {} # id -> Button
	var _ups := {} # id -> {label, button}

	func title() -> String:
		return "Arbre technologique"

	func horizontal_mode() -> int:
		return ScrollContainer.SCROLL_MODE_AUTO

	func sig() -> String:
		var s := ""
		for id in Data.TREE:
			s += str(Game.tree_state(id))
		return s

	func build() -> void:
		_nodes.clear()
		_ups.clear()
		var cols := HBoxContainer.new()
		cols.add_theme_constant_override("separation", 14)
		content.add_child(cols)
		for stage in range(1, Data.STAGES + 1):
			var col := VBoxContainer.new()
			col.custom_minimum_size = Vector2(340, 0)
			col.add_theme_constant_override("separation", 8)
			cols.add_child(col)
			var head := UI.label("ÉTAPE %d" % stage, 20, UI.GOLD)
			head.horizontal_alignment = HORIZONTAL_ALIGNMENT_CENTER
			col.add_child(head)
			for id in Data.TREE:
				var n: Dictionary = Data.TREE[id]
				if int(n.stage) != stage:
					continue
				col.add_child(_node_card(id, n))

	func _node_card(id: String, n: Dictionary) -> Control:
		var card := UI.card()
		var v := VBoxContainer.new()
		v.add_theme_constant_override("separation", 4)
		card.add_child(v)
		var kind := "PLAN" if id.begins_with("p_") else ("CONTRAT" if id.begins_with("c_") else "BONUS")
		v.add_child(UI.label(kind, 14, UI.MUTED))
		v.add_child(UI.label(n.name, 20, Color(0.95, 0.96, 0.98), true))
		var req := []
		for r in n.req:
			req.append(Data.TREE[r].name.replace("Plan : ", ""))
		var cond := ""
		if req.size() > 0:
			cond = "Après : " + ", ".join(req)
		if int(n.get("piles", 0)) > 0:
			cond += ("\n" if cond != "" else "") + "Tas terminés : %d" % n.piles
		if cond != "":
			v.add_child(UI.label(cond, 14, UI.MUTED, true))
		var b := UI.button("", _buy.bind(id))
		b.custom_minimum_size = Vector2(0, 50)
		v.add_child(b)
		_nodes[id] = b
		if n.ups.size() > 0:
			var box := PanelContainer.new()
			box.add_theme_stylebox_override("panel", UI._sb(Color(0.1, 0.11, 0.13), 10, Color(0.25, 0.27, 0.32), 8))
			v.add_child(box)
			var uv := VBoxContainer.new()
			uv.add_theme_constant_override("separation", 4)
			box.add_child(uv)
			uv.add_child(UI.label("AMÉLIORATIONS", 13, UI.MUTED))
			for up in n.ups:
				var h := HBoxContainer.new()
				uv.add_child(h)
				var l := UI.label("", 16, Color(0.9, 0.92, 0.95), true)
				h.add_child(l)
				var ub := UI.button("", _buy_up.bind(up), "GoldButton")
				ub.custom_minimum_size = Vector2(118, 46)
				ub.add_theme_font_size_override("font_size", 16)
				h.add_child(ub)
				_ups[up] = {"label": l, "button": ub}
		return card

	func _buy(id: String) -> void:
		if not Game.buy_tree(id):
			Sfx.play("prick")

	func _buy_up(id: String) -> void:
		if not Game.buy_up(id):
			Sfx.play("prick")

	func refresh() -> void:
		for id in _nodes:
			var b: Button = _nodes[id]
			var st := Game.tree_state(id)
			var c: float = Data.TREE[id].cost
			match st:
				2:
					b.text = "ACHETÉ"
					b.theme_type_variation = "BlueButton"
					b.disabled = true
				1:
					b.text = "Acheter " + Fmt.eur(c)
					b.theme_type_variation = "GoldButton"
					b.disabled = Game.money < c
				_:
					b.text = "Verrouillé"
					b.theme_type_variation = ""
					b.disabled = true
		for up in _ups:
			var u: Dictionary = Data.TREE_UPS[up]
			var l := Game.up_lvl(up)
			var r: Dictionary = _ups[up]
			r.label.text = "%s  %d/%d\n%s" % [u.name, l, u.max, u.fx]
			var ub: Button = r.button
			if not Game.tree.has(Game.up_parent(up)):
				ub.text = "—"
				ub.disabled = true
			elif l >= int(u.max):
				ub.text = "Max"
				ub.disabled = true
			else:
				var cost := Game.up_cost(up)
				ub.text = Fmt.eur(cost)
				ub.disabled = Game.money < cost


# ============================================================ construction
class BuildPanel extends PanelBase:
	var _rows := {}

	func title() -> String:
		return "Construire"

	func header_extra() -> Control:
		return UI.button("Démolir", func() -> void: hud.begin_build("__demolir"), "RedButton")

	func sig() -> String:
		var s := ""
		for t in Data.BUILD_ORDER:
			s += str(Game.has_plan(Data.MACHINES[t].plan))
		return s

	func build() -> void:
		_rows.clear()
		content.add_child(UI.label("Les machines prennent ce qui arrive par l'arrière (flèche verte) et sortent par l'avant (flèche bleue). Un convoyeur posé devant une sortie emporte la production.", 18, UI.MUTED, true))
		for t in Data.BUILD_ORDER:
			var info: Dictionary = Data.MACHINES[t]
			var b := UI.button("", _pick.bind(t), "GoldButton")
			b.custom_minimum_size = Vector2(220, 60)
			var r := row(info.name, info.desc, b)
			r["button"] = b
			_rows[t] = r

	func _pick(t: String) -> void:
		hud.begin_build(t)

	func refresh() -> void:
		for t in _rows:
			var info: Dictionary = Data.MACHINES[t]
			var r: Dictionary = _rows[t]
			var b: Button = r.button
			var sz: Vector2i = info.size
			r.extra.text = "Taille %d×%d · possédés : %d%s" % [sz.x, sz.y, Game.count_type(t), _recipe(t)]
			if not Game.has_plan(info.plan):
				b.text = "Plan requis"
				b.disabled = true
				r.extra.text = "Achète « %s » dans l'Arbre." % Data.TREE[info.plan].name
			else:
				var c := Game.build_cost(t)
				b.text = "Construire\n" + Fmt.eur(c)
				b.disabled = Game.money < c

	func _recipe(t: String) -> String:
		var m: Dictionary = Data.MACHINES[t]
		if not m.has("in"):
			return ""
		var a := []
		for k in m.in:
			a.append("%d %s" % [m.in[k], Data.ITEMS[k].name.to_lower()])
		var o := []
		for k in m.out:
			o.append("%d %s" % [m.out[k], Data.ITEMS[k].name.to_lower()])
		return "\n%s → %s en %s s" % [" + ".join(a), " + ".join(o), Fmt.num(float(m.time) / Game.machine_speed(t), 1)]


# ============================================================ commandes et contrats
class OrderPanel extends PanelBase:
	var _rows := {}
	var _status: Label
	var _contract: Label
	var _offers: Array = []
	var _abandon: Button
	var _recycle: Button
	var _recycle_info: Label
	var _confirm := false

	func title() -> String:
		return "Bureau des commandes"

	func sig() -> String:
		var s := Game.pile_size + str(Game.pile_done) + str(Game.contract.is_empty()) + str(Game.offers.size())
		for id in Data.PILE_ORDER:
			s += str(Game.has_plan(Data.PILES[id].right))
		return s

	func build() -> void:
		_rows.clear()
		_offers.clear()
		_status = UI.label("", 20, UI.GOLD, true)
		content.add_child(_status)
		section("Contrats de livraison")
		_contract = UI.label("", 19, UI.BLUE, true)
		content.add_child(_contract)
		if not Game.contract.is_empty():
			_abandon = UI.button("Abandonner le contrat", Game.abandon_contract, "RedButton")
			content.add_child(_abandon)
		for i in Game.offers.size():
			var o: Dictionary = Game.offers[i]
			var b := UI.button("Accepter", _accept.bind(i), "BlueButton")
			b.custom_minimum_size = Vector2(170, 56)
			b.disabled = not Game.contract.is_empty()
			var r := row("Livrer %d × %s" % [o.qty, Data.ITEMS[o.t].name.to_lower()],
				"Prime : %s · délai %s · à faire tomber dans le trou de vente" % [Fmt.eur(o.reward), Fmt.duration(o.time)], b)
			_offers.append(r)
		section("Commander un tas d'aiguilles")
		for id in Data.PILE_ORDER:
			var p: Dictionary = Data.PILES[id]
			var b2 := UI.button("", _order.bind(id), "GoldButton")
			b2.custom_minimum_size = Vector2(230, 60)
			var r2 := row(p.name, "%s d'aiguilles · 22 brins de foin cachés · %s le brin" % [Fmt.needles(p.needles), Fmt.eur(p.hay_value)], b2)
			r2["button"] = b2
			_rows[id] = r2
		section("Recyclage de l'usine")
		var rc := UI.card()
		content.add_child(rc)
		var rv := VBoxContainer.new()
		rc.add_child(rv)
		_recycle_info = UI.label("", 19, Color(0.86, 0.88, 0.92), true)
		rv.add_child(_recycle_info)
		_confirm = false
		_recycle = UI.button("", _on_recycle, "RedButton")
		_recycle.custom_minimum_size = Vector2(0, 60)
		rv.add_child(_recycle)

	func _on_recycle() -> void:
		if Game.tokens_pending() < 1:
			Sfx.play("prick")
			return
		if not _confirm:
			_confirm = true
			refresh()
			return
		if Game.recycle():
			close()

	func _accept(i: int) -> void:
		Game.accept_offer(i)

	func _order(id: String) -> void:
		if Game.order_pile(id):
			close()
		else:
			Sfx.play("prick")
			hud.toast(Game.can_order(id))

	func refresh() -> void:
		var p: Dictionary = Data.PILES[Game.pile_size]
		_status.text = "Tas actuel : %s — foin trouvé %d / 22 — %s aiguilles restantes%s" % [
			p.name, Game.pile_found, Fmt.needles(Game.pile_n), "\nTas terminé : tu peux en commander un nouveau !" if Game.pile_done else ""]
		if Game.contract.is_empty():
			_contract.text = "Aucun contrat en cours : accepte une offre ci-dessous."
		else:
			var c: Dictionary = Game.contract
			_contract.text = "En cours : %d / %d %s — prime %s — reste %s" % [c.done, c.qty, Data.ITEMS[c.t].name.to_lower(), Fmt.eur(c.reward), Fmt.duration(maxf(0.0, float(c.until) - float(Game.stats.time)))]
		for id in _rows:
			var r: Dictionary = _rows[id]
			var b: Button = r.button
			var why := Game.can_order(id)
			r.extra.text = why if why != "" else "Prêt à être livré"
			if not Game.has_plan(Data.PILES[id].right):
				b.text = "Contrat requis"
				b.disabled = true
			else:
				var c2 := Game.order_cost(id)
				b.text = "Commander\n" + (Fmt.eur(c2) if c2 > 0 else "gratuit")
				b.disabled = why != ""
		var t := Game.tokens_pending()
		var next := pow(float(t + 1), 2.0) * Game.RECYCLE_BASE
		_recycle_info.text = "\n".join([
			"Jetons de recyclage : %d — tes ventes et tes primes de foin rapportent +%d %%." % [Game.prestige, int(round((Game.prestige_mult() - 1.0) * 100))],
			"Recycler, c'est repartir de zéro (argent, machines, arbre, boutique, tas) en gardant tes succès, tes statistiques et tous tes jetons.",
			"Gagné depuis le dernier recyclage : %s. Jetons obtenus maintenant : %d (le suivant à %s)." % [Fmt.eur(Game.run_earned), t, Fmt.eur(next)],
		])
		if t < 1:
			_recycle.text = "Recycler (il faut gagner %s)" % Fmt.eur(Game.RECYCLE_BASE)
			_recycle.disabled = true
		elif _confirm:
			_recycle.text = "Sûr ? Touche encore pour TOUT recommencer avec +%d jeton%s" % [t, "s" if t > 1 else ""]
			_recycle.disabled = false
		else:
			_recycle.text = "Recycler l'usine : +%d jeton%s" % [t, "s" if t > 1 else ""]
			_recycle.disabled = false


# ============================================================ usine et production
class StockPanel extends PanelBase:
	var _lines: Label
	var _machines: Label
	var _sales: Label

	func title() -> String:
		return "Usine et production"

	func build() -> void:
		var c := UI.card()
		content.add_child(c)
		_lines = UI.label("", 21, Color(0.9, 0.92, 0.95), true)
		c.add_child(_lines)
		section("Machines")
		var c2 := UI.card()
		content.add_child(c2)
		_machines = UI.label("", 19, Color(0.9, 0.92, 0.95), true)
		c2.add_child(_machines)
		section("Ventes (trou de vente)")
		var c3 := UI.card()
		content.add_child(c3)
		_sales = UI.label("", 19, Color(0.9, 0.92, 0.95), true)
		c3.add_child(_sales)

	func refresh() -> void:
		var p: Dictionary = Data.PILES[Game.pile_size]
		var on_belts := 0
		for id in Game.entities:
			if Game.entities[id].get("item") != null:
				on_belts += 1
		_lines.text = "\n".join([
			"%s : %s aiguilles restantes, foin trouvé %d / 22" % [p.name, Fmt.needles(Game.pile_n), Game.pile_found],
			"Dans ta main : %s / %s aiguilles · outil : %s" % [Fmt.needles(Game.hand_n + Game.hand_h), Fmt.needles(Game.hand_cap()), Game.tool_name()],
			"Énergie : %s kW consommés / %s kW produits%s" % [Fmt.num(Game.power_demand, 1), Fmt.num(Game.power_supply, 1),
				"" if Game.power_factor >= 0.999 else " — les machines tournent à %d %% : construis des générateurs !" % int(Game.power_factor * 100)],
			"Brins encore cachés : %d · repérés par les radars : %d" % [Game.pile_h, Game.revealed_hay().size()],
			"Objets sur les tapis : %d · vitesse des tapis : %s cases/s" % [on_belts, Fmt.num(Game.belt_speed(), 1)],
			"Revenus récents : %s / min · aiguilles ramassées : %s / min" % [Fmt.eur(float(Game.rates.income) * 60.0), Fmt.needles(float(Game.rates.dig) * 60.0)],
			"Foin perdu dans le trou (non détecté) : %d" % int(Game.stats.lost_hay),
		])
		var counts := {}
		var active := {}
		for id in Game.entities:
			var e: Dictionary = Game.entities[id]
			counts[e.type] = int(counts.get(e.type, 0)) + 1
			if Game.is_active(id):
				active[e.type] = int(active.get(e.type, 0)) + 1
		var lines := []
		for t in Data.BUILD_ORDER:
			if counts.has(t):
				lines.append("%s × %d  (%d en marche)" % [Data.MACHINES[t].name, counts[t], int(active.get(t, 0))])
		_machines.text = "\n".join(lines)
		var s := []
		for t in Data.ITEM_ORDER:
			var n := int(Game.stats.sold.get(t, 0))
			if n > 0:
				s.append("%s : %s vendus · %s pièce" % [Data.ITEMS[t].name, Fmt.num(n), Fmt.eur(Game.item_price({"t": t, "n": Data.LOT}))])
		_sales.text = "\n".join(s) if s.size() > 0 else "Rien vendu pour l'instant : relie une trémie au trou de vente avec des convoyeurs."


# ============================================================ fiche d'une machine
class EntityPanel extends PanelBase:
	var id := -1
	var _info: Label

	func title() -> String:
		return Data.MACHINES[Game.entities[id].type].name if Game.entities.has(id) else ""

	func sig() -> String:
		return str(Game.entities.has(id))

	func build() -> void:
		if not Game.entities.has(id):
			return
		var e: Dictionary = Game.entities[id]
		var m: Dictionary = Data.MACHINES[e.type]
		content.add_child(UI.label(m.desc, 20, UI.MUTED, true))
		_info = UI.label("", 21, Color(0.9, 0.92, 0.95), true)
		content.add_child(_info)
		if not m.get("fixed", false):
			var h := HBoxContainer.new()
			content.add_child(h)
			h.add_child(UI.button("Pivoter", func() -> void: Game.rotate_entity(id), "BlueButton"))
			h.add_child(UI.button("Déplacer", func() -> void: hud.begin_move(id), "BlueButton"))
			h.add_child(UI.button("Démolir", _demolish, "RedButton"))

	func _demolish() -> void:
		if Game.demolish(id):
			close()

	func refresh() -> void:
		if not _info or not Game.entities.has(id):
			return
		var e: Dictionary = Game.entities[id]
		var m: Dictionary = Data.MACHINES[e.type]
		var s := []
		match e.type:
			"tremie":
				s.append("Contenu : %s / %s aiguilles" % [Fmt.needles(int(e.n) + int(e.h)), Fmt.needles(Game.tremie_cap())])
			"bras", "pelle":
				s.append("Portée : %s m depuis le bord du tas" % Fmt.num(Game.dig_range(e.type), 1))
				s.append("À portée du tas" if Game.digger_in_range(e.type, e.c, e.r) else "Trop loin du tas : déplace-le plus près !")
				s.append("Vitesse : %s lots/s" % Fmt.num(float(m.dig) * Game.machine_speed(e.type) * int(m.get("lot_mult", 1)), 2))
			"tampon":
				s.append("Stock : %d / %d objets" % [e.q.size(), int(m.cap)])
			"drone":
				s.append("Charge : %s aiguilles par voyage" % Fmt.needles(30 + 20 * Game.up_lvl("u_drone_charge")))
			"radar":
				s.append("Portée : %s m · brins repérés : %d" % [Fmt.num(Game.radar_range(), 1), Game.revealed_hay().size()])
			"groupe", "eolienne", "solaire":
				s.append("Production actuelle : %s kW" % Fmt.num(Game.generator_output(e.type), 1))
				match e.type:
					"groupe":
						s.append("Carburant : %s par minute" % Fmt.eur(Data.FUEL_COST * 60.0))
					"eolienne":
						s.append("Vent : %d %%" % int(Game.wind() * 100))
					"solaire":
						s.append("Soleil : %d %%%s" % [int(Game.daylight() * 100), " · averse en cours" if Game.weather_rain > 0.1 else ""])
				s.append("Réseau : %s / %s kW" % [Fmt.num(Game.power_demand, 1), Fmt.num(Game.power_supply, 1)])
			"trou":
				s.append("Ventes totales : %s" % Fmt.eur(float(Game.stats.earned)))
		if m.has("power"):
			s.append("Consommation : %s kW%s" % [Fmt.num(float(m.power), 1), "" if Game.power_factor >= 0.999 else " (courant insuffisant : %d %%)" % int(Game.power_factor * 100)])
		if m.has("in"):
			s.append("Vitesse : ×%s" % Fmt.num(Game.machine_speed(e.type), 2))
			s.append("En attente à l'entrée : %d · prêts en sortie : %d" % [e.inq.size(), e.outq.size()])
			if e.outq.size() >= 4:
				s.append("Sortie bloquée : pose un convoyeur devant la flèche bleue !")
		s.append("État : " + ("en marche" if Game.is_active(id) else "en attente"))
		_info.text = "\n".join(s)


# ============================================================ sauvegardes
class SavePanel extends PanelBase:
	var _rows := []

	func title() -> String:
		return "Sauvegardes"

	func build() -> void:
		_rows.clear()
		content.add_child(UI.label("La partie est enregistrée automatiquement toutes les 20 secondes dans l'emplacement actif.", 18, UI.MUTED, true))
		for s in range(1, Game.SLOTS + 1):
			var h := HBoxContainer.new()
			var load_b := UI.button("Charger", _load.bind(s), "BlueButton")
			var save_b := UI.button("Sauver ici", _save.bind(s), "GoldButton")
			var new_b := UI.button("Nouvelle", _new.bind(s))
			var del_b := UI.button("Effacer", _del.bind(s), "RedButton")
			for b in [load_b, save_b, new_b, del_b]:
				b.custom_minimum_size = Vector2(130, 56)
				h.add_child(b)
			var r := row("Emplacement %d" % s, "", h)
			r["load"] = load_b
			r["del"] = del_b
			_rows.append(r)

	func _load(s: int) -> void:
		if Game.load_slot(s):
			hud.toast("Partie %d chargée" % s, true)
			close()

	func _save(s: int) -> void:
		Game.slot = s
		Game.save_slot(s)
		hud.toast("Partie enregistrée dans l'emplacement %d" % s, true)
		refresh()

	func _new(s: int) -> void:
		Game.save_slot(Game.slot)
		Game.new_game_in_slot(s)
		hud.toast("Nouvelle partie dans l'emplacement %d" % s, true)
		close()

	func _del(s: int) -> void:
		if s == Game.slot:
			hud.toast("Impossible d'effacer la partie en cours.")
			return
		Game.delete_slot(s)
		refresh()

	func refresh() -> void:
		for i in _rows.size():
			var s := i + 1
			var r: Dictionary = _rows[i]
			var inf := Game.slot_info(s)
			r.title.text = "Emplacement %d%s" % [s, "  (partie en cours)" if s == Game.slot else ""]
			if inf.is_empty():
				r.desc.text = "Vide"
				r.extra.text = ""
			else:
				r.desc.text = "%s · %s · foin %d/22" % [Fmt.eur(inf.money), inf.pile, inf.found]
				r.extra.text = "Temps de jeu %s · enregistrée le %s" % [Fmt.duration(inf.time), Time.get_datetime_string_from_unix_time(inf.saved_at, true)]
			r.load.disabled = inf.is_empty() or s == Game.slot
			r.del.disabled = inf.is_empty() or s == Game.slot


# ============================================================ succès
class AchievementsPanel extends PanelBase:
	var _stats: Label

	func title() -> String:
		return "Succès et statistiques"

	func sig() -> String:
		return str(Game.achievements.size())

	func build() -> void:
		section("Statistiques")
		var c := UI.card()
		content.add_child(c)
		_stats = UI.label("", 19, Color(0.86, 0.88, 0.92), true)
		c.add_child(_stats)
		section("Succès")
		content.add_child(UI.label("%d / %d débloqués" % [Game.achievements.size(), Data.ACHIEVEMENTS.size()], 22, UI.GOLD))
		for a in Data.ACHIEVEMENTS:
			var ok: bool = Game.achievements.has(a[0])
			var r := row(("✔ " if ok else "") + a[1], a[2], null)
			if not ok:
				r.card.modulate = Color(1, 1, 1, 0.5)

	func refresh() -> void:
		var st: Dictionary = Game.stats
		var sold := 0
		for k in st.get("sold", {}):
			sold += int(st.sold[k])
		_stats.text = "\n".join([
			"Temps de jeu : %s" % Fmt.duration(float(st.get("time", 0.0))),
			"Argent gagné au total : %s" % Fmt.eur(float(st.get("earned", 0.0))),
			"Aiguilles ramassées : %s" % Fmt.needles(float(st.get("needles", 0))),
			"Brins de foin trouvés : %s   ·   foin perdu dans le trou : %s" % [Fmt.num(float(st.get("hay", 0))), Fmt.num(float(st.get("lost_hay", 0)))],
			"Tas terminés : %s   ·   contrats livrés : %s" % [Fmt.num(float(st.get("piles", 0))), Fmt.num(float(st.get("contracts", 0)))],
			"Lingots coulés : %s   ·   objets vendus : %s" % [Fmt.num(float(st.get("ingots", 0))), Fmt.num(float(sold))],
			"Machines et tapis : %d" % Game.entities.size(),
		])


# ============================================================ réglages
class SettingsPanel extends PanelBase:
	var _sound: Button
	var _day: Button
	var _vib: Button
	var _fps: Button
	var _music: Button
	var _weather: Button
	var _sens: HSlider
	var _quality: Array = []

	func title() -> String:
		return "Menu"

	func build() -> void:
		var top := HBoxContainer.new()
		content.add_child(top)
		top.add_child(UI.button("Sauvegardes", func() -> void: hud.open_panel("sauvegardes"), "GoldButton"))
		top.add_child(UI.button("Succès", func() -> void: hud.open_panel("succes"), "BlueButton"))
		top.add_child(UI.button("Objectifs", func() -> void: hud.open_panel("objectifs"), "BlueButton"))
		var save_now := UI.button("Sauvegarder maintenant", func() -> void:
			Game.save_slot(Game.slot)
			hud.toast("Partie enregistrée", true))
		top.add_child(save_now)
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
		_music = UI.button("", func() -> void:
			Game.settings.music = not bool(Game.settings.get("music", true))
			Game.save_device()
			Sfx.refresh()
			refresh())
		content.add_child(_music)
		_weather = UI.button("", func() -> void:
			Game.settings.weather = not bool(Game.settings.get("weather", true))
			Game.save_device()
			refresh())
		content.add_child(_weather)
		_day = UI.button("", _toggle_day)
		content.add_child(_day)
		_vib = UI.button("", func() -> void:
			Game.settings.vibrate = not bool(Game.settings.vibrate)
			Game.save_device()
			refresh())
		content.add_child(_vib)
		section("Graphismes")
		var qh := HBoxContainer.new()
		content.add_child(qh)
		_quality.clear()
		for i in 3:
			var b := UI.button(["Bas", "Moyen", "Élevé"][i], _set_quality.bind(i))
			b.size_flags_horizontal = Control.SIZE_EXPAND_FILL
			qh.add_child(b)
			_quality.append(b)
		content.add_child(UI.label("Bas : sans ombres, image allégée, plus fluide et moins de batterie. Élevé : ombres fines et tas plus détaillé.", 17, UI.MUTED, true))
		_fps = UI.button("", func() -> void:
			Game.settings.fps = not bool(Game.settings.fps)
			Game.save_device()
			refresh())
		content.add_child(_fps)
		section("Comment jouer")
		var help := UI.card()
		content.add_child(help)
		help.add_child(UI.label("\n".join([
			"• Un tas d'AIGUILLES cache 22 brins de FOIN : trouve-les tous pour commander un tas plus gros.",
			"• Joystick à gauche, glisse à droite pour regarder. Vise le tas et garde ACTION appuyé pour ramasser.",
			"• Verse tes aiguilles dans une trémie ou pose-les sur un tapis (ACTION en visant le tapis).",
			"• On ne vend qu'au TROU DE VENTE : amène-y tout par convoyeurs. Construire > Convoyeur, puis garde PLACER appuyé en marchant.",
			"• Le foin non détecté qui tombe dans le trou retourne dans le tas : place des SCANNERS sur tes tapis.",
			"• Bras robots et pelleteuses ne creusent que dans leur rayon d'action : quand leur voyant passe au rouge, déplace-les plus près du tas.",
			"• Chaque brin de foin est enfoui à un endroit précis : suis les bips du DÉTECTEUR et creuse là où le cercle doré se resserre. Le RADAR à foin les révèle de loin.",
			"• Les machines consomment de l'électricité : au-delà des 10 kW du réseau, construis groupes électrogènes, éoliennes (vent, pluie) et panneaux solaires (jour).",
			"• Chaîne de valeur : vrac → scanner → fonderie → purificateur → presse / tréfileuse → aiguilleuse.",
			"• Chaque machine prend par l'arrière (flèche verte) et sort par l'avant (flèche bleue). Les séparateurs répartissent.",
			"• Arbre : achète les plans (droits de construction), puis leurs améliorations par niveaux.",
			"• Bureau : contrats de livraison à prime et commande des tas. L'usine produit aussi hors ligne (8 h max).",
		]), 19, Color(0.86, 0.88, 0.92), true))
		content.add_child(UI.label("Trouve le Foin v1.7 — aucune donnée personnelle collectée, jeu 100 % hors ligne.", 17, UI.MUTED, true))

	func _toggle_sound() -> void:
		Game.settings.sound = not Game.settings.sound
		Game.save_device()
		Sfx.refresh()
		refresh()

	func _set_quality(q: int) -> void:
		Game.settings.quality = q
		Game.save_device()
		if hud and hud.main:
			hud.main.apply_quality()
		refresh()

	func _toggle_day() -> void:
		Game.settings.daynight = not Game.settings.get("daynight", true)
		refresh()

	func refresh() -> void:
		_sound.text = "Son : " + ("activé" if Game.settings.sound else "coupé")
		_day.text = "Cycle jour / nuit : " + ("activé" if Game.settings.get("daynight", true) else "toujours le jour")
		_music.text = "Musique : " + ("activée" if Game.settings.get("music", true) else "coupée")
		_weather.text = "Météo (averses de pluie) : " + ("activée" if Game.settings.get("weather", true) else "toujours beau")
		_vib.text = "Vibrations : " + ("activées" if Game.settings.get("vibrate", true) else "coupées")
		_fps.text = "Compteur d'images par seconde : " + ("affiché" if Game.settings.get("fps", false) else "masqué")
		var q := int(Game.settings.get("quality", 1))
		for i in _quality.size():
			(_quality[i] as Button).theme_type_variation = "GoldButton" if i == q else ""


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
		content.add_child(UI.button("C'est parti !", close, "GoldButton"))


# ============================================================ objectifs
class QuestPanel extends PanelBase:
	func title() -> String:
		return "Objectifs"

	func sig() -> String:
		return str(Game.quest)

	func build() -> void:
		content.add_child(UI.label("Suis les objectifs pour apprendre le jeu : chacun rapporte une prime.", 18, UI.MUTED, true))
		for i in Data.QUESTS.size():
			var q: Array = Data.QUESTS[i]
			var done := i < Game.quest
			var r := row(("✔ " if done else ("➜ " if i == Game.quest else "")) + q[1], "Prime : " + Fmt.eur(q[2]), null)
			if i > Game.quest:
				r.card.modulate = Color(1, 1, 1, 0.45)
			if i == Game.quest:
				var pr := Game.quest_progress(q[0])
				r.extra.text = "Progression : %s / %s" % [Fmt.num(minf(pr.x, pr.y)), Fmt.num(pr.y)]


# ============================================================ quitter
class QuitPanel extends PanelBase:
	func title() -> String:
		return "Quitter le jeu ?"

	func build() -> void:
		content.add_child(UI.label("Ta partie est enregistrée. Ton usine continuera de produire pendant ton absence (jusqu'à 8 h).", 20, UI.MUTED, true))
		var h := HBoxContainer.new()
		content.add_child(h)
		h.add_child(UI.button("Continuer à jouer", close, "BlueButton"))
		h.add_child(UI.button("Quitter", func() -> void:
			Game.save_slot(Game.slot)
			get_tree().quit(), "RedButton"))


# ============================================================ carte
## Vue de dessus de l'usine : machines, tapis (avec leur sens), tas, trou de vente et joueur.
class MapPanel extends PanelBase:
	var view: MapView
	var _whole := false
	var _zoom_btn: Button

	func title() -> String:
		return "Carte de l'usine"

	func header_extra() -> Control:
		_zoom_btn = UI.button("Tout le terrain", func() -> void:
			_whole = not _whole
			view.whole = _whole
			_zoom_btn.text = "Zoom sur l'usine" if _whole else "Tout le terrain"
			view.queue_redraw(), "BlueButton")
		return _zoom_btn

	func build() -> void:
		view = MapView.new()
		view.player = hud.player if hud else null
		view.whole = _whole
		view.custom_minimum_size = Vector2(0, 470)
		view.size_flags_horizontal = Control.SIZE_EXPAND_FILL
		content.add_child(view)
		var legend := HFlowContainer.new()
		content.add_child(legend)
		for t in ["tremie", "scanner", "bras", "pelle", "fonderie", "purif", "presse", "trefileuse", "aiguilleuse", "tampon", "separateur", "drone", "radar", "groupe", "eolienne", "solaire"]:
			if not Game.entities.values().any(func(e: Dictionary) -> bool: return e.type == t):
				continue
			var l := UI.label("■ " + Data.MACHINES[t].name, 17, MapView.color_of(t))
			l.add_theme_constant_override("outline_size", 0)
			legend.add_child(l)
			legend.add_child(UI.label("   ", 17))

	func refresh() -> void:
		if view:
			view.queue_redraw()


class MapView extends Control:
	var player: Node
	var whole := false
	const COLORS := {
		"tremie": Color(0.95, 0.8, 0.2), "scanner": Color(0.3, 0.85, 0.95), "bras": Color(1.0, 0.55, 0.15),
		"pelle": Color(0.95, 0.65, 0.1), "fonderie": Color(0.8, 0.35, 0.25), "purif": Color(0.55, 0.85, 1.0),
		"presse": Color(0.25, 0.45, 0.95), "trefileuse": Color(0.65, 0.45, 0.95), "aiguilleuse": Color(0.95, 0.45, 0.7),
		"tampon": Color(0.6, 0.62, 0.66), "separateur": Color(0.5, 0.65, 0.8), "drone": Color(0.95, 0.95, 0.95),
		"bureau": Color(0.3, 0.8, 0.45), "trou": Color(0.08, 0.08, 0.1),
		"radar": Color(1.0, 0.85, 0.35), "groupe": Color(0.85, 0.62, 0.12), "eolienne": Color(0.95, 0.96, 0.98), "solaire": Color(0.15, 0.25, 0.6),
	}

	static func color_of(t: String) -> Color:
		return COLORS.get(t, Color(0.7, 0.7, 0.7))

	func _bounds() -> Rect2:
		var f := float(Data.FIELD)
		if whole:
			return Rect2(-f, -f, 2 * f, 2 * f)
		var pr := maxf(Game.pile_radius(), 2.0)
		var r := Rect2(Data.PILE_POS.x - pr, Data.PILE_POS.z - pr, pr * 2, pr * 2)
		for e: Dictionary in Game.entities.values():
			r = r.expand(Vector2(e.c.x, e.c.y)).expand(Vector2(e.c.x + 1, e.c.y + 1))
		if player:
			r = r.expand(Vector2(player.global_position.x, player.global_position.z))
		r = r.grow(4.0)
		# au moins 30 m de côté, sans dépasser le terrain
		var c := r.get_center()
		var sz := Vector2(maxf(r.size.x, 30.0), maxf(r.size.y, 30.0))
		r = Rect2(c - sz * 0.5, sz)
		return r.intersection(Rect2(-f - 2, -f - 2, 2 * f + 4, 2 * f + 4))

	func _draw() -> void:
		var b := _bounds()
		var sc := minf(size.x / b.size.x, size.y / b.size.y)
		var off := (size - b.size * sc) * 0.5 - b.position * sc
		var to := func(p: Vector2) -> Vector2: return p * sc + off
		draw_rect(Rect2(Vector2.ZERO, size), Color(0.12, 0.2, 0.13))
		var f := float(Data.FIELD)
		draw_rect(Rect2(to.call(Vector2(-f, -f)), Vector2(2 * f + 1, 2 * f + 1) * sc), Color(0.2, 0.36, 0.2))
		draw_rect(Rect2(to.call(Vector2(-f, -f)), Vector2(2 * f + 1, 2 * f + 1) * sc), Color(0.55, 0.45, 0.3), false, 2.0)
		# tas
		var pc: Vector2 = to.call(Vector2(Data.PILE_POS.x, Data.PILE_POS.z))
		if Game.pile_items() > 0:
			draw_circle(pc, maxf(Game.pile_radius() * sc, 3.0), Color(0.6, 0.62, 0.66))
			draw_arc(pc, maxf(Game.pile_radius() * sc, 3.0), 0, TAU, 48, Color(0.3, 0.3, 0.33), 2.0)
		# tapis d'abord, machines par-dessus
		for pass_belts in [true, false]:
			for e: Dictionary in Game.entities.values():
				var belt: bool = e.type == "convoyeur"
				if belt != pass_belts:
					continue
				var col := Color(0.32, 0.34, 0.38) if belt else color_of(e.type)
				for cell in Game.footprint(e.type, e.c, e.r):
					draw_rect(Rect2(to.call(Vector2(cell.x, cell.y)), Vector2(sc, sc)).grow(-0.5), col)
				if e.type == "trou":
					var cs: Array = Game.footprint(e.type, e.c, e.r)
					var lo: Vector2i = cs[0]
					var hi: Vector2i = cs[0]
					for cell: Vector2i in cs:
						lo = Vector2i(mini(lo.x, cell.x), mini(lo.y, cell.y))
						hi = Vector2i(maxi(hi.x, cell.x), maxi(hi.y, cell.y))
					draw_rect(Rect2(to.call(Vector2(lo)), Vector2(hi - lo + Vector2i.ONE) * sc), UI.GOLD, false, 3.0)
				if belt and sc >= 6.0:
					var d: Vector2i = Data.DIRS[e.r]
					var ctr: Vector2 = to.call(Vector2(e.c) + Vector2(0.5, 0.5))
					var dv := Vector2(d) * sc * 0.3
					draw_line(ctr - dv, ctr + dv, Color(0.95, 0.8, 0.25), maxf(1.0, sc * 0.12))
		# brins repérés par les radars : étoiles dorées
		for sp: Vector3 in Game.revealed_hay():
			var hp: Vector2 = to.call(Vector2(sp.x, sp.z))
			draw_circle(hp, 6.0, Color(1, 0.8, 0.2))
			draw_arc(hp, 9.0, 0, TAU, 16, Color(1, 0.9, 0.4, 0.8), 2.0)
		# joueur : flèche dans le sens du regard
		if player:
			var pp: Vector2 = to.call(Vector2(player.global_position.x, player.global_position.z))
			var fw3: Vector3 = -player.global_transform.basis.z
			var fw := Vector2(fw3.x, fw3.z).normalized()
			var side := Vector2(-fw.y, fw.x)
			var s := 11.0
			draw_colored_polygon(PackedVector2Array([pp + fw * s, pp - fw * s * 0.6 + side * s * 0.7, pp - fw * s * 0.3, pp - fw * s * 0.6 - side * s * 0.7]), Color(1, 0.3, 0.3))
			draw_polyline(PackedVector2Array([pp + fw * s, pp - fw * s * 0.6 + side * s * 0.7, pp - fw * s * 0.3, pp - fw * s * 0.6 - side * s * 0.7, pp + fw * s]), Color.WHITE, 2.0)
