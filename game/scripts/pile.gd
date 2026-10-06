extends Node3D
## Le tas d'aiguilles, rendu d'après son relief (Game.field) : une grille déformée dans le shader,
## couverte d'une fine texture d'aiguilles et de petites aiguilles qui suivent la surface.
## Chaque poignée, chaque bras ou pelleteuse creuse là où il travaille ; le reste ne bouge pas.

const NEEDLE_LEN := 0.17
const NEEDLE_RAD := 0.006
const NEEDLE_COL := Color(0.43, 0.425, 0.41)

## Fonctions communes : lecture du relief (texture flottante, un texel par sommet de la grille).
const HEIGHT_FN := """
uniform sampler2D hmap : filter_nearest;
uniform float cell = 0.1;
uniform int n = 96;
float H(ivec2 p) { return texelFetch(hmap, clamp(p, ivec2(0), ivec2(n - 1)), 0).r; }
// hauteur interpolée en coordonnées de grille (flottantes)
float Hf(vec2 g) {
	vec2 b = floor(g);
	vec2 f = g - b;
	ivec2 i = ivec2(b);
	float a = mix(H(i), H(i + ivec2(1, 0)), f.x);
	float c = mix(H(i + ivec2(0, 1)), H(i + ivec2(1, 1)), f.x);
	return mix(a, c, f.y);
}
vec3 Nrm(vec2 g) {
	float l = Hf(g - vec2(1.0, 0.0));
	float r = Hf(g + vec2(1.0, 0.0));
	float d = Hf(g - vec2(0.0, 1.0));
	float u = Hf(g + vec2(0.0, 1.0));
	return normalize(vec3(l - r, 2.0 * cell, d - u));
}
"""

const MOUND_SHADER := """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 needle_col : source_color = vec3(0.56, 0.56, 0.57);
uniform float seed = 0.0;
varying vec3 lp;
varying vec3 ln;
varying float vh;
varying float cav;
%s
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
	vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
// une couche d'aiguilles : un trait fin par cellule ; renvoie (présence, teinte, angle)
vec3 layer(vec2 p, float s, float w) {
	vec2 g = p * s;
	vec2 id = floor(g);
	float a = hash(id + seed) * 6.2831;
	vec2 f = fract(g) - 0.5 + (vec2(hash(id + 3.1), hash(id + 5.7)) - 0.5) * 0.45;
	vec2 d = vec2(cos(a), sin(a));
	float across = abs(dot(f, vec2(-d.y, d.x)));
	float along = abs(dot(f, d));
	float m = smoothstep(w, w * 0.25, across) * smoothstep(0.66, 0.52, along);
	// au loin les traits deviennent plus fins que le pixel : on passe à la valeur moyenne
	float fade = smoothstep(1.0, 0.3, length(fwidth(g)));
	return vec3(mix(0.42, m, fade), 0.85 + 0.3 * hash(id + 9.2), a);
}
vec3 needles(vec2 p) {
	vec3 a = layer(p, 6.0, 0.035);
	vec3 b = layer(p + 0.37, 8.5, 0.04);
	vec3 c = layer(p + 0.71, 12.0, 0.045);
	vec3 d = layer(p + 0.13, 17.0, 0.05);
	vec3 best = a;
	if (b.x * 0.95 > best.x) { best = vec3(b.x * 0.95, b.y, b.z); }
	if (c.x * 0.9 > best.x) { best = vec3(c.x * 0.9, c.y, c.z); }
	if (d.x * 0.82 > best.x) { best = vec3(d.x * 0.82, d.y, d.z); }
	return best;
}
void vertex() {
	vec2 g = VERTEX.xz + vec2(float(n - 1) * 0.5);
	ivec2 gi = ivec2(round(g));
	float h = H(gi);
	float l = H(gi - ivec2(1, 0));
	float r = H(gi + ivec2(1, 0));
	float d = H(gi - ivec2(0, 1));
	float u = H(gi + ivec2(0, 1));
	float around = max(max(l, r), max(d, u));
	// hors du tas, la grille passe sous le sol
	float y = (h > 0.0005 || around > 0.0005) ? h : -0.08;
	// le bord rendu (5 cm) affleure au sol : pas de marche visible autour du tas
	y -= 0.12 * (1.0 - smoothstep(0.12, 0.5, h));
	VERTEX = vec3(VERTEX.x * cell, y, VERTEX.z * cell);
	NORMAL = normalize(vec3(l - r, 2.0 * cell, d - u));
	// creux (courbure positive) : moins de lumière y entre
	cav = clamp((l + r + u + d - 4.0 * h) / (cell * 4.0), -1.0, 1.0);
	vh = h;
	lp = VERTEX;
	ln = NORMAL;
}
void fragment() {
	// la couche très fine du pied est rendue par l'épandage au sol (transparent) : pas de bord net ici
	if (vh < 0.12) {
		discard;
	}
	vec3 w = pow(abs(ln), vec3(4.0));
	w /= (w.x + w.y + w.z);
	vec3 nx = needles(lp.zy);
	vec3 ny = needles(lp.xz);
	vec3 nz = needles(lp.xy);
	float m = nx.x * w.x + ny.x * w.y + nz.x * w.z;
	float tint = nx.y * w.x + ny.y * w.y + nz.y * w.z;
	float ang = ny.z * w.y + nx.z * w.x + nz.z * w.z;
	// variations à grande échelle : zones plus claires, plus sombres, un peu rouillées
	float big = vnoise(lp.xz * 0.35 + seed) * 0.6 + vnoise(lp.xz * 1.3 - seed) * 0.4;
	vec3 base = needle_col * mix(0.86, 1.06, big);
	base = mix(base, base * vec3(1.08, 0.97, 0.86), smoothstep(0.62, 0.9, vnoise(lp.xz * 0.6 + 7.0)) * 0.5);
	float ao = mix(0.62, 1.0, smoothstep(0.0, 0.9, vh)) * (1.0 - 0.35 * clamp(cav, 0.0, 1.0));
	vec3 col = mix(base * 0.7, base * tint * 0.97, m) * ao;
	// au pied, la couche est si fine qu'on voit la terre entre les aiguilles
	col = mix(col, vec3(0.30, 0.25, 0.19), (1.0 - smoothstep(0.12, 0.4, vh)) * (1.0 - m) * 0.6);
	ALBEDO = col;
	// lumière renvoyée par le sol et le tas lui-même : l'ombre reste grise, pas bleue
	EMISSION = col * vec3(0.4, 0.37, 0.32);
	// chaque aiguille a sa propre inclinaison : des éclats d'acier qui scintillent selon l'angle
	vec3 tilt = vec3(cos(ang), 0.6 * sin(ang * 3.1), sin(ang));
	vec3 nw = normalize(ln + tilt * 0.55 * m);
	NORMAL = normalize((VIEW_MATRIX * vec4(nw, 0.0)).xyz);
	METALLIC = mix(0.03, 0.12, m);
	ROUGHNESS = mix(0.88, 0.38, m);
	SPECULAR = 0.32;
}
"""

## Épandage au sol : aiguilles tombées et terre tassée autour du pied, qui se perdent dans l'herbe.
const SPILL_SHADER := """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx, depth_draw_never, cull_disabled;
uniform vec3 needle_col : source_color = vec3(0.56, 0.56, 0.57);
uniform float inner = 4.0;
uniform float outer = 7.0;
varying vec3 lp;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
	vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}
float strokes(vec2 p, float s, float w) {
	vec2 g = p * s;
	vec2 id = floor(g);
	float a = hash(id) * 6.2831;
	vec2 f = fract(g) - 0.5;
	vec2 d = vec2(cos(a), sin(a));
	float keep = step(0.45, hash(id + 2.7));
	return keep * smoothstep(w, w * 0.25, abs(dot(f, vec2(-d.y, d.x)))) * smoothstep(0.6, 0.45, abs(dot(f, d)));
}
void vertex() {
	lp = VERTEX;
}
void fragment() {
	float dist = length(lp.xz);
	// densité qui décroît doucement vers l'extérieur, irrégulière mais sans trous
	float wob = (vnoise(lp.xz * 0.25) - 0.5) * (outer - inner) * 0.5;
	float dens = 1.0 - smoothstep(inner, outer + wob, dist);
	dens = dens * dens * (0.8 + 0.2 * vnoise(lp.xz * 1.7));
	float m = max(strokes(lp.xz, 5.5, 0.04), strokes(lp.xz + 0.3, 9.0, 0.05) * 0.8);
	float fade = smoothstep(1.0, 0.3, length(fwidth(lp.xz * 9.0)));
	m = mix(0.3, m, fade);
	vec3 soil = vec3(0.36, 0.31, 0.23) * (0.85 + 0.3 * vnoise(lp.xz * 0.9));
	ALBEDO = mix(soil, needle_col * 0.95, clamp(m * (0.4 + 0.6 * dens), 0.0, 1.0));
	ALPHA = clamp(dens * (0.62 + 0.38 * m), 0.0, 0.95);
	METALLIC = 0.15 * m;
	ROUGHNESS = mix(0.95, 0.35, m);
}
"""

## Les petites aiguilles suivent la surface : on lit la hauteur actuelle sous chacune
## et on la déplace d'autant (ou on la cache si le tas a été creusé jusqu'au sol).
const NEEDLE_SHADER := """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx, cull_disabled;
uniform vec3 needle_col : source_color = vec3(0.56, 0.56, 0.57);
uniform vec3 pile_pos = vec3(0.0);
varying vec3 hn;
varying float tint;
%s
void vertex() {
	vec3 o = MODEL_MATRIX[3].xyz - pile_pos;
	vec2 g = o.xz / cell + vec2(float(n - 1) * 0.5);
	float hnow = Hf(g);
	tint = COLOR.r * mix(0.75, 1.0, smoothstep(0.0, 0.8, hnow));
	hn = Nrm(g);
	if (hnow < 0.03 || g.x < 0.0 || g.y < 0.0 || g.x > float(n - 1) || g.y > float(n - 1)) {
		VERTEX = vec3(0.0);
	} else {
		VERTEX += inverse(mat3(MODEL_MATRIX)) * vec3(0.0, hnow + 0.008 - o.y, 0.0);
	}
}
void fragment() {
	NORMAL = normalize((VIEW_MATRIX * vec4(hn, 0.0)).xyz);
	ALBEDO = needle_col * tint * 0.86;
	METALLIC = 0.3;
	ROUGHNESS = 0.32;
	SPECULAR = 0.5;
}
"""

var _mound: MeshInstance3D
var _mound_mat: ShaderMaterial
var _needles: MultiMeshInstance3D
var _needle_mat: ShaderMaterial
var _scatter: MultiMeshInstance3D
var _hay: MultiMeshInstance3D
var _beacons: MultiMeshInstance3D
var _marks_t := 0.0
var _body: StaticBody3D
var _shape: HeightMapShape3D
var _cs: CollisionShape3D
var _spill: MeshInstance3D
var _spill_mat: ShaderMaterial
var _slide_t := 0.0
var _img: Image
var _tex: ImageTexture
var _built_size := ""
var _built_quality := -1
var _ver := -1
var _shape_ver := -1
var _tex_t := 0.0
var _shape_t := 0.0
var radius := 1.0


func _ready() -> void:
	position = Data.PILE_POS
	# épandage au sol (remplace l'ancien disque de terre)
	_spill = MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(2, 2)
	_spill.mesh = pm
	_spill.position = Vector3(0, 0.018, 0)
	_spill_mat = ShaderMaterial.new()
	var ssh := Shader.new()
	ssh.code = SPILL_SHADER
	_spill_mat.shader = ssh
	_spill_mat.set_shader_parameter("needle_col", NEEDLE_COL)
	_spill.material_override = _spill_mat
	_spill.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_spill)
	var n := PileField.N
	_img = Image.create(n, n, false, Image.FORMAT_RF)
	_tex = ImageTexture.create_from_image(_img)

	_mound = MeshInstance3D.new()
	_mound.mesh = _grid_mesh(n)
	_mound_mat = ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = MOUND_SHADER % HEIGHT_FN
	_mound_mat.shader = sh
	_mound_mat.set_shader_parameter("needle_col", NEEDLE_COL)
	_mound_mat.set_shader_parameter("hmap", _tex)
	_mound_mat.set_shader_parameter("n", n)
	_mound.material_override = _mound_mat
	# le relief bouge dans le shader : boîte englobante large pour ne pas être masqué
	_mound.custom_aabb = AABB(Vector3(-34, -1, -34), Vector3(68, 46, 68))
	add_child(_mound)

	_needle_mat = ShaderMaterial.new()
	var nsh := Shader.new()
	nsh.code = NEEDLE_SHADER % HEIGHT_FN
	_needle_mat.shader = nsh
	_needle_mat.set_shader_parameter("needle_col", NEEDLE_COL)
	_needle_mat.set_shader_parameter("hmap", _tex)
	_needle_mat.set_shader_parameter("n", n)
	_needle_mat.set_shader_parameter("pile_pos", Data.PILE_POS)
	_needles = _needle_layer(self, _needle_mat)
	_needles.visibility_range_end = 45.0
	var flat := Mk.mat(NEEDLE_COL * 0.95, 0.12, 0.5)
	flat.vertex_color_use_as_albedo = true
	_scatter = _needle_layer(self, flat)

	_hay = MultiMeshInstance3D.new()
	var hm := MultiMesh.new()
	hm.transform_format = MultiMesh.TRANSFORM_3D
	hm.mesh = Mk.needle_mesh(0.55, 0.02)
	_hay.multimesh = hm
	_hay.material_override = Mk.hay_material()
	add_child(_hay)
	# balises des radars : colonnes dorées au-dessus des brins repérés
	_beacons = MultiMeshInstance3D.new()
	var bm := MultiMesh.new()
	bm.transform_format = MultiMesh.TRANSFORM_3D
	var col := CylinderMesh.new()
	col.top_radius = 0.02
	col.bottom_radius = 0.07
	col.height = 3.5
	col.radial_segments = 8
	col.rings = 1
	bm.mesh = col
	_beacons.multimesh = bm
	var bmat := StandardMaterial3D.new()
	bmat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
	bmat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	bmat.blend_mode = BaseMaterial3D.BLEND_MODE_ADD
	bmat.albedo_color = Color(1.0, 0.75, 0.2, 0.4)
	bmat.cull_mode = BaseMaterial3D.CULL_DISABLED
	_beacons.material_override = bmat
	_beacons.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	_beacons.custom_aabb = AABB(Vector3(-34, -1, -34), Vector3(68, 46, 68))
	add_child(_beacons)

	_body = StaticBody3D.new()
	_body.collision_layer = 3
	_body.set_meta("kind", "pile")
	_cs = CollisionShape3D.new()
	_shape = HeightMapShape3D.new()
	_shape.map_width = n
	_shape.map_depth = n
	_cs.shape = _shape
	_body.add_child(_cs)
	add_child(_body)

	Game.pile_changed.connect(_refresh)
	_refresh()
	_upload(true)


## Grille plate de n × n sommets en unités de grille (le shader la met à l'échelle et en relief).
func _grid_mesh(n: int) -> ArrayMesh:
	var verts := PackedVector3Array()
	verts.resize(n * n)
	var half := float(n - 1) * 0.5
	for j in n:
		for i in n:
			verts[j * n + i] = Vector3(i - half, 0, j - half)
	var idx := PackedInt32Array()
	for j in n - 1:
		for i in n - 1:
			var a := j * n + i
			idx.append_array([a, a + 1, a + n, a + 1, a + n + 1, a + n])
	var arr := []
	arr.resize(Mesh.ARRAY_MAX)
	arr[Mesh.ARRAY_VERTEX] = verts
	arr[Mesh.ARRAY_INDEX] = idx
	var am := ArrayMesh.new()
	am.add_surface_from_arrays(Mesh.PRIMITIVE_TRIANGLES, arr)
	return am


## Couche de petites aiguilles (prismes fins, sans ombre : invisibles à cette taille).
func _needle_layer(parent: Node3D, material: Material) -> MultiMeshInstance3D:
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
	mm.mesh = cm
	var mi := MultiMeshInstance3D.new()
	mi.multimesh = mm
	mi.material_override = material
	mi.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	mi.custom_aabb = AABB(Vector3(-34, -1, -34), Vector3(68, 46, 68))
	parent.add_child(mi)
	return mi


func _refresh() -> void:
	var base: float = Data.PILES[Game.pile_size].radius
	radius = Game.pile_radius()
	var empty := Game.pile_items() <= 0
	_mound.visible = not empty
	_needles.visible = not empty
	_body.process_mode = Node.PROCESS_MODE_DISABLED if empty else Node.PROCESS_MODE_INHERIT
	var outer := base * 1.15 + 2.5
	(_spill.mesh as PlaneMesh).size = Vector2.ONE * outer * 2.0
	_spill_mat.set_shader_parameter("inner", base * 0.9)
	_spill_mat.set_shader_parameter("outer", outer)
	var q := clampi(int(Game.settings.get("quality", 1)), 0, 2)
	if _built_size != Game.pile_size or _built_quality != q:
		_build_needles(q)
	_update_marks()


## Force la reconstruction (changement de qualité graphique).
func rebuild() -> void:
	_built_size = ""
	_refresh()
	_upload(true)


func _process(delta: float) -> void:
	_tex_t += delta
	_shape_t += delta
	var f: PileField = Game.field
	if f.version != _ver and _tex_t >= 0.1:
		_upload(false)
	if f.version != _shape_ver and _shape_t >= 0.3:
		_update_shape()
	_marks_t += delta
	if _marks_t >= 0.5:
		_update_marks()
	# éboulement : des aiguilles dévalent la pente
	_slide_t -= delta
	if f.slide_amount > 0.0 and _slide_t <= 0.0:
		if f.slide_amount > 0.01:
			_slide_fx(f.slide_pos, f.slide_amount)
		f.slide_amount = 0.0
		_slide_t = 0.25


func _slide_fx(at: Vector3, vol: float) -> void:
	var f: PileField = Game.field
	var n := _normal_at(f, at.x, at.z)
	var down := Vector3(n.x, -0.4, n.z).normalized()
	var p := CPUParticles3D.new()
	p.one_shot = true
	p.explosiveness = 0.6
	p.amount = clampi(int(vol * 60.0), 6, 40)
	p.lifetime = 1.1
	p.direction = down
	p.spread = 25.0
	p.initial_velocity_min = 0.8
	p.initial_velocity_max = 2.2
	p.gravity = Vector3(0, -6, 0)
	p.emission_shape = CPUParticles3D.EMISSION_SHAPE_SPHERE
	p.emission_sphere_radius = clampf(f.cell * 2.0, 0.2, 1.2)
	p.particle_flag_align_y = true
	var m := BoxMesh.new()
	m.size = Vector3(0.008, 0.2, 0.008)
	p.mesh = m
	p.material_override = Mk.mat(NEEDLE_COL * 1.1, 0.4, 0.35)
	p.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	p.position = at - Data.PILE_POS + Vector3(0, 0.1, 0)
	add_child(p)
	p.emitting = true
	get_tree().create_timer(1.6).timeout.connect(p.queue_free)


## Brins de foin visibles : ceux que le creusage a mis à nu (posés sur la surface)
## et ceux qu'un radar a repérés (balise dorée au-dessus, même s'ils sont enfouis).
func _update_marks() -> void:
	_marks_t = 0.0
	var f: PileField = Game.field
	var hm := _hay.multimesh
	var exposed: Array = []
	for sp: Vector3 in Game.hay_spots:
		var surf := f.height_at(sp.x, sp.z)
		if sp.y > surf - 0.05:
			exposed.append(Vector3(sp.x, surf, sp.z))
	hm.instance_count = exposed.size()
	for i in exposed.size():
		var e: Vector3 = exposed[i]
		var b := Basis(Vector3.UP, float(i) * 2.1) * Basis(Vector3.RIGHT, 1.25)
		hm.set_instance_transform(i, Transform3D(b, e - Data.PILE_POS + Vector3(0, 0.04, 0)))
	_hay.visible = exposed.size() > 0
	var rev: Array = Game.revealed_hay()
	var bm := _beacons.multimesh
	bm.instance_count = rev.size()
	for i in rev.size():
		var sp2: Vector3 = rev[i]
		var top := f.height_at(sp2.x, sp2.z)
		bm.set_instance_transform(i, Transform3D(Basis(), Vector3(sp2.x, top + 1.75, sp2.z) - Data.PILE_POS))
	_beacons.visible = rev.size() > 0


## Envoie le relief au GPU (texture) et replace les brins de foin visibles.
func _upload(force: bool) -> void:
	var f: PileField = Game.field
	if not force and f.version == _ver:
		return
	_ver = f.version
	_tex_t = 0.0
	_img.set_data(PileField.N, PileField.N, false, Image.FORMAT_RF, f.h.to_byte_array())
	_tex.update(_img)
	_mound_mat.set_shader_parameter("cell", f.cell)
	_needle_mat.set_shader_parameter("cell", f.cell)
	if force:
		_update_shape()


func _update_shape() -> void:
	var f: PileField = Game.field
	_shape_ver = f.version
	_shape_t = 0.0
	var data := PackedFloat32Array()
	data.resize(f.h.size())
	var inv := 1.0 / f.cell
	for k in f.h.size():
		data[k] = f.h[k] * inv
	_shape.map_data = data
	_cs.scale = Vector3.ONE * f.cell


func _build_needles(q: int) -> void:
	_built_size = Game.pile_size
	_built_quality = q
	var f: PileField = Game.field
	var r := float(Data.PILES[Game.pile_size].radius)
	var rng := RandomNumberGenerator.new()
	rng.seed = hash(Game.pile_size)
	_mound_mat.set_shader_parameter("seed", float(hash(Game.pile_size) % 100))
	# petites aiguilles posées sur le relief actuel (elles suivent ensuite la surface dans le shader)
	var dens: float = [0.35, 0.65, 1.0][q]
	var hh := r * PileField.HEIGHT_RATIO
	var area := PI * r * sqrt(r * r + hh * hh)
	var count := clampi(int(area * 110.0 * dens), int(2500 * dens), int(16000 * dens))
	var buf := PackedFloat32Array()
	buf.resize(count * 16)
	var o := 0
	var placed := 0
	var tries := 0
	while placed < count and tries < count * 4:
		tries += 1
		var a := rng.randf() * TAU
		var d := r * PileField.WOBBLE * sqrt(rng.randf())
		var x := cos(a) * d
		var z := sin(a) * d
		var y := f.height_at(x + Data.PILE_POS.x, z + Data.PILE_POS.z)
		if y < 0.03:
			continue
		var n := _normal_at(f, x + Data.PILE_POS.x, z + Data.PILE_POS.z)
		var side := n.cross(Vector3(rng.randf_range(-1, 1), rng.randf_range(-1, 1), rng.randf_range(-1, 1)))
		if side.length_squared() < 0.0001:
			side = n.cross(Vector3.RIGHT)
		side = side.normalized()
		# couchées sur la pente, quelques-unes dépassent un peu
		var tilt := rng.randf_range(-0.12, 0.12) if rng.randf() < 0.85 else rng.randf_range(0.25, 0.55)
		var bs := Basis(Quaternion(Vector3.UP, side.lerp(n, tilt).normalized()))
		o = _put(buf, o, Transform3D(bs, Vector3(x, y, z)), Color(rng.randf_range(0.75, 1.1), 1, 1, 1))
		placed += 1
	buf.resize(placed * 16)
	var mm := _needles.multimesh
	mm.instance_count = placed
	if placed > 0:
		mm.buffer = buf

	# aiguilles tombées au sol autour du tas
	var ns := clampi(int(90.0 * r), 200, 1600)
	var sb := PackedFloat32Array()
	sb.resize(ns * 16)
	o = 0
	for k in ns:
		var an := rng.randf() * TAU
		var dd := r * (0.85 + pow(rng.randf(), 2.0) * 0.5)
		var fl := Basis(Vector3.UP, rng.randf() * TAU) * Basis(Vector3.FORWARD, PI / 2 + rng.randf_range(-0.08, 0.08))
		var col := NEEDLE_COL * rng.randf_range(0.75, 1.1)
		o = _put(sb, o, Transform3D(fl, Vector3(cos(an) * dd, 0.012, sin(an) * dd)), col)
	var sm := _scatter.multimesh
	sm.instance_count = ns
	sm.buffer = sb

	_upload(true)


func _normal_at(f: PileField, x: float, z: float) -> Vector3:
	var e := f.cell
	var n := Vector3(f.height_at(x - e, z) - f.height_at(x + e, z), 2.0 * e, f.height_at(x, z - e) - f.height_at(x, z + e))
	return n.normalized()


## Écrit une instance (transformation 3x4 puis couleur) dans le tampon d'un MultiMesh.
func _put(buf: PackedFloat32Array, o: int, tr: Transform3D, c: Color) -> int:
	var bs := tr.basis
	buf[o] = bs.x.x; buf[o + 1] = bs.y.x; buf[o + 2] = bs.z.x; buf[o + 3] = tr.origin.x
	buf[o + 4] = bs.x.y; buf[o + 5] = bs.y.y; buf[o + 6] = bs.z.y; buf[o + 7] = tr.origin.y
	buf[o + 8] = bs.x.z; buf[o + 9] = bs.y.z; buf[o + 10] = bs.z.z; buf[o + 11] = tr.origin.z
	buf[o + 12] = c.r; buf[o + 13] = c.g; buf[o + 14] = c.b; buf[o + 15] = c.a
	return o + 16


## Point de la surface le plus proche d'une position (pour viser les machines).
func surface_toward(from: Vector3) -> Vector3:
	var d := from - global_position
	d.y = 0
	if d.length() < 0.01:
		d = Vector3(1, 0, 0)
	var p := global_position + d.normalized() * radius * 0.8
	p.y = Game.field.height_at(p.x, p.z)
	return p
