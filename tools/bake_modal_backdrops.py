"""Build Gaussian-filtered scenery for devices that cannot capture spatial render targets."""
from pathlib import Path
import io
import json
import struct

import numpy as np
from PIL import Image, ImageFilter
from import_world_horizons import sphere
from import_world_models import ASSETS, WORLDS, write_glb


def source_panorama(model):
    data = model.read_bytes()
    size, = struct.unpack_from('<I', data, 12)
    doc = json.loads(data[20:20+size])
    view = doc['bufferViews'][doc['images'][0]['bufferView']]
    start = 28 + size + view.get('byteOffset', 0)
    return Image.open(io.BytesIO(data[start:start+view['byteLength']])).convert('RGB')


def blur_panorama(photo, radius=13):
    # Wrap the longitude before convolution to avoid a vertical stripe at the seam.
    pad = radius * 4
    wrapped = Image.fromarray(np.pad(np.asarray(photo), ((0, 0), (pad, pad), (0, 0)), mode='wrap'))
    return wrapped.filter(ImageFilter.GaussianBlur(radius)).crop((pad, 0, pad+photo.width, photo.height))


def room_backdrop(destination):
    capture = Image.open(ASSETS.parents[3]/'design/screens/daily-draw-machine.png').convert('RGB')
    # Remove legacy clock/machine controls from the captured glass/window before filtering.
    for bounds in [(250, 115, 569, 188), (653, 145, 873, 303)]:
        x0, y0, x1, y1 = bounds
        strip = capture.crop((x0, y0-5, x1, y0))
        capture.paste(strip.resize((x1-x0, y1-y0)), (x0, y0))
    pic = capture.resize((768,576),Image.Resampling.LANCZOS).filter(ImageFilter.GaussianBlur(17))
    # This plate covers a 118-degree viewport behind the sharp presentation.
    tmp = io.BytesIO()
    pic.save(tmp, 'JPEG', quality=91)
    data = bytearray()
    views = []
    for part in (np.array([[-5.4,-4.05,0],[5.4,-4.05,0],[5.4,4.05,0],[-5.4,4.05,0]],'<f4').tobytes(),
                 np.array([[0,1],[1,1],[1,0],[0,0]],'<f4').tobytes(),
                 np.array([0,1,2,0,2,3],'<u2').tobytes(), tmp.getvalue()):
        data.extend(bytes(-len(data)%4))
        views.append(dict(buffer=0,byteOffset=len(data),byteLength=len(part)))
        data.extend(part)
    doc = dict(asset={'version':'2.0'},scene=0,scenes=[{'nodes':[0]}],nodes=[{'mesh':0}],
        meshes=[{'primitives':[{'attributes':{'POSITION':0,'TEXCOORD_0':1},'indices':2,'material':0}]}],
        extensionsUsed=['KHR_materials_unlit'], materials=[{'doubleSided':True,'extensions':{'KHR_materials_unlit':{}},
        'pbrMetallicRoughness':{'baseColorTexture':{'index':0}}}],textures=[{'source':0}],images=[{'bufferView':3,'mimeType':'image/jpeg'}],
        bufferViews=views,accessors=[{'bufferView':0,'componentType':5126,'type':'VEC3','count':4,
        'min':[-5.4,-4.05,0],'max':[5.4,4.05,0]},{'bufferView':1,'componentType':5126,'type':'VEC2','count':4},
        {'bufferView':2,'componentType':5123,'type':'SCALAR','count':6}])
    write_glb(destination,doc,data)


if __name__ == '__main__':
    out = ASSETS/'atmosphere/modal'
    out.mkdir(exist_ok=True)
    for slug in WORLDS:
        sphere(out/f'{slug}.glb',blur_panorama(source_panorama(ASSETS/'worlds'/slug/'horizon.glb')))
    room_backdrop(out/'room.glb')
