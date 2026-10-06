class_name PileField
extends RefCounted
## Relief du tas : une grille de hauteurs (N × N sommets) centrée sur Data.PILE_POS.
## On y creuse localement (main, bras, pelleteuse, drone) ; le matériau s'éboule ensuite
## quand une pente dépasse l'angle de talus. Le volume restant suit le nombre d'aiguilles.

const N := 80
const SLOPE := 1.8 # pente maximale (≈ 61°) avant éboulement
const GEN_SLOPE := 1.45 # pente des flancs d'un tas neuf (≈ 55°), sous le seuil d'éboulement
const WOBBLE := 1.25 # demi-côté de la grille / rayon nominal (le pied s'étale jusque-là)
const HEIGHT_RATIO := GEN_SLOPE * 0.86 # hauteur approximative du tas neuf / rayon

var h := PackedFloat32Array()
var cell := 0.1 # écart entre deux sommets (m)
var origin := Vector2.ZERO # position (x, z) du sommet (0, 0)
var radius := 1.0 # rayon nominal du tas neuf
var volume := 0.0 # volume total actuel (m³)
var version := 0 # augmente à chaque modification (pour le rendu et la collision)
var slide_amount := 0.0 # volume éboulé depuis la dernière lecture (pour les effets)
var slide_pos := Vector3.ZERO # où l'éboulement a eu lieu (monde)
var _dirty := Rect2i()
var _has_dirty := false
var _noise := FastNoiseLite.new()


## Construit un tas neuf de rayon nominal r, réduit à la fraction `frac` de son volume.
## Comme un vrai tas déversé : un cône principal, quelques cônes secondaires qui s'y appuient,
## des ravines et des bosses, un pied qui s'étale ; puis un éboulement final le rend stable.
func generate(r: float, seed_value: int, frac := 1.0) -> void:
	radius = r
	cell = 2.0 * r * WOBBLE / float(N - 1)
	origin = Vector2(Data.PILE_POS.x, Data.PILE_POS.z) - Vector2.ONE * (N - 1) * 0.5 * cell
	h.resize(N * N)
	h.fill(0.0)
	_noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	_noise.frequency = 1.0
	_noise.seed = seed_value
	var rng := RandomNumberGenerator.new()
	rng.seed = seed_value
	# un tas partiellement vidé garde sa forme, en plus petit
	var rr := r * pow(clampf(frac, 0.0, 1.0), 1.0 / 3.0)
	if rr < 0.05:
		_recount()
		version += 1
		return
	var hmain := rr * HEIGHT_RATIO
	var cones: Array = [[Vector2(rng.randf_range(-0.06, 0.06), rng.randf_range(-0.06, 0.06)) * rr, hmain]]
	for k in rng.randi_range(2, 4):
		var a := rng.randf() * TAU
		cones.append([Vector2(cos(a), sin(a)) * rr * rng.randf_range(0.3, 0.5), hmain * rng.randf_range(0.42, 0.62)])
	var apex := rr * 0.12
	var center := Vector2(Data.PILE_POS.x, Data.PILE_POS.z)
	for j in N:
		for i in N:
			var p := Vector2(i, j) * cell + origin - center
			var dist := p.length()
			var y := 0.0
			for c in cones:
				var dc: float = p.distance_to(c[0])
				y = maxf(y, float(c[1]) - GEN_SLOPE * (sqrt(dc * dc + apex * apex) - apex))
			# ravines et crêtes qui descendent le long des flancs
			var th := atan2(p.y, p.x)
			var gully := _noise.get_noise_2d(cos(th) * 2.2 + 11.0, sin(th) * 2.2) * smoothstep(0.35 * rr, 0.75 * rr, dist)
			y *= 1.0 - 0.04 * gully
			# bosses à petite échelle
			var n := p / rr
			y += rr * 0.015 * (_noise.get_noise_2d(n.x * 6.0, n.y * 6.0) + 0.5 * _noise.get_noise_2d(n.x * 15.0 + 4.0, n.y * 15.0))
			# pied qui s'étale : une couche fine qui se perd dans l'herbe
			var toe := minf(rr * 0.045, 0.3) * exp(-maxf(0.0, dist - rr * 0.78) / minf(0.13 * rr, 2.2))
			toe *= 1.0 - smoothstep(rr * 1.05, rr * WOBBLE * 0.97, dist)
			toe *= 0.9 + 0.2 * (_noise.get_noise_2d(cos(th) * 3.0 - 5.0, sin(th) * 3.0) * 0.5 + 0.5)
			h[j * N + i] = maxf(maxf(y, toe), 0.0)
	# bord de grille toujours vide
	for k in N:
		h[k] = 0.0
		h[(N - 1) * N + k] = 0.0
		h[k * N] = 0.0
		h[k * N + N - 1] = 0.0
	# éboulement final : le tas neuf tient debout tout seul
	_mark(Rect2i(0, 0, N, N))
	for it in 40:
		if not relax(1):
			break
	_has_dirty = false
	slide_amount = 0.0
	_recount()
	version += 1


## Pente maximale entre deux sommets voisins (pour les tests de stabilité).
func max_slope() -> float:
	var m := 0.0
	for j in N:
		for i in N - 1:
			m = maxf(m, absf(h[j * N + i + 1] - h[j * N + i]))
	for j in N - 1:
		for i in N:
			m = maxf(m, absf(h[(j + 1) * N + i] - h[j * N + i]))
	return m / cell


func _recount() -> void:
	var s := 0.0
	for v in h:
		s += v
	volume = s * cell * cell


func clear() -> void:
	h.fill(0.0)
	volume = 0.0
	version += 1


## Multiplie toutes les hauteurs (production hors ligne, sans lieu précis).
func shrink(ratio: float) -> void:
	scale_all(clampf(ratio, 0.0, 1.0))


func scale_all(ratio: float) -> void:
	ratio = maxf(ratio, 0.0)
	for k in h.size():
		h[k] *= ratio
	volume *= ratio
	version += 1


# ============================================================ lecture
func _idx(i: int, j: int) -> int:
	return clampi(j, 0, N - 1) * N + clampi(i, 0, N - 1)


## Hauteur du tas au point (x, z) du monde (interpolée).
func height_at(x: float, z: float) -> float:
	var g := (Vector2(x, z) - origin) / cell
	if g.x < 0.0 or g.y < 0.0 or g.x > N - 1 or g.y > N - 1:
		return 0.0
	var i := mini(int(g.x), N - 2)
	var j := mini(int(g.y), N - 2)
	var fx := g.x - i
	var fz := g.y - j
	var a := lerpf(h[j * N + i], h[j * N + i + 1], fx)
	var b := lerpf(h[(j + 1) * N + i], h[(j + 1) * N + i + 1], fx)
	return lerpf(a, b, fz)


## Volume (m³) dans un disque de rayon rad autour de (x, z).
func volume_in(x: float, z: float, rad: float) -> float:
	var s := 0.0
	var c := (Vector2(x, z) - origin) / cell
	var rc := rad / cell
	for j in range(maxi(0, floori(c.y - rc)), mini(N - 1, ceili(c.y + rc)) + 1):
		for i in range(maxi(0, floori(c.x - rc)), mini(N - 1, ceili(c.x + rc)) + 1):
			if Vector2(i - c.x, j - c.y).length() <= rc:
				s += h[j * N + i]
	return s * cell * cell


## Point le plus haut du tas dans un disque (pour les drones).
func top_near(x: float, z: float, rad: float) -> Vector3:
	var c := (Vector2(x, z) - origin) / cell
	var rc := rad / cell
	var best := -1.0
	var bp := Vector3(x, 0, z)
	for j in range(maxi(0, floori(c.y - rc)), mini(N - 1, ceili(c.y + rc)) + 1, 2):
		for i in range(maxi(0, floori(c.x - rc)), mini(N - 1, ceili(c.x + rc)) + 1, 2):
			var v := h[j * N + i]
			if v > best and Vector2(i - c.x, j - c.y).length() <= rc:
				best = v
				bp = Vector3(origin.x + i * cell, v, origin.y + j * cell)
	return bp


# ============================================================ creuser
## Retire `vol` m³ autour de (x, z) dans un disque de rayon rad, en prenant d'abord le haut.
## Renvoie le volume réellement retiré.
func take(x: float, z: float, rad: float, vol: float) -> float:
	if vol <= 0.0:
		return 0.0
	var c := (Vector2(x, z) - origin) / cell
	var rc := maxf(rad / cell, 1.0)
	var i0 := maxi(0, floori(c.x - rc))
	var i1 := mini(N - 1, ceili(c.x + rc))
	var j0 := maxi(0, floori(c.y - rc))
	var j1 := mini(N - 1, ceili(c.y + rc))
	if i0 > i1 or j0 > j1:
		return 0.0
	var area := cell * cell
	var left := vol / area # en « hauteur × sommets »
	var removed := 0.0
	# deux passes : la seconde reprend ce que la première n'a pas pu enlever
	for pass_i in 2:
		var wsum := 0.0
		for j in range(j0, j1 + 1):
			for i in range(i0, i1 + 1):
				var d := Vector2(i - c.x, j - c.y).length()
				if d <= rc:
					wsum += (1.0 - 0.6 * d / rc) * h[j * N + i]
		if wsum <= 1e-6:
			break
		var k := left / wsum
		for j in range(j0, j1 + 1):
			for i in range(i0, i1 + 1):
				var d := Vector2(i - c.x, j - c.y).length()
				if d <= rc:
					var idx := j * N + i
					var dv := minf(h[idx], k * (1.0 - 0.6 * d / rc) * h[idx])
					h[idx] -= dv
					removed += dv
					left -= dv
		if left <= 1e-6:
			break
	if removed > 0.0:
		volume = maxf(0.0, volume - removed * area)
		_mark(Rect2i(i0, j0, i1 - i0 + 1, j1 - j0 + 1))
		version += 1
	return removed * area


func _mark(r: Rect2i) -> void:
	r = r.grow(1).intersection(Rect2i(0, 0, N, N))
	if _has_dirty:
		_dirty = _dirty.merge(r)
	else:
		_dirty = r
		_has_dirty = true


## Éboulement : là où l'on a creusé, le matériau trop pentu glisse vers le bas.
## À appeler régulièrement ; ne travaille que sur la zone touchée. Renvoie vrai s'il a bougé.
func relax(iterations := 2) -> bool:
	if not _has_dirty:
		return false
	var moved_any := false
	var lim := SLOPE * cell
	var eps := lim + 0.004 # en dessous, on laisse : évite les micro-glissements sans fin
	var a := h # copie locale (plus rapide), réécrite à la fin
	var mv := 0.0
	var sx := 0.0
	var sz := 0.0
	for it in iterations:
		var r := _dirty
		_has_dirty = false
		var x0 := N
		var y0 := N
		var x1 := -1
		var y1 := -1
		for j in range(r.position.y, r.end.y):
			var row := j * N
			for i in range(r.position.x, r.end.x):
				var idx := row + i
				var v := a[idx]
				if v <= 0.0:
					continue
				var moved_here := false
				if i + 1 < N and v - a[idx + 1] > eps:
					var dv := (v - a[idx + 1] - lim) * 0.25
					v -= dv
					a[idx + 1] += dv
					moved_here = true
				if i > 0 and v - a[idx - 1] > eps:
					var dv := (v - a[idx - 1] - lim) * 0.25
					v -= dv
					a[idx - 1] += dv
					moved_here = true
				if j + 1 < N and v - a[idx + N] > eps:
					var dv := (v - a[idx + N] - lim) * 0.25
					v -= dv
					a[idx + N] += dv
					moved_here = true
				if j > 0 and v - a[idx - N] > eps:
					var dv := (v - a[idx - N] - lim) * 0.25
					v -= dv
					a[idx - N] += dv
					moved_here = true
				if moved_here:
					var dm := a[idx] - v
					mv += dm
					sx += i * dm
					sz += j * dm
					a[idx] = v
					x0 = mini(x0, i)
					y0 = mini(y0, j)
					x1 = maxi(x1, i)
					y1 = maxi(y1, j)
		if x1 < 0:
			break
		moved_any = true
		_mark(Rect2i(x0, y0, x1 - x0 + 1, y1 - y0 + 1))
	h = a
	if moved_any:
		version += 1
		if mv > 0.0:
			slide_amount += mv * cell * cell
			var gx := sx / mv
			var gz := sz / mv
			slide_pos = Vector3(origin.x + gx * cell, height_at(origin.x + gx * cell, origin.y + gz * cell), origin.y + gz * cell)
	return moved_any


# ============================================================ sauvegarde
## Hauteurs quantifiées sur 16 bits, en base64 (≈ 25 ko).
func to_save() -> Dictionary:
	var top := 0.0
	for v in h:
		top = maxf(top, v)
	var b := PackedByteArray()
	b.resize(N * N * 2)
	for k in h.size():
		b.encode_u16(k * 2, int(round(h[k] / maxf(top, 1e-6) * 65535.0)))
	return {"n": N, "r": radius, "top": top, "data": Marshalls.raw_to_base64(b)}


func from_save(d: Dictionary, r: float) -> bool:
	if int(d.get("n", 0)) != N or absf(float(d.get("r", 0.0)) - r) > 0.001:
		return false
	var b := Marshalls.base64_to_raw(str(d.get("data", "")))
	if b.size() != N * N * 2:
		return false
	radius = r
	cell = 2.0 * r * WOBBLE / float(N - 1)
	origin = Vector2(Data.PILE_POS.x, Data.PILE_POS.z) - Vector2.ONE * (N - 1) * 0.5 * cell
	h.resize(N * N)
	var top := float(d.get("top", 0.0))
	for k in h.size():
		h[k] = float(b.decode_u16(k * 2)) / 65535.0 * top
	_recount()
	version += 1
	return true
