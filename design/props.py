"""Runtime props: glass vitrine for the museum plinth, the working crank, and a coin. See gumball_machine.py for the cabinet and stock."""
import bpy, bmesh, math, random, sys, struct, json
from mathutils import Vector
from pathlib import Path
here = Path(__file__).resolve().parent
out = Path(sys.argv[sys.argv.index('--out') + 1]) if '--out' in sys.argv else here.parent / "app/src/main/assets/props"
out.mkdir(parents=True, exist_ok=True)
def rgb(h):
    lin = lambda v: ((v + .055) / 1.055) ** 2.4 if v > .04045 else v / 12.92
    return tuple(lin(int(h[i:i + 2], 16) / 255) for i in (0, 2, 4)) + (1,)
def mat(n, h, rough=.5, metal=0, alpha=1, emit=0):
    m = bpy.data.materials.get(n) or bpy.data.materials.new(n); m.use_nodes = True
    p = m.node_tree.nodes.get("Principled BSDF"); p.inputs['Base Color'].default_value = rgb(h)
    p.inputs['Roughness'].default_value = rough; p.inputs['Metallic'].default_value = metal; p.inputs['Alpha'].default_value = alpha
    if emit: p.inputs['Emission Color'].default_value = rgb(h); p.inputs['Emission Strength'].default_value = emit
    if alpha < 1:
        try: m.surface_render_method = 'BLENDED'
        except Exception: m.blend_method = 'BLEND'
    return m
def box(n, c, s, m, bev=.004):
    bpy.ops.mesh.primitive_cube_add(size=1, location=c); o = bpy.context.object; o.scale = s
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True); o.name = n; o.data.materials.append(m)
    if bev: b = o.modifiers.new('b', 'BEVEL'); b.width = bev; b.segments = 2
    return o
def cyl(n, a, b, r, m, verts=40, r2=None):
    a, b = Vector(a), Vector(b); d = b - a
    bpy.ops.mesh.primitive_cone_add(vertices=verts, radius1=r, radius2=r if r2 is None else r2, depth=d.length, location=(a + b) / 2)
    o = bpy.context.object; o.rotation_euler = d.to_track_quat('Z', 'Y').to_euler(); o.name = n; o.data.materials.append(m); return o
def sphere(n, c, r, m, seg=48):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=seg, ring_count=seg // 2, radius=r, location=c); o = bpy.context.object
    for f in o.data.polygons: f.use_smooth = True
    o.name = n; o.data.materials.append(m); return o
def export(name):
    bpy.ops.object.select_all(action='SELECT'); bpy.context.view_layer.objects.active = bpy.context.selected_objects[0]
    bpy.ops.object.convert(target='MESH')
    if len(bpy.context.selected_objects) > 1: bpy.ops.object.join()
    bpy.context.object.name = name
    p = out / f"{name}.glb"
    bpy.ops.export_scene.gltf(filepath=str(p), export_format='GLB', export_yup=True, export_apply=True)
    data = p.read_bytes(); n = struct.unpack_from('<I', data, 12)[0]; doc = json.loads(data[20:20 + n])
    for m in doc.get('materials', []):  # same dim-emulator ambient term as the room
        if 'emissiveFactor' in m or m.get('alphaMode') == 'BLEND': continue
        pb = m.get('pbrMetallicRoughness', {}); m['emissiveFactor'] = [x * .39 for x in pb.get('baseColorFactor', [1] * 4)[:3]]
    j = json.dumps(doc, separators=(',', ':')).encode(); j += b' ' * (-len(j) % 4); tail = data[20 + n:]
    p.write_bytes(struct.pack('<4sIIII', b'glTF', 2, 20 + len(j) + len(tail), len(j), 0x4e4f534a) + j + tail)
    bpy.ops.object.select_all(action='SELECT'); bpy.ops.object.delete()
bpy.ops.wm.read_factory_settings(use_empty=True)
glass = mat('Museum glass', 'AACDD4', .12, .08, .20)
edge = mat('Polished glass edge', '9BCAD0', .18, .22, emit=.25)
glint = mat('Softbox reflection', 'E4F4F6', .18, .05, emit=.38)
chrome = mat('Chrome', 'D7DADD', .14, .9); red = mat('Slop red enamel', 'C42F27', .35, .15); cream = mat('Cream enamel', 'F1E6CF', .45)
ink = mat('Ink', '1E1B1A', .5); gold = mat('Coin gold', 'E2B23C', .25, .9); pink = mat('Rare pink', 'EE7AA8', .4); gild = mat('Secret gold', 'E7C04A', .25, .8)
sign = mat('Sign glow', 'FFD96B', .4, 0, 1, 1.4)
W, D, H = .48, .48, .58
for n, c, s in [('front', (0, -D / 2, H / 2), (W, .006, H)), ('back', (0, D / 2, H / 2), (W, .006, H)),
                ('left', (-W / 2, 0, H / 2), (.006, D, H)), ('right', (W / 2, 0, H / 2), (.006, D, H)), ('top', (0, 0, H), (W, D, .006))]:
    box('Vitrine ' + n, c, s, glass, 0)
for sx in (-1, 1):
    for sy in (-1, 1): box('Vitrine edge', (sx * W / 2, sy * D / 2, H / 2), (.008, .008, H), edge, 0)
for z in (0, H):
    for side in (-1, 1):
        box('Horizontal glass edge', (0, side * D / 2, z), (W, .005, .005), edge, 0)
        box('Side glass edge', (side * W / 2, 0, z), (.005, D, .005), edge, 0)
export('vitrine')
cyl('Crank dial', (0, 0, 0), (0, -.03, 0), .085, chrome, 48)
for k in range(3):
    a = k * math.tau / 3; box('Crank spoke', (math.cos(a) * .045, -.035, math.sin(a) * .045), (.09, .02, .028), chrome, .006).rotation_euler = (0, -a, 0)
cyl('Crank grip', (.06, -.035, 0), (.06, -.10, 0), .018, red)
export('gacha_crank')
cyl('Coin', (0, 0, -.0025), (0, 0, .0025), .022, gold, 48)
cyl('Coin face', (0, 0, .0025), (0, 0, .0032), .016, gold, 48)
export('coin')
print('props written to', out)
