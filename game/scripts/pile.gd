extends Node3D
## Le tas d'aiguilles : un monticule haut et bosselé, couvert d'une fine texture d'aiguilles,
## avec de petites aiguilles posées dessus qui se fondent dans la texture. Il rétrécit quand on le vide.

const HEIGHT_RATIO := 1.0      # hauteur / rayon : un vrai tas, pas une demi-sphère
const RINGS := 30
const SEGS := 72
const NEEDLE_LEN := 0.22
const NEEDLE_RAD := 0.0075
const NEEDLE_COL := Color(0.56, 0.56, 0.57)

const MOUND_SHADER := """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 needle_col : source_color = vec3(0.56, 0.56, 0.57);
uniform float seed = 0.0;
varying vec3 lp;
varying vec3 ln;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
// une couche d'aiguilles : un trait fin par cellule, orientation et teinte aléatoires
vec2 layer(vec2 p, float s, float w) {
	vec2 g = p * s;
	vec2 id = floor(g);
	float a = hash(id + seed) * 6.2831;
	vec2 f = fract(g) - 0.5 + (vec2(hash(id + 3.1), hash(id + 5.7)) - 0.5) * 0.5;
	vec2 d = vec2(cos(a), sin(a));
	float across = abs(dot(f, vec2(-d.y, d.x)));
	float along = abs(dot(f, d));
	float m = smoothstep(w, w * 0.3, across) * smoothstep(0.62, 0.5, along);
	// au loin les traits deviennent plus fins que le pixel : on passe à la teinte moyenne
	float fw = length(fwidth(g));
	float fade = smoothstep(0.9, 0.25, fw);
	return vec2(mix(0.5, m, fade), 0.75 + 0.35 * hash(id + 9.2));
}
vec2 needles(vec2 p) {
	vec2 a = layer(p, 4.6, 0.045);
	vec2 b = layer(p + 0.37, 5.9, 0.05);
	vec2 c = layer(p + 0.71, 8.3, 0.06);
	vec2 d = layer(p + 0.13, 12.5, 0.07);
	float m = max(max(a.x, b.x * 0.95), max(c.x * 0.88, d.x * 0.75));
	float tint = a.x >= b.x ? a.y : b.y;
	return vec2(m, tint);
}
void vertex() {
	lp = VERTEX;
	ln = NORMAL;
}
void fragment() {
	vec3 w = pow(abs(ln), vec3(4.0));
	w /= (w.x + w.y + w.z);
	vec2 nx = needles(lp.zy);
	vec2 ny = needles(lp.xz);
	vec2 nz = needles(lp.xy);
	float m = nx.x * w.x + ny.x * w.y + nz.x * w.z;
	float tint = nx.y * w.x + ny.y * w.y + nz.y * w.z;
	// creux entre les aiguilles : sombres ; aiguilles : même teinte que les vraies
	vec3 gap = needle_col * 0.36;
	vec3 col = mix(gap, needle_col * tint, m) * COLOR.r;
	ALBEDO = col;
	METALLIC = mix(0.15, 0.25, m);
	ROUGHNESS = mix(0.8, 0.4, m);
	SPECULAR = 0.5;
}
"""

## Les petites aiguilles sont éclairées avec la normale du tas sous elles (INSTANCE_CUSTOM) :
## même teinte, même reflet que la texture, on ne voit pas où finit le décor.
const NEEDLE_SHADER := """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx, cull_disabled;
uniform vec3 needle_col : source_color = vec3(0.56, 0.56, 0.57);
varying vec3 hn;
varying float tint;
varying float ao;
void vertex() {
	hn = INSTANCE_CUSTOM.xyz * 2.0 - 1.0;
	tint = COLOR.r;
	ao = COLOR.g;
}
void fragment() {
	NORMAL = normalize((VIEW_MATRIX * vec4(normalize(hn), 0.0)).xyz);
	ALBEDO = needle_col * tint * ao * 0.92;
	METALLIC = 0.25;
	ROUGHNESS = 0.4;
	SPECULAR = 0.5;
}
"""

var _heap: Node3D
var _mound: MeshInstance3D
var _needles: MultiMeshInstance3D
var _scatter: MultiMeshInstance3D
var _hay: MultiMeshInstance3D
var _body: StaticBody3D
var _shape: ConvexPolygonShape3D
var _dirt: MeshInstance3D
var _noise := FastNoiseLite.new()
var _built_radius := -1.0
var _built_size := ""
var _R := 1.0
var _H := 1.0
var radius := 1.0


func _ready() -> void:
	position = Data.PILE_POS
	_dirt = Mk.cyl(self, 1.0, 0.04, Vector3(0, 0.01, 0), Mk.mat(Color(0.36, 0.29, 0.2), 0.0, 1.0), -1.0, 48)
	_noise.noise_type = FastNoiseLite.TYPE_SIMPLEX_SMOOTH
	_noise.frequency = 1.0

	_heap = Node3D.new()
	add_child(_heap)

	_mound = MeshInstance3D.new()
	var shm := ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = MOUND_SHADER
	shm.shader = sh
	shm.set_shader_parameter("needle_col", NEEDLE_COL)
	_mound.material_override = shm
	_heap.add_child(_mound)

	_needles = _needle_layer(_heap)
	_needles.visibility_range_end = 45.0
	_scatter = _needle_layer(self)

	_hay = MultiMeshInstance3D.new()
	var hm := MultiMesh.new()
	hm.transform_format = MultiMesh.TRANSFORM_3D
	hm.mesh = Mk.needle_mesh(0.55, 0.02)
	_hay.multimesh = hm
	_hay.material_override = Mk.hay_material()
	_heap.add_child(_hay)

	_body = StaticBody3D.new()
	_body.collision_layer = 3
	_body.set_meta("kind", "pile")
	var cs := CollisionShape3D.new()
	_shape = ConvexPolygonShape3D.new()
	# enveloppe provisoire : une forme vide ne peut pas être construite par le moteur physique
	_shape.points = PackedVector3Array([Vector3(-1, 0, -1), Vector3(1, 0, -1), Vector3(0, 0, 1), Vector3(0, 1, 0)])
	cs.shape = _shape
	_body.add_child(cs)
	add_child(_body)

	Game.pile_changed.connect(_refresh)
	_refresh()


## Couche de petites aiguilles (prismes fins, sans ombre : invisibles à cette taille).
func _needle_layer(parent: Node3D) -> MultiMeshInstance3D:
	var cm := CylinderMesh.new()
	cm.top_radius = NEEDLE_RAD * 0.2
	cm.bottom_radius = NEEDLE_RAD
	cm.height = NEEDLE_LEN
	cm.radial_segments = 3
	cm.rings = 0
	cm.cap_top = false
	cm.cap_bottom = false
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.use_colors = true
	mm.use_custom_data = true
	mm.mesh = cm
	var m := ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = NEEDLE_SHADER
	m.shader = sh
	m.set_shader_parameter("needle_col", NEEDLE_COL)
	var mi := MultiMeshInstance3D.new()
	mi.multimesh = mm
	mi.material_override = m
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	parent.add_child(mi)
	return mi


func _refresh() -> void:
	var base: float = Data.PILES[Game.pile_size].radius
	radius = Game.pile_radius()
	var empty := Game.pile_items() <= 0
	_heap.visible = not empty
	_body.process_mode = Node.PROCESS_MODE_DISABLED if empty else Node.PROCESS_MODE_INHERIT
	_dirt.scale = Vector3(base + 2.0, 1, base + 2.0)
	if empty:
		return
	var r := maxf(radius, 0.35)
	# reconstruction seulement quand la taille a vraiment changé ; entre-temps on met à l'échelle
	if _built_size != Game.pile_size or absf(r - _built_radius) > maxf(0.06, _built_radius * 0.1):
		_build(r)
	var k := r / _built_radius
	_heap.scale = Vector3.ONE * k
	_update_shape(k)
	_hay.visible = Game.pile_h > 0
	if _hay.visible:
		_hay.multimesh.visible_instance_count = mini(Game.pile_h, 3)


## Profil du tas : sommet arrondi, flancs raides, pied évasé ; plus des bosses.
## th : angle autour du tas, t : 0 au sommet, 1 au pied.
func _surf(th: float, t: float) -> Vector3:
	var wob := 1.0 + 0.08 * _noise.get_noise_2d(cos(th) * 1.6, sin(th) * 1.6)
	var rr := t * _R * wob
	var x := cos(th) * rr
	var z := sin(th) * rr
	var y := _H * pow(maxf(0.0, 1.0 - pow(t, 1.7)), 1.25)
	var nx := x / _R
	var nz := z / _R
	var lump := _noise.get_noise_2d(nx * 3.2 + 7.0, nz * 3.2) * 0.65 + _noise.get_noise_2d(nx * 8.0, nz * 8.0 - 3.0) * 0.25 + _noise.get_noise_2d(nx * 19.0, nz * 19.0) * 0.1
	y += _R * 0.085 * lump * (1.0 - pow(t, 4.0))
	return Vector3(x, maxf(y, 0.0), z)


func _surf_normal(th: float, t: float) -> Vector3:
	var tt := clampf(t, 0.02, 0.98)
	var e := 0.01
	var a := _surf(th, tt + e) - _surf(th, tt - e)
	var b := _surf(th + e, tt) - _surf(th - e, tt)
	var n := b.cross(a).normalized()
	if n.y < 0.0:
		n = -n
	return n


func _build(r: float) -> void:
	_built_radius = r
	_built_size = Game.pile_size
	_R = r
	_H = r * HEIGHT_RATIO
	var sd := hash(Game.pile_size)
	_noise.seed = sd
	var rng := RandomNumberGenerator.new()
	rng.seed = sd
	(_mound.material_override as ShaderMaterial).set_shader_parameter("seed", float(sd % 100))

	# grille de la surface (sommet = anneau 0) : sert au maillage et à poser les aiguilles
	var gp := PackedVector3Array()
	var gn := PackedVector3Array()
	var ga := PackedFloat32Array()
	gp.resize((RINGS + 1) * SEGS)
	gn.resize((RINGS + 1) * SEGS)
	ga.resize((RINGS + 1) * SEGS)
	for i in RINGS + 1:
		var t := float(i) / RINGS
		for j in SEGS:
			var q := _surf(float(j) / SEGS * TAU, t)
			gp[i * SEGS + j] = q
			ga[i * SEGS + j] = _ao(q, t)
	for i in RINGS + 1:
		for j in SEGS:
			if i == 0:
				gn[j] = Vector3.UP
				continue
			var up := gp[(i - 1) * SEGS + j]
			var dn := gp[mini(i + 1, RINGS) * SEGS + j]
			var lf := gp[i * SEGS + (j + SEGS - 1) % SEGS]
			var rt := gp[i * SEGS + (j + 1) % SEGS]
			var n := (rt - lf).cross(dn - up).normalized()
			if n.y < 0.0:
				n = -n
			gn[i * SEGS + j] = n
	_mound.mesh = _build_mesh(gp, gn, ga)

	# petites aiguilles posées sur la surface, réparties selon l'aire ; écrites d'un bloc
	var area := PI * r * sqrt(r * r + _H * _H)
	var count := clampi(int(area * 110.0), 2500, 16000)
	var buf := PackedFloat32Array()
	buf.resize(count * 20)
	var o := 0
	for k in count:
		var fi := sqrt(rng.randf()) * 0.98 * RINGS
		var fj := rng.randf() * SEGS
		var i0 := int(fi)
		var j0 := int(fj) % SEGS
		var j1 := (j0 + 1) % SEGS
		var f := fi - i0
		var g := fj - floorf(fj)
		var a0 := i0 * SEGS
		var a1 := mini(i0 + 1, RINGS) * SEGS
		var p := gp[a0 + j0].lerp(gp[a0 + j1], g).lerp(gp[a1 + j0].lerp(gp[a1 + j1], g), f)
		var n := gn[a0 + j0].lerp(gn[a0 + j1], g).lerp(gn[a1 + j0].lerp(gn[a1 + j1], g), f).normalized()
		var ao := lerpf(ga[a0 + j0], ga[a1 + j0], f)
		var side := n.cross(Vector3(rng.randf_range(-1, 1), rng.randf_range(-1, 1), rng.randf_range(-1, 1)))
		if side.length_squared() < 0.0001:
			side = n.cross(Vector3.RIGHT)
		side = side.normalized()
		# couchées sur la pente, quelques-unes dépassent un peu
		var tilt := rng.randf_range(-0.12, 0.12) if rng.randf() < 0.85 else rng.randf_range(0.25, 0.55)
		var bs := Basis(Quaternion(Vector3.UP, side.lerp(n, tilt).normalized()))
		o = _put(buf, o, Transform3D(bs, p + n * 0.01), Color(rng.randf_range(0.75, 1.1), ao, 0, 1), _pack(n))
	var mm := _needles.multimesh
	mm.instance_count = count
	mm.buffer = buf

	# aiguilles tombées au sol autour du tas
	var ns := clampi(int(90.0 * r), 200, 1200)
	var sb := PackedFloat32Array()
	sb.resize(ns * 20)
	o = 0
	var flat_n := _pack(Vector3.UP)
	for k in ns:
		var an := rng.randf() * TAU
		var d := r * (1.0 + 0.08 * _noise.get_noise_2d(cos(an) * 1.6, sin(an) * 1.6)) * (0.97 + pow(rng.randf(), 2.0) * 0.3)
		var flat := Basis(Vector3.UP, rng.randf() * TAU) * Basis(Vector3.FORWARD, PI / 2 + rng.randf_range(-0.08, 0.08))
		o = _put(sb, o, Transform3D(flat, Vector3(cos(an) * d, 0.012, sin(an) * d)), Color(rng.randf_range(0.75, 1.1), 0.8, 0, 1), flat_n)
	var sm := _scatter.multimesh
	sm.instance_count = ns
	sm.buffer = sb

	# quelques brins de foin qui dépassent : un indice qu'il en reste
	var hm := _hay.multimesh
	hm.instance_count = 3
	for k in 3:
		var th2 := rng.randf() * TAU
		var t2 := rng.randf_range(0.3, 0.8)
		var p2 := _surf(th2, t2)
		var n2 := _surf_normal(th2, t2)
		var side2 := n2.cross(Vector3.UP).normalized()
		var b2 := Basis(Quaternion(Vector3.UP, side2.lerp(n2, 0.45).normalized()))
		hm.set_instance_transform(k, Transform3D(b2, p2 + n2 * 0.03))


## Écrit une instance (transformation 3x4, couleur, données perso) dans le tampon d'un MultiMesh.
func _put(buf: PackedFloat32Array, o: int, tr: Transform3D, c: Color, cd: Color) -> int:
	var bs := tr.basis
	buf[o] = bs.x.x; buf[o + 1] = bs.y.x; buf[o + 2] = bs.z.x; buf[o + 3] = tr.origin.x
	buf[o + 4] = bs.x.y; buf[o + 5] = bs.y.y; buf[o + 6] = bs.z.y; buf[o + 7] = tr.origin.y
	buf[o + 8] = bs.x.z; buf[o + 9] = bs.y.z; buf[o + 10] = bs.z.z; buf[o + 11] = tr.origin.z
	buf[o + 12] = c.r; buf[o + 13] = c.g; buf[o + 14] = c.b; buf[o + 15] = c.a
	buf[o + 16] = cd.r; buf[o + 17] = cd.g; buf[o + 18] = cd.b; buf[o + 19] = cd.a
	return o + 20


## Assombrissement au pied et dans les creux (partagé par la surface et les aiguilles).
func _ao(p: Vector3, t: float) -> float:
	var cav := 0.5 + 0.5 * clampf(_noise.get_noise_2d(p.x * 10.0 / _R, p.z * 10.0 / _R) + 0.3, 0.0, 1.0)
	return lerpf(1.0, 0.75, smoothstep(0.75, 1.0, t)) * lerpf(0.85, 1.0, cav)


func _pack(n: Vector3) -> Color:
	return Color(n.x * 0.5 + 0.5, n.y * 0.5 + 0.5, n.z * 0.5 + 0.5, 1.0)


func _build_mesh(gp: PackedVector3Array, gn: PackedVector3Array, ga: PackedFloat32Array) -> ArrayMesh:
	# sommet : un seul sommet au centre, puis les anneaux
	var verts := PackedVector3Array([gp[0]])
	var norms := PackedVector3Array([Vector3.UP])
	var cols := PackedColorArray([Color(1, 1, 1)])
	for i in range(1, RINGS + 1):
		for j in SEGS:
			var a := ga[i * SEGS + j]
			verts.append(gp[i * SEGS + j])
			norms.append(gn[i * SEGS + j])
			cols.append(Color(a, a, a))
	var idx := PackedInt32Array()
	for j in SEGS:
		var j2 := (j + 1) % SEGS
		idx.append_array([0, 1 + j, 1 + j2])
	for i in range(1, RINGS):
		var a0 := 1 + (i - 1) * SEGS
		var b0 := 1 + i * SEGS
		for j in SEGS:
			var j2 := (j + 1) % SEGS
			idx.append_array([a0 + j, b0 + j, a0 + j2, a0 + j2, b0 + j, b0 + j2])
	var arr := []
	arr.resize(Mesh.ARRAY_MAX)
	arr[Mesh.ARRAY_VERTEX] = verts
	arr[Mesh.ARRAY_NORMAL] = norms
	arr[Mesh.ARRAY_COLOR] = cols
	arr[Mesh.ARRAY_INDEX] = idx
	var am := ArrayMesh.new()
	am.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr)
	return am


## Collision : enveloppe convexe un peu plus étroite que le pied évasé (on ne grimpe pas dessus).
func _update_shape(k: float) -> void:
	var pts := PackedVector3Array()
	pts.append(_surf(0.0, 0.0) * k)
	for t in [0.3, 0.55, 0.75, 0.88]:
		for j in 16:
			pts.append(_surf(float(j) / 16.0 * TAU, t) * k)
	for j in 16:
		var th := float(j) / 16.0 * TAU
		pts.append(Vector3(cos(th), 0.0, sin(th)) * _R * 0.9 * k)
	_shape.points = pts


## Point de la surface le plus proche d'une position (pour viser les machines).
func surface_toward(from: Vector3) -> Vector3:
	var d := from - global_position
	d.y = 0
	if d.length() < 0.01:
		d = Vector3(1, 0, 0)
	var th := atan2(d.z, d.x)
	return global_position + _surf(th, 0.8) * (radius / maxf(_built_radius, 0.01))
