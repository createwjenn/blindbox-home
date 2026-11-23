"""Split the shipped printed box and two candy shells into their actual moving parts."""
from pathlib import Path
import json
import struct
import numpy as np

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
OUT = ASSETS / 'props/opening'
OUT.mkdir(exist_ok=True)
DT = {5123: '<u2', 5125: '<u4', 5126: '<f4'}

class Model:
    def __init__(self, path):
        raw = path.read_bytes()
        size, = struct.unpack_from('<I', raw, 12)
        self.doc = json.loads(raw[20:20+size])
        self.bin = raw[28+size:]

    def array(self, index):
        a = self.doc['accessors'][index]
        v = self.doc['bufferViews'][a['bufferView']]
        dims = {'VEC3':3, 'VEC2':2, 'SCALAR':1}[a['type']]
        kind = np.dtype(DT[a['componentType']])
        return np.ndarray((a['count'],dims), dtype=kind, buffer=self.bin,
            offset=v.get('byteOffset',0)+a.get('byteOffset',0),strides=(v.get('byteStride',dims*kind.itemsize),kind.itemsize))

    def write(self, name, parts):
        doc = dict(asset={'version':'2.0'}, scene=0, scenes=[{'nodes':[0]}], nodes=[{'name':name,'mesh':0}],
                   meshes=[{'primitives':[]}], materials=[], bufferViews=[], accessors=[])
        data=bytearray()
        def view(b):
            start=len(data); data.extend(b);data.extend(bytes(-len(data)%4))
            doc['bufferViews'].append(dict(buffer=0,byteOffset=start,byteLength=len(b)))
            return len(doc['bufferViews'])-1
        def accessor(array, component, attr):
            a=dict(bufferView=view(array.tobytes()), componentType=component,count=len(array),
                   type={1:'SCALAR',2:'VEC2',3:'VEC3'}[array.shape[1]])
            if attr=='POSITION': a.update(min=array.min(axis=0).tolist(),max=array.max(axis=0).tolist())
            doc['accessors'].append(a)
            return len(doc['accessors'])-1
        for p, triangles in parts:
            used, indices=np.unique(triangles,return_inverse=True)
            attrs={key:accessor(self.array(i)[used],5126,key) for key,i in p['attributes'].items()}
            mat=json.loads(json.dumps(self.doc['materials'][p['material']]))
            for value in [mat.get('pbrMetallicRoughness',{}).get('baseColorTexture')]:
                if value is None: continue
                source=self.doc['textures'][value['index']]['source'];image=self.doc['images'][source]
                iv=self.doc['bufferViews'][image['bufferView']];start=iv.get('byteOffset',0)
                dest=len(doc.setdefault('images',[]))
                doc['images'].append(dict(mimeType=image['mimeType'],bufferView=view(self.bin[start:start+iv['byteLength']])))
                doc.setdefault('textures',[]).append(dict(source=dest));value['index']=dest
                if 'emissiveTexture' in mat: mat['emissiveTexture']['index']=dest
            pi=accessor(indices.reshape(-1,1).astype('<u4'),5125,'indices')
            doc['meshes'][0]['primitives'].append(dict(attributes=attrs,indices=pi,material=len(doc['materials'])))
            doc['materials'].append(mat)
        doc['buffers']=[dict(byteLength=len(data))]
        meta=json.dumps(doc,separators=(',',':')).encode();meta+=b' '*(-len(meta)%4)
        (OUT/(name+'.glb')).write_bytes(struct.pack('<4sIIII',b'glTF',2,28+len(meta)+len(data),len(meta),0x4e4f534a)+meta+
                                      struct.pack('<II',len(data),0x004e4942)+data)

box=Model(ASSETS/'box.glb')
groups={name:[] for name in ('lid','front','right','back','left','base')}
for i,m in enumerate(box.doc['meshes']):
    p=m['primitives'][0];vertices=box.array(p['attributes']['POSITION']);tris=box.array(p['indices']).reshape(-1,3)
    if i==0:
        centre=vertices[tris].mean(axis=1)
        side=np.argmax(np.stack((centre[:,2]/.22,centre[:,0]/.31,-centre[:,2]/.22,-centre[:,0]/.31),axis=1),axis=1)
        side[centre[:,1]<.025]=4
        for k,name in enumerate(('front','right','back','left','base')):
            groups[name].append((p,tris[side==k]))
    else:
        x,y,z=vertices.mean(axis=0)
        name='lid' if y>.76 else ('front' if abs(z/.22)>abs(x/.31) and z>0 else 'back' if abs(z/.22)>abs(x/.31) else 'right' if x>0 else 'left')
        groups[name].append((p,tris))
for name,parts in groups.items(): box.write('box-'+name,parts)
for color in ('red','pink'):
    ball=Model(ASSETS/f'props/gumball-{color}.glb')
    prim=ball.doc['meshes'][0]['primitives']
    for label,sections in [('lower',prim[:2]),('upper',prim[2:])]:
        ball.write(f'{color}-{label}',[(p,ball.array(p['indices']).reshape(-1,3)) for p in sections])
