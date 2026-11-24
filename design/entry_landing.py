"""Author the 404 cloud entrance. Use --landing-only to export just the entrance."""
from pathlib import Path
import bpy, math, json, struct, sys
from mathutils import Vector
out=Path(__file__).resolve().parent.parent/'app/src/main/assets/atmosphere'
out.mkdir(exist_ok=True)
bpy.ops.wm.read_factory_settings(use_empty=True)
def mat(name, rgb, metal=0, glow=.52):
    color=tuple(((int(rgb[k:k+2],16)/255+.055)/1.055)**2.4 for k in (0,2,4))
    m=bpy.data.materials.new(name);m.use_nodes=True;p=m.node_tree.nodes['Principled BSDF']
    p.inputs['Base Color'].default_value=(*color,1);p.inputs['Metallic'].default_value=metal
    p.inputs['Roughness'].default_value=.43;p.inputs['Emission Color'].default_value=(*color,1);p.inputs['Emission Strength'].default_value=glow
    return m
def finish(o,name,m):o.name=name;o.data.materials.append(m);return o
def box(name,loc,size,m,bevel=.004):
    bpy.ops.mesh.primitive_cube_add(size=1,location=loc);o=bpy.context.object;o.dimensions=size
    bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if bevel:
        b=o.modifiers.new('Joinery bevel','BEVEL');b.width=bevel;b.segments=3
        o.modifiers.new('Normals','WEIGHTED_NORMAL')
    return finish(o,name,m)
def ellipsoid(name,loc,scale,m):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=24,ring_count=12,location=loc);o=bpy.context.object;o.scale=scale
    for f in o.data.polygons:f.use_smooth=True
    return finish(o,name,m)
def rod(name,a,b,r,m):
    a,b=Vector(a),Vector(b);d=b-a
    bpy.ops.mesh.primitive_cylinder_add(vertices=32,radius=r,depth=d.length,location=(a+b)/2)
    o=bpy.context.object;o.rotation_euler=d.to_track_quat('Z','Y').to_euler();return finish(o,name,m)
def export(name):
    bpy.ops.object.select_all(action='SELECT');bpy.ops.object.convert(target='MESH')
    for m in list(bpy.data.materials):
        group=[o for o in bpy.context.scene.objects if o.type=='MESH' and o.active_material==m]
        if len(group)<2:continue
        bpy.ops.object.select_all(action='DESELECT')
        for o in group:o.select_set(True)
        bpy.context.view_layer.objects.active=group[0];bpy.ops.object.join()
    bpy.ops.export_scene.gltf(filepath=str(out/(name+'.glb')),export_format='GLB')
    bpy.ops.object.select_all(action='SELECT');bpy.ops.object.delete()
red=mat('Oxblood lacquer','B82B22',.08,.76);inset=mat('Inset vermilion','A52620',.06,.70)
trim=mat('Door moulding','CE3B2B',.12,.75);shadow=mat('Ebony','342720')
walnut=mat('Oiled walnut','63402B')
brass=mat('Satin brass','CDAB67',.52,.48)
D=0
# Open jamb: the doorway must remain hollow when the leaf swings inward.
for x in [-.665,.665]:
    box('Door jamb',(x,D+.04,1.25),(.09,.20,2.52),walnut,.018)
box('Door lintel',(0,D+.04,2.465),(1.42,.20,.09),walnut,.018)
# A deep, dark recess occludes the sky, with enough depth for the inward leaf.
interior=mat('Unlit dark doorway','080706',0,0)
box('Dark rear of doorway',(0,D+1.45,1.25),(1.42,.06,2.52),interior,0)
for x in [-.70,.70]:box('Dark doorway side',(x,D+.74,1.25),(.05,1.45,2.52),interior,0)
for z in [0,2.50]:box('Dark doorway soffit',(0,D+.74,z),(1.42,1.45,.05),interior,0)
fixed=list(bpy.context.scene.objects)
box('Front door',(0,D-.063,1.21),(1.19,.068,2.38),red,.008)
def panel(x,z,w,h):
    box('Field',(x,D-.102,z),(w,.012,h),inset)
    for side in [-1,1]:
        box('Bolection moulding',(x+side*w/2,D-.115,z),(.024,.018,h+.024),trim)
        box('Bolection moulding',(x,D-.115,z+side*h/2),(w,.018,.024),trim)
for x in [-.245,.245]:panel(x,.53,.39,.72)
panel(0,1.63,.88,1.12)
box('Number plaque',(0,D-.140,1.87),(.38,.035,.185),brass,.013)
bpy.ops.object.text_add(location=(0,D-.16,1.87),rotation=(math.pi/2,0,0));label=bpy.context.object
label.data.body='404';label.data.align_x='CENTER';label.data.align_y='CENTER';label.data.size=.141;label.data.extrude=.001
label.data.materials.append(shadow)
for x in [-.168,.168]:ellipsoid('Plaque screw',(x,D-.161,1.87),(.005,.003,.005),shadow)
rod('Rosette',(.457,D-.095,1.02),(.457,D-.126,1.02),.065,brass)
rod('Spindle',(.457,D-.126,1.02),(.457,D-.178,1.02),.022,brass)
ellipsoid('Round knob',(.457,D-.197,1.02),(.059,.057,.059),brass)
# Keep the fixed jamb and hinged leaf separate, with the original entry anchor.
leaf=[o for o in bpy.context.scene.objects if o not in fixed]
bpy.ops.object.select_all(action='DESELECT')
for o in leaf:o.select_set(True)
bpy.context.view_layer.objects.active=leaf[0]
bpy.ops.object.convert(target='MESH');bpy.ops.object.join()
leaf=bpy.context.object
bpy.context.scene.cursor.location=(-.595,0,0)
bpy.ops.object.origin_set(type='ORIGIN_CURSOR')
leaf.name='Door leaf'
bpy.ops.object.empty_add(location=(0,4.12,.86));door=bpy.context.object;door.name='Entry door'
for o in fixed+[leaf]:o.parent=door

# Unboxing blue is visible immediately while the live cirrus shader attaches.
bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=48, radius=48, location=(0, 0, 1.55))
sky = bpy.context.object
sky.name = 'Sky blue atmosphere'
mesh = sky.data
colors = mesh.color_attributes.new(name='Sky', type='FLOAT_COLOR', domain='POINT')
for v in mesh.vertices:
    # Blender +Y points towards the entrance door; after glTF export this is -Z.
    h = max(0., (v.co.z*math.cos(.69755)+v.co.y*math.sin(.69755)) / 48.) ** .7
    tint = [(low+(high-low)*h)/255. for low,high in zip((101,159,214),(65,108,175))]
    colors.data[v.index].color = tuple(((c+.055)/1.055)**2.4 for c in tint) + (1.,)
skyMaterial = bpy.data.materials.new('Atmospheric gradient')
skyMaterial.use_nodes = True
vertex = skyMaterial.node_tree.nodes.new('ShaderNodeVertexColor')
vertex.layer_name = 'Sky'
skyMaterial.node_tree.links.new(vertex.outputs['Color'],skyMaterial.node_tree.nodes['Principled BSDF'].inputs['Base Color'])
sky.data.materials.append(skyMaterial)
path=out/'landing.glb'
bpy.ops.export_scene.gltf(filepath=str(path),export_format='GLB',export_yup=True)
raw=path.read_bytes()
size=struct.unpack_from('<I',raw,12)[0]
doc=json.loads(raw[20:20+size])
material=next(m for m in doc['materials'] if m['name']=='Atmospheric gradient')
material['extensions']={'KHR_materials_unlit':{}}
material['doubleSided']=True
for m in doc['materials']:
    if m['name']=='Unlit dark doorway':
        m['extensions']={'KHR_materials_unlit':{}}
        m['doubleSided']=True

doc['extensionsUsed']=sorted(set(doc.get('extensionsUsed',[]))|{'KHR_materials_unlit'})
header=json.dumps(doc,separators=(',',':')).encode()
header+=b' '*(-len(header)%4)
tail=raw[20+size:]
path.write_bytes(struct.pack('<4sIIII',b'glTF',2,20+len(header)+len(tail),len(header),0x4e4f534a)+header+tail)
if '--landing-only' in sys.argv:
    raise SystemExit(0)
bpy.ops.wm.read_factory_settings(use_empty=True)

def silhouette(name,verts,faces,m):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(verts,[],faces);mesh.update()
    o=bpy.data.objects.new(name,mesh);bpy.context.scene.collection.objects.link(o);finish(o,name,m)
plume=mat('Swallow feathers','384252')
silhouette('Banked swallow',[(0,-.16,0),(.05,.01,.02),(.24,.015,.08),(.48,-.06,.15),(.24,.12,.04),(.04,.10,0),(.08,.23,-.02),(0,.16,0),(-.08,.23,-.02),(-.04,.10,0),(-.24,.12,.04),(-.48,-.06,.15),(-.24,.015,.08),(-.05,.01,.02)],[(0,1,5,7,9,13),(1,2,3,4,5),(9,10,11,12,13),(5,6,7),(7,8,9)],plume)
export('swallow')
alloy=mat('Distant aircraft','DBDFE3',.12)
ellipsoid('Fuselage',(0,0,0),(.10,.89,.105),alloy)
silhouette('Wings',[(0,-.30,.02),(.98,.25,.045),(.98,.34,.05),(0,.08,0),(-.98,.34,.05),(-.98,.25,.045),(0,.56,.02),(.35,.80,.05),(-.35,.80,.05),(0,.84,.32),(0,.85,.02)],[(0,1,2,3),(3,4,5,0),(6,7,10,8),(6,9,10)],alloy)
export('airliner')
# Two vertically tiled copies of each batch animate hundreds of drops/flakes in two draw calls.
import random
for weather in ['rain','snow']:
    rng=random.Random(482)
    verts=[];faces=[]
    for i in range(570 if weather=='rain' else 420):
        if i%4:
            x,y=rng.uniform(-10,-4.48),rng.uniform(-9,9)
        else:
            x,y=rng.uniform(-4.4,3.8),rng.uniform(4.55,9)
        z=rng.uniform(0,6)
        h=rng.uniform(.065,.18) if weather=='rain' else rng.uniform(.009,.028)
        w=rng.uniform(.003,.008) if weather=='rain' else h*.72
        lean=h*.12 if weather=='rain' else 0
        j=len(verts)
        verts.extend([(x,y+lean,z+h),(x+w,y,z),(x,y+w,z),(x-w,y,z),(x,y-w,z),(x,y-lean,z-h)])
        for k in range(4):
            a=j+1+k;b=j+1+(k+1)%4;faces.extend([(j,a,b),(j+5,b,a)])
    silhouette(weather,verts,faces,mat(weather,'A3B4C4' if weather=='rain' else 'E5E9E7',0,.72))
    export(weather)
