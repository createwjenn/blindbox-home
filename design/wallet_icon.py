"""Render the room wallet from bevelled leather, stitching, notes and a brass snap."""
from pathlib import Path
import bpy
from mathutils import Vector
bpy.ops.wm.read_factory_settings(use_empty=True)
def material(name,color,rough=.46,metal=0):
    m=bpy.data.materials.new(name);m.diffuse_color=(*color,1);m.use_nodes=True
    p=m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value=(*color,1);p.inputs['Roughness'].default_value=rough;p.inputs['Metallic'].default_value=metal
    if name=='Saddle leather':
        noise=m.node_tree.nodes.new('ShaderNodeTexNoise');noise.inputs['Scale'].default_value=155
        bump=m.node_tree.nodes.new('ShaderNodeBump');bump.inputs['Strength'].default_value=.16;bump.inputs['Distance'].default_value=.008
        m.node_tree.links.new(noise.outputs['Fac'],bump.inputs['Height']);m.node_tree.links.new(bump.outputs['Normal'],p.inputs['Normal'])
    return m
leather=material('Saddle leather',(.32,.095,.040)); welt=material('Burnished edge',(.10,.025,.01))
thread=material('Waxed linen',(.78,.46,.20));brass=material('Brushed brass',(.78,.44,.12),.24,.7)
paper=material('Cotton note',(.30,.48,.28));cream=material('Ticket',(.82,.62,.37))
def slab(name,xyz,size,mat,r=.05):
    bpy.ops.mesh.primitive_cube_add(size=1,location=xyz);o=bpy.context.object;o.name=name;o.dimensions=size
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    m=o.modifiers.new('Rolled corners','BEVEL');m.width=r;m.segments=6
    o.modifiers.new('Weighted normals','WEIGHTED_NORMAL');o.data.materials.append(mat);return o
slab('Back pocket',(0,.20,.02),(2.05,.22,1.35),leather,.19)
for i,m in enumerate([cream,paper]):
    o=slab('Notes',(0,.07-i*.05,.20+i*.09),(1.75,.05,1.15),m,.05);o.rotation_euler.y=.06-i*.15
slab('Leather edge',(0,-.04,0),(2.09,.30,1.34),welt,.15)
slab('Leather face',(0,-.17,0),(2.04,.23,1.28),leather,.13)
# Small capsule stitches along the hand-sewn edges.
for i in range(18):
    x=-.84+i*.1
    for z in [-.50,.50]: slab('Stitch',(x,-.29,z),(.061,.016,.014),thread,.006)
for side in [-.9,.9]:
    for i in range(8):slab('Stitch',(side,-.29,-.38+i*.108),(.014,.016,.065),thread,.006)
slab('Tab',(.83,-.27,.03),(.54,.20,.34),welt,.09)
slab('Tab leather',(.83,-.35,.03),(.51,.11,.29),leather,.05)
bpy.ops.mesh.primitive_uv_sphere_add(segments=32,ring_count=12,location=(.70,-.416,.035));o=bpy.context.object;o.scale=(.095,.035,.095);o.data.materials.append(brass)
def light(pos,power,size):
    bpy.ops.object.light_add(type='AREA',location=pos);o=bpy.context.object;o.rotation_euler=(-o.location).to_track_quat('-Z','Y').to_euler();o.data.energy=power;o.data.shape='DISK';o.data.size=size
light((-3,-4,5),490,4);light((3,-2,2),280,3);light((0,3,3),380,3)
bpy.ops.object.camera_add(location=(2.3,-7,2.45));cam=bpy.context.object;cam.rotation_euler=(Vector((0,0,.05))-cam.location).to_track_quat('-Z','Y').to_euler();cam.data.type='ORTHO';cam.data.ortho_scale=2.55
scene=bpy.context.scene;scene.camera=cam;scene.render.engine='CYCLES';scene.cycles.samples=20;scene.render.film_transparent=True
scene.render.resolution_x=320;scene.render.resolution_y=252;scene.render.resolution_percentage=100
scene.world=bpy.data.worlds.new('Studio');scene.world.color=(.35,.34,.32)
scene.render.filepath=str(Path(__file__).resolve().parents[1]/'app/src/main/res/drawable-nodpi/room_wallet.png')
bpy.ops.render.render(write_still=True)
