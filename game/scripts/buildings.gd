class_name Buildings
## Modèles 3D procéduraux des machines. Convention : la sortie est à l'avant (-Z local),
## l'entrée à l'arrière (+Z local). Les pièces animées sont rangées dans la méta "parts".

const C_DARK := Color(0.18, 0.2, 0.23)
const C_ORANGE := Color(0.95, 0.5, 0.12)
const C_YELLOW := Color(0.97, 0.75, 0.1)
const C_WOOD := Color(0.52, 0.34, 0.2)
const C_RED := Color(0.62, 0.17, 0.13)
const C_BRICK := Color(0.55, 0.27, 0.2)
const C_WHITE := Color(0.92, 0.92, 0.9)
const C_STEEL := Color(0.62, 0.64, 0.68)


static func create(type: String) -> Node3D:
	var root := Node3D.new()
	var p := {}
	match type:
		"separateur":
			_separateur(root, p)
		"tremie":
			_tremie(root, p)
		"scanner":
			_scanner(root, p)
		"bras":
			_bras(root, p)
		"pelle":
			_pelle(root, p)
		"fonderie":
			_fonderie(root, p)
		"purif":
			_purif(root, p)
		"presse":
			_presse(root, p)
		"trefileuse":
			_trefileuse(root, p)
		"aiguilleuse":
			_aiguilleuse(root, p)
		"tampon":
			_tampon(root, p)
		"drone":
			_drone_pad(root, p)
		"trou":
			_trou(root, p)
		"bureau":
			_bureau(root, p)
		"convoyeur":
			_belt_ghost(root, p)
	root.set_meta("parts", p)
	if type != "convoyeur" and type != "separateur":
		var info: Dictionary = Data.MACHINES[type]
		p["label"] = Mk.label(root, info.name, Vector3(0, float(info.h) + 0.9, 0), 40)
	return root


## Corps de collision : bloque le joueur et sert à viser la machine.
static func add_body(root: Node3D, type: String, id: int) -> void:
	var body := StaticBody3D.new()
	body.collision_layer = 3
	body.set_meta("kind", "entity")
	body.set_meta("id", id)
	var cs := CollisionShape3D.new()
	var sh := BoxShape3D.new()
	var sz: Vector2i = Data.MACHINES[type].size
	var hgt: float = maxf(0.4, float(Data.MACHINES[type].h))
	sh.size = Vector3(sz.x * 0.94, hgt, sz.y * 0.94)
	cs.shape = sh
	cs.position = Vector3(0, hgt * 0.5, 0)
	body.add_child(cs)
	root.add_child(body)


static func _arrow(parent: Node3D, pos: Vector3, color: Color) -> MeshInstance3D:
	var mi := MeshInstance3D.new()
	var pm := PrismMesh.new()
	pm.size = Vector3(0.45, 0.45, 0.03)
	mi.mesh = pm
	mi.position = pos
	mi.rotation = Vector3(-PI / 2, 0, 0)
	mi.material_override = Mk.mat(color, 0.0, 0.6, color, 0.6)
	parent.add_child(mi)
	return mi


static func _belt_ghost(r: Node3D, p: Dictionary) -> void:
	Mk.box(r, Vector3(0.98, 0.16, 1.0), Vector3(0, 0.08, 0), Mk.mat(C_DARK))
	_arrow(r, Vector3(0, 0.2, 0), Color(0.3, 1, 0.4))
	p["h"] = 0.3


static func _separateur(r: Node3D, p: Dictionary) -> void:
	Mk.box(r, Vector3(0.98, 0.3, 0.98), Vector3(0, 0.15, 0), Mk.mat(Color(0.25, 0.45, 0.8), 0.3, 0.5))
	Mk.box(r, Vector3(0.7, 0.05, 0.7), Vector3(0, 0.32, 0), Mk.mat(C_DARK, 0.2, 0.8))
	var a := _arrow(r, Vector3(0, 0.36, -0.15), C_WHITE)
	a.scale = Vector3(0.6, 0.6, 1)
	var l := _arrow(r, Vector3(-0.2, 0.36, 0.05), C_WHITE)
	l.rotation.y = PI / 2
	l.scale = Vector3(0.5, 0.5, 1)
	var rr := _arrow(r, Vector3(0.2, 0.36, 0.05), C_WHITE)
	rr.rotation.y = -PI / 2
	rr.scale = Vector3(0.5, 0.5, 1)
	p["spin"] = Mk.cyl(r, 0.08, 0.3, Vector3(0, 0.5, 0.2), Mk.mat(C_YELLOW))


static func _tremie(r: Node3D, p: Dictionary) -> void:
	var steel := Mk.mat(Color(0.5, 0.55, 0.6), 0.6, 0.4)
	for x in [-1.2, 1.2]:
		for z in [-1.2, 1.2]:
			Mk.box(r, Vector3(0.15, 1.2, 0.15), Vector3(x, 0.6, z), Mk.mat(C_DARK))
	var funnel := Mk.cyl(r, 1.45, 1.0, Vector3(0, 1.4, 0), steel, -1.0, 4)
	funnel.mesh.top_radius = 1.65
	funnel.mesh.bottom_radius = 0.4
	funnel.rotation.y = PI / 4
	var heap := Mk.sphere(r, 1.0, Vector3(0, 1.75, 0), Mk.needle_material())
	heap.scale = Vector3(1.0, 0.25, 1.0)
	p["heap"] = heap
	Mk.box(r, Vector3(0.6, 0.5, 1.3), Vector3(0, 0.55, -0.9), steel)
	_arrow(r, Vector3(0, 0.05, -1.4), Color(0.3, 0.6, 1.0))
	p["h"] = 1.9


static func _scanner(r: Node3D, p: Dictionary) -> void:
	var body := Mk.mat(Color(0.2, 0.45, 0.55), 0.3, 0.5)
	Mk.box(r, Vector3(0.98, 0.3, 2.95), Vector3(0, 0.15, 0), body)
	var post := Mk.mat(Color(0.85, 0.85, 0.88), 0.6, 0.3)
	for z in [-0.5, 0.5]:
		Mk.box(r, Vector3(0.12, 1.6, 0.12), Vector3(-0.48, 0.95, z), post)
		Mk.box(r, Vector3(0.12, 1.6, 0.12), Vector3(0.48, 0.95, z), post)
	Mk.box(r, Vector3(1.2, 0.35, 1.2), Vector3(0, 1.85, 0), post)
	var scan := Mk.box(r, Vector3(0.9, 0.05, 0.9), Vector3(0, 1.6, 0), Mk.mat(Color(0.2, 1, 0.4, 0.5), 0.0, 0.5, Color(0.2, 1, 0.4), 2.5))
	p["scan"] = scan
	var beam := Mk.box(r, Vector3(0.85, 1.3, 0.03), Vector3(0, 0.95, 0), Mk.mat(Color(0.2, 1, 0.4, 0.25), 0.0, 0.5, Color(0.2, 1, 0.4), 1.5))
	p["beam"] = beam
	var lamp := Mk.sphere(r, 0.14, Vector3(0, 2.1, 0), Mk.mat(Color(1, 0.2, 0.1), 0.0, 0.4, Color(1, 0.2, 0.1), 3.0))
	p["lamp"] = lamp
	p["h"] = 2.1


static func _bras(r: Node3D, p: Dictionary) -> void:
	var dark := Mk.mat(C_DARK, 0.5, 0.4)
	var orange := Mk.mat(C_ORANGE, 0.3, 0.45)
	Mk.cyl(r, 0.48, 0.3, Vector3(0, 0.15, 0), dark)
	var turret := Mk.pivot(r, Vector3(0, 0.3, 0))
	Mk.cyl(turret, 0.35, 0.45, Vector3(0, 0.22, 0), orange)
	var shoulder := Mk.pivot(turret, Vector3(0, 0.55, 0))
	Mk.sphere(shoulder, 0.22, Vector3.ZERO, dark)
	Mk.box(shoulder, Vector3(0.2, 1.6, 0.2), Vector3(0, 0.8, 0), orange)
	var elbow := Mk.pivot(shoulder, Vector3(0, 1.6, 0))
	Mk.sphere(elbow, 0.18, Vector3.ZERO, dark)
	Mk.box(elbow, Vector3(0.17, 1.4, 0.17), Vector3(0, 0.7, 0), orange)
	var wrist := Mk.pivot(elbow, Vector3(0, 1.4, 0))
	Mk.box(wrist, Vector3(0.3, 0.1, 0.3), Vector3.ZERO, dark)
	Mk.box(wrist, Vector3(0.05, 0.3, 0.16), Vector3(-0.1, 0.17, 0), dark)
	Mk.box(wrist, Vector3(0.05, 0.3, 0.16), Vector3(0.1, 0.17, 0), dark)
	var load := Mk.box(wrist, Vector3(0.16, 0.08, 0.36), Vector3(0, 0.22, 0), Mk.needle_material())
	p.merge({"turret": turret, "shoulder": shoulder, "elbow": elbow, "load": load, "h": 1.0})


static func _pelle(r: Node3D, p: Dictionary) -> void:
	var yellow := Mk.mat(C_YELLOW, 0.2, 0.5)
	var dark := Mk.mat(Color(0.12, 0.12, 0.13), 0.2, 0.8)
	Mk.box(r, Vector3(0.6, 0.6, 2.8), Vector3(-0.95, 0.3, 0), dark)
	Mk.box(r, Vector3(0.6, 0.6, 2.8), Vector3(0.95, 0.3, 0), dark)
	var house := Mk.pivot(r, Vector3(0, 0.65, 0))
	Mk.box(house, Vector3(2.2, 0.9, 2.3), Vector3(0, 0.45, 0.25), yellow)
	Mk.box(house, Vector3(0.95, 1.1, 1.0), Vector3(-0.55, 1.45, -0.35), Mk.mat(Color(0.5, 0.75, 0.9, 0.55), 0.6, 0.1))
	Mk.box(house, Vector3(1.0, 0.08, 1.05), Vector3(-0.55, 2.02, -0.35), yellow)
	Mk.cyl(house, 0.08, 0.8, Vector3(0.7, 1.3, 0.9), dark)
	var boom := Mk.pivot(house, Vector3(0.45, 1.0, -0.8))
	Mk.box(boom, Vector3(0.35, 0.4, 3.0), Vector3(0, 0, -1.5), yellow)
	var stick := Mk.pivot(boom, Vector3(0, 0, -3.0))
	Mk.box(stick, Vector3(0.28, 1.9, 0.3), Vector3(0, -0.95, 0), yellow)
	var bucket := Mk.pivot(stick, Vector3(0, -1.9, 0))
	Mk.box(bucket, Vector3(0.9, 0.5, 0.6), Vector3(0, -0.15, -0.25), dark)
	var load := Mk.box(bucket, Vector3(0.8, 0.2, 0.5), Vector3(0, 0.08, -0.25), Mk.needle_material())
	p.merge({"house": house, "boom": boom, "stick": stick, "bucket": bucket, "load": load, "h": 2.4})


static func _furnace_common(r: Node3D, p: Dictionary, wall: Material, h: float) -> void:
	var mouth := Mk.box(r, Vector3(1.0, 0.7, 0.08), Vector3(0, 0.75, -1.46), Mk.mat(Color(1, 0.45, 0.1), 0.0, 0.5, Color(1, 0.45, 0.05), 3.0))
	p["mouth"] = mouth
	var light := OmniLight3D.new()
	light.position = Vector3(0, 1.0, -2.1)
	light.light_color = Color(1, 0.55, 0.2)
	light.light_energy = 1.2
	light.omni_range = 4.5
	r.add_child(light)
	p["light"] = light
	_arrow(r, Vector3(0, 0.05, -1.75), Color(0.3, 0.6, 1.0))
	_arrow(r, Vector3(0, 0.05, 1.75), Color(0.3, 1.0, 0.4))
	p["h"] = h


static func _smoke(r: Node3D, pos: Vector3, color: Color) -> CPUParticles3D:
	var smoke := CPUParticles3D.new()
	smoke.position = pos
	smoke.amount = 20
	smoke.lifetime = 2.8
	smoke.direction = Vector3(0, 1, 0)
	smoke.spread = 12.0
	smoke.initial_velocity_min = 0.8
	smoke.initial_velocity_max = 1.4
	smoke.gravity = Vector3(0.3, 0.2, 0)
	smoke.scale_amount_min = 0.6
	smoke.scale_amount_max = 1.4
	var sm := SphereMesh.new()
	sm.radius = 0.4
	sm.height = 0.8
	sm.radial_segments = 8
	sm.rings = 4
	smoke.mesh = sm
	smoke.material_override = Mk.mat(color, 0.0, 1.0)
	r.add_child(smoke)
	return smoke


static func _fonderie(r: Node3D, p: Dictionary) -> void:
	var brick := Mk.mat(C_BRICK, 0.0, 0.95)
	Mk.box(r, Vector3(2.9, 2.3, 2.9), Vector3(0, 1.15, 0), brick)
	Mk.box(r, Vector3(3.0, 0.25, 3.0), Vector3(0, 2.4, 0), Mk.mat(C_DARK, 0.3, 0.6))
	Mk.cyl(r, 0.35, 2.4, Vector3(0.8, 3.6, 0.8), brick, 0.3)
	Mk.box(r, Vector3(1.2, 0.6, 0.5), Vector3(0, 0.6, 1.5), Mk.mat(Color(0.4, 0.42, 0.45), 0.6, 0.4))
	_furnace_common(r, p, brick, 2.6)
	p["smoke"] = _smoke(r, Vector3(0.8, 4.9, 0.8), Color(0.35, 0.35, 0.37, 0.45))


static func _purif(r: Node3D, p: Dictionary) -> void:
	var steel := Mk.mat(Color(0.8, 0.82, 0.86), 0.8, 0.25)
	Mk.box(r, Vector3(2.9, 0.3, 2.9), Vector3(0, 0.15, 0), Mk.mat(C_DARK, 0.4, 0.6))
	var rings: Array = []
	for x in [-0.7, 0.7]:
		Mk.cyl(r, 0.6, 2.3, Vector3(x, 1.45, 0.2), steel)
		Mk.sphere(r, 0.6, Vector3(x, 2.6, 0.2), steel)
		for y in [0.9, 1.6, 2.2]:
			rings.append(Mk.cyl(r, 0.63, 0.08, Vector3(x, y, 0.2), Mk.mat(Color(0.2, 0.6, 1), 0.0, 0.3, Color(0.2, 0.6, 1), 2.0), -1.0, 24))
	var pipe := Mk.cyl(r, 0.1, 1.4, Vector3(0, 2.1, 0.2), steel)
	pipe.rotation.z = PI / 2
	Mk.box(r, Vector3(1.0, 0.6, 0.5), Vector3(0, 0.6, -1.3), steel)
	p["rings"] = rings
	_arrow(r, Vector3(0, 0.05, -1.75), Color(0.3, 0.6, 1.0))
	_arrow(r, Vector3(0, 0.05, 1.75), Color(0.3, 1.0, 0.4))
	p["h"] = 3.0


static func _presse(r: Node3D, p: Dictionary) -> void:
	var blue := Mk.mat(Color(0.2, 0.32, 0.55), 0.4, 0.45)
	var steel := Mk.mat(C_STEEL, 0.8, 0.3)
	Mk.box(r, Vector3(2.9, 0.9, 2.9), Vector3(0, 0.45, 0), blue)
	for x in [-1.2, 1.2]:
		Mk.box(r, Vector3(0.35, 2.4, 0.6), Vector3(x, 1.6, 0), blue)
	Mk.box(r, Vector3(2.8, 0.4, 0.8), Vector3(0, 2.8, 0), blue)
	var ram := Mk.box(r, Vector3(2.0, 0.5, 0.7), Vector3(0, 1.9, 0), steel)
	p["ram"] = ram
	for x in [-0.6, 0.6]:
		var roll := Mk.cyl(r, 0.18, 2.6, Vector3(x * 1.5, 1.0, -1.1), steel)
		roll.rotation.z = PI / 2
		roll.position.x = 0
		roll.position.z = -1.1 + x * 0.4
	_arrow(r, Vector3(0, 0.05, -1.75), Color(0.3, 0.6, 1.0))
	_arrow(r, Vector3(0, 0.05, 1.75), Color(0.3, 1.0, 0.4))
	p["h"] = 3.0


static func _trefileuse(r: Node3D, p: Dictionary) -> void:
	var green := Mk.mat(Color(0.25, 0.5, 0.3), 0.3, 0.5)
	var copper := Mk.mat(Color(0.85, 0.55, 0.3), 0.8, 0.35)
	Mk.box(r, Vector3(2.9, 0.8, 2.9), Vector3(0, 0.4, 0), green)
	var spools: Array = []
	for i in 3:
		var s := Mk.cyl(r, 0.45, 0.35, Vector3(-0.9 + i * 0.9, 1.25, 0.3), copper)
		s.rotation.x = PI / 2
		spools.append(s)
	Mk.box(r, Vector3(2.4, 0.06, 0.06), Vector3(0, 1.25, -0.4), copper)
	Mk.box(r, Vector3(0.8, 1.2, 0.8), Vector3(1.0, 1.4, -0.8), green)
	p["spools"] = spools
	_arrow(r, Vector3(0, 0.05, -1.75), Color(0.3, 0.6, 1.0))
	_arrow(r, Vector3(0, 0.05, 1.75), Color(0.3, 1.0, 0.4))
	p["h"] = 2.2


static func _aiguilleuse(r: Node3D, p: Dictionary) -> void:
	var red := Mk.mat(Color(0.7, 0.18, 0.2), 0.3, 0.45)
	var steel := Mk.mat(C_STEEL, 0.8, 0.3)
	Mk.box(r, Vector3(2.9, 1.2, 2.9), Vector3(0, 0.6, 0), red)
	Mk.box(r, Vector3(2.2, 0.8, 1.6), Vector3(0, 1.6, 0.3), Mk.mat(Color(0.85, 0.85, 0.9, 0.5), 0.5, 0.1))
	var head := Mk.box(r, Vector3(0.4, 0.6, 0.4), Vector3(0, 2.0, 0.3), steel)
	p["head"] = head
	var needles := MultiMeshInstance3D.new()
	var mm := MultiMesh.new()
	mm.transform_format = MultiMesh.TRANSFORM_3D
	mm.mesh = Mk.needle_mesh(0.4, 0.008)
	mm.instance_count = 12
	for i in 12:
		mm.set_instance_transform(i, Transform3D(Basis(Vector3.FORWARD, PI / 2), Vector3(-0.8 + i * 0.14, 1.25, 0.3)))
	needles.multimesh = mm
	needles.material_override = Mk.needle_material()
	r.add_child(needles)
	_arrow(r, Vector3(0, 0.05, -1.75), Color(0.3, 0.6, 1.0))
	_arrow(r, Vector3(0, 0.05, 1.75), Color(0.3, 1.0, 0.4))
	p["h"] = 2.4


static func _tampon(r: Node3D, p: Dictionary) -> void:
	var frame := Mk.mat(Color(0.3, 0.32, 0.35), 0.5, 0.5)
	for x in [-1.35, 1.35]:
		for z in [-1.35, 1.35]:
			Mk.box(r, Vector3(0.12, 2.2, 0.12), Vector3(x, 1.1, z), frame)
	for y in [0.3, 1.1, 1.9]:
		Mk.box(r, Vector3(2.8, 0.08, 2.8), Vector3(0, y, 0), frame)
	var crates: Array = []
	var cm := Mk.mat(C_WOOD.lightened(0.1), 0.0, 0.9)
	for i in 9:
		crates.append(Mk.box(r, Vector3(0.75, 0.6, 0.75), Vector3(-0.9 + (i % 3) * 0.9, 0.64 + (i / 3) * 0.8, 0), cm))
	p["crates"] = crates
	_arrow(r, Vector3(0, 0.05, -1.75), Color(0.3, 0.6, 1.0))
	_arrow(r, Vector3(0, 0.05, 1.75), Color(0.3, 1.0, 0.4))
	p["h"] = 2.2


static func _drone_pad(r: Node3D, p: Dictionary) -> void:
	Mk.cyl(r, 0.48, 0.1, Vector3(0, 0.05, 0), Mk.mat(C_DARK, 0.3, 0.6), -1.0, 20)
	Mk.cyl(r, 0.3, 0.02, Vector3(0, 0.11, 0), Mk.mat(C_YELLOW, 0.0, 0.6), -1.0, 20)
	p["h"] = 0.3


## Le drone lui-même (ajouté séparément, il vole).
static func create_drone() -> Node3D:
	var d := Node3D.new()
	Mk.box(d, Vector3(0.5, 0.18, 0.5), Vector3.ZERO, Mk.mat(Color(0.9, 0.9, 0.92), 0.4, 0.4))
	var rotors: Array = []
	for x in [-0.4, 0.4]:
		for z in [-0.4, 0.4]:
			Mk.box(d, Vector3(0.5, 0.04, 0.06), Vector3(x * 0.5, 0, z * 0.5), Mk.mat(C_DARK))
			var rot := Mk.box(d, Vector3(0.45, 0.02, 0.06), Vector3(x, 0.12, z), Mk.mat(C_DARK))
			rotors.append(rot)
	var load := Mk.box(d, Vector3(0.35, 0.15, 0.25), Vector3(0, -0.18, 0), Mk.needle_material())
	d.set_meta("parts", {"rotors": rotors, "load": load})
	return d


static func _trou(r: Node3D, p: Dictionary) -> void:
	# un grand puits : bord jaune et noir, parois qui plongent dans le noir
	Mk.box(r, Vector3(4.6, 0.02, 4.6), Vector3(0, 0.006, 0), Mk.mat(Color(0.02, 0.02, 0.025), 0.0, 1.0))
	var wall := Mk.mat(Color(0.12, 0.11, 0.1), 0.0, 1.0)
	for i in 4:
		var piv := Mk.pivot(r, Vector3.ZERO)
		piv.rotation.y = i * PI / 2
		var w := Mk.box(piv, Vector3(4.6, 0.05, 1.6), Vector3(0, -0.15, 1.75), wall)
		w.rotation.x = 0.55
	for i in 4:
		var piv2 := Mk.pivot(r, Vector3.ZERO)
		piv2.rotation.y = i * PI / 2
		for k in 10:
			var col := C_YELLOW if k % 2 == 0 else Color(0.08, 0.08, 0.08)
			Mk.box(piv2, Vector3(0.5, 0.18, 0.25), Vector3(-2.25 + k * 0.5, 0.09, 2.4), Mk.mat(col, 0.0, 0.7))
	var sign_post := Mk.box(r, Vector3(0.12, 3.0, 0.12), Vector3(2.7, 1.5, 2.7), Mk.mat(C_DARK))
	sign_post.name = "Poteau"
	var board := Mk.box(r, Vector3(1.8, 0.8, 0.08), Vector3(2.7, 3.2, 2.7), Mk.mat(Color(0.12, 0.35, 0.15), 0.0, 0.8))
	board.rotation.y = PI / 4
	var lbl := Mk.label(r, "VENTE", Vector3(2.7, 3.2, 2.7), 64, Color(1, 0.9, 0.4))
	lbl.name = "Vente"
	p["h"] = 0.4


static func _bureau(r: Node3D, p: Dictionary) -> void:
	var wall := Mk.mat(Color(0.86, 0.82, 0.7), 0.0, 0.9)
	Mk.box(r, Vector3(2.8, 2.4, 2.8), Vector3(0, 1.2, 0), wall)
	Mk.box(r, Vector3(3.1, 0.2, 3.1), Vector3(0, 2.5, 0), Mk.mat(Color(0.3, 0.45, 0.3), 0.0, 0.8))
	Mk.box(r, Vector3(1.2, 0.8, 0.05), Vector3(0, 1.4, -1.41), Mk.mat(Color(0.5, 0.75, 0.9, 0.7), 0.6, 0.1))
	Mk.box(r, Vector3(1.6, 0.9, 0.08), Vector3(0, 3.2, 0), Mk.mat(Color(0.12, 0.25, 0.15), 0.0, 0.8))
	Mk.box(r, Vector3(0.08, 0.9, 0.08), Vector3(0, 2.7, 0), Mk.mat(C_DARK))
	p["h"] = 2.5


## Anime une machine. active = elle a travaillé récemment.
static func animate(root: Node3D, type: String, t: float, active: bool, info: Dictionary) -> void:
	var p: Dictionary = root.get_meta("parts")
	var ph: float = root.get_meta("phase", 0.0)
	match type:
		"bras":
			var aim: float = info.get("aim", 0.0)
			var cyc := fmod(t * 0.7 + ph, 1.0) if active else 0.25
			var toward := 1.0 - smoothstep(0.35, 0.65, cyc) + smoothstep(0.9, 1.0, cyc)
			# vers le tas, puis vers le tapis de sortie (avant de la machine)
			p.turret.rotation.y = lerp_angle(0.0, aim, clampf(toward, 0.0, 1.0))
			var dip := sin(clampf(cyc / 0.4, 0.0, 1.0) * PI) if cyc < 0.4 else 0.0
			p.shoulder.rotation.x = -0.55 - dip * 0.45
			p.elbow.rotation.x = -0.9 - dip * 0.5
			p.load.visible = active and cyc > 0.3 and cyc < 0.85
		"pelle":
			var aim2: float = info.get("aim", 0.0)
			var c2 := fmod(t * 0.35 + ph, 1.0) if active else 0.3
			var toward2 := 1.0 - smoothstep(0.4, 0.6, c2) + smoothstep(0.9, 1.0, c2)
			p.house.rotation.y = lerp_angle(0.0, aim2, clampf(toward2, 0.0, 1.0))
			var dig := sin(clampf(c2 / 0.4, 0.0, 1.0) * PI) if c2 < 0.4 else 0.0
			p.boom.rotation.x = 0.25 - dig * 0.45
			p.stick.rotation.x = -0.2 + dig * 0.5
			p.bucket.rotation.x = 0.4 - dig * 0.9
			p.load.visible = active and c2 > 0.3 and c2 < 0.8
		"scanner":
			p.scan.position.y = 1.0 + sin(t * 4.0 + ph) * 0.55 if active else 1.6
			p.beam.visible = active
			p.lamp.visible = info.get("alert", false) or (active and fmod(t, 1.0) < 0.15)
		"fonderie":
			p.light.light_energy = (1.3 + 0.5 * sin(t * 13.0 + ph) + 0.3 * sin(t * 7.3)) if active else 0.2
			p.smoke.emitting = active
		"purif":
			for i in p.rings.size():
				p.rings[i].visible = (not active) or fmod(t * 2.0 + i * 0.33 + ph, 1.0) < 0.6
		"presse":
			p.ram.position.y = 1.9 - (absf(sin(t * 3.0 + ph)) * 0.7 if active else 0.0)
		"trefileuse":
			for s: MeshInstance3D in p.spools:
				s.rotation.y = t * 4.0 if active else 0.0
		"aiguilleuse":
			p.head.position.x = sin(t * 5.0 + ph) * 0.8 if active else 0.0
		"separateur":
			p.spin.rotation.y = t * 6.0
		"tremie":
			var f: float = info.get("fill", 0.0)
			p.heap.visible = f > 0.0
			p.heap.scale = Vector3(1.0, 0.08 + 0.4 * f, 1.0)
		"tampon":
			var n := int(ceil(float(info.get("fill", 0.0)) * p.crates.size()))
			for i in p.crates.size():
				p.crates[i].visible = i < n
