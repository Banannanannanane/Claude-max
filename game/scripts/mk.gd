class_name Mk
## Petits outils de modélisation procédurale (boîtes, cylindres, matériaux partagés).

static var _mats := {}


static func mat(color: Color, metallic := 0.0, rough := 0.8, emission := Color.BLACK, energy := 0.0) -> StandardMaterial3D:
	var key := "%s|%.2f|%.2f|%s|%.2f" % [color.to_html(), metallic, rough, emission.to_html(), energy]
	if _mats.has(key):
		return _mats[key]
	var m := StandardMaterial3D.new()
	m.albedo_color = color
	m.metallic = metallic
	m.roughness = rough
	if energy > 0.0:
		m.emission_enabled = true
		m.emission = emission
		m.emission_energy_multiplier = energy
	if color.a < 1.0:
		m.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
	_mats[key] = m
	return m


static func box(parent: Node3D, size: Vector3, pos: Vector3, material: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = size
	mi.mesh = bm
	mi.position = pos
	mi.material_override = material
	parent.add_child(mi)
	return mi


static func cyl(parent: Node3D, radius: float, height: float, pos: Vector3, material: Material, top := -1.0, segments := 16) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var cm := CylinderMesh.new()
	cm.top_radius = radius if top < 0.0 else top
	cm.bottom_radius = radius
	cm.height = height
	cm.radial_segments = segments
	cm.rings = 1
	mi.mesh = cm
	mi.position = pos
	mi.material_override = material
	parent.add_child(mi)
	return mi


static func sphere(parent: Node3D, radius: float, pos: Vector3, material: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = radius
	sm.height = radius * 2.0
	sm.radial_segments = 16
	sm.rings = 8
	mi.mesh = sm
	mi.position = pos
	mi.material_override = material
	parent.add_child(mi)
	return mi


static func pivot(parent: Node3D, pos: Vector3) -> Node3D:
	var n := Node3D.new()
	n.position = pos
	parent.add_child(n)
	return n


static func label(parent: Node3D, text: String, pos: Vector3, size := 48, color := Color.WHITE) -> Label3D:
	var l := Label3D.new()
	l.text = text
	l.position = pos
	l.font_size = size
	l.pixel_size = 0.0011
	l.fixed_size = true
	l.modulate = color
	l.outline_size = 10
	l.outline_modulate = Color(0, 0, 0, 0.85)
	l.billboard = BaseMaterial3D.BILLBOARD_ENABLED
	l.no_depth_test = false
	parent.add_child(l)
	return l


## Mesh d'aiguille : un cylindre très fin, pointu d'un côté.
static func needle_mesh(length := 0.55, radius := 0.014) -> CylinderMesh:
	var cm := CylinderMesh.new()
	cm.top_radius = radius * 0.25
	cm.bottom_radius = radius
	cm.height = length
	cm.radial_segments = 5
	cm.rings = 1
	return cm


static func needle_material() -> StandardMaterial3D:
	return mat(Color(0.56, 0.56, 0.57), 0.35, 0.4)


static func hay_material() -> StandardMaterial3D:
	return mat(Color(0.93, 0.76, 0.27), 0.0, 0.7, Color(0.9, 0.7, 0.2), 0.35)


# ============================================================ matériaux « réalistes » (shaders partagés)
const _PAINT := """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 col : source_color = vec3(0.5);
uniform float metal = 0.2;
uniform float rough = 0.55;
uniform float wear = 0.12;
varying vec3 wp;
float h(vec3 p) { return fract(sin(dot(p, vec3(127.1, 311.7, 74.7))) * 43758.5453); }
float n3(vec3 p) {
	vec3 i = floor(p); vec3 f = fract(p); f = f * f * (3.0 - 2.0 * f);
	return mix(mix(mix(h(i), h(i + vec3(1,0,0)), f.x), mix(h(i + vec3(0,1,0)), h(i + vec3(1,1,0)), f.x), f.y),
		mix(mix(h(i + vec3(0,0,1)), h(i + vec3(1,0,1)), f.x), mix(h(i + vec3(0,1,1)), h(i + vec3(1,1,1)), f.x), f.y), f.z);
}
void vertex() { wp = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	float g = n3(wp * 3.0) * 0.6 + n3(wp * 11.0) * 0.4;
	float dirt = smoothstep(0.55, 0.9, n3(wp * 1.7 + 4.0)) * wear;
	ALBEDO = col * (0.9 + 0.18 * g) * (1.0 - dirt * 0.6);
	METALLIC = metal;
	ROUGHNESS = clamp(rough + (g - 0.5) * 0.15 + dirt * 0.3, 0.05, 1.0);
}
"""
const _HAZARD := """
shader_type spatial;
varying vec3 wp;
void vertex() { wp = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	float s = step(0.5, fract((wp.x + wp.z + wp.y) * 2.5));
	ALBEDO = mix(vec3(0.05), vec3(0.95, 0.72, 0.08), s);
	ROUGHNESS = 0.6;
}
"""
const _BRICK := """
shader_type spatial;
uniform vec3 col : source_color = vec3(0.55, 0.26, 0.18);
varying vec3 wp;
varying vec3 wn;
void vertex() { wp = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; wn = abs((MODEL_MATRIX * vec4(NORMAL, 0.0)).xyz); }
float h(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void fragment() {
	vec2 uv = (wn.x > wn.z ? wp.zy : wp.xy) * vec2(4.0, 8.0);
	if (wn.y > 0.7) uv = wp.xz * vec2(4.0, 8.0);
	float row = floor(uv.y);
	uv.x += mod(row, 2.0) * 0.5;
	vec2 f = fract(uv);
	float mortar = step(f.x, 0.06) + step(f.y, 0.1);
	float v = 0.8 + 0.35 * h(floor(uv));
	ALBEDO = mix(col * v, vec3(0.62, 0.6, 0.55), clamp(mortar, 0.0, 1.0));
	ROUGHNESS = 0.95;
}
"""
static var _shader_cache := {}


static func _shader(code: String) -> Shader:
	if not _shader_cache.has(code):
		var s := Shader.new()
		s.code = code
		_shader_cache[code] = s
	return _shader_cache[code]


## Peinture industrielle légèrement usée.
static func paint(color: Color, metallic := 0.2, rough := 0.55, wear := 0.12) -> ShaderMaterial:
	var key := "paint|%s|%.2f|%.2f|%.2f" % [color.to_html(), metallic, rough, wear]
	if _mats.has(key):
		return _mats[key]
	var m := ShaderMaterial.new()
	m.shader = _shader(_PAINT)
	m.set_shader_parameter("col", Vector3(color.r, color.g, color.b))
	m.set_shader_parameter("metal", metallic)
	m.set_shader_parameter("rough", rough)
	m.set_shader_parameter("wear", wear)
	_mats[key] = m
	return m


static func steel() -> ShaderMaterial:
	return paint(Color(0.62, 0.64, 0.67), 0.85, 0.32, 0.05)


static func dark_steel() -> ShaderMaterial:
	return paint(Color(0.2, 0.21, 0.23), 0.6, 0.45, 0.1)


static func hazard() -> ShaderMaterial:
	if not _mats.has("hazard"):
		var m := ShaderMaterial.new()
		m.shader = _shader(_HAZARD)
		_mats["hazard"] = m
	return _mats["hazard"]


static func brick(color := Color(0.55, 0.26, 0.18)) -> ShaderMaterial:
	var key := "brick|" + color.to_html()
	if not _mats.has(key):
		var m := ShaderMaterial.new()
		m.shader = _shader(_BRICK)
		m.set_shader_parameter("col", Vector3(color.r, color.g, color.b))
		_mats[key] = m
	return _mats[key]


static func glass(tint := Color(0.6, 0.8, 0.95, 0.35)) -> StandardMaterial3D:
	return mat(tint, 0.4, 0.05)


static func glow(color: Color, energy := 2.5) -> StandardMaterial3D:
	return mat(color, 0.0, 0.5, color, energy)


static func rubber() -> StandardMaterial3D:
	return mat(Color(0.08, 0.08, 0.09), 0.0, 0.9)


## Cylindre orienté entre deux points (tuyaux, barres).
static func bar(parent: Node3D, a: Vector3, b: Vector3, radius: float, material: Material, segments := 10) -> MeshInstance3D:
	var mi := cyl(parent, radius, a.distance_to(b), (a + b) * 0.5, material, -1.0, segments)
	var d := (b - a).normalized()
	if absf(d.dot(Vector3.UP)) < 0.999:
		mi.basis = Basis(Quaternion(Vector3.UP, d))
	return mi


static func torus(parent: Node3D, inner: float, outer: float, pos: Vector3, material: Material) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var tm := TorusMesh.new()
	tm.inner_radius = inner
	tm.outer_radius = outer
	tm.rings = 24
	tm.ring_segments = 10
	mi.mesh = tm
	mi.position = pos
	mi.material_override = material
	parent.add_child(mi)
	return mi


## Fusionne des maillages primitifs en un seul (pour les MultiMesh).
static func merge(parts: Array) -> ArrayMesh:
	var st := SurfaceTool.new()
	st.begin(Mesh.PRIMITIVE_TRIANGLES)
	for p in parts:
		st.append_from(p[0], 0, p[1])
	return st.commit()
