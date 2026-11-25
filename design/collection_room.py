"""Build the Y-up PICO collection room (Blender uses Z-up) and render its three design views."""
import bpy, math, random, sys, struct, json
import numpy as np
from PIL import Image
from mathutils import Vector
from pathlib import Path

here = Path(__file__).resolve().parent
assets = here.parent / "app/src/main/assets"
bpy.ops.wm.read_factory_settings(use_empty=True)
rng = random.Random(48)
def rgb(h):
    def linear(v): return ((v+.055)/1.055)**2.4 if v>.04045 else v/12.92
    return tuple(linear(int(h[i:i+2],16)/255) for i in (0,2,4))+(1,)
def material(name, h, rough=.65, metallic=0):
    m=bpy.data.materials.new(name); m.use_nodes=True
    p=m.node_tree.nodes.get("Principled BSDF")
    p.inputs['Base Color'].default_value=rgb(h); p.inputs['Roughness'].default_value=rough
    p.inputs['Metallic'].default_value=metallic
    return m
walnut=material("Quarter-cut American walnut","825035",.42)
# Long-grain veneer with flowing growth rings and small pore variation.
yy,xx=np.mgrid[0:512,0:1024]; noise=np.random.default_rng(774)
phase=yy*.26+3.3*np.sin(xx*.007+yy*.035)+1.2*np.sin(xx*.015+yy*.054)
grain=.95+.09*np.sin(phase)+.055*np.sin(phase*3)+.028*noise.normal(size=yy.shape)
path=Path('/tmp/slop-walnut.jpg'); Image.fromarray(np.clip(grain[:,:,None]*np.array([153,99,65]),0,255).astype('uint8')).save(path,quality=93)
img=bpy.data.images.load(str(path));img.pack()
tex=walnut.node_tree.nodes.new('ShaderNodeTexImage');tex.image=img
walnut.node_tree.links.new(tex.outputs['Color'],walnut.node_tree.nodes.get('Principled BSDF').inputs['Base Color'])
cream=material("Ivory limewash","E9DEC8"); olive=material("Olive lacquer","666B45")
brass=material("Brushed honey brass","B88D51",.36,.48); ink=material("Espresso","322B26")
rose=material("Ochre upholstery","BF6E41"); linen=material("Natural linen","D5C4A4")
teal=material("Petrol wool","385A54"); plaster=material("Clay plaster","CAA788")
glass=material("Opal glass","FFE5B4"); lampmat=glass.node_tree.nodes.get("Principled BSDF")
lampmat.inputs['Emission Color'].default_value=rgb("FFDAA1");lampmat.inputs['Emission Strength'].default_value=.75
def finish(o,n,m):
    o.name=n; o.data.materials.append(m);return o
def block(n,xyz,size,m,edge=.012):
    bpy.ops.mesh.primitive_cube_add(size=1,location=xyz);o=bpy.context.object
    o.dimensions=size;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True)
    if m==walnut:
        # Project onto the grain axis of each board, including vertical stiles.
        axis=max(range(3),key=lambda i:size[i])
        uv=o.data.uv_layers.active
        for poly in o.data.polygons:
            normal=max(range(3),key=lambda i:abs(poly.normal[i]))
            u=axis if axis!=normal else (axis+1)%3;v=3-u-normal
            for i in poly.loop_indices:
                co=o.data.vertices[o.data.loops[i].vertex_index].co
                uv.data[i].uv=(co[u]/1.4+xyz[0],co[v]/.55+xyz[2])
    if edge:
        bevel=o.modifiers.new('Softened edges','BEVEL');bevel.width=min(edge,min(size)*.4);bevel.segments=3
        normals=o.modifiers.new('Normals','WEIGHTED_NORMAL')
    return finish(o,n,m)
def rod(n,a,b,r,m,r2=None):
    a,b=Vector(a),Vector(b);d=b-a
    bpy.ops.mesh.primitive_cone_add(vertices=28,radius1=r,radius2=r if r2 is None else r2,depth=d.length,location=(a+b)/2)
    o=bpy.context.object;o.rotation_euler=d.to_track_quat('Z','Y').to_euler();return finish(o,n,m)
def ball(n,p,s,m):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=32,ring_count=16,location=p);o=bpy.context.object;o.scale=s
    for f in o.data.polygons:f.use_smooth=True
    return finish(o,n,m)
# Continuous room envelope, including the player's rear hemisphere.
block('Oak subfloor',(0,0,-.065),(8.8,9,.12),ink)
floor=[material('Oiled oak '+str(i),c) for i,c in enumerate(['AC8061','BA9070','B18768','B98C68'])]
for i in range(36):
    x=-4.375+(i+.5)*.243
    y=-4.5
    for length in [1.2+(i%3)*.7,3.05,3.05,3.05]:
        length=min(length,4.5-y)
        if length<=0:break
        block('Oak plank',(x,y+length/2,-.014),(.241,length-.006,.028),rng.choice(floor),.001);y+=length
# Leave the shop window open on both faces of the wall. The separate shop asset supplies the reveals.
for left, right, bottom, top in ((-4.40,2.31,0,4.24), (3.91,4.40,0,4.24),
                                  (2.31,3.91,0,.98), (2.31,3.91,1.90,4.24)):
    block('Entrance wall',((left+right)/2,-4.45,(bottom+top)/2),(right-left,.12,top-bottom),cream)

block('Lounge wall',(4.34,0,2.12),(.12,9,4.24),cream)
smoked = material('Smoked chestnut beams', '463027', .68)
block('Walnut ceiling',(0,0,4.255),(8.8,9,.09),smoked)
for x in (-4.26,4.26):block('Skirting',(x,0,.07),(.05,8.8,.14),walnut)
for y in (-4.36,4.36):block('Skirting',(0,y,.07),(8.55,.05,.14),walnut)
# Listening console: three galvanized bays under one walnut top, styled after Jenn's reference photo.
import bmesh
textures = here / "textures"
steel = material("Galvanized steel", "A7ACAD", .42, .55)
chrome = material("Polished chrome", "D4D7DA", .16, .72)
marble = material("Carrara marble", "ECEAE6", .22)
orange = material("Orange acrylic", "E8520C", .18)
op = orange.node_tree.nodes.get("Principled BSDF")
op.inputs['Emission Color'].default_value = rgb("FF8A3D"); op.inputs["Emission Strength"].default_value = .3
vinyl = material("Black vinyl", "141414", .3); grille = material("Speaker cloth", "1B1B1C", .9)
alu = material("Brushed aluminium", "BFC2C4", .32, .6); leaf = material("Monstera leaf", "2F5A2C", .55)
vase = material("Smoked glass", "B9C4BD", .08); offwhite = material("Warm off-white", "E7DFD4")
mirror = material("Convex mirror", "DDE3E6", .04, .95)
def picture(n, img, x, z, w, h, y=4.357):
    m = bpy.data.materials.new(n); m.use_nodes = True; p = m.node_tree.nodes.get("Principled BSDF")
    p.inputs['Roughness'].default_value = .5
    t = m.node_tree.nodes.new('ShaderNodeTexImage'); t.image = bpy.data.images.load(str(textures / img)); t.image.pack()
    m.node_tree.links.new(t.outputs['Color'], p.inputs['Base Color'])
    bpy.ops.mesh.primitive_plane_add(size=1, location=(x, y, z), rotation=(math.pi / 2, 0, 0))
    o = bpy.context.object; o.scale = (w, h, 1); bpy.ops.object.transform_apply(scale=True, rotation=True, location=False)
    return finish(o, n, m)
def frame(x, z, w, h, m, t=.025):
    for dz in (-1, 1): block('Poster frame', (x, 4.372, z + dz * (h / 2 + t / 2)), (w + 2 * t, .03, t), m, .004)
    for dx in (-1, 1): block('Poster frame', (x + dx * (w / 2 + t / 2), 4.372, z), (t, .03, h), m, .004)
    block('Mat board', (x, 4.383, z), (w, .006, h), m, 0)
def tube(n, pts, r, m):
    cu = bpy.data.curves.new(n, 'CURVE'); cu.dimensions = '3D'; cu.bevel_depth = r; cu.bevel_resolution = 4
    sp = cu.splines.new('POLY'); sp.points.add(len(pts) - 1)
    for p, c in zip(sp.points, pts): p.co = (*c, 1)
    o = bpy.data.objects.new(n, cu); bpy.context.scene.collection.objects.link(o)
    o.data.materials.append(m); o.name = n; return o
def dome(n, p, r, m, squash=1):
    o = ball(n, p, (r, r, r * squash), m); bm = bmesh.new(); bm.from_mesh(o.data)
    bmesh.ops.delete(bm, geom=[v for v in bm.verts if v.co.z < -1e-4], context='VERTS')
    bm.to_mesh(o.data); bm.free(); o.data.materials.clear(); o.data.materials.append(m)
    o.modifiers.new('Shell', 'SOLIDIFY').thickness = .02; return o
block('Shelf wall', (.72, 4.45, 2.12), (7.22, .12, 4.24), offwhite)
top = .905
for i in range(3):
    cx = (i - 1) * .99
    for sx in (-1, 1):
        for y in (3.93, 4.31):
            block('Square steel post', (cx + sx * .47, y, (top - .045) / 2), (.03, .03, top - .045), steel, .004)
            for z in (.10, .41): block('Side rail', (cx + sx * .47, 4.12, z - .02), (.03, .38, .022), steel, .003)
            block('Top side rail', (cx + sx * .47, 4.12, top - .06), (.03, .38, .03), steel, .003)
    for y in (3.93, 4.31): block('Top rail', (cx, y, top - .06), (.91, .03, .03), steel, .003)
    for z in (.10, .41):
        block('Galvanized shelf', (cx, 4.12, z), (.93, .38, .012), steel, .002)
        block('Shelf lip', (cx, 3.937, z - .012), (.91, .012, .03), steel, .002)
    for y in (3.93, 4.31):
        for sx in (-1, 1): rod('Levelling foot', (cx + sx * .47, y, 0), (cx + sx * .47, y, .012), .018, ink)
block('Walnut console top', (0, 4.12, top - .0225), (3.0, .44, .045), walnut, .008)
# Hi-fi speakers in walnut cabinets either side.
for x in (-1.95, 1.95):
    block('Speaker cabinet', (x, 4.15, .32), (.32, .36, .64), walnut, .01)
    block('Speaker grille', (x, 3.966, .305), (.295, .012, .6), grille, .004)
    block('Badge', (x + .09, 3.958, .56), (.05, .004, .012), alu, 0)
# Monstera in a glass cylinder on the left speaker.
rod('Glass vase', (-1.95, 4.15, .64), (-1.95, 4.15, .86), .075, vase)
rod('Soil', (-1.95, 4.15, .645), (-1.95, 4.15, .70), .07, ink)
for k in range(8):
    a = k * 2.39; h = .98 + .06 * (k % 3); lx, ly = -1.95 + .2 * math.cos(a), 4.1 + .12 * math.sin(a)
    rod('Stem', (-1.95, 4.15, .78), (lx, ly, h), .005, leaf)
    o = ball('Leaf', (lx, ly, h + .03), (.085, .06, .012), leaf); o.rotation_euler = (.9 * math.sin(a), .7 * math.cos(a), a)
# Arc floor lamp: marble base, chrome arc, orange acrylic dome.
rod('Marble base', (-2.75, 3.95, 0), (-2.75, 3.95, .07), .2, marble)
bx, bz, ex, ez = -2.75, .07, -1.42, 1.98
pts = []
for j in range(41):
    t = j / 40; c = (bx, 2.85)
    if t < .3: pts.append((bx, 3.95, bz + (1.35 - bz) * t / .3))
    else:
        s = (t - .3) / .7; x = (1 - s) ** 2 * bx + 2 * (1 - s) * s * c[0] + s * s * ex
        z = (1 - s) ** 2 * 1.35 + 2 * (1 - s) * s * c[1] + s * s * ez; pts.append((x, 3.95, z))
tube('Chrome arc', pts, .014, chrome)
dome('Orange shade', (-1.42, 3.95, 1.70), .27, orange, .9)
rod('Shade cap', (-1.42, 3.95, 1.93), (-1.42, 3.95, 1.99), .03, chrome)
ball('Lamp bulb', (-1.42, 3.95, 1.70), (.07, .07, .07), glass)
# Mushroom table lamp.
rod('Glass foot', (-1.18, 4.12, top), (-1.18, 4.12, top + .02), .085, vase)
rod('Lamp stem', (-1.18, 4.12, top + .02), (-1.18, 4.12, top + .3), .009, chrome)
dome('Opal mushroom', (-1.18, 4.12, top + .29), .105, glass, .85)
# Turntable.
block('Turntable plinth', (-.55, 4.12, top + .04), (.46, .36, .08), alu, .01)
rod('Platter', (-.6, 4.12, top + .08), (-.6, 4.12, top + .095), .15, vinyl)
rod('Record label', (-.6, 4.12, top + .095), (-.6, 4.12, top + .098), .045, cream)
rod('Tonearm pivot', (-.38, 4.24, top + .08), (-.38, 4.24, top + .12), .02, chrome)
rod('Tonearm', (-.38, 4.24, top + .115), (-.52, 4.04, top + .11), .005, chrome)
for sx in (-1, 1):
    for sy in (-1, 1): rod('Dust cover hinge', (-.55 + sx * .22, 4.12 + sy * .17, top + .08), (-.55 + sx * .22, 4.12 + sy * .17, top + .2), .003, chrome)
# Chrome wave rack holding a few record sleeves.
x0, L, turns, r = .08, 1.15, 5.5, .12
tube('Chrome wave rack', [(x0 + L * j / 300, 4.12 + r * .9 * math.cos(math.tau * turns * j / 300 + math.pi / 2),
     top + .012 + r * (1 - math.cos(math.tau * turns * j / 300))) for j in range(301)], .009, chrome)
sleeves = [("E9E1D2", "9AA0A6"), ("F1ECE2", "D6B27A"), ("2A2A2C", "C9C9C9"), ("3E4C7A", "E7C9A0"), ("F0E6D8", "C76B4F")]
for k, (c, accent) in enumerate(sleeves):
    x = x0 + .38 + k * .105; sm = material('Sleeve ' + str(k), c, .55); am = material('Sleeve art ' + str(k), accent, .55)
    o = block('Record sleeve', (x, 4.12, top + .165), (.008, .31, .31), sm, .002); o.rotation_euler = (0, -.32, 0)
    o = block('Sleeve art', (x - .006, 4.08, top + .2), (.002, .14, .12), am, 0); o.rotation_euler = (0, -.32, 0)
# Framed posters, a convex mirror and a wall calendar.
frame(-.62, 1.47, .62, .86, ink); picture('Poster: Unboxing', 'poster_lenses.png', -.62, 1.47, .62, .86)
frame(.92, 1.98, .66, .94, ink); picture('Poster: Slop', 'poster_flowers.png', .92, 1.98, .66, .94)
frame(.10, 2.48, .40, .50, walnut); picture('Print: Capybara', 'print_capybara.png', .10, 2.48, .40, .50)
picture('Wall calendar', 'calendar.png', 2.25, 1.32, .36, .54, 4.385)
rod('Calendar hook', (2.25, 4.39, 1.61), (2.25, 4.37, 1.61), .008, chrome)
rod('Mirror frame', (-1.72, 4.39, 2.58), (-1.72, 4.35, 2.58), .215, ink)
ball('Convex mirror', (-1.72, 4.35, 2.58), (.19, .045, .19), mirror)
# Entry opposite the listening corner.
block('Door surround',(-1.88,-4.36,1.14),(1.14,.12,2.28),ink)
block('Walnut entrance',(-1.88,-4.28,1.11),(1.04,.06,2.19),walnut)
ball('Door knob',(-1.50,-4.21,1.05),(.044,.04,.044),brass)
# West-facing factory windows. The glazing wraps the corner beside the retained hi-fi wall.
iron = material("Blackened window steel", "312F2C", .31, .42)
brick = material("Sandstone brick", "A08466", .91)
mortar = material("Recessed mortar", "897660", .92)
def brick_pier(x, y, w):
    block("Brick pier core", (x,y,2.12), (.24,w,4.24), mortar, .003)
    for row in range(41):
        z = (row+.5)*4.24/41
        for j in range(2 if row%2 else 1):
            count = 2 if row%2 else 1
            block("Pier stretcher", (x+.012,y+(j+.5-count/2)*w/count,z),
                  (.25,w/count-.009,.095), brick, .004)
for y,w in [(-4.39,.23),(-1.52,.30),(1.48,.30),(4.35,.25)]:
    brick_pier(-4.28,y,w)
for y in [-4.22,-2.91,-1.68,-1.35,0,1.31,1.64,2.92,4.24]:
    block("Steel window mullion",(-4.26,y,2.11),(.08,.043,4.22),iron,.004)
for z in [.045,1.17,3.48,4.19]:
    block("Steel transom",(-4.26,0,z),(.08,8.62,.048),iron,.004)
for x in [-4.26,-3.58,-2.89]:
    block("Corner light mullion",(x,4.37,2.11),(.04,.08,4.22),iron,.003)
for z in [.045,1.17,3.48,4.19]:
    block("Corner light transom",(-3.58,4.37,z),(1.39,.08,.045),iron,.003)

# Skyline: layered dimensional facades and cornices with individually lit window bays.
from PIL import ImageDraw
def mapped_material(name, image, rough=.8, emission=0):
    m=material(name,'FFFFFF',rough)
    path=Path('/tmp')/('blindbox-'+name.lower().replace(' ','-')+'.png')
    image.save(path); t=m.node_tree.nodes.new('ShaderNodeTexImage')
    t.image=bpy.data.images.load(str(path));t.image.pack()
    p=m.node_tree.nodes.get('Principled BSDF')
    m.node_tree.links.new(t.outputs['Color'],p.inputs['Base Color'])
    if emission:
        m.node_tree.links.new(t.outputs['Color'],p.inputs['Emission Color']);p.inputs['Emission Strength'].default_value=emission
    return m
def textured_face(name, coords, m, uv=((0,0),(1,0),(1,1),(0,1))):
    mesh=bpy.data.meshes.new(name);mesh.from_pydata(coords,[],[(0,1,2,3)]);mesh.update()
    layer=mesh.uv_layers.new()
    for i,p in enumerate(uv):layer.data[i].uv=p
    o=bpy.data.objects.new(name,mesh);bpy.context.scene.collection.objects.link(o);return finish(o,name,m)
# Preserve the same scene geometry for every light study.
from sf_skyline import build_skyline
sky = build_skyline()

# Deep walnut joists and the soft oak fascia above the sitting area.
for j in range(12):
    block("Exposed walnut joist",(0,-4.12+j*.748,4.09),(8.62,.102,.22),smoked,.007)
block("Walnut picture rail",(4.16,0,2.98),(.25,8.72,.38),walnut,.01)
for z in [2.81,2.84,2.87]:
    block("Fascia shadow line",(4.027,0,z),(.006,8.67,.009),ink,0)

# Textured boucle and wool. The woollen sofa has a continuous sculpted seat/back profile.
fabric_rng=np.random.default_rng(84)
def cloth(name,base,spread):
    y,x=np.mgrid[:384,:384]
    n=fabric_rng.normal(0,spread,(384,384)) + 3*np.sin(y*2.1)+2*np.sin(x*2.6)
    return mapped_material(name,Image.fromarray(np.uint8(np.clip(np.array(base)+n[:,:,None],0,255))),.94)
leather=cloth('Cognac ribbed hide',(171,120,70),4)
wool=cloth('Parchment loop wool',(201,186,159),13)
rust=cloth('Cinnabar linen',(167,74,42),6)
pillow=cloth('Flax cushion',(223,207,177),6)
cord=material('Saddle welt','926341',.85)
# Round sampled profile with a low nose, nestled seat, and wraparound raised back.
profile=[(-.51,.20),(-.57,.29),(-.54,.43),(-.43,.495),(-.21,.465),(.02,.413),(.12,.43),
         (.17,.565),(.25,.735),(.35,.82),(.445,.81),(.51,.70),(.51,.32),(.45,.21),(.21,.175),(-.31,.174)]
def section(t):
    i=int(t);f=t-i
    a,b,c,d=[np.array(profile[(i+k)%len(profile)]) for k in [-1,0,1,2]]
    return .5*((2*b)+(-a+c)*f+(2*a-5*b+4*c-d)*f*f+(-a+3*b-3*c+d)*f**3)
def cushion(cx,cy,width):
    nx,nt=52,64
    vertices=[];faces=[];uvs=[]
    for i in range(nx+1):
        u=i/nx; rounding=1-.14*math.exp(-min(u,1-u)*32)
        for j in range(nt):
            y,z=section(j*len(profile)/nt)
            flute=(.5+.5*math.cos(2*math.pi*(u*6+.12*math.sin(y*4))))**4
            z=.42+(z-.42)*rounding - .022*flute*min(1,max(0,(z-.29)*10))
            vertices.append((cx+(u-.5)*width,cy+y*rounding,z))
    for i in range(nx):
        for j in range(nt):
            k=(j+1)%nt; faces.append((i*nt+j,i*nt+k,(i+1)*nt+k,(i+1)*nt+j))
            uvs.append([(i/nx*3,j/nt*5),(i/nx*3,(j+1)/nt*5),((i+1)/nx*3,(j+1)/nt*5),((i+1)/nx*3,j/nt*5)])
    for i in [0,nx]:
        faces.append(tuple(i*nt+j for j in (range(nt) if i==nx else reversed(range(nt)))))
        uvs.append([(vertices[k][1]-cy,vertices[k][2]) for k in faces[-1]])
    mesh=bpy.data.meshes.new('Channeled upholstery');mesh.from_pydata(vertices,[],[tuple(reversed(face)) for face in faces]);mesh.update()
    uv=mesh.uv_layers.new()
    for face,coords in zip(mesh.polygons,uvs):
        face.use_smooth=True
        for loop,coord in zip(face.loop_indices,reversed(coords)):uv.data[loop].uv=coord
    o=bpy.data.objects.new('Channeled sofa',mesh);bpy.context.scene.collection.objects.link(o);finish(o,o.name,leather)
    for side in [0,nx]:
        pts=vertices[side*nt:(side+1)*nt]
        tube('Piped seam',pts+[pts[0]],.004,cord)
for x in [1.12,2.14,3.16]:cushion(x,1.69,1.016)
block('Floating sofa base',(2.14,1.71,.13),(2.86,.79,.14),walnut,.045)
for x,y,angle,m in [(1.24,1.79,-13,pillow),(1.56,1.70,14,rust),(3.30,1.84,-14,pillow)]:
    o=block('Loose throw cushion',(x,y,.625),(.39,.14,.36),m,.065);o.rotation_euler=(math.radians(-20),math.radians(angle),.04)
block('Lounge carpet',(2.06,.67,.015),(3.96,2.76,.03),wool,.014)
# Transparent coffee table, green laminated edges and a pair of crossed walnut trestles.
tableglass=material('Float glass','ABCFCA',.10,.08)
shader=tableglass.node_tree.nodes.get('Principled BSDF');shader.inputs['Alpha'].default_value=.17
tableglass.surface_render_method='BLENDED';glass_edge=material('Polished bottle green edge','718E83',.2,.26)
block('Glass tabletop',(2.10,.33,.402),(1.57,.69,.018),tableglass,.008)
for y in [-.012,.672]:block('Glass long edge',(2.10,y,.401),(1.56,.005,.013),glass_edge,.002)
for x in [1.319,2.881]:block('Glass short edge',(x,.33,.401),(.005,.68,.013),glass_edge,.002)
for x,angle in [(1.76,.36),(2.44,-.36)]:
    foot=block('Trestle',(x,.33,.207),(.125,.50,.37),walnut,.027);foot.rotation_euler.z=angle
for j,m in enumerate([ink,linen,olive]):
    o=block('Art monograph',(2.39,.28,.426+j*.027),(.30-j*.013,.225,.026),m,.003);o.rotation_euler.z=.07+j*.08
block('Lower magazine shelf',(2.10,.33,.114),(.86,.38,.027),walnut,.009)
for j,m in enumerate([linen,rose,pillow]):
    book=block('Reading stack',(2.11,.325,.142+j*.029),(.34,.236,.027),m,.003)
    book.rotation_euler.z=-.08+j*.07
# Turned ceramic dish with a recessed centre.
def dish(cx,cy,z):
    terracotta=material('Speckled ceramic','937354',.52)
    rings=[(.045,0),(.105,.016),(.14,.047),(.139,.057),(.125,.050),(.078,.026),(.008,.022)]
    vs=[(cx+r*math.cos(a*math.tau/48),cy+r*math.sin(a*math.tau/48),z+h) for r,h in rings for a in range(48)]
    fs=[(k*48+i,k*48+(i+1)%48,(k+1)*48+(i+1)%48,(k+1)*48+i) for k in range(len(rings)-1) for i in range(48)]
    mesh=bpy.data.meshes.new('Wheel thrown ceramic');mesh.from_pydata(vs,[],fs);mesh.update()
    ob=bpy.data.objects.new('Stoneware bowl',mesh);bpy.context.scene.collection.objects.link(ob);finish(ob,ob.name,terracotta)
dish(1.72,.38,.412)
# A low window chair with slung arms and a matching footstool.
for x in [-3.54,-2.91]:
    rod('Chair front leg',(x,-.94,.03),(x,-.87,.48),.024,walnut,.032)
    rod('Chair rear leg',(x,-.19,.03),(x,-.48,.72),.022,walnut,.032)
    rod('Chair arm',(x,-.93,.50),(x,-.40,.63),.035,walnut)
block('Lounge chair seat',(-3.22,-.65,.355),(.65,.62,.15),leather,.06)
ob=block('Lounge chair back',(-3.22,-.38,.64),(.66,.14,.53),leather,.065);ob.rotation_euler.x=-.23
block('Footstool cushion',(-3.09,-1.54,.27),(.49,.42,.12),leather,.05)
for x in [-3.25,-2.93]:
    for y in [-1.69,-1.39]:rod('Stool foot',(x,y,.015),(x,y,.22),.022,walnut)
# Diffusing amber lamps and a low window credenza.
opal=material('Amber opaline','FFD99C',.28)
shader=opal.node_tree.nodes.get('Principled BSDF')
shader.inputs['Emission Color'].default_value=rgb('FFBC72');shader.inputs['Emission Strength'].default_value=1.8
def globe(x,y,z,r):
    rod('Lamp brass collar',(x,y,z),(x,y,z+.036),r*.47,brass)
    ball('Opaline globe',(x,y,z+r+.024),(r,r,r),opal)
globe(3.64,2.48,.74,.198)
rod('Sofa lamp plinth',(3.64,2.48,.02),(3.64,2.48,.74),.205,walnut)
globe(-3.68,1.53,.62,.16)
globe(3.66,-1.72,.025,.243)
block('Window sideboard',(-3.84,1.38,.36),(.48,2.35,.52),walnut,.012)
for y in [.56,1.32,2.08]:
    block('Inset drawer',(-3.591,y,.38),(.019,.735,.41),walnut,.005)
    block('Finger pull',(-3.577,y,.52),(.014,.18,.009),brass,.002)
# Upright studio pottery and foliage cut as ribbed leaf surfaces with pointed tips.
fern=material('Laurel leaf','54653C',.54);fern_light=material('Laurel new growth','788450',.57)
def plant(x,y,h,r):
    rod('Unglazed planter',(x,y,.012),(x,y,.012+r*1.6),r*.68,ink,r)
    origin=np.array([x,y,r*1.53])
    leaves=[];polys=[]
    for n in range(27):
        az=n*2.39996;length=h*(.44+.49*((n*7)%19)/18)
        radial=np.array([math.cos(az),math.sin(az),0]);lateral=np.array([-radial[1],radial[0],0])
        start=origin+radial*r*.35;end=start+radial*(length*.40)+[0,0,length*.80]
        rod('Leaf stalk',start,end,.006,fern)
        k=len(leaves)
        for j in range(9):
            t=j/8; width=(math.sin(t*math.pi)**.7)*length*.105
            mid=end+radial*t*.42*length+[0,0,length*(.11*math.sin(t*math.pi)-.23*t*t)]
            leaves.extend([mid-lateral*width,mid+[0,0,width*.20],mid+lateral*width])
        for j in range(8):
            i=k+3*j;polys.extend([(i,i+3,i+4,i+1),(i+1,i+4,i+5,i+2)])
    mesh=bpy.data.meshes.new('Laurel');mesh.from_pydata(leaves,[],polys);mesh.update()
    ob=bpy.data.objects.new('Floor laurel',mesh);bpy.context.scene.collection.objects.link(ob);finish(ob,ob.name,fern)
    mesh.materials.append(fern_light)
    for i,poly in enumerate(mesh.polygons):poly.material_index=int(i//16%4==0)
plant(-3.72,-2.63,1.17,.19);plant(3.72,3.55,1.33,.19)
# Foreground dining area across the room from the exhibition aisle.
block('Dining top',(1.16,-3.27,.755),(2.10,1.0,.058),walnut,.028)
for x in [.44,1.88]:
    for y in [-3.57,-2.97]:rod('Tapered dining leg',(x,y,.015),(x,y,.73),.032,walnut,.055)
for x in [.61,1.71]:
    for side in [-1,1]:
        cy=-3.27+side*.66
        block('Dining chair seat',(x,cy,.434),(.43,.43,.050),walnut,.024)
        ob=block('Curved backrest',(x,cy+side*.16,.67),(.445,.054,.31),walnut,.023);ob.rotation_euler.x=-side*.13
        for dx in [-.15,.15]:
            for dy in [-.14,.14]:rod('Chair leg',(x+dx*1.12,cy+dy*1.15,.01),(x+dx,cy+dy,.43),.017,walnut,.025)
# Open art book, folded at the binding, on the dining table.
paper=Image.new('RGB',(768,480),(232,223,199));draw=ImageDraw.Draw(paper)
draw.rectangle((48,43,330,285),fill=(104,124,102))
draw.polygon([(48,248),(140,124),(241,226),(266,173),(330,209),(330,285),(48,285)],fill=(70,79,65))
draw.ellipse((211,71,247,107),fill=(215,173,112))
for row in range(15):
    yy=42+row*15
    for left in (425,581):
        draw.rectangle((left,yy,left+district.randint(94,126),yy+2),fill=(89,82,70))
for row in range(4):
    yy=324+row*13;draw.line((49,yy,283-row*11,yy),fill=(83,79,72),width=2)
bookmat=mapped_material('Open catalogue pages',paper)
block('Clothbound catalogue',(.89,-3.26,.797),(.42,.29,.021),ink,.003)
for x1,x2,z1,z2,u1,u2 in [(.687,.89,.821,.812,0,.50),(.89,1.093,.812,.823,.50,1)]:
    textured_face('Catalogue leaves',[(x1,-3.40,z1),(x2,-3.40,z2),(x2,-3.12,z2),(x1,-3.12,z1)],bookmat,[(u1,0),(u2,0),(u2,1),(u1,1)])
for x,h in [(1.83,.22),(2,.31)]:
    rod('Candle foot',(x,-3.26,.785),(x,-3.26,.797),.054,brass)
    rod('Turned candlestick',(x,-3.26,.797),(x,-3.26,.85+h*.3),.018,brass,.011)
    rod('Ivory taper',(x,-3.26,.85+h*.3),(x,-3.26,.85+h),.011,cream,.008)

# Abstract mineral pigments on linen; bands and imperfect contours follow the hand of a brush.
ay,ax=np.mgrid[-1:1:820j,-1:1:680j]
angle=np.arctan2((ay-.09)*1.16,ax+.07)
radius=np.hypot(ax+.07,(ay-.09)*1.16)
wobble=.038*np.sin(3*angle+.4)+.025*np.sin(5*angle)+.02*np.sin(ax*8+ay*10)
ring=radius+wobble+.12*np.sin(angle+radius*6)
art=np.broadcast_to([77.,83.,68.],(*radius.shape,3)).copy()
for limit,color in [(.92,[65,64,53]),(.79,[152,85,48]),(.68,[182,109,58]),(.59,[116,77,47]),(.49,[57,58,51]),(.39,[168,97,55]),(.30,[185,128,72]),(.19,[64,64,57])]:
    art[ring<limit]=color
grain_noise=fabric_rng.normal(0,4,radius.shape)+2*np.sin(ay*1600)+2*np.sin(ax*1700)+3*np.sin(55*radius+angle*3)
art=Image.fromarray(np.uint8(np.clip(art+grain_noise[:,:,None],0,255)))
canvas=mapped_material('Earth study in ochre',art)
block('Painting shadow frame',(4.22,1.48,1.88),(.075,1.28,1.53),walnut,.006)
textured_face('Painted canvas',[(4.178,.86,1.14),(4.178,2.10,1.14),(4.178,2.10,2.62),(4.178,.86,2.62)],canvas)

# Museum plinth: a minimalist white block with a shadow-gap base; the glass vitrine is a separate runtime model (props/vitrine.glb).
gallery_white = material("Gallery white", "F3F1EC", .62)
block('Plinth shadow gap', (0, 1.70, .02), (.50, .50, .04), ink, .002)
block('Museum plinth', (0, 1.70, .04 + (.677 - .04) / 2), (.56, .56, .677 - .04), gallery_white, .004)
# Brushed aluminium label and recessed charcoal lip under the clear cover.
block('Case sill', (0, 1.70, .683), (.494, .494, .012), ink, .002)
metal = material('Brushed exhibition aluminium', 'DADBDD', .39, .56)
noise = np.random.default_rng(212)
lines = noise.normal(0, 4, (160, 1)) + noise.normal(0, 1.6, (160, 768))
tone = 202 + 25*np.cos(np.linspace(-1, 2.8, 768))[None,:] + lines
plate_img = Path('/tmp/blindbox-brushed-aluminium.png')
Image.fromarray(np.clip(tone,0,255).astype('uint8')).convert('RGB').save(plate_img)
tex = metal.node_tree.nodes.new('ShaderNodeTexImage')
tex.image = bpy.data.images.load(str(plate_img)); tex.image.pack()
metal.node_tree.links.new(tex.outputs['Color'], metal.node_tree.nodes.get('Principled BSDF').inputs['Base Color'])
block('Plaque mounting pad', (0, 1.412, .537), (.352,.014,.121), ink, .003).rotation_euler.x = math.radians(-10)
block('Exhibit plaque', (0, 1.399, .537), (.366,.008,.136), metal, .003).rotation_euler.x = math.radians(-10)
# Bake daylight, occlusion and the opal lamps into the fixed architectural surfaces.
# The atlas supplies indirect lighting even in runtimes without real-time GI.
scene=bpy.context.scene
scene.render.engine='CYCLES'
scene.cycles.samples=48
scene.world=bpy.data.worlds.new('Late afternoon ambient')
scene.world.use_nodes=True
scene.world.node_tree.nodes['Background'].inputs[0].default_value=(.62,.65,.71,1)
scene.world.node_tree.nodes['Background'].inputs[1].default_value=.32
def room_light(name,kind,position,color,energy,aim=None,size=1):
    light=bpy.data.lights.new(name,kind);light.energy=energy;light.color=color
    ob=bpy.data.objects.new(name,light);scene.collection.objects.link(ob);ob.location=position
    if aim is not None:ob.rotation_euler=(Vector(aim)-ob.location).to_track_quat('-Z','Y').to_euler()
    if kind=='AREA':light.shape='DISK';light.size=size
    if kind=='POINT':light.shadow_soft_size=size
    return ob
room_light('West window light','AREA',(-4.7,-.7,2.9),(1,.84,.63),650,(1,1,1),4.5)
sun=room_light('Late sun','SUN',(-8,-4,5.4),(1,.80,.55),2.4,(1,1,.7))
sun.data.angle=math.radians(4)
room_light('Sky fill','AREA',(-3.5,4.1,3.1),(.75,.84,1),150,(.8,.3,1),2)
room_light('Interior bounce','AREA',(1,0,3.85),(1,.86,.70),90,(.5,1.8,0),4)
for position,power in [((3.64,2.48,.96),65),((-3.68,1.53,.80),32),((3.66,-1.72,.29),42),((-1.42,3.95,1.69),18)]:
    room_light('Warm opal spill','POINT',position,(1,.57,.27),power,size=.18)
# Shells are diffusers; let the bulbs shine through them during the bake.
for ob in scene.objects:
    if ob.type=='MESH' and ob.active_material in (opal,sky,tableglass):ob.visible_shadow=False
bake_prefixes=('Oak plank','Entrance wall','Lounge wall','Shelf wall','Channeled sofa','Loose throw cushion','Lounge carpet',
               'Walnut ceiling','Exposed walnut joist','Brick pier core','Pier stretcher','Window sideboard','Museum plinth','Lounge chair seat','Lounge chair back','Footstool cushion')
surfaces=[o for o in scene.objects if o.type=='MESH' and o.name.startswith(bake_prefixes)]
bpy.ops.object.select_all(action='DESELECT')
for o in surfaces:o.select_set(True)
bpy.context.view_layer.objects.active=surfaces[0]
bpy.ops.object.convert(target='MESH');bpy.ops.object.join()
shell=bpy.context.object
shell.name='Loft architecture and furnishings'
bpy.ops.object.transform_apply(location=True,scale=True,rotation=True)
# Explicitly keep original texture lookups on UV0 while packing a non-overlapping bake UV.
for m in shell.data.materials:
    for texnode in list(m.node_tree.nodes):
        if texnode.type=='TEX_IMAGE' and not texnode.inputs['Vector'].is_linked:
            uv=m.node_tree.nodes.new('ShaderNodeUVMap');uv.uv_map=shell.data.uv_layers[0].name
            m.node_tree.links.new(uv.outputs[0],texnode.inputs['Vector'])
atlas_uv=shell.data.uv_layers.new(name='Baked lighting')
shell.data.uv_layers.active=atlas_uv
bpy.ops.object.mode_set(mode='EDIT');bpy.ops.mesh.select_all(action='SELECT')
bpy.ops.uv.smart_project(angle_limit=math.radians(76),island_margin=.0015,area_weight=.4)
bpy.ops.object.mode_set(mode='OBJECT')
atlas=bpy.data.images.new('Loft daylight and lamplight',width=2048,height=2048,alpha=False,float_buffer=True)
for m in shell.data.materials:
    target=m.node_tree.nodes.new('ShaderNodeTexImage');target.image=atlas
    m.node_tree.nodes.active=target
bpy.ops.object.bake(type='DIFFUSE',pass_filter={'COLOR','DIRECT','INDIRECT'},margin=6,uv_layer='Baked lighting')
# Denoise the linear radiance before encoding the atlas. VR magnifies individual texels.
import ctypes
from ctypes import c_void_p, c_char_p, c_int, c_size_t, c_bool
module_dir=Path(bpy.__file__).resolve().parent
library=next((d/'lib/libOpenImageDenoise.dylib' for d in [module_dir,*module_dir.parents]
              if (d/'lib/libOpenImageDenoise.dylib').is_file()),None)
if library is None:
    import ctypes.util
    library=ctypes.util.find_library('OpenImageDenoise')
if library:
    print('Denoising light atlas with',library,flush=True)
    oidn=ctypes.CDLL(str(library))
    def bind(name, restype, *args):
        f=getattr(oidn,name);f.restype=restype;f.argtypes=args;return f
    dev=bind('oidnNewDevice',c_void_p,c_int)(1)
    bind('oidnCommitDevice',None,c_void_p)(dev)
    filt=bind('oidnNewFilter',c_void_p,c_void_p,c_char_p)(dev,b'RT')
    w,h=atlas.size
    rgba=np.empty(w*h*4,np.float32);atlas.pixels.foreach_get(rgba);rgba=rgba.reshape(h,w,4)
    radiance=np.ascontiguousarray(rgba[:,:,:3]);clean=np.empty_like(radiance)
    attach=bind('oidnSetSharedFilterImage',None,c_void_p,c_char_p,c_void_p,c_int,c_size_t,c_size_t,c_size_t,c_size_t,c_size_t)
    attach(filt,b'color',radiance.ctypes.data,3,w,h,0,0,0)
    attach(filt,b'output',clean.ctypes.data,3,w,h,0,0,0)
    bind('oidnSetFilterBool',None,c_void_p,c_char_p,c_bool)(filt,b'hdr',True)
    bind('oidnCommitFilter',None,c_void_p)(filt)
    bind('oidnExecuteFilter',None,c_void_p)(filt)
    errmsg=c_char_p()
    error=bind('oidnGetDeviceError',c_int,c_void_p,ctypes.POINTER(c_char_p))(dev,ctypes.byref(errmsg))
    if error: raise RuntimeError(errmsg.value)
    rgba[:,:,:3]=np.maximum(clean,0);atlas.pixels.foreach_set(rgba.ravel());atlas.update()
    bind('oidnReleaseFilter',None,c_void_p)(filt);bind('oidnReleaseDevice',None,c_void_p)(dev)

atlas.filepath_raw='/tmp/blindbox-loft-lighting.jpg';atlas.file_format='JPEG';atlas.save()
lit=material('Baked loft illumination','FFFFFF',.8)
p=lit.node_tree.nodes.get('Principled BSDF')
tex=lit.node_tree.nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(atlas.filepath_raw);tex.image.pack()
lit.node_tree.links.new(tex.outputs[0],p.inputs['Emission Color']);p.inputs['Emission Strength'].default_value=1
p.inputs['Base Color'].default_value=(.008,.008,.008,1)
shell.data.materials.clear();shell.data.materials.append(lit)
for face in shell.data.polygons:face.material_index=0
for uv in list(shell.data.uv_layers):
    if uv.name!='Baked lighting':shell.data.uv_layers.remove(uv)
# Static lights have been baked; omit them from the game export.

# Single-sheet glazing retains the skyline parallax without doubling blended surfaces.
window_glass=material('Clear architectural glazing','D6E7E5',.10,.08)
window_glass.node_tree.nodes.get('Principled BSDF').inputs['Alpha'].default_value=.045
window_glass.surface_render_method='BLENDED'
textured_face('West glazing',[(-4.29,-4.41,.02),(-4.29,4.41,.02),(-4.29,4.41,4.21),(-4.29,-4.41,4.21)],window_glass)
textured_face('Corner glazing',[(-4.30,4.42,.02),(-2.885,4.42,.02),(-2.885,4.42,4.21),(-4.30,4.42,4.21)],window_glass)
block('Distant street',(-11,0,-10.8),(56,75,.03),roof,0)
# Export merged meshes by material to reduce mobile draw calls.
bpy.ops.object.select_all(action='SELECT');bpy.ops.object.convert(target='MESH')
for mat in list(bpy.data.materials):
    group=[o for o in bpy.context.scene.objects if o.type=='MESH' and o.active_material==mat]
    if not group:continue
    bpy.ops.object.select_all(action='DESELECT')
    for o in group:o.select_set(True)
    bpy.context.view_layer.objects.active=group[0];bpy.ops.object.join();group[0].name=mat.name
out=assets/'collection-room.glb'
bpy.ops.export_scene.gltf(filepath=str(out),export_format='GLB',export_yup=True,export_lights=False)
# The emulator has a dim default lighting rig. Give surfaces a modest ambient term.
data=out.read_bytes();n=struct.unpack_from('<I',data,12)[0];doc=json.loads(data[20:20+n])
for m in doc['materials']:
    if 'emissiveFactor' in m or m.get('alphaMode') == 'BLEND':continue
    p=m['pbrMetallicRoughness'];m['emissiveFactor']=[x*.39 for x in p.get('baseColorFactor',[1]*4)[:3]]
    if 'baseColorTexture' in p:m['emissiveTexture']=p['baseColorTexture']
j=json.dumps(doc,separators=(',',':')).encode();j+=b' '*(-len(j)%4);tail=data[20+n:]
out.write_bytes(struct.pack('<4sIIII',b'glTF',2,20+len(j)+len(tail),len(j),0x4e4f534a)+j+tail)
# Optional overview for reviewing the model from outside the headset play area.
if '--asset-only' in sys.argv:
    raise SystemExit(0)
# Reimport the actual runtime geometry so that shading here matches the exported atlas.
bpy.ops.wm.read_factory_settings(use_empty=True)
bpy.ops.import_scene.gltf(filepath=str(out))
scene=bpy.context.scene
def reference_prop(path,location,scale=1,yaw=0):
    bpy.ops.import_scene.gltf(filepath=str(assets/path))
    roots=[o for o in bpy.context.selected_objects if o.parent is None]
    mount=bpy.data.objects.new('Prop mount',None);scene.collection.objects.link(mount)
    for ob in roots:ob.parent=mount
    mount.location=location;mount.scale=(scale,)*3;mount.rotation_euler.z=yaw
reference_prop('props/vitrine.glb',(0,1.70,.677))
reference_prop('props/display-grass.glb',(0,1.70,.689),.47)
reference_prop('figures/ballerina-capuccina.glb',(0,1.70,.743),.38)
reference_prop('props/gachapon.glb',(-3.62,3.70,0),yaw=math.atan2(3.62,3.70))
for figure,x,z in [('niu-lai',-1.38,.905),('strawberlina',1.38,.905),('bananito',-1.05,.41),
                   ('tung-tung-tung',-.55,.41),('tralalero-tralala',.55,.41),('ballerina-capuccina',1.05,.41),
                   ('chill-guy',-.80,.10),('secret-capybara-toilet',.80,.10)]:
    reference_prop('figures/'+figure+'.glb',(x,4.12,z),.12/.98)
scene.world=bpy.data.worlds.new('Preview sky');scene.world.color=(.26,.24,.22)
room_light('Window fill','AREA',(-3.64,-1.2,3.0),(1,.84,.66),320,(.5,1.4,.6),4)
room_light('Back fill','AREA',(2.5,0,3.7),(1,.84,.72),170,(0,3,.6),3)
cam=bpy.data.cameras.new('Loft overview');camera=bpy.data.objects.new('Loft overview',cam)
scene.collection.objects.link(camera);scene.camera=camera
camera.location=(1.12,-4.11,2.54)
camera.rotation_euler=(Vector((-.22,1.54,.67))-camera.location).to_track_quat('-Z','Y').to_euler()
cam.lens=18.5;cam.clip_start=.08;cam.clip_end=120
scene.render.engine='CYCLES';scene.cycles.samples=20;scene.cycles.use_denoising=True
scene.render.resolution_x=1440;scene.render.resolution_y=1000;scene.render.resolution_percentage=100
scene.render.image_settings.file_format='JPEG';scene.render.filepath=str(here/'Living room loft.jpg')
bpy.ops.render.render(write_still=True)
