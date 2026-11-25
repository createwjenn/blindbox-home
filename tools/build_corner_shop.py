"""Build the furniture shop recessed into the loft's rear wall. Dimensions are in meters."""
from pathlib import Path
from math import pi, sin, cos, radians
import bpy
from mathutils import Matrix, Vector

DEST = Path(__file__).resolve().parents[1] / "app/src/main/assets/props"
bpy.ops.wm.read_factory_settings(use_empty=True)


def material(name, rgb, metallic=0, rough=.48):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    p = m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value = (*rgb, 1)
    p.inputs['Metallic'].default_value = metallic
    p.inputs['Roughness'].default_value = rough
    p.inputs['Emission Color'].default_value = (*rgb, 1)
    p.inputs['Emission Strength'].default_value = .23
    return m


wood = material('Oiled walnut', (.25,.088,.033))
rib = material('Honey oak', (.51,.26,.095))
cream = material('Oat canvas', (.94,.81,.6))
red = material('Vermilion enamel', (.59,.065,.032))
lining = material('Espresso alcove lining', (.075,.043,.028), rough=.85)
brass = material('Brushed brass', (.82,.5,.16), .55)
ink = material('Dark bronze', (.062,.045,.029))


def finish(name, mat, bevel=0):
    ob = bpy.context.object
    ob.name = name
    ob.data.materials.append(mat)
    if bevel:
        mod = ob.modifiers.new('Rounded edges','BEVEL')
        mod.width = bevel
        mod.segments = 3
        ob.modifiers.new('Weighted normals','WEIGHTED_NORMAL')
    return ob


def box(name, xyz, dims, mat, bevel=.008):
    bpy.ops.mesh.primitive_cube_add(size=1, location=xyz)
    bpy.context.object.dimensions = dims
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    return finish(name,mat,bevel)


def cyl(name, xyz, radius, depth, mat, rot=(0,0,0)):
    bpy.ops.mesh.primitive_cylinder_add(vertices=12 if radius < .015 else 48,radius=radius,depth=depth,location=xyz,rotation=rot)
    return finish(name,mat,min(.004,depth/5))


def label(string, xyz, size, mat):
    font=bpy.data.curves.new(string,'FONT')
    font.body=string
    font.size=size
    font.align_x='CENTER'
    font.align_y='CENTER'
    font.extrude=.0015
    ob=bpy.data.objects.new(string,font)
    bpy.context.collection.objects.link(ob)
    ob.location=xyz
    ob.rotation_euler.x=pi/2
    ob.data.materials.append(mat)


def export(name):
    bpy.ops.object.select_all(action='SELECT')
    bpy.context.view_layer.objects.active=bpy.context.selected_objects[0]
    bpy.ops.object.convert(target='MESH')
    bpy.ops.object.join()
    bpy.ops.object.origin_set(type='ORIGIN_CURSOR')
    bpy.ops.export_scene.gltf(filepath=str(DEST/name),export_format='GLB',export_animations=False)


# Positive Blender Y is behind the plaster face. Front trim laps the cut on all sides.
textures = DEST.parents[4] / 'design/reference-library'
def textured(mat, file):
    node = mat.node_tree.nodes.new('ShaderNodeTexImage')
    node.image = bpy.data.images.load(str(textures / file), check_existing=True)
    mat.node_tree.links.new(node.outputs['Color'], mat.node_tree.nodes['Principled BSDF'].inputs['Base Color'])

textured(wood, 'texture-wood-805332.png')
linen = material('Miniature ivory woven linen', (.84,.72,.54), rough=.88)
textured(linen, 'texture-fabric-E9E2CF.png')
leather = material('Miniature charcoal leather', (.045,.032,.026), rough=.42)
textured(leather, 'texture-leather-262222.png')

box('Alcove back', (0,.54,1.44), (1.62,.05,.94), lining)
for x in (-.796,.796):
    box('Side reveal', (x,.257,1.44), (.025,.56,.94), lining)
    box('Walnut casing', (x,-.010,1.44), (.045,.040,.967), wood)
box('Soffit', (0,.255,1.899), (1.61,.56,.035), wood)
box('Oak display sill', (0,.239,.988), (1.64,.61,.042), rib)
box('Upper casing', (0,-.011,1.90), (1.64,.048,.042), wood)
box('Shelf front', (0,-.048,.985), (1.64,.035,.05), wood)
for i in range(9):
    box('Striped canvas valance', ((i-4)*.174,.025,1.826), (.174,.021,.106), cream if i%2==0 else red,.002)
box('Walnut shop plaque',(0,-.028,2.127),(1.04,.055,.244),wood,.013)
label('S H O P',(0,-.060,2.129),.172,cream)
for x in (-.481,.481):
    for z in (2.043,2.211):
        cyl('Brass sign pin',(x,-.060,z),.009,.009,brass,(pi/2,0,0))

def miniature(asset, x, y, width, angle, fabric):
    # Reuse the shop catalogue mesh at dollhouse scale, grounding its lowest vertex on the sill.
    bpy.ops.import_scene.gltf(filepath=str(DEST.parent/'furniture'/f'{asset}.glb'))
    mesh, = [obj for obj in bpy.context.selected_objects if obj.type == 'MESH']
    mesh.data.transform(Matrix.Rotation(radians(angle),4,'Z') @ mesh.matrix_world)
    mesh.matrix_world = Matrix.Identity(4)
    coords = [v.co for v in mesh.data.vertices]
    lo = Vector(tuple(min(v[k] for v in coords) for k in range(3)))
    hi = Vector(tuple(max(v[k] for v in coords) for k in range(3)))
    factor = width/(hi.x-lo.x)
    mesh.scale = (factor,)*3
    mesh.location = (x-factor*(lo.x+hi.x)/2, y-factor*(lo.y+hi.y)/2,1.010-factor*lo.z)
    for slot in mesh.material_slots:
        if 'upholstery' in slot.material.name.lower():
            slot.material = fabric
        elif 'walnut' in slot.material.name.lower():
            slot.material = wood

miniature('ref05_11',-.49,.245,.46,9,linen)
miniature('ref06_09',.008,.295,.44,-18,leather)
# A separate upholstered ottoman and a three-legged turned side table.
cushion=box('Ottoman leather cushion',(.278,.114,1.168),(.195,.146,.059),leather,.026)
cushion.rotation_euler.z=-.22
cyl('Ottoman pedestal',(.278,.114,1.082),.012,.102,ink)
for angle in (0,pi/2):
    foot=box('Ottoman star base',(.278,.114,1.022),(.149,.014,.014),ink,.006)
    foot.rotation_euler.z=angle+.3
cyl('Side table top',(.61,.265,1.272),.116,.023,rib)
for angle in (0,2*pi/3,4*pi/3):
    lower=Vector((.61+.095*cos(angle),.265+.095*sin(angle),1.01))
    upper=Vector((.61+.051*cos(angle),.265+.051*sin(angle),1.265))
    direction=upper-lower
    leg=cyl('Splayed walnut leg',(lower+upper)/2,.009,direction.length,wood)
    leg.rotation_euler=direction.to_track_quat('Z','Y').to_euler()
export('corner-shop.glb')
