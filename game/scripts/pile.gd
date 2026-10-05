extends Node3D
## Le tas d'aiguilles : un monticule métallique hérissé d'aiguilles, qui rétrécit quand on le vide.

const MOUND_SHADER := """
shader_type spatial;
render_mode diffuse_burley, specular_schlick_ggx;
uniform vec3 base_col : source_color = vec3(0.52, 0.52, 0.53);
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float streaks(vec2 uv, float s) {
	vec2 g = uv * s;
	vec2 id = floor(g);
	float a = hash(id) * 6.2831;
	vec2 f = fract(g) - 0.5;
	vec2 d = vec2(cos(a), sin(a));
	float l = abs(dot(f, vec2(-d.y, d.x)));
	float along = abs(dot(f, d));
	return smoothstep(0.07, 0.0, l) * step(along, 0.48) * (0.5 + 0.5 * hash(id + 7.3));
}
void fragment() {
	float s1 = streaks(UV * vec2(2.0, 1.0), 70.0);
	float s2 = streaks(UV * vec2(2.0, 1.0) + 0.37, 45.0);
	float s = max(s1, s2 * 0.8);
	ALBEDO = base_col * (0.35 + 0.85 * s);
	METALLIC = 0.6;
	ROUGHNESS = 0.55 - 0.3 * s;
}
"""

var _mound: MeshInstance3D
var _needles: MultiMeshInstance3D
var _hay: MultiMeshInstance3D
var _body: StaticBody3D
var _shape: CylinderShape3D
var _dirt: MeshInstance3D
var _built_radius := -1.0
var _built_size := ""
var radius := 1.0


func _ready() -> void:
	position = Data.PILE_POS
	_dirt = Mk.cyl(self, 1.0, 0.04, Vector3(0, 0.01, 0), Mk.mat(Color(0.36, 0.29, 0.2), 0.0, 1.0), -1.0, 48)

	_mound = MeshInstance3D.new()
	var sm := SphereMesh.new()
	sm.radius = 1.0
	sm.height = 1.0
	sm.is_hemisphere = true
	sm.radial_segments = 48
	sm.rings = 16
	_mound.mesh = sm
	var shm := ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = MOUND_SHADER
	shm.shader = sh
	_mound.material_override = shm
	add_child(_mound)

	_needles = MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = Mk.needle_mesh()
	_needles.multimesh = mm
	_needles.material_override = Mk.needle_material()
	add_child(_needles)

	_hay = MultiMeshInstance3D.new()
	var hm := MultiMesh.new()
	hm.transform_format = MultiMesh.TRANSFORM_3D
	hm.mesh = Mk.needle_mesh(0.7, 0.022)
	_hay.multimesh = hm
	_hay.material_override = Mk.hay_material()
	add_child(_hay)

	_body = StaticBody3D.new()
	_body.collision_layer = 3
	_body.set_meta("kind", "pile")
	var cs := CollisionShape3D.new()
	_shape = CylinderShape3D.new()
	cs.shape = _shape
	_body.add_child(cs)
	add_child(_body)

	Game.pile_changed.connect(_refresh)
	_refresh()


func _refresh() -> void:
	var base: float = Data.PILES[Game.pile_size].radius
	radius = Game.pile_radius()
	var empty := Game.pile_items() <= 0
	_mound.visible = not empty
	_needles.visible = not empty
	_body.process_mode = Node.PROCESS_MODE_DISABLED if empty else Node.PROCESS_MODE_INHERIT
	_dirt.scale = Vector3(base + 2.0, 1, base + 2.0)
	if empty:
		_hay.visible = false
		return
	var r := maxf(radius, 0.35)
	var h := r * 0.62
	_mound.scale = Vector3(r, h, r)
	_shape.radius = r * 0.92
	_shape.height = h * 2.0
	# recalcul des aiguilles seulement quand la taille a vraiment changé
	if _built_size != Game.pile_size or absf(r - _built_radius) > maxf(0.06, _built_radius * 0.035):
		_build_needles(r, h)
	_hay.visible = Game.pile_h > 0
	if _hay.visible:
		_hay.multimesh.visible_instance_count = mini(Game.pile_h, 3)


func _surface_point(r: float, h: float, rng: RandomNumberGenerator) -> Vector3:
	var th := rng.randf() * TAU
	var y := pow(rng.randf(), 0.75)
	var s := sqrt(maxf(0.0, 1.0 - y * y))
	var k := rng.randf_range(0.97, 1.03)
	return Vector3(cos(th) * s * r * k, y * h * k, sin(th) * s * r * k)


func _build_needles(r: float, h: float) -> void:
	_built_radius = r
	_built_size = Game.pile_size
	var rng := RandomNumberGenerator.new()
	rng.seed = hash(Game.pile_size)
	var count := clampi(int(150.0 * r * r), 250, 5000)
	var mm := _needles.multimesh
	mm.instance_count = count
	for i in count:
		var p := _surface_point(r, h, rng)
		var axis := Vector3(rng.randf_range(-1, 1), rng.randf_range(-0.4, 1), rng.randf_range(-1, 1)).normalized()
		var b := Basis(axis, rng.randf() * TAU) * Basis(Vector3.RIGHT, rng.randf_range(0.4, 2.7))
		mm.set_instance_transform(i, Transform3D(b, p))
	# quelques brins de foin qui dépassent : un indice qu'il en reste
	var hm := _hay.multimesh
	hm.instance_count = 3
	for i in 3:
		var p2 := _surface_point(r, h, rng) * 1.01
		var b2 := Basis(Vector3(rng.randf_range(-1, 1), 0.3, rng.randf_range(-1, 1)).normalized(), rng.randf() * TAU) * Basis(Vector3.RIGHT, 1.2)
		hm.set_instance_transform(i, Transform3D(b2, p2))


## Point de la surface le plus proche d'une position (pour viser les machines).
func surface_toward(from: Vector3) -> Vector3:
	var d := from - global_position
	d.y = 0
	if d.length() < 0.01:
		d = Vector3(1, 0, 0)
	return global_position + d.normalized() * radius * 0.8 + Vector3(0, radius * 0.3, 0)
