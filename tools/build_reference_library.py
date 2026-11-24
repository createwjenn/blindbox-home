"""Local reference furniture authoring. Independent GLBs; thumbnails render exported geometry.
Reference index: design/reference-library/inventory.json. Blender Python, metre units.
"""
import sys,math,json,random,re
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'design'))
import furniture_atelier as a
from shop_pricing import shop_price
import bpy
from mathutils import Vector
from PIL import Image
import numpy as np
ROOT=Path(__file__).resolve().parents[1]
rows=json.loads((ROOT/'design/reference-library/inventory.json').read_text())['items']
ORIGINAL_MAT=a.mat

def textured(name,h,kind):
 m=ORIGINAL_MAT(name,h,rough=.85 if kind=='fabric' else .38)
 rng=np.random.default_rng(47);y,x=np.mgrid[:256,:256]
 if kind=='wood':v=.78+.16*np.sin(y*.32+3*np.sin(x*.024))+.04*rng.normal(size=x.shape)
 elif kind=='fabric':v=.80+.13*rng.random(x.shape)+.06*np.sin(x*2)*np.sin(y*2)
 elif kind=='leather':v=.88+.07*rng.random(x.shape)+.04*np.sin(x*.4+np.sin(y*.5)*2)
 elif kind=='zebra':v=(np.sin(x*.12+4*np.sin(y*.035))>0)*.80+.13
 elif kind=='cow':v=(np.sin(x*.04+2*np.sin(y*.055))*np.cos(y*.07+x*.02)>.10)*.76+.16
 else:v=.86+.12*rng.random(x.shape)
 rgb=np.array([int(h[i:i+2],16) for i in (0,2,4)])
 p=ROOT/'design/reference-library'/('texture-'+kind+'-'+h+'.png');Image.fromarray(np.uint8(np.clip(v[:,:,None]*rgb,0,255))).save(p)
 tex=m.node_tree.nodes.new('ShaderNodeTexImage');tex.image=bpy.data.images.load(str(p));tex.image.pack()
 bs=m.node_tree.nodes.get('Principled BSDF');bs.inputs['Base Color'].default_value=(1,1,1,1);m.node_tree.links.new(tex.outputs['Color'],bs.inputs['Base Color'])
 return m

def rod(p,q,r,m):
 p,q=Vector(p),Vector(q);d=q-p;o=a.tube((p+q)/2,r,r,d.length,m);o.rotation_euler=d.to_track_quat('Z','Y').to_euler();return o

def curve(points,r,m,cyclic=False):
 c=bpy.data.curves.new('Bent continuous frame','CURVE');c.dimensions='3D';c.resolution_u=12;c.bevel_depth=r;c.bevel_resolution=3;c.use_fill_caps=True
 sp=c.splines.new('BEZIER');sp.bezier_points.add(len(points)-1)
 for v,p in zip(sp.bezier_points,points):v.co=p;v.handle_left_type=v.handle_right_type='AUTO'
 sp.use_cyclic_u=cyclic;o=bpy.data.objects.new('Continuous tubular form',c);bpy.context.collection.objects.link(o);o.data.materials.append(m);return o

def cushion(p,s,m,wrinkle=False):
 o=a.block(p,s,m,min(s)*.44)
 if wrinkle:
  sub=o.modifiers.new('Soft surface','SUBSURF');sub.levels=2
  tex=bpy.data.textures.new('Fine upholstery folds',type='CLOUDS');tex.noise_scale=.075
  dis=o.modifiers.new('Relaxed fabric','DISPLACE');dis.texture=tex;dis.strength=.009;dis.texture_coords='GLOBAL'
 # Welt cord following cushion perimeter, visible instead of printed-on seams.
 x,y,z=p;w,d,h=s;r=min(w,d)*.14
 curve([(x-w/2+r,y-d/2,z),(x+w/2-r,y-d/2,z),(x+w/2,y-d/2+r,z),(x+w/2,y+d/2-r,z),(x+w/2-r,y+d/2,z),(x-w/2+r,y+d/2,z),(x-w/2,y+d/2-r,z),(x-w/2,y-d/2+r,z)],.0025,m,True)
 return o

def disc(p,r,h,m):return a.tube(p,r,r,h,m)
def lathe(rz,m):a.profile(rz,m)
def foot(x,y,h,m):rod((x,y,.025),(x,y,h),.025,m);disc((x,y,.012),.028,.024,black)
def swivel(m):
 rod((0,0,.06),(0,0,.38),.035,m)
 for i in range(4):
  t=i*math.pi/2;rod((0,0,.09),(.35*math.cos(t),.35*math.sin(t),.035),.017,m)

def chair(style,c,wood,metal,black):
 if style in ['sled','sling','cantilever','zigzag']:
  cushion((0,0,.40),(.68,.60,.20),c,True);back=cushion((0,.23,.72),(.64,.17,.35),c,True);back.rotation_euler.x=-.12
  for x in [-.37,.37]:
   pts=[(x,.27,.08),(x,-.30,.08),(x,-.32,.55),(x,.25,.55),(x,.29,.76)] if style!='zigzag' else [(x,.30,.07),(x,-.29,.07),(x,.08,.30),(x,-.30,.55),(x,.28,.73)]
   curve(pts,.019,metal)
   if style=='sling':cushion((x,-.01,.57),(.075,.50,.04),wood)
 elif style in ['pillar','loop','twin','tubular','multi']:
  cushion((0,0,.43),(.64,.59,.15),c)
  if style=='tubular':
   for x in [-.26,.26]:curve([(x,-.36,.04),(x,-.18,.43),(x,.25,.46),(x,.25,.88)],.055,c)
   curve([(-.26,.25,.88),(0,.25,.91),(.26,.25,.88)],.055,c)
  else:
   for x in [-.23,.23]:disc((x,0,.21),.13,.42,c)
   if style=='twin':
    for x in [-.16,.16]:cushion((x,.22,.76),(.29,.20,.68),c)
   elif style=='multi':
    for x in [-.27,0,.27]:curve([(x,-.28,.06),(x,-.24,.53),(x,.22,.54),(x,.29,1.0),(x,.39,.98),(x,.40,.04)],.075,c)
   elif style=='loop':curve([(-.30,-.17,.45),(-.35,.20,.81),(0,.32,.92),(.35,.20,.81),(.30,-.17,.45)],.105,c)
   else:cushion((0,.22,.70),(.56,.21,.56),c)
 elif style in ['bolster','globe','disc']:
  if style=='globe':
   a.ball((0,0,.32),(.28,.28,.24),c)
   for x in [-.34,.34]:curve([(x,-.22,.02),(x,-.25,.76),(x,.23,.77),(x,.26,.02)],.012,metal)
  else:
   disc((0,0,.18),.35,.28,c);cushion((0,0,.39),(.75,.69,.20),c)
  curve([(-.32,-.13,.68),(-.32,.23,.70),(0,.28,.72),(.32,.23,.70),(.32,-.13,.68)],.10,c)
 elif style in ['floor','ribbed','pleated']:
  # Individually curved cushion channels follow the seat-to-back bend.
  for i in range(6):
   x=(i-2.5)*.125
   curve([(x,-.38,.25),(x,-.17,.20),(x,.11,.28),(x,.30,.63),(x,.31,.81)],.085,c)
   a.ball((x,-.38,.25),(.085,.085,.085),c);a.ball((x,.31,.81),(.085,.085,.085),c)
   if style=='pleated':
    for j in range(3):curve([(x-.045+j*.04,-.40,.27),(x-.035+j*.04,-.15,.29),(x-.045+j*.04,.25,.68)],.004,c)
  if style=='ribbed':
   for x in [-.27,.27]:rod((x,.22,.40),(x,.40,.025),.015,metal)
 elif style=='cone':
  swivel(metal)
  o=a.tube((0,.10,.68),.06,.43,.65,c);o.rotation_euler.x=-.35
  cushion((0,-.06,.47),(.55,.49,.12),c)
 elif style=='fold':
  for x in [-.28,.28]:
   for y in [-.21,.21]:foot(x,y,.40,metal)
  cushion((0,0,.43),(.57,.53,.12),c)
  for x in [-.25,.25]:
   o=a.ball((x,.10,.62),(.10,.31,.25),c);o.rotation_euler.y=-x*1.6
  a.ball((0,.25,.72),(.28,.08,.23),c)
 elif style in ['ball','egg','shell','s']:
  if style=='s':
   # Extruded continuous bent seat, not a block approximation.
   path=[(-.38,.03),(.14,.03),(.23,.16),(-.18,.40),(-.32,.48),(-.25,.55),(.20,.56),(.28,.87),(.35,1.0)]
   verts=[(x,y,z) for x in [-.25,.25] for y,z in path];n=len(path);faces=[(i,i+1,n+i+1,n+i) for i in range(n-1)]
   mesh=bpy.data.meshes.new('Bent shell');mesh.from_pydata(verts,[],faces);o=bpy.data.objects.new('Continuous S shell',mesh);bpy.context.collection.objects.link(o);o.data.materials.append(c);mod=o.modifiers.new('Smooth molded shell','SUBSURF');mod.levels=2;mod=o.modifiers.new('Shell thickness','SOLIDIFY');mod.thickness=.035
  else:
   swivel(metal if style!='ball' else ivory)
   # Open forward bowl with continuous exterior and lining.
   n=48;k=20;v=[];f=[]
   for j in range(k+1):
    theta=.24+(math.pi*.70)*j/k
    for i in range(n):
     t=math.tau*i/n;v.append((.46*math.sin(theta)*math.cos(t),.38*math.cos(theta),.72+(.63 if style=='egg' else .49)*math.sin(theta)*math.sin(t)))
   for j in range(k):
    for i in range(n):f.append((j*n+i,j*n+(i+1)%n,(j+1)*n+(i+1)%n,(j+1)*n+i))
   me=bpy.data.meshes.new('Open lounge shell');me.from_pydata(v,[],f);o=bpy.data.objects.new('Open shell',me);bpy.context.collection.objects.link(o);o.data.materials.append(ivory if style=='ball' else c)
   for p in me.polygons:p.use_smooth=True
   mod=o.modifiers.new('Shell thickness','SOLIDIFY');mod.thickness=.025
   cushion((0,-.12,.48),(.63,.54,.13),c)
   if style=='ball':cushion((0,.24,.73),(.49,.12,.48),c)
 elif style=='butterfly':
  for x in [-1,1]:
   curve([(x*.38,.30,.99),(x*.28,-.32,.07),(x*.10,.05,.39),(x*.34,-.34,.52)],.014,metal)
  N=22;v=[];f=[]
  for j in range(N):
   t=j/(N-1)
   for i in range(N):
    u=2*i/(N-1)-1;v.append((u*(.35-.10*math.sin(t*math.pi)),-.32+.62*t,.37+.52*t*t+.16*u*u))
  for j in range(N-1):
   for i in range(N-1):q=j*N+i;f.append((q,q+1,q+N+1,q+N))
  me=bpy.data.meshes.new('Saddle hide');me.from_pydata(v,[],f);o=bpy.data.objects.new('Slung hide',me);bpy.context.collection.objects.link(o);o.data.materials.append(c);uv=me.uv_layers.new()
  for p in me.polygons:
   p.use_smooth=True
   for li in p.loop_indices:idx=me.loops[li].vertex_index;uv.data[li].uv=(idx%N/(N-1),idx//N/(N-1))
  mod=o.modifiers.new('Leather thickness','SOLIDIFY');mod.thickness=.008
 elif style in ['flower','hand','pumpkin']:
  disc((0,0,.12),.27,.23,c);cushion((0,-.02,.34),(.68,.65,.18),c)
  for i in range(7 if style=='pumpkin' else 5):
   t=math.pi*(i/(6 if style=='pumpkin' else 4));x=.31*math.cos(t);y=.21*math.sin(t)
   a.ball((x,y,.55),(.15,.17,.30 if style=='hand' else .23),c)
 elif style=='roller':
  for x in [-.36,.36]:a.block((x,.02,.40),(.035,.8,.64),c,.015)
  for y,z in [(-.25,.33),(0,.35),(.24,.58),(.27,.82)]:
   rod((-.32,y,z),(.32,y,z),.105,blue)
   for x in [-.38,.38]:rod((x-.005,y,z),(x+.005,y,z),.024,metal)
 elif style=='wire':
  for x in [-.31,.31]:curve([(x,-.34,.03),(x,.33,.03),(x,.22,.83)],.013,c)
  for i in range(15):
   x=-.31+i*.044;curve([(x,-.29,.43),(x,0,.34),(x,.20,.49),(x,.24,.85)],.003,c)
  for j in range(15):
   t=j/14;rod((-.31,-.29+.53*t,.36+.49*t*t),(.31,-.29+.53*t,.36+.49*t*t),.003,c)
 else:
  # Club, cube, panel, barrel, knitted and slipcover constructions.
  for x in [-.29,.29]:
   for y in [-.22,.22]:foot(x,y,.21,wood)
  cushion((0,0,.35),(.76,.68,.22),c,True)
  cushion((0,.26,.66),(.75,.22,.55),c,True)
  for x in [-.36,.36]:
   cushion((x,0,.49),(.18,.65,.34),wood if style=='panel' else c,style!='panel')
   if style=='cube':curve([(x,-.31,.04),(x,-.31,.59),(x,.32,.59),(x,.32,.04)],.014,metal)
  if style=='knitted':
   for i in range(11):
    for j in range(8):a.ring((-.32+i*.064,-.25+j*.065,.48),.033,.018,c)

def lamp(style,c,wood,metal):
 if style in ['mushroom','stone','rattan','redbell']:
  lathe([(0,0),(.10,0),(.07,.25),(.08,.31)],wood if style in ['stone','rattan'] else c)
  if style=='redbell':
   # scalloped bell mesh
   n=64;vs=[];fs=[]
   for j in range(12):
    t=j/11;r=.04+.18*t*t
    for i in range(n):u=math.tau*i/n;vs.append((r*math.cos(u),r*math.sin(u),.53-.25*t+.018*math.cos(u*8)*t))
   for j in range(11):
    for i in range(n):fs.append((j*n+i,j*n+(i+1)%n,(j+1)*n+(i+1)%n,(j+1)*n+i))
   me=bpy.data.meshes.new('Scalloped shade');me.from_pydata(vs,[],fs);o=bpy.data.objects.new('Scalloped shade',me);bpy.context.collection.objects.link(o);o.data.materials.append(c);o.modifiers.new('Shade gauge','SOLIDIFY').thickness=.006
  else:lathe([(.23,.29),(.23,.34),(.21,.41),(.15,.47),(.06,.50),(0,.505)],c)
  disc((0,0,.295),.19,.008,ivory)
  if style=='rattan':
   for i in range(48):
    t=math.tau*i/48;curve([(.23*math.cos(t),.23*math.sin(t),.29),(.21*math.cos(t),.21*math.sin(t),.40),(.10*math.cos(t),.10*math.sin(t),.48)],.0035,wood)
 elif style in ['double','pinksolid','tiered']:
  disc((0,0,.015),.15,.03,metal if style!='pinksolid' else c);rod((0,0,.03),(0,0,.32),.016 if style!='pinksolid' else .085,metal if style!='pinksolid' else c)
  for z,r in [(.34,.18),(.44,.18)] if style!='tiered' else [(.30,.12),(.35,.20),(.40,.24),(.45,.18),(.49,.11)]:a.ball((0,0,z),(r,r,.065),c)
 elif style in ['orb','dome','glass','clear','stained']:
  if style in ['orb','stained']:a.ball((0,0,.14),(.13,.13,.14),ivory)
  elif style=='clear':a.tube((0,0,.19),.09,.06,.38,wood)
  else:disc((0,0,.015),.145,.03,metal);rod((0,0,.03),(0,0,.38),.012,metal)
  rod((0,0,.26),(0,0,.42),.014,metal)
  lathe([(.20,.34),(.20,.40),(.18,.48),(.10,.52),(0,.53)],c)
  disc((0,0,.34),.17,.008,ivory)
  if style=='stained':
   for i in range(12):
    t=math.tau*i/12;rod((.20*math.cos(t),.20*math.sin(t),.34),(.10*math.cos(t),.10*math.sin(t),.52),.003,metal)
 elif style=='paper':
  rz=[]
  for z,r,h in [(.16,.22,.15),(.39,.19,.14),(.60,.13,.12)]:
   for j in range(25):t=-math.pi/2+math.pi*j/24;rz.append((max(.025,r*math.cos(t)),z+h*math.sin(t)))
  lathe(rz,ivory)
  for r,z in rz[::2]:a.ring((0,0,z),r,.0015,wood)
  for x,y in [(-.13,-.10),(.13,-.10),(0,.12)]:rod((x,y,0),(x,y,.14),.004,black)
 elif style=='spindle':
  rz=[(0,0),(.14,0),(.14,.03)]
  for i in range(6):rz.extend([(.04,.08+i*.19),(.045,.12+i*.19),(.12,.18+i*.19),(.04,.23+i*.19)])
  lathe(rz,metal);a.ball((0,0,1.25),(.14,.14,.15),ivory)
 elif style=='bowl':a.tube((0,0,.16),.09,.09,.32,wood);a.ball((0,0,.37),(.20,.20,.09),c)
 else:disc((0,0,.10),.15,.20,wood);a.ball((0,0,.27),(.20,.20,.15),ivory)
 # Cable and switch remain separate geometries, baked into each export.
 curve([(0,.07,.02),(.12,.18,.008),(.24,.20,.008)],.002,black)

CHAIRS={
'ref01_04':'panel','ref01_05':'sled','ref01_07':'cantilever','ref02_05':'bolster','ref02_09':'multi',
'ref03_01':'butterfly','ref03_02':'butterfly',
**{f'ref04_{i:02}':v for i,v in enumerate(['loop','globe','sling','disc','tubular','twin','barrel','knitted','s','cone','ball','hand','sled','roller'],1)},
**{f'ref05_{i:02}':v for i,v in enumerate(['pillar','cube','egg','ribbed','sling','sled','ball','fold','egg','wire','pleated','club','pumpkin'],1)},
**{f'ref06_{i:02}':v for i,v in enumerate(['disc','barrel','cantilever','flower','butterfly','sled','floor','sled','panel','cube','shell','s'],1)},
**{f'ref07_{i:02}':v for i,v in enumerate(['sling','club','pleated','club','barrel','cantilever','flower','zigzag','sling','floor','bolster','sling'],1)}}
LAMPS={'ref01_01':'mushroom','ref01_06':'spindle','ref01_10':'orb','ref02_04':'orb','ref03_03':'double','ref03_04':'paper',**{f'ref08_{i:02}':v for i,v in enumerate(['glass','redbell','dome','short','stone','double','bowl','stained','tiered','clear','rattan','pinksolid'],1)}}

def tint(name):
 if 'Zebra' in name:return 'EEE6D3'
 if name.startswith('Ivory'):return 'E9E2CF'
 if name.startswith('White'):return 'F0EFE8'
 for word,c in [('Burgundy','681D2A'),('Red','B52216'),('Orange','DA601F'),('Burnt','AD4619'),('Terracotta','A95A37'),('Cognac','975124'),('Caramel','A56025'),('Mustard','A8862A'),('Amber','C48229'),('Ivory','E9E2CF'),('White','F0EFE8'),('Sage','9FAF82'),('Olive','6E742E'),('Chartreuse','A2AD4C'),('Teal','246369'),('Petrol','345B69'),('Pale blue','98BBCB'),('Sky blue','5297B5'),('Navy','132541'),('Royal','184D99'),('Cobalt','103BA5'),('Blue','176795'),('Lavender','A397C0'),('Pink','DC91A8'),('Dusty','AC7273'),('Rose','C98F8B'),('Rust','A85D32'),('Black','262222'),('Dark','3F302B'),('Brown','70513C'),('Walnut','68452C'),('Natural','B29169'),('Taupe','887769'),('Zebra','EEE6D3')]:
  if re.search(r'\b'+re.escape(word.lower())+r'\b',name.lower()):return c
 return 'C5AB87'

def make(typ,k):
 global ivory,black,blue
 key=CURRENT['id'];name=CURRENT['title'];c=a.color
 wood=textured('Oiled walnut veneer','805332','wood');black=ORIGINAL_MAT('Rubber and dark fittings','171819',rough=.8);metal=ORIGINAL_MAT('Polished tubular chrome','B9BEC1',metal=.92,rough=.19);ivory=ORIGINAL_MAT('Warm opal diffuser','F0E6CF',rough=.35);blue=textured('Blue padded rollers','2876A0','fabric')
 if key in CHAIRS:
  kind='cow' if 'cowhide' in name else 'zebra' if 'Zebra' in name else 'leather' if any(s in name.lower() for s in ['leather','cognac','caramel']) else 'fabric'
  if not any(s in name.lower() for s in ['transparent','translucent','lacquer','wire']):c=textured('Reference upholstery',tint(name),kind)
  chair(CHAIRS[key],c,wood,metal,black)
 elif key in LAMPS:lamp(LAMPS[key],c,wood if 'rattan' in name.lower() else textured('Natural stone','C4B293','stone'),metal)
 elif 'mirror' in name:
  pts=[]
  for i in range(80):
   t=math.tau*i/80;xx=math.copysign(abs(math.cos(t))**.35,math.cos(t));zz=math.copysign(abs(math.sin(t))**.35,math.sin(t));pts.append((.30*xx*(1+.045*math.sin(zz*13)),0,.60+.53*zz))
  for d in [0,.014,.028]:curve([(x*(1+d*3),y,z) for x,y,z in pts],.009,c,True)
  a.block((0,.015,.60),(.54,.012,1.0),metal,.08)
 elif 'vase' in name.lower():
  if 'arch' in name:
   scale=.7 if 'small' in name else 1 if 'medium' in name else 1.3
   curve([(-.09*scale,0,.03),(-.12*scale,0,.18*scale),(0,0,.30*scale),(.12*scale,0,.18*scale),(.09*scale,0,.03)],.045*scale,c)
   rod((.02,0,.26*scale),(.02,0,.37*scale),.025,c)
  else:
   lathe([(0,0),(.09,0),(.14,.10),(.10,.22),(.045,.33),(.045,.37),(.035,.37),(.03,.32)],c)
 elif 'cushion' in name:a.ball((0,0,.22),(.22,.22,.22),textured('Boucle loops',tint(name),'fabric'))
 elif 'rug' in name:
  a.block((0,0,.008),(1.42,1.90,.012),textured('Wool backing','947957','fabric'),.005)
  # Color bands follow the reference's irregular topographic contours.
  pal=['947957','BC965E','913C35','394E58','BCB6A2','706553']
  for i in range(37):
   r=1-i*.027;pts=[(r*(.75+.045*math.sin(t*7))*math.cos(t),r*(1.03+.04*math.sin(t*9))*math.sin(t),.012+i*.0001) for t in np.linspace(0,math.tau,100,endpoint=False)]
   curve(pts,.017,textured('Wool contour '+str(i),pal[i%6],'fabric'),True)
 elif 'shelf' in name:
  for i in range(7):
   z=.15+i*.23;curve([(-.20,0,z),(-.22,.03,z+.07),(0,.04,z+.10),(.22,.04,z+.13),(.20,0,z+.23)],.025,c)
   a.block((0,0,z),(.42,.25,.025),c,.012)
 elif 'serpentine' in name:
  for i in range(24):
   y=-.20+i*.017;curve([(-.25,y,.02),(.20,y,.02),(.22,y,.12),(-.16,y,.14),(-.18,y,.24),(.16,y,.26),(.20,y,.36),(-.22,y,.39),(-.25,y,.02)],.009,c)
 elif 'checkerboard' in name:
  for i in range(4):
   for j in range(4):
    for side in [0,1]:a.block(((-.225 if side else -.17+i*.113),(-.17+i*.113 if side else -.225),.06+j*.113),(.012 if side else .113,.113 if side else .012,.113),c if (i+j)%2 else ivory,.001)
  a.block((0,0,.46),(.46,.46,.025),wood)
 elif 'acrylic' in name.lower() or 'glass' in name.lower():
  if 'twisted' in name:
   for i in range(12):
    t=i*math.tau/12;curve([(.13*math.cos(t+z*2),.13*math.sin(t+z*2),z) for z in np.linspace(.02,.48,15)],.025,c)
  else:
   w,d,h=(1.0,.55,.40) if 'coffee' in name else (.43,.40,.48)
   for x in [-w/2,w/2]:a.block((x,0,h/2),(.018,d,h),c,.008)
   a.block((0,0,h),(w,d,.018),c,.008)
   if 'two-tier' in name or 'three-drawer' in name:a.block((0,0,h*.5),(w,d,.018),c,.008)
 elif 'rolling' in name:
  disc((0,0,.30),.22,.52,c)
  for z in [.18,.35,.52]:a.ring((0,0,z),.221,.003,black)
  for x,y in [(-.14,-.14),(.14,-.14),(-.14,.14),(.14,.14)]:a.ball((x,y,.035),(.025,.025,.03),black)
 elif 'slab' in name or 'corner' in name:
  a.block((0,0,.47),(.48,.44,.04),wood)
  for x in [-.17,.17]:a.block((x,0,.23),(.08,.40,.46),wood)
  if 'corner' in name:a.block((0,0,.22),(.48,.44,.03),wood)
 else:
  h=.38 if 'short' in name else .50;r=.25
  if 'fluted' in name:
   for i in range(9):t=math.tau*i/9;disc((.12*math.cos(t),.12*math.sin(t),h/2),.05,h,c)
  elif 'sphere' in name:
   a.ball((0,0,.17),(.14,.14,.17),c);a.ball((0,0,.34),(.10,.10,.10),c)
  else:a.tube((0,0,h/2),.13,.08,h,wood if 'Walnut' in name or 'oak' in name else c)
  disc((0,0,h),r,.04,c)
 # Exportable gloss/translucency, with opaque edges where appropriate.
 if any(s in name.lower() for s in ['acrylic','transparent','translucent','glass']):
  bs=c.node_tree.nodes.get('Principled BSDF');bs.inputs['Alpha'].default_value=.30;bs.inputs['Roughness'].default_value=.12;c.surface_render_method='BLENDED'

def build_meshes(typ,k):
 make(typ,k)
 bpy.ops.object.select_all(action='SELECT')
 bpy.context.view_layer.objects.active=list(bpy.context.scene.objects)[0]
 bpy.ops.object.convert(target='MESH')
 bpy.ops.object.join()
 o=bpy.context.object
 # Wall placement uses a centered origin; floor furniture uses its feet.
 if typ=='frame':
  middle=(min(v.co.z for v in o.data.vertices)+max(v.co.z for v in o.data.vertices))/2
  for v in o.data.vertices:v.co.z-=middle
 triangles=sum(len(p.vertices)-2 for p in o.data.polygons)
 if triangles>24000:
  mod=o.modifiers.new('VR geometry budget','DECIMATE');mod.ratio=24000/triangles
  bpy.context.view_layer.objects.active=o;bpy.ops.object.modifier_apply(modifier=mod.name)

a.make=build_meshes
if __name__=='__main__':
 import argparse
 p=argparse.ArgumentParser();p.add_argument('--start',type=int,default=0);p.add_argument('--end',type=int,default=999);args=p.parse_args()
 out=[]
 for CURRENT in rows[args.start:args.end]:
  key=CURRENT['id'];name=CURRENT['title'];typ='chair' if key in CHAIRS else 'lamp' if key in LAMPS else 'frame' if 'mirror' in name else 'vase' if 'vase' in name.lower() else 'rug' if 'rug' in name else 'shelf' if 'shelf' in name else 'table'
  dims=a.setup(key,typ,0,tint(name));out.append([key,name,typ,tint(name),shop_price(typ,*dims),*dims]);print('FINISHED',key,name,flush=True)
 (ROOT/f'design/reference-library/generated-{args.start}.json').write_text(json.dumps(out,indent=2))
