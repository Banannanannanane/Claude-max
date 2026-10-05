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

const BELT_SHADER := """
shader_type spatial;
render_mode diffuse_burley;
uniform float speed = 1.6;
void fragment() {
	// chevrons qui défilent vers l'avant (-Z local = haut de l'UV)
	vec2 uv = UV;
	float v = fract(uv.y * 2.0 + TIME * speed * 2.0 + abs(uv.x - 0.5) * 1.2);
	float chevron = smoothstep(0.0, 0.08, v) * smoothstep(0.32, 0.24, v);
	vec3 base = vec3(0.07, 0.075, 0.08);
	ALBEDO = mix(base, vec3(0.9, 0.75, 0.15), chevron * 0.55);
	ROUGHNESS = 0.85;
}
"""

var pile: Node3D
var nodes := {} # id -> Node3D
var drones := {} # id -> Node3D
var _belts: MultiMeshInstance3D
var _belt_tops: MultiMeshInstance3D
var _items: MultiMeshInstance3D
var _belt_mat: ShaderMaterial
var _t := 0.0
var _fall_budget := 0.0


func _ready() -> void:
	_environment()
	_ground()
	_fence()
	_trees()
	_barn()
	pile = Node3D.new()
	pile.set_script(load("res://scripts/pile.gd"))
	add_child(pile)
	_make_belt_layers()
	Game.entities_changed.connect(rebuild)
	Game.sold.connect(_on_sold)
	rebuild()


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




func _make_belt_layers() -> void:
	_belts = MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	var frame := BoxMesh.new()
	frame.size = Vector3(0.98, 0.16, 1.0)
	mm.mesh = frame
	_belts.multimesh = mm
	_belts.material_override = Mk.mat(Color(0.3, 0.32, 0.36), 0.5, 0.5)
	add_child(_belts)
	_belt_tops = MultiMeshInstance3D.new()
	var mt := MultiMesh.new()
	mt.transform_format = MultiMesh.TRANSFORM_3D
	var top := PlaneMesh.new()
	top.size = Vector2(0.78, 1.0)
	mt.mesh = top
	_belt_tops.multimesh = mt
	_belt_mat = ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = BELT_SHADER
	_belt_mat.shader = sh
	_belt_tops.material_override = _belt_mat
	_belt_tops.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_belt_tops)
	_items = MultiMeshInstance3D.new()
	var mi := MultiMesh.new()
	mi.transform_format = MultiMesh.TRANSFORM_3D
	mi.use_colors = true
	mi.mesh = BoxMesh.new()
	mi.instance_count = 256
	mi.visible_instance_count = 0
	_items.multimesh = mi
	var im := StandardMaterial3D.new()
	im.vertex_color_use_as_albedo = true
	im.metallic = 0.6
	im.roughness = 0.35
	_items.material_override = im
	add_child(_items)


static func basis_for(r: int) -> Basis:
	return Basis(Vector3.UP, -r * PI / 2.0)


func rebuild() -> void:
	# machines : on garde les nœuds existants, on crée les nouveaux, on supprime les disparus
	for id in nodes.keys():
		if not Game.entities.has(id) or nodes[id].get_meta("sig") != _sig(Game.entities[id]):
			nodes[id].queue_free()
			nodes.erase(id)
	for id in drones.keys():
		if not Game.entities.has(id):
			drones[id].queue_free()
			drones.erase(id)
	var belts: Array = []
	for id in Game.entities:
		var e: Dictionary = Game.entities[id]
		if e.type == "convoyeur":
			belts.append(e)
			continue
		if nodes.has(id):
			continue
		var n := Buildings.create(e.type)
		n.position = Game.cell_center(e.c)
		n.basis = basis_for(e.r)
		n.set_meta("phase", randf())
		n.set_meta("sig", _sig(e))
		Buildings.add_body(n, e.type, id)
		add_child(n)
		nodes[id] = n
		if e.type == "drone" and not drones.has(id):
			var d := Buildings.create_drone()
			add_child(d)
			drones[id] = d
	var mm := _belts.multimesh
	mm.instance_count = belts.size()
	_belt_tops.multimesh.instance_count = belts.size()
	for i in belts.size():
		var e: Dictionary = belts[i]
		var o := Game.cell_center(e.c)
		mm.set_instance_transform(i, Transform3D(basis_for(e.r), o + Vector3(0, 0.08, 0)))
		_belt_tops.multimesh.set_instance_transform(i, Transform3D(basis_for(e.r), o + Vector3(0, 0.165, 0)))


func _sig(e: Dictionary) -> String:
	return "%s%s%d" % [e.type, e.c, e.r]


func _process(delta: float) -> void:
	_t += delta
	_fall_budget = minf(_fall_budget + delta * 6.0, 6.0)
	_belt_mat.set_shader_parameter("speed", Game.belt_speed())
	# objets sur les tapis
	var mm := _items.multimesh
	var n := 0
	for id in Game.entities:
		var e: Dictionary = Game.entities[id]
		var it = e.get("item")
		if it == null or not (e.type == "convoyeur" or e.type == "separateur"):
			continue
		if n >= mm.instance_count:
			mm.instance_count = mm.instance_count * 2
		var info: Dictionary = Data.ITEMS[it.t]
		var dir := Data.DIRS[e.r] as Vector2i
		var prog: float = it.p - 0.5
		var pos := Game.cell_center(e.c) + Vector3(dir.x, 0, dir.y) * prog
		var sc: Vector3 = info.scale
		pos.y = 0.2 + sc.y * 0.5
		var b := basis_for(e.r).scaled(sc) if it.t != "fil" else Basis(Vector3.FORWARD, PI / 2).scaled(sc)
		mm.set_instance_transform(n, Transform3D(b, pos))
		var col: Color = info.color
		if it.t == "vrac" and int(it.get("h", 0)) > 0 and Game.shop_lvl("oeil") >= 4:
			col = col.lerp(Color(1, 0.85, 0.3), 0.35)
		mm.set_instance_color(n, col)
		n += 1
	mm.visible_instance_count = n
	# animation des machines
	for id in nodes:
		if not Game.entities.has(id):
			continue
		var e: Dictionary = Game.entities[id]
		var node: Node3D = nodes[id]
		var info := {}
		var active := Game.is_active(id)
		match e.type:
			"bras", "pelle":
				var d: Vector3 = Data.PILE_POS - node.position
				var world_yaw := atan2(-d.x, -d.z)
				info["aim"] = world_yaw - node.rotation.y
				active = active and Game.digger_in_range(e.type, e.c)
			"tremie":
				info["fill"] = clampf(float(int(e.n) + int(e.h)) / Game.tremie_cap(), 0.0, 1.0)
			"tampon":
				info["fill"] = clampf(float(e.q.size()) / float(Data.MACHINES.tampon.cap), 0.0, 1.0)
		Buildings.animate(node, e.type, _t, active, info)
	for id in drones:
		var d: Node3D = drones[id]
		var e2: Dictionary = Game.entities[id]
		d.position = d.position.lerp(e2.pos, minf(1.0, delta * 10.0))
		var parts: Dictionary = d.get_meta("parts")
		for rr: MeshInstance3D in parts.rotors:
			rr.rotation.y = _t * 30.0
		parts.load.visible = int(e2.carry_n) > 0


func _on_sold(cell: Vector2i, t: String) -> void:
	if _fall_budget < 1.0:
		return
	_fall_budget -= 1.0
	var info: Dictionary = Data.ITEMS[t]
	var box := MeshInstance3D.new()
	var bm := BoxMesh.new()
	bm.size = info.scale
	box.mesh = bm
	box.material_override = Mk.mat(info.color, 0.6, 0.35)
	box.position = Game.cell_center(cell) + Vector3(0, 0.3, 0)
	add_child(box)
	var trou := Vector3.ZERO
	for id in Game.entities:
		if Game.entities[id].type == "trou":
			trou = Game.cell_center(Game.entities[id].c)
	var tw := create_tween()
	tw.tween_property(box, "position", trou + Vector3(randf_range(-0.6, 0.6), -2.5, randf_range(-0.6, 0.6)), 0.7).set_ease(Tween.EASE_IN).set_trans(Tween.TRANS_QUAD)
	tw.parallel().tween_property(box, "scale", Vector3(0.3, 0.3, 0.3), 0.7)
	tw.tween_callback(box.queue_free)
