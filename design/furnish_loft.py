"""Recover each complete furnishing from the material-batched, light-baked loft without re-baking.
Run after collection_room.py and room_looks.py. Geometry, daylight UVs and original image buffers are preserved.
"""
from pathlib import Path
from collections import defaultdict
import copy,json,struct
import numpy as np

ASSETS=Path(__file__).resolve().parents[1]/'app/src/main/assets'
# glTF Y-up pivots are the point that rests on the floor/table or centre of the artwork.
PIVOTS={
 'stand':(0,0,-1.70),'console':(0,0,-4.12),'speaker-left':(-1.95,0,-4.15),'speaker-right':(1.95,0,-4.15),
 'arc-lamp':(-2.75,0,-3.95),'record-player':(-.55,.905,-4.12),'vinyl-rack':(.65,.905,-4.12),
 'mushroom':(-1.18,.905,-4.12),'sofa':(2.14,0,-1.69),'wool-rug':(2.06,0,-.67),
 'coffee-table':(2.10,0,-.33),'chair':(-3.22,0,.58),'ottoman':(-3.09,0,1.54),
 'sideboard':(-3.84,0,-1.38),'table-globe':(-3.68,.62,-1.53),'sofa-lamp':(3.64,0,-2.48),
 'orb':(3.66,0,1.72),'laurel-left':(-3.72,0,2.63),'laurel-right':(3.72,0,-3.55),
 'dining-table':(1.16,0,3.27),'chair-a':(.61,0,2.61),'chair-b':(1.71,0,2.61),'chair-c':(.61,0,3.93),'chair-d':(1.71,0,3.93),
 'ochre-canvas':(4.18,1.88,-1.48),'unboxing-print':(-.62,1.47,-4.36),'slop-print':(.92,1.98,-4.36),
 'capybara-print':(.10,2.48,-4.36),'calendar':(2.25,1.32,-4.38),'convex-mirror':(-1.72,2.58,-4.35)
}
def identify(name,lo,hi):
    x,y,z=(lo+hi)/2; w,h,d=hi-lo
    if name.startswith(('City','Zinc','Clear architectural','Evening','Blackened window')):return None
    if name=='Baked loft illumination':
        if y<.005 or y>3 or w>6 or d>5 or x < -4.1 or h>2:return None
        if .008<y<.03 and w>3:return 'wool-rug'
    if name in ('Convex mirror','Earth study in ochre','Poster: Unboxing','Poster: Slop','Print: Capybara','Wall calendar'):
        return {'Convex mirror':'convex-mirror','Earth study in ochre':'ochre-canvas','Poster: Unboxing':'unboxing-print',
                'Poster: Slop':'slop-print','Print: Capybara':'capybara-print','Wall calendar':'calendar'}[name]
    if w>4.1 or d>4.0 or h>3.2 or y>3.05 or y<0:return None
    if z < -4.345 and y>1 and -2.1<x<2.5:
        for k in ['convex-mirror','calendar','capybara-print','slop-print','unboxing-print']:
            px,py,pz=PIVOTS[k];ww,hh={'unboxing-print':(.34,.46),'slop-print':(.36,.50),'capybara-print':(.23,.28),'calendar':(.22,.33),'convex-mirror':(.23,.24)}[k]
            if abs(x-px)<=ww and abs(y-py)<=hh:return k
    if 4.13<x<4.27 and -2.15<z<-.80 and 1.08<y<2.70:return 'ochre-canvas'
    if -4.45<z<-3.68:
        if (name in ('Orange acrylic','Carrara marble') or -2.99<x<-2.48 or
            y>1.40 and -2.9<x<-1.1 and z> -4.26): return 'arc-lamp'
        if x<-1.55 and -2.30<x and y<1.19:return 'speaker-left'
        if 1.69<x<2.19 and y<.68:return 'speaker-right'
        if -1.55<x<1.55:
            if y<.906:return 'console'
            if x<-.86:return 'mushroom'
            if x<-.19:return 'record-player'
            return 'vinyl-rack'
    if abs(x)<.32 and -2.02<z<-1.36 and y<.75:return 'stand'
    if name in ('Laurel leaf','Laurel new growth') or (name=='Espresso' and (abs(z-2.63)<.04 or abs(z+3.55)<.04) and abs(x)>3.6):
        return 'laurel-left' if x<0 else 'laurel-right'
    if abs(x-3.64)<.2 and abs(z+2.48)<.15 and y<1.35:return 'sofa-lamp'
    if abs(x-3.66)<.2 and abs(z-1.72)<.15 and y<.65:return 'orb'
    if -4.1<x<-3.53 and -2.58<z<-.17 and y<1.2:return 'table-globe' if y>.62 else 'sideboard'
    if -3.64<x<-2.8 and .1<z<1.04 and y<1:return 'chair'
    if -3.44<x<-2.78 and 1.28<z<1.79 and y<.44:return 'ottoman'
    if .51<x<3.80 and -2.24<z<-1.08 and y<1:return 'sofa'
    if 1.0<x<3.15 and -.74<z<.04 and y<.65:return 'coffee-table'
    if .05<x<2.28 and 2.34<z<4.26 and y<1.30:
        if .09<abs(z-3.27)<.495 and y<.43 and (abs(x-.44)<.09 or abs(x-1.88)<.09):return 'dining-table'
        if abs(z-3.27)>.42:return ('chair-a' if x<1 else 'chair-b') if z<3.27 else ('chair-c' if x<1 else 'chair-d')
        return 'dining-table'
    return None

def split(p):
    raw=p.read_bytes(); n=struct.unpack_from('<I',raw,12)[0];g=json.loads(raw[20:20+n]);data=bytearray(raw[28+n:])
    if any(n['name'].startswith('furnishing_') for n in g['nodes']):return
    old_size=len(data)
    def get(i):
        a=g['accessors'][i]; v=g['bufferViews'][a['bufferView']];typ={5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']]
        return np.frombuffer(data,dtype=typ,count=a['count']*(3 if a['type']=='VEC3' else 1),offset=v.get('byteOffset',0)+a.get('byteOffset',0)).copy()
    def indices(ids):
        typ=5123 if max(ids)<65536 else 5125
        data.extend(bytes(-len(data)%4)); offset=len(data);raw=np.asarray(ids,dtype='<u2' if typ==5123 else '<u4').tobytes();data.extend(raw)
        view=len(g['bufferViews']);g['bufferViews'].append(dict(buffer=0,byteOffset=offset,byteLength=len(raw),target=34963))
        ai=len(g['accessors']);g['accessors'].append(dict(bufferView=view,componentType=typ,count=len(ids),type='SCALAR',min=[int(min(ids))],max=[int(max(ids))]));return ai
    source_nodes=list(g['nodes']);new_nodes=[];groups=defaultdict(list);bounds={}
    def node(o,k,prims):
        m=len(g['meshes']);g['meshes'].append(dict(name=o['name'],primitives=prims))
        nn=dict(o,mesh=m)
        if k:
            nn['translation']=(np.array(o.get('translation',(0,0,0)))-PIVOTS[k]).tolist()
            groups[k].append(len(new_nodes))
        new_nodes.append(nn);return len(new_nodes)-1
    roots=[]
    for o in source_nodes:
        mat=o['name']; buckets=defaultdict(list)
        for prim in g['meshes'][o['mesh']]['primitives']:
            v=get(prim['attributes']['POSITION']).reshape(-1,3).astype(float)*o.get('scale',[1]*3)
            q=o.get('rotation',[0,0,0,1]); t=2*np.cross(q[:3],v);v+=q[3]*t+np.cross(q[:3],t)+o.get('translation',[0,0,0])
            tri=get(prim['indices']).reshape(-1,3); parents=list(range(len(v)));same={}
            def root(i):
                while parents[i]!=i:parents[i]=parents[parents[i]];i=parents[i]
                return i
            for i,co in enumerate(np.round(v,5)):parents[i]=same.setdefault(tuple(co),i)
            for a,b,c in tri.tolist():
                r=root(a);parents[root(b)]=r;parents[root(c)]=r
            components=defaultdict(list)
            for face in tri.tolist():components[root(face[0])].extend(face)
            parts=defaultdict(list)
            for ids in components.values():
                coords=v[ids];lo=coords.min(0);hi=coords.max(0);k=identify(mat,lo,hi);parts[k].extend(ids)
                if k:bounds[k]=[np.minimum(bounds.get(k,[lo,hi])[0],lo),np.maximum(bounds.get(k,[lo,hi])[1],hi)]
            assert sum(map(len,parts.values()))==tri.size
            for k,ids in parts.items():
                buckets[k].append(prim if len(parts)==1 else dict(prim,indices=indices(ids)))
        for k,prims in buckets.items():
            i=node(o,k,prims)
            if not k:roots.append(i)
    for k,children in groups.items():
        roots.append(len(new_nodes));new_nodes.append(dict(name='furnishing_'+k,translation=PIVOTS[k],children=children))
    assert set(groups)==set(PIVOTS),set(PIVOTS)-set(groups)
    for k in PIVOTS: print(k,[(v-PIVOTS[k]).round(3).tolist() for v in bounds[k]])
    data.extend(bytes(-len(data)%4));g['nodes']=new_nodes;g['scenes'][0]['nodes']=roots
    for fp in (p.parent/'atmosphere').glob('*.look'):
        raw=fp.read_bytes();n=struct.unpack_from('<I',raw)[0];v=json.loads(raw[4:4+n]);extra=raw[4+n:]
        for key in ['nodes','meshes','scenes','accessors']:v[key]=copy.deepcopy(g[key])
        v['nodes']=[dict(x) for x in v['nodes']]
        for x in v['nodes']:
            if x['name']=='Clear architectural glazing':x.pop('mesh',None)
        # Existing texture references in the variant point to views appended after the original lattice.
        nbase=len(json.loads(p.read_bytes()[20:20+struct.unpack_from('<I',p.read_bytes(),12)[0]])['bufferViews'])
        original=v['bufferViews'][nbase:]; shift=len(g['bufferViews'])-nbase
        for image in v['images']:
            if image.get('bufferView',0)>=nbase:image['bufferView']+=shift
        v['bufferViews']=g['bufferViews']+[dict(view,byteOffset=view['byteOffset']+len(data)-old_size) for view in original]
        v['buffers'][0]['byteLength']=len(data)+len(extra)
        hdr=json.dumps(v,separators=(',',':')).encode();hdr+=b' '*(-len(hdr)%4);fp.write_bytes(struct.pack('<I',len(hdr))+hdr+extra)
    g['buffers'][0]['byteLength']=len(data);hdr=json.dumps(g,separators=(',',':')).encode();hdr+=b' '*(-len(hdr)%4)
    p.write_bytes(struct.pack('<4sIIII',b'glTF',2,28+len(hdr)+len(data),len(hdr),0x4e4f534a)+hdr+struct.pack('<II',len(data),0x004e4942)+data)


def compact(path):
    """Give each group a tightly bounded vertex buffer. Simplify mobile GPU residency after splitting."""
    raw=path.read_bytes()
    n=struct.unpack_from('<I',raw,12)[0]
    base=json.loads(raw[20:20+n])
    if base['asset'].get('extras',{}).get('furnitureVerticesCompacted'): return
    src=raw[28+n:]
    out=bytearray();views=[];access=[];meshes=[]
    previous=dict(base)
    def view(data,target=None):
        out.extend(bytes(-len(out)%4))
        v={'buffer':0,'byteOffset':len(out),'byteLength':len(data)}
        if target is not None:v['target']=target
        out.extend(data);views.append(v)
        return len(views)-1
    def read(a):
        typ={5123:'<u2',5125:'<u4',5126:'<f4'}[a['componentType']]
        size={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']]
        v=base['bufferViews'][a['bufferView']]
        assert 'byteStride' not in v
        return np.frombuffer(src,dtype=typ,count=a['count']*size,offset=v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,size)
    def attribute(indices,a):
        values=read(a)[indices]
        nxt={k:v for k,v in a.items() if k not in ('byteOffset','bufferView','count','min','max')}
        nxt.update(bufferView=view(values.tobytes(),34962),count=len(values))
        if 'min' in a:nxt['min']=values.min(0).tolist();nxt['max']=values.max(0).tolist()
        access.append(nxt);return len(access)-1
    nodes=copy.deepcopy(base['nodes'])
    for node in nodes:
        if 'mesh' not in node:continue
        mesh=copy.deepcopy(base['meshes'][node['mesh']])
        for prim in mesh['primitives']:
            idx=read(base['accessors'][prim['indices']]).reshape(-1)
            ids,remap=np.unique(idx,return_inverse=True)
            prim['attributes']={k:attribute(ids,base['accessors'][a]) for k,a in prim['attributes'].items()}
            small=len(ids)<=65536
            acc={'componentType':5123 if small else 5125,'type':'SCALAR','count':len(idx),'min':[0],'max':[len(ids)-1],
                 'bufferView':view(remap.astype('<u2' if small else '<u4').tobytes(),34963)}
            prim['indices']=len(access);access.append(acc)
        node['mesh']=len(meshes);meshes.append(mesh)
    image_views={}
    for img in base['images']:
        i=img['bufferView']
        v=base['bufferViews'][i];start=v.get('byteOffset',0)
        image_views[i]=view(src[start:start+v['byteLength']])
    out.extend(bytes(-len(out)%4))
    def header(document,isLook):
        d=copy.deepcopy(document)
        n0=len(base['bufferViews'])
        d['nodes']=copy.deepcopy(nodes)
        if isLook:
            for node in d['nodes']:
                if node['name']=='Clear architectural glazing':node.pop('mesh',None)
        d['meshes']=meshes;d['accessors']=access;d['bufferViews']=views.copy()
        for v in document['bufferViews'][n0:]:
            d['bufferViews'].append(dict(v,byteOffset=v['byteOffset']-len(src)+len(out)))
        for img in d['images']:
            i=img['bufferView']
            img['bufferView']=image_views[i] if i<n0 else len(views)+i-n0
        d['buffers'][0]['byteLength']+=len(out)-len(src)
        d['asset'].setdefault('extras',{})['furnitureVerticesCompacted']=True
        h=json.dumps(d,separators=(',',':')).encode();return h+b' '*(-len(h)%4)
    for p in (path.parent/'atmosphere').glob('*.look'):
        r=p.read_bytes();n=struct.unpack_from('<I',r)[0];h=header(json.loads(r[4:4+n]),True)
        p.write_bytes(struct.pack('<I',len(h))+h+r[4+n:])
    h=header(base,False);path.write_bytes(struct.pack('<4sIIII',b'glTF',2,28+len(h)+len(out),len(h),0x4e4f534a)+h+struct.pack('<II',len(out),0x004e4942)+out)

if __name__=='__main__':
    path=ASSETS/'collection-room.glb'
    split(path)
    compact(path)
