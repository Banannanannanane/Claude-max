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
	l.pixel_size = 0.008
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
