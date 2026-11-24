"""Author miniature furniture and photograph each actual sale model against a transparent studio backdrop."""
import bpy,math,json,struct
from pathlib import Path
from mathutils import Vector
from PIL import Image
BASE=Path(__file__).resolve().parents[1]/'app/src/main'
MODELS=BASE/'assets/furniture'; THUMBS=BASE/'res/drawable-nodpi'
MODELS.mkdir(exist_ok=True);THUMBS.mkdir(exist_ok=True)
CAT=[
('lotus_lounge','Lotus lounge','chair','D98796',65),('sage_reader','Sage reading chair','chair','98B79F',55),('tangerine_club','Tangerine club chair','chair','D8915E',80),
('matcha_table','Matcha pedestal','table','98AD8C',45),('onyx_nightstand','Onyx nightstand','table','62626A',70),('coral_sidecar','Coral side table','table','CD8A81',60),
('opal_saucer','Opal saucer lamp','lamp','E8B56C',40),('mint_beacon','Mint beacon lamp','lamp','8DAFA2',45),('blush_lantern','Blush lantern','lamp','C88991',50),
('marigold_pouf','Marigold pouf','pouf','D7AB65',35),('plum_pouf','Plum button stool','pouf','927891',30),('cloud_pouf','Cloud ottoman','pouf','DFD7BD',50),
('fern_bowl','Fern bowl','plant','76947D',25),('olive_tree','Dwarf olive','plant','84905E',40),('rose_agave','Rose agave','plant','AD8088',35),
('kiln_vase','Kiln vase','vase','BD8168',20),('celadon_vase','Celadon amphora','vase','8BB8AA',25),('cobalt_pitcher','Cobalt pitcher','vase','657D9F',30),
('linen_rack','Linen bookcase','shelf','DCC9AB',85),('moss_cabinet','Moss cabinet','shelf','829379',100),('berry_credenza','Berry credenza','shelf','A37480',110),
('oculus','Brass oculus','sculpt','DCBC71',30),('pebble_stack','River stones','sculpt','97A9A6',25),('arch_pair','Twin terracotta arches','sculpt','C18769',40),
('petite_maple_frame','Small blank canvas · Maple frame','frame','D3AB82',25),('gallery_oak_frame','Medium blank canvas · Oak frame','frame','97704D',40),('salon_gilt_frame','Large blank canvas · Gilt frame','frame','D7B16C',65),
]
def mat(n,h,metal=0,rough=.48):
 m=bpy.data.materials.new(n);m.use_nodes=True;p=m.node_tree.nodes.get('Principled BSDF');a=[int(h[i:i+2],16)/255 for i in (0,2,4)];p.inputs['Base Color'].default_value=tuple(((v+.055)/1.055)**2.4 if v>.04045 else v/12.92 for v in a)+(1,);p.inputs['Metallic'].default_value=metal;p.inputs['Roughness'].default_value=rough;return m
def finish(m,name):
 o=bpy.context.object;o.name=name;o.data.materials.append(m);return o
def block(p,s,m,b=.015):
 bpy.ops.mesh.primitive_cube_add(size=1,location=p);o=bpy.context.object;o.dimensions=s;bpy.ops.object.transform_apply(location=False,rotation=False,scale=True);o=finish(m,'Solid joinery')
 if b:mod=o.modifiers.new('Edge radii','BEVEL');mod.width=min(b,min(s)*.45);mod.segments=4;o.modifiers.new('Studio normals','WEIGHTED_NORMAL')
 return o
def tube(p,r1,r2,h,m):
 bpy.ops.mesh.primitive_cone_add(vertices=48,radius1=r1,radius2=r2,depth=h,location=p);o=finish(m,'Turned profile')
 for f in o.data.polygons:f.use_smooth=len(f.vertices)==4
 return o
def ball(p,s,m):
 bpy.ops.mesh.primitive_uv_sphere_add(segments=32,ring_count=16,radius=1,location=p);o=finish(m,'Sculpted form');o.scale=s
 for f in o.data.polygons:f.use_smooth=True
 return o
def ring(p,r,t,m,vertical=False):
 bpy.ops.mesh.primitive_torus_add(major_radius=r,minor_radius=t,major_segments=56,minor_segments=14,location=p);o=finish(m,'Raised rim')
 if vertical:o.rotation_euler.x=math.pi/2
 for f in o.data.polygons:f.use_smooth=True
 return o
def legs(w,d,h):
 for x in [-w,w]:
  for y in [-d,d]:tube((x,y,h/2),.018,.028,h,wood)
def profile(rz,m):
 vs=[];fs=[];n=48
 for r,z in rz:
  for i in range(n):
   a=math.tau*i/n;vs.append((r*math.cos(a),r*math.sin(a),z))
 for j in range(len(rz)-1):
  for i in range(n):fs.append((j*n+i,j*n+(i+1)%n,(j+1)*n+(i+1)%n,(j+1)*n+i))
 mesh=bpy.data.meshes.new('Thrown ceramic');mesh.from_pydata(vs,[],fs);o=bpy.data.objects.new('Thrown ceramic',mesh);bpy.context.collection.objects.link(o);mesh.materials.append(m)
 for f in mesh.polygons:f.use_smooth=True
def make(typ,k):
 if typ=='chair':
  legs(.25,.21,.26);block((0,0,.30),(.72,.63,.16),color,.08);block((0,.25,.56),(.70,.16,.53),color,.08).rotation_euler.x=-.10
  for x in [-.33,.33]:
   if k==1:block((x,0,.47),(.065,.54,.06),wood,.024)
   else:block((x,0,.43),(.11,.58,.22+k*.025),color,.05)
  for x in [-.20,0,.20]:ball((x,.152,.6),(.024,.018,.024),brass)
 elif typ=='table':
  if k!=1:
   tube((0,0,.025),.22,.20,.05,wood);tube((0,0,.22),.095,.055,.36,color);tube((0,0,.435),.32,.32,.075,color);ring((0,0,.419),.314,.007,brass)
   if k==2:tube((0,0,.15),.23,.22,.025,wood)
  else:
   legs(.22,.18,.13);block((0,0,.30),(.56,.44,.35),color,.035)
   for z in [.23,.38]:block((0,-.227,z),(.50,.021,.13),wood);block((0,-.248,z),(.11,.024,.012),brass)
 elif typ=='lamp':
  tube((0,0,.025),.14,.145,.05,color);tube((0,0,.24),.024,.014,.4,brass)
  if k==0:
   ball((0,0,.425),(.22,.22,.072),color);ball((0,0,.409),(.14,.14,.072),cream)
  elif k==1:
   for z,r in [(.43,.18),(.50,.13),(.55,.08)]:tube((0,0,z),r,r*.40,.076,color)
   ball((0,0,.39),(.09,.09,.07),cream)
  else:
   ball((0,0,.44),(.14,.14,.18),cream)
   for z,r in [(.34,.12),(.44,.142),(.54,.12)]:ring((0,0,z),r,.007,color)
 elif typ=='pouf':
  tube((0,0,.045),.23,.23,.09,wood)
  if k==0:
   ball((0,0,.205),(.33,.33,.17),color)
   for i in range(10):
    a=math.tau*i/10;ball((.22*math.cos(a),.22*math.sin(a),.21),(.12,.12,.152),color)
  elif k==1:
   tube((0,0,.175),.24,.28,.23,color);ball((0,0,.275),(.28,.28,.07),color);ball((0,0,.341),(.018,.018,.006),brass)
  else:block((0,0,.20),(.77,.48,.30),color,.11)
 elif typ=='plant':
  tube((0,0,.12),.108,.155,.24,clay);ring((0,0,.238),.151,.012,clay);tube((0,0,.238),.138,.138,.01,wood)
  for i in range(22):
   a=i*2.4;h=.25+(i%7)*.045+(k==1)*.22;r=.06+(.065 if k==2 else .014)*(i%3);x=r*math.cos(a);y=r*math.sin(a)
   tube((x/2,y/2,(.24+h)/2),.005,.004,h-.22,green)
   leaf=ball((x,y,h),(.045 if k!=2 else .055,.021,.118),green if i%3 else color);leaf.rotation_euler=(.6*math.sin(a),-.62*math.cos(a),a)
 elif typ=='vase':
  r=.11 if k!=1 else .14;h=[.30,.39,.32][k];profile([(0,0),(.064,0),(.076,.018),(r,h*.44),(r*.75,h*.70),(.054,h*.79),(.055,h*.93),(.065,h),(.056,h),(.047,h*.90),(.043,h*.74),(0,.026)],color)
  ring((0,0,h*.66),r*.81,.004,brass)
  if k in [1,2]:
   for x in ([-.096,.096] if k==1 else [.084]):
    t=ring((x,0,h*.71),.058,.012,color,True);t.scale.z=.68
 elif typ=='shelf':
  w=[.70,.68,.94][k];h=[.96,.78,.64][k];legs(w*.40,.14,.12)
  for z in [.13,h*.53,h]:block((0,0,z),(w,.38,.042),wood)
  for x in [-w*.475,w*.475]:block((x,0,(h+.13)/2),(.035,.37,h-.13),color)
  if k:
   block((0,.169,(h+.13)/2),(w*.9,.028,h-.13),color)
   for x in [-w*.24,w*.24]:
    block((x,-.195,(h*.53+.13)/2),(w*.46,.022,h*.53-.16),color);ball((x*.43,-.216,h*.36),(.012,.012,.012),brass)
  for i in range(5):
   block((-.22+i*.052,0,h*.53+.10),(.038,.17,.15+i%3*.024),[cream,clay,color][i%3])
 elif typ=='sculpt':
  block((0,0,.024),(.27,.16,.048),wood)
  if k==0:ring((0,0,.21),.138,.027,brass,True);ball((-.05,0,.13),(.055,.055,.055),color)
  elif k==1:
   for i in range(4):ball(((-1)**i*.024,0,.07+i*.061),(.115-i*.015,.078-i*.008,.04),[color,cream,clay][i%3])
  else:
   for i in range(2):
    x=-.055+.10*i;r=.064-i*.012;ring((x,.03*i,.19-.04*i),r,.019,color,True)
    for xx in [x-r,x+r]:tube((xx,.03*i,.11-.02*i),.018,.018,.16-.04*i,color)
 elif typ=='frame':
  width,height=[(.32,.41),(.61,.77),(1.05,.84)][k]
  trim=.027+k*.009
  block((0,.008,0),(width,.03,height),wood,.004)
  block((0,-.012,0),(width-2*trim,.009,height-2*trim),cream,.002)
  for side in [-1,1]:
   block((side*(width-trim)/2,-.018,0),(trim,.040,height),color,.008)
   block((0,-.018,side*(height-trim)/2),(width-2*trim,.040,trim),color,.008)
   block((side*(width-2*trim-.006)/2,-.024,0),(.006,.012,height-2*trim),brass,.002)
   block((0,-.024,side*(height-2*trim-.006)/2),(width-2*trim,.012,.006),brass,.002)
  block((0,-.019,0),((width-2*trim)*.72,.003,(height-2*trim)*.72),mat('Unprinted linen','D4C8B6'),.002)
 elif typ=='shop':
  block((0,0,.28),(.85,.48,.51),wood,.038);block((0,-.02,.06),(.91,.54,.08),brass);block((0,-.02,.56),(.92,.56,.065),cream)
  for x in [-.37,.37]:tube((x,.08,.83),.021,.021,.60,brass)
  for i in range(7):
   x=(i-3)*.132;c=[clay,cream][i%2];block((x,-.015,1.13),(.132,.61,.059),c).rotation_euler.x=.13
   ball((x,-.312,1.069),(.067,.029,.076),c)
  for x in [-.24,-.09,.11,.26]:ball((x,-.072,.647),(.077,.055,.061),[color,clay,green][int((x+1)*13)%3])
  for x in [-.27,0,.27]:block((x,-.254,.28),(.19,.018,.33),clay,.014)
def setup(key,typ,k,tint):
 global color,wood,brass,cream,clay,green
 bpy.ops.wm.read_factory_settings(use_empty=True)
 color=mat('Signature glaze',tint,rough=.34);wood=mat('Oiled walnut','8C6149');brass=mat('Honey brass','C89B58',.46,.28);cream=mat('Cream linen','F5DEC0');clay=mat('Burnished terracotta','B36C5E');green=mat('Sage foliage','718C6C')
 make(typ,k)
 obs=[o for o in bpy.context.scene.objects if o.type=='MESH'];bpy.ops.object.select_all(action='SELECT');bpy.context.view_layer.objects.active=obs[0];bpy.ops.object.convert(target='MESH');bpy.ops.object.join();o=bpy.context.object;o.name=key
 bpy.ops.object.transform_apply(location=True,rotation=True,scale=True);v=[o.matrix_world@Vector(a) for a in o.bound_box];lo=Vector(tuple(min(p[j] for p in v) for j in range(3)));hi=Vector(tuple(max(p[j] for p in v) for j in range(3)));center=(lo+hi)/2;span=hi-lo
 if key!='shop':
  path=MODELS/(key+'.glb');bpy.ops.export_scene.gltf(filepath=str(path),export_format='GLB',use_selection=True)
  data=path.read_bytes();n=struct.unpack_from('<I',data,12)[0];doc=json.loads(data[20:20+n])
  for m in doc['materials']:m['emissiveFactor']=[v*.39 for v in m['pbrMetallicRoughness'].get('baseColorFactor',[1]*4)[:3]]
  j=json.dumps(doc,separators=(',',':')).encode();j+=b' '*(-len(j)%4);tail=data[20+n:];path.write_bytes(struct.pack('<4sIIII',b'glTF',2,20+len(j)+len(tail),len(j),0x4e4f534a)+j+tail)
 sc=bpy.context.scene;sc.render.engine='CYCLES';sc.cycles.samples=7;sc.cycles.use_denoising=True;sc.render.threads_mode='FIXED';sc.render.threads=4;sc.render.film_transparent=True;sc.render.image_settings.color_mode='RGBA';sc.render.resolution_percentage=100;sc.render.resolution_y=sc.render.resolution_x=242;sc.world=bpy.data.worlds.new('Studio');sc.world.use_nodes=True;sc.world.node_tree.nodes['Background'].inputs[0].default_value=(.80,.76,.70,1);sc.world.node_tree.nodes['Background'].inputs[1].default_value=.65
 for p,e in [((-1,-2,2.6),180),((2,-.4,1),75),((0,1.7,2),140)]:
  lamp=bpy.data.lights.new('Softbox','AREA');lamp.energy=e;lamp.size=2;ob=bpy.data.objects.new('Softbox',lamp);sc.collection.objects.link(ob);ob.location=p;ob.rotation_euler=(center-ob.location).to_track_quat('-Z','Y').to_euler()
 c=bpy.data.cameras.new('Lens');cam=bpy.data.objects.new('Camera',c);sc.collection.objects.link(cam);sc.camera=cam;c.type='ORTHO';c.ortho_scale=max(span)*1.35;cam.location=center+Vector((1.3,-2.2,1.08));cam.rotation_euler=(center-cam.location).to_track_quat('-Z','Y').to_euler()
 out=THUMBS/('room_'+key+'.png');sc.render.filepath=str(out);bpy.ops.render.render(write_still=True);im=Image.open(out).convert('RGBA');box=im.getbbox()
 if box:
  im=im.crop(box);im.thumbnail((220,220));bg=Image.new('RGBA',(232,232));bg.alpha_composite(im,((232-im.width)//2,(232-im.height)//2));bg.save(out,optimize=True)
 return tuple(round(x,3) for x in (span.x,span.z,span.y))
if __name__=='__main__':
 meta=[]
 setup('shop','shop',0,'DEBC65')
 from shop_pricing import shop_price
 for i,(key,title,typ,tint,price) in enumerate(CAT):
  dims=setup(key,typ,i%3,tint);meta.append((key,title,typ,tint,shop_price(typ,*dims),*dims))
 (MODELS/'catalogue.json').write_text(json.dumps(meta,indent=1)+'\n')
