extends Node3D
## Le décor et le rendu de l'usine : ciel, jour/nuit, prairie, clôture, forêt, ferme,
## tas d'aiguilles, machines, tapis et objets transportés.

const GRASS_SHADER := """
shader_type spatial;
render_mode diffuse_burley;
uniform vec3 c1 : source_color = vec3(0.15, 0.3, 0.09);
uniform vec3 c2 : source_color = vec3(0.24, 0.4, 0.12);
uniform vec3 c3 : source_color = vec3(0.36, 0.38, 0.15);
uniform vec3 dirt : source_color = vec3(0.36, 0.29, 0.19);
uniform float field = 80.0;
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
	// zones de terre piétinée
	float worn = smoothstep(0.62, 0.8, noise(wp.xz * 0.35 + 9.0)) * 0.6;
	col = mix(col, dirt * (0.85 + 0.3 * noise(wp.xz * 4.0)), worn * 0.5);
	// léger quadrillage de chantier à l'intérieur de la clôture
	vec2 g = abs(fract(wp.xz) - 0.5);
	float inside = step(abs(wp.x), field) * step(abs(wp.z), field);
	float grid = smoothstep(0.488, 0.5, max(g.x, g.y)) * 0.045 * inside;
	col = col * (1.0 - grid);
	ALBEDO = col * (0.9 + 0.16 * noise(wp.xz * 22.0));
	ROUGHNESS = 0.95;
}
"""

const BELT_SHADER := """
shader_type spatial;
render_mode diffuse_burley;
uniform float speed = 1.6;
varying float k;
varying vec3 tint;
void vertex() {
	// tapis express : chevrons bleus qui défilent deux fois plus vite
	k = INSTANCE_CUSTOM.r > 0.5 ? 2.0 : 1.0;
	tint = COLOR.rgb;
}
void fragment() {
	vec2 uv = UV;
	float v = fract(uv.y * 2.0 + TIME * speed * k * 2.0 + abs(uv.x - 0.5) * 1.2);
	float chevron = smoothstep(0.0, 0.08, v) * smoothstep(0.32, 0.24, v);
	vec3 base = vec3(0.07, 0.075, 0.08);
	ALBEDO = mix(base, tint, chevron * 0.5);
	ROUGHNESS = 0.85;
}
"""

const DAY_LENGTH := 900.0 # secondes pour un cycle complet

var pile: Node3D
var nodes := {} # id -> Node3D
var drones := {} # id -> Node3D
var sun: DirectionalLight3D
var env: Environment
var sky_mat: ProceduralSkyMaterial
var lamps: Array = []
var _belts: MultiMeshInstance3D
var _belt_tops: MultiMeshInstance3D
var _belt_mat: ShaderMaterial
var _items := {} # type d'objet -> MultiMeshInstance3D
var _item_meshes := {}
var _t := 0.0
var _fall_budget := 0.0
var _last_cash := 0.0
var _cash_timer := 0.0
var _item_ids: Array = [] # tapis, séparateurs et trieurs (seuls à porter des objets visibles)
var _frame := 0
var _alerts := {} # id du scanner -> horloge de la dernière détection
var night := 0.0
var rain := 0.0 # intensité de l'averse en cours (0 à 1)
var _rain_target := 0.0
var _weather_timer := 240.0 # secondes avant le prochain changement de temps
var _rain_fx: CPUParticles3D
var _rain_mat: StandardMaterial3D
var _rain_amount := -1


func _ready() -> void:
	_environment()
	_ground()
	_fence()
	_trees()
	_farm()
	_lamps()
	pile = Node3D.new()
	pile.set_script(load("res://scripts/pile.gd"))
	add_child(pile)
	_make_belt_layers()
	_make_item_layers()
	Game.entities_changed.connect(rebuild)
	Game.sold.connect(_on_sold)
	Game.hay_found.connect(_on_hay)
	rebuild()


# ============================================================ décor
func _environment() -> void:
	var we := WorldEnvironment.new()
	env = Environment.new()
	env.background_mode = Environment.BG_SKY
	var sky := Sky.new()
	sky_mat = ProceduralSkyMaterial.new()
	sky_mat.sky_top_color = Color(0.25, 0.48, 0.85)
	sky_mat.sky_horizon_color = Color(0.7, 0.8, 0.92)
	sky_mat.ground_horizon_color = Color(0.55, 0.62, 0.5)
	sky_mat.ground_bottom_color = Color(0.25, 0.3, 0.2)
	sky_mat.sun_angle_max = 30.0
	sky.sky_material = sky_mat
	env.sky = sky
	env.ambient_light_source = Environment.AMBIENT_SOURCE_SKY
	env.ambient_light_energy = 0.55
	env.reflected_light_source = Environment.REFLECTION_SOURCE_SKY
	env.tonemap_mode = Environment.TONE_MAPPER_FILMIC
	env.tonemap_exposure = 0.95
	env.glow_enabled = true
	env.glow_intensity = 0.55
	env.glow_bloom = 0.05
	env.fog_enabled = true
	env.fog_light_color = Color(0.72, 0.8, 0.9)
	env.fog_density = 0.0018
	env.fog_sky_affect = 0.15
	we.environment = env
	add_child(we)
	sun = DirectionalLight3D.new()
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
	pm.size = Vector2(480, 480)
	pm.subdivide_width = 8
	pm.subdivide_depth = 8
	mi.mesh = pm
	var sm := ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = GRASS_SHADER
	sm.shader = sh
	sm.set_shader_parameter("field", float(Data.FIELD) + 0.5)
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


func _multimesh(mesh: Mesh, material: Material, xforms: Array) -> MultiMeshInstance3D:
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
	return mmi


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
	post.size = Vector3(0.16, 1.2, 0.16)
	var rail := BoxMesh.new()
	rail.size = Vector3(step, 0.1, 0.06)
	var wood := Mk.paint(Color(0.55, 0.4, 0.26), 0.0, 0.9, 0.3)
	_multimesh(post, wood, posts)
	_multimesh(rail, wood, rails)


func _trees() -> void:
	var rng := RandomNumberGenerator.new()
	rng.seed = 42
	var trunks: Array = []
	var tops: Array = []
	var tops2: Array = []
	var rocks: Array = []
	var bushes: Array = []
	for i in 260:
		var a := rng.randf() * TAU
		var d := rng.randf_range(Data.FIELD + 6.0, Data.FIELD + 70.0)
		var p := Vector3(cos(a) * d, 0, sin(a) * d)
		var s := rng.randf_range(0.8, 1.8)
		trunks.append(Transform3D(Basis().scaled(Vector3(s, s, s)), p + Vector3(0, 1.0 * s, 0)))
		var b := Basis(Vector3.UP, rng.randf() * TAU).scaled(Vector3(s, s * rng.randf_range(0.9, 1.3), s))
		tops.append(Transform3D(b, p + Vector3(0, 3.2 * s, 0)))
		tops2.append(Transform3D(b.scaled(Vector3(0.7, 0.75, 0.7)), p + Vector3(0, 4.9 * s, 0)))
	for i in 120:
		var a2 := rng.randf() * TAU
		var d2 := rng.randf_range(Data.FIELD + 3.0, Data.FIELD + 40.0)
		var p2 := Vector3(cos(a2) * d2, 0, sin(a2) * d2)
		var s2 := rng.randf_range(0.4, 1.3)
		if i % 2 == 0:
			rocks.append(Transform3D(Basis(Vector3(rng.randf(), 1, rng.randf()).normalized(), rng.randf() * TAU).scaled(Vector3(s2 * 1.3, s2 * 0.7, s2)), p2 + Vector3(0, 0.2 * s2, 0)))
		else:
			bushes.append(Transform3D(Basis().scaled(Vector3(s2 * 1.4, s2, s2 * 1.4)), p2 + Vector3(0, 0.4 * s2, 0)))
	var trunk := CylinderMesh.new()
	trunk.top_radius = 0.18
	trunk.bottom_radius = 0.28
	trunk.height = 2.0
	trunk.radial_segments = 6
	var top := CylinderMesh.new()
	top.top_radius = 0.2
	top.bottom_radius = 1.7
	top.height = 3.2
	top.radial_segments = 8
	var rock := SphereMesh.new()
	rock.radius = 0.6
	rock.height = 1.2
	rock.radial_segments = 7
	rock.rings = 4
	var bush := SphereMesh.new()
	bush.radius = 0.6
	bush.height = 1.0
	bush.radial_segments = 8
	bush.rings = 5
	_multimesh(trunk, Mk.paint(Color(0.4, 0.27, 0.16), 0.0, 1.0, 0.0), trunks)
	_multimesh(top, Mk.paint(Color(0.15, 0.34, 0.16), 0.0, 0.9, 0.0), tops)
	_multimesh(top, Mk.paint(Color(0.18, 0.4, 0.19), 0.0, 0.9, 0.0), tops2)
	_multimesh(rock, Mk.paint(Color(0.45, 0.45, 0.43), 0.0, 0.95, 0.4), rocks)
	_multimesh(bush, Mk.paint(Color(0.2, 0.38, 0.15), 0.0, 0.95, 0.0), bushes)


func _farm() -> void:
	var barn := Node3D.new()
	barn.position = Vector3(-30, 0, Data.FIELD + 22)
	barn.rotation.y = 0.3
	add_child(barn)
	Mk.box(barn, Vector3(12, 7, 16), Vector3(0, 3.5, 0), Mk.paint(Color(0.6, 0.15, 0.12), 0.0, 0.9, 0.3))
	var roof := MeshInstance3D.new()
	var pm := PrismMesh.new()
	pm.size = Vector3(13, 4, 17)
	roof.mesh = pm
	roof.position = Vector3(0, 9, 0)
	roof.material_override = Mk.paint(Color(0.28, 0.28, 0.3), 0.2, 0.7)
	barn.add_child(roof)
	Mk.box(barn, Vector3(5, 5.5, 0.1), Vector3(0, 2.75, -8.02), Mk.paint(Color(0.95, 0.93, 0.88), 0.0, 0.8))
	for i in 2:
		var x := Mk.box(barn, Vector3(0.25, 6.6, 0.12), Vector3(0, 2.75, -8.08), Mk.paint(Color(0.6, 0.15, 0.12)))
		x.rotation.z = 0.72 if i == 0 else -0.72
	for k in 2:
		var silo_pos := Vector3(-48 + k * 7, 0, Data.FIELD + 14)
		Mk.cyl(self, 2.6, 13, silo_pos + Vector3(0, 6.5, 0), Mk.steel())
		Mk.sphere(self, 2.6, silo_pos + Vector3(0, 13, 0), Mk.paint(Color(0.6, 0.15, 0.12), 0.2, 0.6))
	# bottes de foin décoratives près de la grange (le seul foin facile à trouver !)
	for i in 6:
		var bale := Mk.cyl(self, 0.7, 1.2, Vector3(-18 + i * 1.6, 0.7, Data.FIELD + 10), Mk.paint(Color(0.85, 0.7, 0.3), 0.0, 1.0, 0.2), -1.0, 18)
		bale.rotation.x = PI / 2


func _lamps() -> void:
	var spots := [Vector3(4, 0, 4), Vector3(-9, 0, -4), Vector3(17, 0, -9), Vector3(-26, 0, -14), Vector3(25, 0, -16), Vector3(20, 0, 6)]
	for s in spots:
		var lp := Node3D.new()
		lp.position = s
		add_child(lp)
		Mk.cyl(lp, 0.07, 4.2, Vector3(0, 2.1, 0), Mk.dark_steel(), 0.05)
		Mk.box(lp, Vector3(0.9, 0.06, 0.08), Vector3(0.4, 4.15, 0), Mk.dark_steel())
		Mk.box(lp, Vector3(0.35, 0.12, 0.25), Vector3(0.8, 4.08, 0), Mk.dark_steel())
		Mk.box(lp, Vector3(0.28, 0.02, 0.18), Vector3(0.8, 4.01, 0), Mk.glow(Color(1, 0.88, 0.6), 2.0))
		var l := OmniLight3D.new()
		l.position = Vector3(0.8, 3.8, 0)
		l.light_color = Color(1, 0.85, 0.6)
		l.omni_range = 11.0
		l.light_energy = 0.0
		lp.add_child(l)
		lamps.append(l)


func _update_daynight(delta: float) -> void:
	var target := 0.0
	if Game.settings.get("daynight", true):
		var ph := fmod(float(Game.stats.time) / DAY_LENGTH, 1.0)
		# jour la plupart du temps, courte nuit douce
		target = smoothstep(0.62, 0.72, ph) * (1.0 - smoothstep(0.9, 1.0, ph))
	night = move_toward(night, target, delta * 0.2)
	sun.rotation_degrees = Vector3(lerpf(-48.0, -12.0, night), -35.0 + night * 60.0, 0)
	sun.light_energy = lerpf(1.1, 0.34, night) # clair de lune : on voit encore où l'on marche
	sun.light_color = Color(1, 0.96, 0.88).lerp(Color(0.6, 0.7, 1.0), night)
	env.ambient_light_energy = lerpf(0.55, 0.62, night)
	sky_mat.sky_top_color = Color(0.25, 0.48, 0.85).lerp(Color(0.06, 0.09, 0.22), night)
	sky_mat.sky_horizon_color = Color(0.7, 0.8, 0.92).lerp(Color(0.24, 0.26, 0.42), night)
	env.fog_light_color = Color(0.72, 0.8, 0.9).lerp(Color(0.12, 0.13, 0.2), night)
	for l: OmniLight3D in lamps:
		l.light_energy = smoothstep(0.3, 0.7, night) * 1.6


# ============================================================ météo
## Averses de temps en temps : ciel gris, brouillard, pluie autour du joueur et bruit de pluie.
func _update_weather(delta: float) -> void:
	if not Game.settings.get("weather", true):
		_rain_target = 0.0
		_weather_timer = 240.0
	else:
		_weather_timer -= delta
		if _weather_timer <= 0.0:
			if _rain_target > 0.0:
				_rain_target = 0.0
				_weather_timer = randf_range(360.0, 720.0)
			else:
				_rain_target = randf_range(0.55, 1.0)
				_weather_timer = randf_range(90.0, 180.0)
	if Game.event_id() == "tempete":
		_rain_target = maxf(_rain_target, 1.0)
		_weather_timer = maxf(_weather_timer, 20.0)
	rain = move_toward(rain, _rain_target, delta * 0.05)
	Game.weather_rain = rain
	Sfx.set_rain(rain)
	var g := rain * 0.75
	if g > 0.001:
		sun.light_energy *= 1.0 - 0.55 * g
		env.ambient_light_energy *= 1.0 - 0.25 * g
		var grey := Color(0.42, 0.45, 0.5).lerp(Color(0.05, 0.06, 0.1), night)
		sky_mat.sky_top_color = sky_mat.sky_top_color.lerp(grey, g)
		sky_mat.sky_horizon_color = sky_mat.sky_horizon_color.lerp(grey.lightened(0.15), g)
		env.fog_light_color = env.fog_light_color.lerp(grey, g)
	env.fog_density = 0.0018 + 0.012 * g
	_update_rain_fx()


func _update_rain_fx() -> void:
	var want := clampi(int(Game.settings.get("quality", 1)), 0, 2)
	var amount: int = [160, 420, 800][want]
	if rain < 0.03:
		if _rain_fx:
			_rain_fx.emitting = false
		return
	if _rain_fx == null or _rain_amount != amount:
		if _rain_fx:
			_rain_fx.queue_free()
		_rain_amount = amount
		_rain_fx = CPUParticles3D.new()
		_rain_fx.amount = amount
		_rain_fx.lifetime = 0.9
		_rain_fx.emission_shape = CPUParticles3D.EMISSION_SHAPE_BOX
		_rain_fx.emission_box_extents = Vector3(14, 0.5, 14)
		_rain_fx.direction = Vector3(0.08, -1, 0.04)
		_rain_fx.spread = 2.0
		_rain_fx.initial_velocity_min = 16.0
		_rain_fx.initial_velocity_max = 20.0
		_rain_fx.gravity = Vector3(0, -6, 0)
		_rain_fx.particle_flag_align_y = true
		_rain_fx.local_coords = false
		var m := BoxMesh.new()
		m.size = Vector3(0.006, 0.5, 0.006)
		_rain_fx.mesh = m
		_rain_mat = StandardMaterial3D.new()
		_rain_mat.shading_mode = BaseMaterial3D.SHADING_MODE_UNSHADED
		_rain_mat.transparency = BaseMaterial3D.TRANSPARENCY_ALPHA
		_rain_mat.albedo_color = Color(0.75, 0.8, 0.88, 0.28)
		_rain_fx.material_override = _rain_mat
		_rain_fx.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
		_rain_fx.visibility_aabb = AABB(Vector3(-16, -20, -16), Vector3(32, 24, 32))
		add_child(_rain_fx)
	var cam := get_viewport().get_camera_3d()
	if cam:
		_rain_fx.global_position = cam.global_position + Vector3(0, 9, 0)
	_rain_fx.emitting = true
	_rain_mat.albedo_color.a = 0.28 * clampf(rain * 1.4, 0.0, 1.0)


## Pour les tests et les captures : impose une averse (ou le beau temps).
func force_rain(v: float) -> void:
	_rain_target = v
	rain = v
	_weather_timer = 600.0


# ============================================================ tapis et objets
func _make_belt_layers() -> void:
	_belts = MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	var frame := BoxMesh.new()
	frame.size = Vector3(0.98, 0.12, 1.0)
	var rail := BoxMesh.new()
	rail.size = Vector3(0.06, 0.1, 1.0)
	var leg := BoxMesh.new()
	leg.size = Vector3(0.06, 0.08, 0.06)
	var roller := CylinderMesh.new()
	roller.top_radius = 0.045
	roller.bottom_radius = 0.045
	roller.height = 0.84
	roller.radial_segments = 8
	var parts := [
		[frame, Transform3D(Basis(), Vector3(0, 0.12, 0))],
		[rail, Transform3D(Basis(), Vector3(-0.46, 0.22, 0))],
		[rail, Transform3D(Basis(), Vector3(0.46, 0.22, 0))],
	]
	for z in [-0.42, 0.42]:
		for x in [-0.42, 0.42]:
			parts.append([leg, Transform3D(Basis(), Vector3(x, 0.04, z))])
		parts.append([roller, Transform3D(Basis(Vector3.FORWARD, PI / 2), Vector3(0, 0.2, z))])
	mm.mesh = Mk.merge(parts)
	_belts.multimesh = mm
	_belts.material_override = Mk.paint(Color(0.32, 0.34, 0.38), 0.6, 0.45, 0.1)
	add_child(_belts)
	_belt_tops = MultiMeshInstance3D.new()
	var mt := MultiMesh.new()
	mt.transform_format = MultiMesh.TRANSFORM_3D
	mt.use_colors = true
	mt.use_custom_data = true
	var top := PlaneMesh.new()
	top.size = Vector2(0.84, 1.0)
	mt.mesh = top
	_belt_tops.multimesh = mt
	_belt_mat = ShaderMaterial.new()
	var sh := Shader.new()
	sh.code = BELT_SHADER
	_belt_mat.shader = sh
	_belt_tops.material_override = _belt_mat
	_belt_tops.cast_shadow = GeometryInstance3D.SHADOW_CASTING_SETTING_OFF
	add_child(_belt_tops)


## Maillage de chaque type d'objet transporté (orienté dans le sens du tapis, -Z).
func item_mesh(t: String) -> Mesh:
	if _item_meshes.has(t):
		return _item_meshes[t]
	var m: Mesh
	match t:
		"vrac", "acier":
			var nd := Mk.needle_mesh(0.48, 0.02)
			var parts := []
			for i in 7:
				var a := i * TAU / 6.0
				var off := Vector3.ZERO if i == 6 else Vector3(cos(a) * 0.045, sin(a) * 0.045, 0)
				parts.append([nd, Transform3D(Basis(Vector3.RIGHT, PI / 2), off + Vector3(0, 0.07, (i % 3) * 0.02))])
			var band := BoxMesh.new()
			band.size = Vector3(0.15, 0.15, 0.04)
			parts.append([band, Transform3D(Basis(), Vector3(0, 0.07, 0))])
			m = Mk.merge(parts)
		"brut", "pur":
			var cm := CylinderMesh.new()
			cm.top_radius = 0.15
			cm.bottom_radius = 0.2
			cm.height = 0.11
			cm.radial_segments = 4
			cm.rings = 1
			m = Mk.merge([[cm, Transform3D(Basis(Vector3.UP, PI / 4).scaled(Vector3(1.35, 1, 0.75)), Vector3(0, 0.055, 0))]])
		"tole":
			var b := BoxMesh.new()
			b.size = Vector3(0.6, 0.025, 0.5)
			m = Mk.merge([[b, Transform3D(Basis(), Vector3(0, 0.015, 0))], [b, Transform3D(Basis(Vector3.UP, 0.08), Vector3(0, 0.04, 0))]])
		"fil":
			var tm := TorusMesh.new()
			tm.inner_radius = 0.06
			tm.outer_radius = 0.17
			tm.rings = 16
			tm.ring_segments = 8
			var core := CylinderMesh.new()
			core.top_radius = 0.07
			core.bottom_radius = 0.07
			core.height = 0.14
			core.radial_segments = 10
			m = Mk.merge([[tm, Transform3D(Basis(), Vector3(0, 0.06, 0))], [core, Transform3D(Basis(), Vector3(0, 0.07, 0))]])
		"boite":
			var bx := BoxMesh.new()
			bx.size = Vector3(0.34, 0.18, 0.28)
			var lid := BoxMesh.new()
			lid.size = Vector3(0.36, 0.04, 0.3)
			m = Mk.merge([[bx, Transform3D(Basis(), Vector3(0, 0.09, 0))], [lid, Transform3D(Basis(), Vector3(0, 0.19, 0))]])
		"carton":
			var cb := BoxMesh.new()
			cb.size = Vector3(0.48, 0.38, 0.42)
			var tp := BoxMesh.new()
			tp.size = Vector3(0.1, 0.39, 0.43)
			m = Mk.merge([[cb, Transform3D(Basis(), Vector3(0, 0.19, 0))], [tp, Transform3D(Basis(), Vector3(0, 0.19, 0))]])
		"kit":
			var kb := BoxMesh.new()
			kb.size = Vector3(0.4, 0.12, 0.3)
			var hd := BoxMesh.new()
			hd.size = Vector3(0.16, 0.05, 0.04)
			m = Mk.merge([[kb, Transform3D(Basis(), Vector3(0, 0.06, 0))], [hd, Transform3D(Basis(), Vector3(0, 0.14, 0))]])
		"balle":
			var bb := BoxMesh.new()
			bb.size = Vector3(0.42, 0.28, 0.32)
			var strap := BoxMesh.new()
			strap.size = Vector3(0.44, 0.3, 0.03)
			m = Mk.merge([[bb, Transform3D(Basis(), Vector3(0, 0.14, 0))], [strap, Transform3D(Basis(), Vector3(0, 0.14, -0.09))], [strap, Transform3D(Basis(), Vector3(0, 0.14, 0.09))]])
		_:
			var d := BoxMesh.new()
			d.size = Vector3(0.3, 0.2, 0.3)
			m = d
	_item_meshes[t] = m
	return m


func _make_item_layers() -> void:
	for t in Data.ITEM_ORDER:
		var mmi := MultiMeshInstance3D.new()
		var mm := MultiMesh.new()
		mm.transform_format = MultiMesh.TRANSFORM_3D
		mm.use_colors = true
		mm.mesh = item_mesh(t)
		mm.instance_count = 64
		mm.visible_instance_count = 0
		mmi.multimesh = mm
		var im := StandardMaterial3D.new()
		im.vertex_color_use_as_albedo = true
		im.metallic = 0.0 if t == "boite" else 0.75
		im.roughness = 0.35 if t != "boite" else 0.6
		mmi.material_override = im
		add_child(mmi)
		_items[t] = mmi


static func basis_for(r: int) -> Basis:
	return Basis(Vector3.UP, -r * PI / 2.0)


func rebuild() -> void:
	for id in nodes.keys():
		if not Game.entities.has(id) or nodes[id].get_meta("sig") != _sig(Game.entities[id]):
			nodes[id].queue_free()
			nodes.erase(id)
	for id in drones.keys():
		if not Game.entities.has(id):
			drones[id].queue_free()
			drones.erase(id)
	var belts: Array = []
	_item_ids.clear()
	for id in Game.entities:
		var e: Dictionary = Game.entities[id]
		if Game.belt_like(e.type):
			_item_ids.append(id)
		if Game.is_belt(e.type):
			belts.append(e)
			continue
		if nodes.has(id):
			continue
		var n := Buildings.create(e.type)
		n.position = Game.machine_center(e.type, e.c, e.r)
		n.basis = basis_for(e.r)
		n.set_meta("phase", randf())
		n.set_meta("sig", _sig(e))
		Buildings.add_body(n, e.type, id)
		add_child(n)
		nodes[id] = n
		if e.type == "ouvrier" and not drones.has(id):
			var wk := Buildings.create_worker()
			wk.position = e.pos
			add_child(wk)
			drones[id] = wk
		if e.type == "drone" and not drones.has(id):
			var dr := Buildings.create_drone()
			dr.position = e.pos
			add_child(dr)
			drones[id] = dr
	var mm := _belts.multimesh
	mm.instance_count = belts.size()
	_belt_tops.multimesh.instance_count = belts.size()
	for i in belts.size():
		var e: Dictionary = belts[i]
		var o := Game.cell_center(e.c)
		mm.set_instance_transform(i, Transform3D(basis_for(e.r), o))
		_belt_tops.multimesh.set_instance_transform(i, Transform3D(basis_for(e.r), o + Vector3(0, 0.215, 0)))
		var ex: bool = e.type == "express"
		_belt_tops.multimesh.set_instance_color(i, Color(0.25, 0.75, 1.0) if ex else Color(0.9, 0.75, 0.15))
		_belt_tops.multimesh.set_instance_custom_data(i, Color(1.0 if ex else 0.0, 0, 0, 0))


func _sig(e: Dictionary) -> String:
	return "%s%s%d" % [e.type, e.c, e.r]


func _process(delta: float) -> void:
	_t += delta
	_fall_budget = minf(_fall_budget + delta * 6.0, 6.0)
	_belt_mat.set_shader_parameter("speed", Game.belt_speed())
	_update_daynight(delta)
	_update_weather(delta)
	_draw_items()
	_animate_machines(delta)


func _draw_items() -> void:
	var counts := {}
	for t in _items:
		counts[t] = 0
	for id in _item_ids:
		var e: Dictionary = Game.entities.get(id, {})
		var it = e.get("item")
		if it == null:
			continue
		var t: String = it.t
		if not _items.has(t):
			continue
		var mm: MultiMesh = _items[t].multimesh
		var n: int = counts[t]
		if n >= mm.instance_count:
			mm.instance_count = mm.instance_count * 2
		var dir: Vector2i = Data.DIRS[e.r]
		var prog: float = float(it.p) - 0.5
		var pos := Game.cell_center(e.c) + Vector3(dir.x, 0, dir.y) * prog
		pos.y = 0.22 if Game.is_belt(e.type) else 0.24
		mm.set_instance_transform(n, Transform3D(basis_for(e.r), pos))
		var col: Color = Data.ITEMS[t].color
		if t == "vrac" and int(it.get("h", 0)) > 0 and Game.shop_lvl("oeil") >= 4:
			col = col.lerp(Color(1, 0.85, 0.3), 0.4)
		mm.set_instance_color(n, col)
		counts[t] = n + 1
	for t in _items:
		_items[t].multimesh.visible_instance_count = counts[t]


func _animate_machines(delta: float) -> void:
	_cash_timer = maxf(0.0, _cash_timer - delta)
	_frame += 1
	var cam := get_viewport().get_camera_3d()
	var eye: Vector3 = cam.global_position if cam else Vector3.ZERO
	for id in nodes:
		if not Game.entities.has(id):
			continue
		var e: Dictionary = Game.entities[id]
		var node: Node3D = nodes[id]
		# au loin, les détails ne se voient pas : on n'anime qu'une image sur six
		if cam and node.position.distance_squared_to(eye) > 2500.0 and (_frame + id) % 6 != 0:
			continue
		var info := {}
		var state := 1 if Game.is_active(id) else 0
		match e.type:
			"bras", "pelle":
				var d: Vector3 = Data.PILE_POS - node.position
				info["aim"] = atan2(-d.x, -d.z) - node.rotation.y
				if not Game.digger_ok(id):
					state = 2
			"tremie":
				info["fill"] = clampf(float(int(e.n) + int(e.h)) / Game.tremie_cap(), 0.0, 1.0)
			"tampon", "entrepot":
				info["fill"] = clampf(float(e.q.size()) / float(Data.MACHINES[e.type].cap), 0.0, 1.0)
			"batterie":
				info["charge"] = clampf(float(e.get("charge", 0.0)) / Game.battery_cap(), 0.0, 1.0)
			"scanner":
				info["alert"] = _t - float(_alerts.get(id, -10.0)) < 2.0
			"trou":
				info["cash"] = ("+" + Fmt.eur(_last_cash)) if _cash_timer > 0.0 else ""
			"eolienne":
				info["wind"] = Game.wind()
			"trieur":
				info["filter"] = Data.ITEMS.get(str(e.get("f", "")), {}).get("color", Color.WHITE)
			"atelier":
				info["repairing"] = Game.atelier_busy()
			"radar":
				if Game.power_factor < 0.2:
					state = 2
		if e.has("outq") and e.outq.size() >= 4:
			state = 2
		if bool(e.get("broken", false)):
			state = 3
		var parts: Dictionary = node.get_meta("parts")
		if parts.has("label"):
			var broken := state == 3
			var lb: Label3D = parts.label
			if lb.get_meta("broken", false) != broken:
				lb.set_meta("broken", broken)
				lb.text = ("EN PANNE — " if broken else "") + Data.MACHINES[e.type].name
				lb.modulate = Color(1, 0.35, 0.3) if broken else Color.WHITE
		Buildings.animate(node, e.type, _t, state, info)
	for id in drones:
		if not Game.entities.has(id):
			continue
		var dn: Node3D = drones[id]
		var e2: Dictionary = Game.entities[id]
		if e2.type == "ouvrier":
			var wd: Vector3 = e2.pos - dn.position
			dn.position = dn.position.lerp(e2.pos, minf(1.0, delta * 10.0))
			var wp: Dictionary = dn.get_meta("parts")
			var walking := Vector2(wd.x, wd.z).length() > 0.005
			if walking:
				dn.rotation.y = lerp_angle(dn.rotation.y, atan2(-wd.x, -wd.z), minf(1.0, delta * 8.0))
			var sw := sin(_t * 9.0 + id) * 0.5 if walking else 0.0
			wp.legs[0].rotation.x = sw
			wp.legs[1].rotation.x = -sw
			wp.body.position.y = absf(sw) * 0.04
			wp.load.visible = int(e2.carry_n) > 0
			continue
		var target: Vector3 = e2.pos + Vector3(0, sin(_t * 2.0 + id) * 0.06, 0)
		var dir := target - dn.position
		dn.position = dn.position.lerp(target, minf(1.0, delta * 10.0))
		if Vector2(dir.x, dir.z).length() > 0.05:
			dn.rotation.y = lerp_angle(dn.rotation.y, atan2(-dir.x, -dir.z), minf(1.0, delta * 5.0))
		var parts: Dictionary = dn.get_meta("parts")
		for rr: MeshInstance3D in parts.rotors:
			rr.rotation.y = _t * 40.0
		parts.load.visible = int(e2.carry_n) > 0


func _on_hay(_count: int, by_hand: bool) -> void:
	if by_hand:
		return
	# allume l'écran « FOIN ! » des scanners qui viennent de travailler
	for id in nodes:
		if Game.entities.has(id) and Game.entities[id].type == "scanner" and Game.is_active(id):
			_alerts[id] = _t


func _on_sold(cell: Vector2i, t: String) -> void:
	_last_cash = Game.item_price({"t": t, "n": Data.LOT})
	_cash_timer = 1.5
	if _fall_budget < 1.0:
		return
	_fall_budget -= 1.0
	var mi := MeshInstance3D.new()
	mi.mesh = item_mesh(t)
	mi.material_override = Mk.mat(Data.ITEMS[t].color, 0.6, 0.35)
	mi.position = Game.cell_center(cell) + Vector3(0, 0.25, 0)
	add_child(mi)
	var trou := Vector3.ZERO
	for id in Game.entities:
		var e: Dictionary = Game.entities[id]
		if e.type == "trou":
			trou = Game.machine_center("trou", e.c, e.r)
	var tw := create_tween()
	tw.tween_property(mi, "position", trou + Vector3(randf_range(-0.5, 0.5), -2.2, randf_range(-0.5, 0.5)), 0.7).set_ease(Tween.EASE_IN).set_trans(Tween.TRANS_QUAD)
	tw.parallel().tween_property(mi, "rotation", Vector3(randf_range(-3, 3), randf_range(-3, 3), 0), 0.7)
	tw.parallel().tween_property(mi, "scale", Vector3(0.4, 0.4, 0.4), 0.7)
	tw.tween_callback(mi.queue_free)
