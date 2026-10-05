extends Node3D
## Le décor : ciel, soleil, prairie, clôture, arbres, tas d'aiguilles et bâtiments.

const GRASS_SHADER := """
shader_type spatial;
render_mode diffuse_burley;
uniform vec3 c1 : source_color = vec3(0.15, 0.3, 0.09);
uniform vec3 c2 : source_color = vec3(0.24, 0.4, 0.12);
uniform vec3 c3 : source_color = vec3(0.36, 0.38, 0.15);
varying vec3 wp;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
	vec2 i = floor(p); vec2 f = fract(p);
	vec2 u = f * f * (3.0 - 2.0 * f);
	return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}
void vertex() { wp = (MODEL_MATRIX * vec4(VERTEX, 1.0)).xyz; }
void fragment() {
	float n = noise(wp.xz * 0.15) * 0.6 + noise(wp.xz * 0.9) * 0.3 + noise(wp.xz * 6.0) * 0.1;
	vec3 col = mix(c1, c2, smoothstep(0.3, 0.7, n));
	col = mix(col, c3, smoothstep(0.75, 0.95, noise(wp.xz * 0.05 + 3.0)) * 0.5);
	float blade = noise(wp.xz * 22.0);
	ALBEDO = col * (0.9 + 0.16 * blade);
	ROUGHNESS = 0.95;
}
"""

var pile: Node3D
var building_nodes: Array = []
var _t := 0.0


func _ready() -> void:
	_environment()
	_ground()
	_fence()
	_trees()
	_barn()
	pile = Node3D.new()
	pile.set_script(load("res://scripts/pile.gd"))
	add_child(pile)
	Game.buildings_changed.connect(rebuild_buildings)
	rebuild_buildings()


func _environment() -> void:
	var we := WorldEnvironment.new()
	var env := Environment.new()
	env.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	var psm := ProceduralSkyMaterial.new()
	psm.sky_top_color = Color(0.25, 0.48, 0.85)
	psm.sky_horizon_color = Color(0.7, 0.8, 0.92)
	psm.ground_horizon_color = Color(0.55, 0.62, 0.5)
	psm.ground_bottom_color = Color(0.25, 0.3, 0.2)
	psm.sun_angle_max = 30.0
	sky.sky_material = psm
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_energy = 0.55
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_exposure = 0.95
	env.glow_enabled = true
	env.glow_intensity = 0.5
	env.glow_bloom = 0.05
	env.fog_enabled = true
	env.fog_light_color = Color(0.72, 0.8, 0.9)
	env.fog_density = 0.0022
	env.fog_sky_affect = 0.2
	we.environment = env
	add_child(we)

	var sun := DirectionalLight3D.new()
	sun.rotation_degrees = Vector3(-48, -35, 0)
	sun.light_color = Color(1, 0.96, 0.88)
	sun.light_energy = 1.1
	sun.shadow_enabled = true
	sun.directional_shadow_mode = DirectionalLight3D.SHADOW_PARALLEL_2_SPLITS
	sun.directional_shadow_max_distance = 55.0
	add_child(sun)


func _ground() -> void:
	var mi := MeshInstance3D.new()
	var pm := PlaneMesh.new()
	pm.size = Vector2(240, 240)
	mi.mesh = pm
	var sm := ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = GRASS_SHADER
	sm.shader = sh
	mi.material_override = sm
	add_child(mi)
	var body := StaticBody3D.new()
	body.collision_layer = 1
	var cs := CollisionShape3D.new()
	cs.shape = WorldBoundaryShape3D.new()
	body.add_child(cs)
	add_child(body)
	# chemin de terre entre la ferme et le tas
	var path := Mk.box(self, Vector3(3.0, 0.02, 20.0), Vector3(0, 0.005, 2.0), Mk.mat(Color(0.36, 0.29, 0.19), 0.0, 1.0))
	path.name = "Chemin"
	# murs invisibles le long de la clôture
	var L := Data.FIELD + 1.5
	for i in 4:
		var wall := StaticBody3D.new()
		wall.collision_layer = 1
		var wcs := CollisionShape3D.new()
		var bs := BoxShape3D.new()
		bs.size = Vector3(L * 2.0, 4, 0.5) if i < 2 else Vector3(0.5, 4, L * 2.0)
		wcs.shape = bs
		wall.add_child(wcs)
		wall.position = [Vector3(0, 2, -L), Vector3(0, 2, L), Vector3(-L, 2, 0), Vector3(L, 2, 0)][i]
		add_child(wall)


func _multimesh(mesh: Mesh, material: Material, xforms: Array) -> void:
	var mmi := MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = mesh
	mm.instance_count = xforms.size()
	for i in xforms.size():
		mm.set_instance_transform(i, xforms[i])
	mmi.multimesh = mm
	mmi.material_override = material
	add_child(mmi)


func _fence() -> void:
	var L := Data.FIELD + 1.5
	var posts: Array = []
	var rails: Array = []
	var step := 4.0
	var n := int(L * 2.0 / step)
	for side in 4:
		for i in n + 1:
			var a := -L + i * step
			var p: Vector3 = [Vector3(a, 0.6, -L), Vector3(a, 0.6, L), Vector3(-L, 0.6, a), Vector3(L, 0.6, a)][side]
			posts.append(Transform3D(Basis(), p))
			if i < n:
				var mid := a + step * 0.5
				var rp: Vector3 = [Vector3(mid, 0, -L), Vector3(mid, 0, L), Vector3(-L, 0, mid), Vector3(L, 0, mid)][side]
				var rb := Basis() if side < 2 else Basis(Vector3.UP, PI / 2)
				for y in [0.5, 0.95]:
					rails.append(Transform3D(rb, rp + Vector3(0, y, 0)))
	var post := BoxMesh.new()
	post.size = Vector3(0.18, 1.2, 0.18)
	var rail := BoxMesh.new()
	rail.size = Vector3(step, 0.1, 0.06)
	var wood := Mk.mat(Color(0.55, 0.4, 0.26), 0.0, 0.9)
	_multimesh(post, wood, posts)
	_multimesh(rail, wood, rails)


func _trees() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 42
	var trunks: Array = []
	var tops: Array = []
	for i in 90:
		var a := rng.randf() * TAU
		var d := rng.randf_range(Data.FIELD + 6.0, Data.FIELD + 40.0)
		var p := Vector3(cos(a) * d, 0, sin(a) * d)
		var s := rng.randf_range(0.8, 1.6)
		trunks.append(Transform3D(Basis().scaled(Vector3(s, s, s)), p + Vector3(0, 1.0 * s, 0)))
		tops.append(Transform3D(Basis(Vector3.UP, rng.randf() * TAU).scaled(Vector3(s, s * rng.randf_range(0.9, 1.3), s)), p + Vector3(0, 3.6 * s, 0)))
	var trunk := CylinderMesh.new()
	trunk.top_radius = 0.18
	trunk.bottom_radius = 0.28
	trunk.height = 2.0
	trunk.radial_segments = 6
	var top := CylinderMesh.new()
	top.top_radius = 0.0
	top.bottom_radius = 1.6
	top.height = 4.2
	top.radial_segments = 7
	_multimesh(trunk, Mk.mat(Color(0.4, 0.27, 0.16), 0.0, 1.0), trunks)
	_multimesh(top, Mk.mat(Color(0.16, 0.36, 0.17), 0.0, 0.9), tops)


func _barn() -> void:
	var barn := Node3D.new()
	barn.position = Vector3(-30, 0, 30)
	barn.rotation.y = 0.5
	add_child(barn)
	Mk.box(barn, Vector3(10, 6, 14), Vector3(0, 3, 0), Mk.mat(Color(0.6, 0.15, 0.12), 0.0, 0.9))
	var roof := MeshInstance3D.new()
	var pm := PrismMesh.new()
	pm.size = Vector3(11, 3.5, 15)
	roof.mesh = pm
	roof.position = Vector3(0, 7.75, 0)
	roof.material_override = Mk.mat(Color(0.28, 0.28, 0.3), 0.2, 0.7)
	barn.add_child(roof)
	Mk.box(barn, Vector3(4, 4.5, 0.1), Vector3(0, 2.25, 7.02), Mk.mat(Color(0.95, 0.93, 0.88), 0.0, 0.8))
	var silo := Mk.cyl(self, 2.2, 11, Vector3(-38, 5.5, 22), Mk.mat(Color(0.75, 0.77, 0.8), 0.7, 0.35))
	silo.name = "Silo"
	Mk.sphere(self, 2.2, Vector3(-38, 11, 22), Mk.mat(Color(0.6, 0.15, 0.12), 0.2, 0.6))


func rebuild_buildings() -> void:
	for n in building_nodes:
		n.queue_free()
	building_nodes.clear()
	for i in Game.buildings.size():
		var b: Dictionary = Game.buildings[i]
		var node := Buildings.create(b.type)
		node.position = Vector3(b.x, 0, b.z)
		node.rotation.y = b.rot * PI / 2.0
		node.set_meta("phase", randf())
		node.set_meta("type", b.type)
		node.set_meta("index", i)
		Buildings.add_body(node, b.type, i)
		add_child(node)
		building_nodes.append(node)


func _process(delta: float) -> void:
	_t += delta
	var cap := maxf(1.0, Game.capacity())
	for node: Node3D in building_nodes:
		var type: String = node.get_meta("type")
		var i: int = node.get_meta("index")
		if i >= Game.buildings.size():
			continue
		var info := {}
		var active := Game.is_active(type)
		if type == "bras" or type == "pelle":
			var b: Dictionary = Game.buildings[i]
			active = active and Game.digger_in_range(b)
			var d := Data.PILE_POS - node.position
			info["aim"] = atan2(-d.x, -d.z) - node.rotation.y
		elif type == "table":
			info["fill"] = clampf(float(Game.table_n + Game.table_h) / maxf(1.0, Game.table_cap()), 0.0, 1.0)
		elif type == "entrepot":
			info["fill"] = clampf(Game.used() / cap, 0.0, 1.0)
		Buildings.animate(node, type, _t, active, info)
