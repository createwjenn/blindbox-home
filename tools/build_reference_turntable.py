"""Reference-inspired turntable, metres/Y-up, origin at the supporting shelf."""
from pathlib import Path
import math
import numpy as np
import trimesh
from trimesh.visual.material import PBRMaterial
from PIL import Image
OUT=Path(__file__).resolve().parents[1]/'app/src/main/assets/props/reference-turntable.glb'
scene=trimesh.Scene()
def mat(name,color,rough=.4,metal=0,alpha=None,texture=None):
 return PBRMaterial(name=name,baseColorFactor=np.array(color,dtype=np.uint8),roughnessFactor=rough,metallicFactor=metal,alphaMode='BLEND' if alpha else 'OPAQUE',doubleSided=bool(alpha),baseColorTexture=texture)
y,x=np.mgrid[:256,:512];rng=np.random.default_rng(51);grain=.85+.10*np.sin(y*.5+4*np.sin(x*.02))+.035*rng.normal(size=x.shape)
woodTex=Image.fromarray(np.uint8(np.clip(grain[:,:,None]*np.array([83,48,29]),0,255)))
wood=mat('Dark walnut veneer',[255,255,255,255],.42,texture=woodTex)
black=mat('Satin charcoal',[25,28,28,255],.32)
metal=mat('Brushed aluminium',[161,157,142,255],.25,.75)
rubber=mat('Rubber feet',[15,17,16,255],.9)
amber=mat('Amber pressed vinyl',[240,154,24,255],.27,.08)
groove=mat('Fine amber grooves',[183,107,12,255],.36)
label=mat('Warm paper label',[249,189,63,255],.8)
ink=mat('Label typography',[58,39,22,255],.7)
clear=mat('Smoked acrylic',[174,151,114,36],.12,.08,alpha=True)
edge=mat('Polished acrylic edges',[160,142,108,105],.17,.1,alpha=True)
red=mat('Power indicator',[255,115,28,255],.3)
def add(mesh,name,m,position=(0,0,0),rotation=None):
 mesh.visual=trimesh.visual.TextureVisuals(uv=mesh.vertices[:,[0,2]]*5,material=m)
 transform=np.eye(4)
 if rotation is not None:
  baked=np.eye(4);baked[:3,:3]=rotation;mesh.apply_transform(baked)
 transform[:3,3]=position
 scene.add_geometry(mesh,node_name=name,geom_name=name,transform=transform)
def box(name,size,pos,m):add(trimesh.creation.box(size),name,m,pos)
def rod(name,a,b,r,m,sections=48):
 a=np.array(a);b=np.array(b);d=b-a
 mesh=trimesh.creation.cylinder(radius=r,height=np.linalg.norm(d),sections=sections)
 rot=trimesh.geometry.align_vectors([0,0,1],d/np.linalg.norm(d))[:3,:3]
 add(mesh,name,m,(a+b)/2,rot)
def rounded(name,w,d,h,y,m,r=.008):
 pts=[]
 for cx,cz,start in [(w/2-r,d/2-r,0),(-w/2+r,d/2-r,90),(-w/2+r,-d/2+r,180),(w/2-r,-d/2+r,270)]:
  for angle in np.linspace(start,start+90,9):
   a=math.radians(angle);pts.append((cx+r*math.cos(a),cz+r*math.sin(a)))
 verts=[(px,py,pz) for py in [y-h/2,y+h/2] for px,pz in pts];n=len(pts);faces=[]
 for i in range(1,n-1):faces.extend([(0,i+1,i),(n,n+i,n+i+1)])
 for i in range(n):j=(i+1)%n;faces.extend([(i,j,n+j),(i,n+j,n+i)])
 mesh=trimesh.Trimesh(verts,faces,process=False);mesh.fix_normals();add(mesh,name,m)
for xx in [-.175,.175]:
 for zz in [-.12,.12]:rod('Isolation foot '+str((xx,zz)),(xx,.002,zz),(xx,.015,zz),.023,rubber)
rounded('Walnut plinth',.46,.36,.043,.0365,wood)
rounded('Black inset top plate',.445,.345,.006,.061,black,r=.006)
rounded('Front aluminium lip',.448,.35,.003,.014,metal,r=.006)
# Record spindle at the same centre across all individually named spinning discs.
cx=-.047;cz=.012
rod('Machined platter',(cx,.065,cz),(cx,.078,cz),.153,metal,96)
rod('Amber vinyl',(cx,.078,cz),(cx,.081,cz),.149,amber,128)
# Grooves as separate thin annular meshes, grouped under one spin node by their local symmetry.
for i,r in enumerate(np.linspace(.047,.144,32)):
 verts=[];faces=[]
 for j in range(96):
  a=2*math.pi*j/96
  for rr in [r,r+.00028]:verts.append((cx+rr*math.cos(a),.0813,cz+rr*math.sin(a)))
 for j in range(96):k=j*2;l=((j+1)%96)*2;faces.extend([(k,l,l+1),(k,l+1,k+1)])
 add(trimesh.Trimesh(verts,faces,process=False),'Record groove '+str(i),groove)
rod('Ivory limewash',(cx,.0815,cz),(cx,.082,cz),.043,label,64)
for j in range(4):
 name='Label print '+str(j)
 box(name,(.021-j*.003,.0005,.001),(cx,.0826,cz-.017+j*.004),ink)
 scene.graph.update(frame_from='Ivory limewash',frame_to=name,matrix=trimesh.transformations.translation_matrix([0,.00085,-.017+j*.004]),geometry=name)
rod('Spindle',(cx,.079,cz),(cx,.092,cz),.003,metal,24)
# Tonearm: weighted pivot, articulated chrome tube, angled headshell and stylus.
rod('Tonearm bearing',(.166,.064,-.115),(.166,.100,-.115),.019,black)
rod('Tonearm gimbal',(.166,.09,-.115),(.166,.117,-.115),.011,metal)
rod('Rear counterweight',(.166,.116,-.15),(.166,.116,-.121),.015,black)
rod('Tonearm tube',(.166,.116,-.114),(.155,.102,.059),.0037,metal)
rod('Tonearm bend',(.155,.102,.059),(.122,.095,.096),.0037,metal)
box('Black headshell',(.015,.008,.029),(.119,.094,.107),black)
box('Amber cartridge',(.009,.008,.012),(.116,.087,.111),amber)
rod('Stylus',(.116,.083,.111),(.116,.081,.111),.001,metal,12)
box('Arm rest',(.017,.028,.015),(.181,.079,.026),black)
# Slim front control strip, selector, warm status LED.
box('Front control strip',(.137,.009,.018),(.147,.065,.153),black)
rod('Start button',(.183,.070,.152),(.183,.074,.152),.008,metal,32)
box('Speed selector',(.022,.003,.009),(.122,.071,.152),metal)
box('Warm status light',(.006,.002,.003),(.091,.071,.152),red)
# Raised dust cover: top and shallow side skirts, rotated around rear hinges.
a=math.radians(66);rot=np.array([[1,0,0],[0,math.cos(a),math.sin(a)],[0,-math.sin(a),math.cos(a)]])
def lidpart(name,size,local,m):
 pos=np.array([0,.071,-.169])+rot@np.array(local)
 add(trimesh.creation.box(size),name,m,pos,rot)
lidpart('Clear raised dust cover',(.452,.0025,.342),(0,.034,.171),clear)
for xx in [-.225,.225]:lidpart('Clear cover side '+str(xx),(.0025,.034,.342),(xx,.017,.171),clear)
lidpart('Clear cover front',(.452,.034,.0025),(0,.017,.342),clear)
for xx in [-.226,.226]:lidpart('Lid edge '+str(xx),(.0015,.0015,.343),(xx,.035,.171),edge)
lidpart('Lid front edge',(.452,.0015,.0015),(0,.035,.342),edge)
for xx in [-.16,.16]:box('Rear hinge '+str(xx),(.035,.018,.019),(xx,.068,-.164),metal)
# Match the room's baked/unlit pipeline for warm surfaces; keep metal physically shaded.
import json,struct
raw=scene.export(file_type='glb');n=struct.unpack_from('<I',raw,12)[0]
doc=json.loads(raw[20:20+n]);binary=raw[28+n:]
for material in doc['materials']:
 if material['name'] in ['Dark walnut veneer','Amber pressed vinyl','Fine amber grooves','Warm paper label','Label typography','Polished acrylic edges','Warm status light']:
  material.setdefault('extensions',{})['KHR_materials_unlit']={}
doc.setdefault('extensionsUsed',[]).append('KHR_materials_unlit')
encoded=json.dumps(doc,separators=(',',':')).encode();encoded+=b' '*((-len(encoded))%4)
binary+=b'\0'*((-len(binary))%4)
result=struct.pack('<III',0x46546c67,2,28+len(encoded)+len(binary))+struct.pack('<II',len(encoded),0x4e4f534a)+encoded+struct.pack('<II',len(binary),0x004e4942)+binary
OUT.parent.mkdir(parents=True,exist_ok=True);OUT.write_bytes(result);print(OUT,OUT.stat().st_size,scene.bounds.tolist())
