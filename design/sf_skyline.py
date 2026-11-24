"""Rincon Hill-inspired 3D exterior. Blender coordinates: Z up, NW is -X, bay is +Y.
An art-directed panorama, not a survey from a specific address. All looks share geometry.
Run with Blender Python to replace only exterior nodes in the already-baked loft GLB.
"""
from pathlib import Path
import math, json, struct, copy, random
import bpy
import numpy as np
from PIL import Image, ImageDraw
from mathutils import Vector
ROOT=Path(__file__).resolve().parents[1]

def material(name, color, emission=.55, metallic=0):
    m=bpy.data.materials.new(name);m.use_nodes=True;p=m.node_tree.nodes.get('Principled BSDF')
    rgb=tuple(int(color[i:i+2],16)/255 for i in (0,2,4))
    p.inputs['Base Color'].default_value=(*rgb,1);p.inputs['Roughness'].default_value=.65;p.inputs['Metallic'].default_value=metallic
    p.inputs['Emission Color'].default_value=(*rgb,1);p.inputs['Emission Strength'].default_value=emission
    return m

def textured(name, im, emission=.7):
    m=material(name,'FFFFFF',emission);p=m.node_tree.nodes.get('Principled BSDF')
    path=Path('/tmp')/('sf-'+name.replace(' ','-')+'.png');im.save(path)
    tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(path));tex.image.pack()
    for slot in ['Base Color','Emission Color']:m.node_tree.links.new(tex.outputs['Color'],p.inputs[slot])
    return m

def mesh(name, verts, faces, mat, uvs=None):
    d=bpy.data.meshes.new(name);d.from_pydata(verts,[],faces);d.update();o=bpy.data.objects.new('SF '+name,d);bpy.context.collection.objects.link(o);d.materials.append(mat)
    if uvs:
        uv=d.uv_layers.new()
        for face,coords in zip(d.polygons,uvs):
            for loop,p in zip(face.loop_indices,coords):uv.data[loop].uv=p
    return o

def box(name,x,y,z,w,d,h,mat):
    v=[(x+sx*w/2,y+sy*d/2,z+sz*h/2) for sx,sy,sz in [(-1,-1,-1),(1,-1,-1),(1,1,-1),(-1,1,-1),(-1,-1,1),(1,-1,1),(1,1,1),(-1,1,1)]]
    return mesh(name,v,[(0,3,2,1),(4,5,6,7),(0,1,5,4),(1,2,6,5),(2,3,7,6),(3,0,4,7)],mat,[[(0,0),(w/3,0),(w/3,h/5),(0,h/5)]]*6)

def beam(name,a,b,r,mat,sides=6):
    a,b=Vector(a),Vector(b);axis=(b-a).normalized();u=axis.cross(Vector((0,0,1)))
    if u.length<.01:u=axis.cross(Vector((0,1,0)))
    u.normalize();v=axis.cross(u);verts=[]
    for p in [a,b]:
        verts.extend(tuple(p+r*(u*math.cos(t*math.tau/sides)+v*math.sin(t*math.tau/sides))) for t in range(sides))
    faces=[tuple(range(sides-1,-1,-1)),tuple(range(sides,2*sides))]+[(i,(i+1)%sides,(i+1)%sides+sides,i+sides) for i in range(sides)]
    return mesh(name,verts,faces,mat)

def facade_texture(index):
    from skyline_textures import facade_image
    return facade_image('day',index)

from skyline_textures import water_image

def build_skyline():
    rng=random.Random(91)
    facade=[textured('City facade '+str(i),facade_texture(i)) for i in range(4)]
    roof=material('SF rooftop','59646D',.45);steel=material('SF bridge steel','718490',.45);cable=material('SF bridge cables','B4C6CC',.75)
    road=material('SF roadway','485661',.4);lamps=material('SF bridge lights','FFE4A3',1)
    land=material('SF island','526E69',.6);shore=material('SF shoreline','798A84');crown=material('SF crown','BACBD0',.8)
    water=textured('SF bay water',water_image(),.85)
    mesh('San Francisco Bay',[(-105,-105,-12),(105,-105,-12),(105,105,-12),(-105,105,-12)],[(0,1,2,3)],water,[[(0,0),(4,0),(4,4),(0,4)]])
    # Large inward-facing sky enclosure: no system-home gaps when looking through either window.
    gradient=np.zeros((512,16,3),np.uint8)
    for i in range(512):gradient[i,:,:]=np.array((81,138,199))*(1-i/511)+np.array((225,235,233))*i/511
    sky=textured('Evening sky',Image.fromarray(gradient),1)
    for i in range(32):
        a,b=i*math.tau/32,(i+1)*math.tau/32
        mesh('Horizon sky',[(160*math.cos(a),160*math.sin(a),-20),(160*math.cos(b),160*math.sin(b),-20),(160*math.cos(b),160*math.sin(b),140),(160*math.cos(a),160*math.sin(a),140)],[(0,1,2,3)],sky,[[(i/32,0),((i+1)/32,0),((i+1)/32,1),(i/32,1)]])
    mesh('Sky cap',[(-170,-170,139),(170,-170,139),(170,170,139),(-170,170,139)],[(3,2,1,0)],sky,[[(0,1),(1,1),(1,1),(0,1)]])
    # Continuous rolling East Bay ridgeline, with layered coastal silhouettes.
    for layer in range(3):
        verts=[]
        for i in range(65):
            x=-100+i*3.15
            top=-10+layer*.6+1.7*math.sin(i*.24+layer)+.55*math.sin(i*.63)
            verts.extend([(x,72+layer*7,-14),(x,72+layer*7,top)])
        mesh('East Bay ridgeline',verts,[(i*2,i*2+1,i*2+3,i*2+2) for i in range(64)],land)
    # Rounded Yerba Buena island at the end of the bridge, rather than a pyramidal wedge.
    verts=[]
    for j in range(13):
        for i in range(25):
            x=-58+i; y=49+j
            z=-12+3.2*math.exp(-((x+46)/7.8)**2-((y-55)/4.4)**2)
            verts.append((x,y,z))
    mesh('Yerba Buena Island',verts,[(j*25+i,j*25+i+1,(j+1)*25+i+1,(j+1)*25+i) for j in range(12) for i in range(24)],land)
    asphalt=material('SF city streets','3D4549',.5)
    pavement=material('SF city blocks','ABAAA1',.55)
    masonry=[textured('City facade '+str(i),facade_texture(i)) for i in range(4,8)]
    facade.extend(masonry)
    parks=material('SF parks','3D5547',.5)
    equipment=material('SF rooftop equipment','515B61',.5)
    box('San Francisco peninsula',-49,-32,-12.2,88,96,.5,asphalt)
    # Dense, varied, street-aligned blocks fill the foreground and skyline. Keep only
    # the actual bay water open. Tower envelopes stay clear so landmarks remain legible.
    landmarks=[(-24,-3,3.4),(-32,12,3.4)]
    count=0
    for ix,x in enumerate(range(-88,-7,6)):
        for iy,y in enumerate(range(-74,15,7)):
            box('Sidewalk block',x,y,-11.88,5.15,6.05,.16,pavement)
            for k in range(3):
                bx=x+(-1.3 if k!=1 else 1.3);by=y+(-1.55 if k<2 else 1.5)
                w=2.15 if k<2 else 4.65;d=2.55
                if any(abs(bx-lx)<lr+w/2 and abs(by-ly)<lr+d/2 for lx,ly,lr in landmarks):continue
                distance=math.hypot(bx+24,by+3)
                height=rng.uniform(3.0,8.5) if distance>35 else rng.uniform(6,16)
                if bx>-20:height=rng.uniform(3.5,8.5)
                if rng.random()<.13 and bx<-24:height+=rng.uniform(2,5)
                # Keep the pyramid's full upper silhouette visible from the window bank.
                if bx>-33 and abs(math.atan2(by, -bx-3.5)-math.atan2(12,28.5))<.17:
                    height=min(height,8.5)
                z=-11.8+height; mat=rng.choice(facade)
                box('District building',bx,by,-11.8+height/2,w,d,height,mat)
                box('Roof parapet',bx,by,z+.035,w+.10,d+.10,.12,roof)
                if height>10:
                    box('Setback penthouse',bx,by,z+.36,w*.65,d*.65,.65,mat)
                    box('Penthouse roof',bx,by,z+.71,w*.69,d*.69,.08,roof)
                for unit in range(2):
                    box('Rooftop HVAC',bx+(unit-.5)*.6,by-.3,z+.21,.40,.56,.28,equipment)
                count+=1
            # Pocket parks and repeated trees give streets a human scale.
            if (ix+iy)%4==0:
                box('Pocket garden',x+2.2,y+2.5,-11.74,.75,.8,.08,parks)
                for dx in [-.18,.18]:
                    beam('Street tree trunk',(x+2.2+dx,y+2.5,-11.7),(x+2.2+dx,y+2.5,-10.9),.045,equipment)
                    bpy.ops.mesh.primitive_ico_sphere_add(subdivisions=1,radius=.37,location=(x+2.2+dx,y+2.5,-10.8))
                    tree=bpy.context.object;tree.name='SF Street canopy';tree.data.materials.append(parks)
    # Embarcadero aprons, long low warehouses, and wharves meet the bay continuously.
    box('Embarcadero seawall',-46,16,-11.9,87,2.1,.55,shore)
    for x in range(-80,-8,7):
        box('Wharf apron',x,19,-11.75,3.8,6,.3,shore)
        box('Pier warehouse',x,19,-10.9,2.8,4.9,1.4,facade[5])
        box('Pier roof',x,19,-10.15,3,5.2,.1,roof)
    print('Dense SF district buildings:',count,flush=True)
    # Salesforce Tower: elliptical tapered glass shaft, rounded crown and tight horizontal banding.
    x,y=-24,-3
    rings=[(-12,1.9),(-8,1.92),(-3,1.86),(2,1.74),(5,1.53),(7,1.24),(8,.96),(8.5,.55),(8.7,.05)]
    verts=[(x+r*math.cos(t*math.tau/48),y+r*.85*math.sin(t*math.tau/48),z) for z,r in rings for t in range(48)]
    faces=[];uv=[]
    for j in range(len(rings)-1):
        for i in range(48):
            faces.append((j*48+i,j*48+(i+1)%48,(j+1)*48+(i+1)%48,(j+1)*48+i));uv.append([(i/12,(rings[j][0]+12)/7),((i+1)/12,(rings[j][0]+12)/7),((i+1)/12,(rings[j+1][0]+12)/7),(i/12,(rings[j+1][0]+12)/7)])
    ob=mesh('Salesforce Tower',verts,faces,facade[0],uv)
    for p in ob.data.polygons:p.use_smooth=True
    for z in np.arange(-11,8.5,.27):
        j=next((j for j in range(len(rings)-1) if rings[j][0]<=z<=rings[j+1][0]),0);f=(z-rings[j][0])/(rings[j+1][0]-rings[j][0]);r=rings[j][1]*(1-f)+rings[j+1][1]*f
        # Fine silver perimeter bands retain the tower's rounded silhouette.
        for i in range(32):
            a,b=i*math.tau/32,(i+1)*math.tau/32
            beam('Salesforce floor band',(x+(r+.012)*math.cos(a),y+(r+.012)*.85*math.sin(a),z),(x+(r+.012)*math.cos(b),y+(r+.012)*.85*math.sin(b),z),.016,crown,4)
    # Transamerica Pyramid with square taper and distinctive projecting elevator wings.
    x,y=-32,12;bottom=-12;top=6.8;r=1.8
    mesh('Transamerica Pyramid',[(x-r,y-r,bottom),(x+r,y-r,bottom),(x+r,y+r,bottom),(x-r,y+r,bottom),(x,y,top)],[(0,1,4),(1,2,4),(2,3,4),(3,0,4)],facade[2],[[(0,0),(1,0),(.5,4)]]*4)
    for side in [-1,1]:box('Transamerica wing',x+side*.94,y,.5,.38,.60,5.4,crown)
    beam('Transamerica spire',(x,y,6.3),(x,y,7.6),.035,crown)
    # Bay Bridge western double suspension spans; roadway, two parallel cable planes, braced towers.
    origin=Vector((-19,15,-7.5));axis=Vector((-.5,.866,0));side=Vector((-.866,-.5,0))
    def p(t,l=0,z=0):return tuple(origin+axis*t+side*l+Vector((0,0,z)))
    for l in [-.64,.64]:beam('Bridge roadway edge',p(-4,l),p(48,l),.095,steel)
    for t in np.arange(-4,48,.55):beam('Bridge deck',p(t,-.61),p(t,.61),.075,road)
    towers=[3,15,27,39]
    for t in towers:
        for l in [-.72,.72]:
            beam('Bridge tower pier',p(t,l,-4.5),p(t,l,7.1),.18,steel)
        for z in [0,1.5,3.3,5.2,7.05]:beam('Bridge tower crossbeam',p(t,-.72,z),p(t,.72,z),.13,steel)
        for z in [1.5,3.3,5.2]:
            beam('Bridge tower brace',p(t,-.72,z),p(t,.72,z+1.75),.055,steel);beam('Bridge tower brace',p(t,.72,z),p(t,-.72,z+1.75),.055,steel)
    for start,end in [(-4,3),(3,15),(15,27),(27,39),(39,48)]:
        points=np.linspace(start,end,max(10,int((end-start)*3)))
        def height(t):
            u=(t-start)/(end-start)
            if start==-4:return 1+6*u
            if end==48:return 7-6*u
            return 1.4+5.6*(2*u-1)**2
        for l in [-.72,.72]:
            for a,b in zip(points,points[1:]):beam('Suspension cable',p(a,l,height(a)),p(b,l,height(b)),.036,cable)
            for t in np.arange(start+.6,end,.8):
                beam('Vertical hanger',p(t,l,.05),p(t,l,height(t)),.018,cable,4)
                beam('Bridge light',p(t,l,.15),p(t,l,.21),.035,lamps,6)
    # Embarcadero waterfront below the corner window.
    box('Waterfront promenade',-5,13,-11.2,18,3,.65,shore)
    for x in [-11,-6,-1]:box('Pier',x,16,-11.35,1.3,6,.35,shore)
    # Merge by material for a bounded draw-call count.
    for mat in list(bpy.data.materials):
        group=[o for o in bpy.context.scene.objects if o.type=='MESH' and o.name.startswith('SF ') and o.active_material==mat]
        if not group:continue
        bpy.ops.object.select_all(action='DESELECT')
        for o in group:o.select_set(True)
        bpy.context.view_layer.objects.active=group[0];bpy.ops.object.join();group[0].name='SF '+mat.name
    return sky

def read_glb(path):
    data=path.read_bytes();n=struct.unpack_from('<I',data,12)[0];return json.loads(data[20:20+n]),data[28+n:]
def write_glb(path,doc,body):
    j=json.dumps(doc,separators=(',',':')).encode();j+=b' '*(-len(j)%4);body+=b'\0'*(-len(body)%4)
    path.write_bytes(struct.pack('<III',0x46546c67,2,28+len(j)+len(body))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(body),0x004e4942)+body)

def replace_exterior(base,addition):
    doc,body=read_glb(base);add,extra=read_glb(addition)
    kinds=['nodes','meshes','materials','accessors','bufferViews','images','textures','samplers']
    # Preserve the baked interior byte-for-byte and replace prior skyline append on rebuild.
    saved=doc.get('extras',{}).get('sfSkylineBase')
    if saved:
        for k in kinds:doc[k]=doc.get(k,[])[:saved['counts'][k]]
        body=body[:saved['binaryLength']]
        for s,nodes in zip(doc['scenes'],saved['sceneNodes']):s['nodes']=nodes
    else:saved={'counts':{k:len(doc.get(k,[])) for k in kinds},'binaryLength':len(body),'sceneNodes':[s['nodes'][:] for s in doc['scenes']]}
    old={'Evening sky','Zinc roof',*[f'City facade {i}' for i in range(4)]}
    hidden={i for i,n in enumerate(doc['nodes']) if n.get('name') in old or n.get('name','').startswith('SF ')}
    for s in doc['scenes']:s['nodes']=[i for i in s['nodes'] if i not in hidden]
    # Some importers materialize orphan nodes; remove retired meshes as well as scene links.
    for i in hidden:doc['nodes'][i].pop('mesh',None)
    offset={k:len(doc.get(k,[])) for k in kinds}
    for v in add.get('bufferViews',[]):v['buffer']=0;v['byteOffset']=v.get('byteOffset',0)+len(body)
    for a in add.get('accessors',[]):
        if 'bufferView' in a:a['bufferView']+=offset['bufferViews']
    for im in add.get('images',[]):
        if 'bufferView' in im:im['bufferView']+=offset['bufferViews']
    for t in add.get('textures',[]):
        if 'source' in t:t['source']+=offset['images']
        if 'sampler' in t:t['sampler']+=offset['samplers']
    def fix_textures(o):
        if isinstance(o,dict):
            for k,v in o.items():
                if k.endswith('Texture') and isinstance(v,dict) and 'index' in v:v['index']+=offset['textures']
                else:fix_textures(v)
        elif isinstance(o,list):
            for v in o:fix_textures(v)
    for m in add.get('materials',[]):fix_textures(m)
    for m in add.get('meshes',[]):
        for p in m['primitives']:
            p['attributes']={k:v+offset['accessors'] for k,v in p['attributes'].items()}
            if 'indices' in p:p['indices']+=offset['accessors']
            if 'material' in p:p['material']+=offset['materials']
    for n in add.get('nodes',[]):
        if 'mesh' in n:n['mesh']+=offset['meshes']
        if 'children' in n:n['children']=[i+offset['nodes'] for i in n['children']]
    for k in kinds:doc.setdefault(k,[]).extend(add.get(k,[]))
    doc['scenes'][doc.get('scene',0)]['nodes'].extend(i+offset['nodes'] for i in add['scenes'][add.get('scene',0)]['nodes'])
    for k in ['extensionsUsed','extensionsRequired']:doc[k]=list(dict.fromkeys(doc.get(k,[])+add.get(k,[])))
    doc['buffers'][0]['byteLength']=len(body)+len(extra);doc.setdefault('extras',{})['sfSkylineBase']=saved
    write_glb(base,doc,body+extra)

if __name__=='__main__':
    bpy.ops.wm.read_factory_settings(use_empty=True);build_skyline()
    out=ROOT/'design/reference-library/sf-skyline.glb'
    bpy.ops.export_scene.gltf(filepath=str(out),export_format='GLB',export_yup=True)
    replace_exterior(ROOT/'app/src/main/assets/collection-room.glb',out)
    print('SF exterior exported and integrated',flush=True)
