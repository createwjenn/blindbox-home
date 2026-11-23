"""Author the loft's sky, skyline and baked-light variants. Store the unchanged GLB bin only once."""
from pathlib import Path
from io import BytesIO
import copy
import json
import random
import struct
from PIL import Image, ImageDraw, ImageFilter

ASSETS = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
PHASES = {
    'day': (['518AC7', '90C4E9', 'E1EBE9', 'DDDCD0'], [1, 1, 1]),
    'sunrise': (['596EAA', 'B7A7C1', 'FFC6A1', 'F4D8B9'], [.84, .69, .63]),
    'sunset': (['535684', 'C18191', 'FFAD6E', 'EAC2A9'], [.92, .61, .43]),
    'night': (['0C152C', '172943', '3B4662', '293345'], [.31, .25, .25])
}

def rgb(s):
    return tuple(bytes.fromhex(s))

def mix(a, b, f):
    return tuple(round(x + (y-x)*f) for x,y in zip(a,b))

def sky_texture(phase, cloudy):
    colors = [rgb(s) for s in PHASES[phase][0]]
    w, h = 1024, 352
    sky = Image.new('RGB',(w,h))
    draw = ImageDraw.Draw(sky)
    stops = [0, .34, .63, 1]
    for y in range(h):
        v = y/(h-1)
        i = next(i for i in range(3) if v <= stops[i+1])
        c = mix(colors[i], colors[i+1], (v-stops[i])/(stops[i+1]-stops[i]))
        if cloudy:
            gray = round(sum(c)/3 * .80)
            c = mix(c,(gray, gray+2, gray+5),.72)
        draw.line((0,y,w,y),fill=c)
    rng = random.Random(423)
    if phase == 'night' and not cloudy:
        for _ in range(260):
            x,y = rng.randrange(w),rng.randrange(round(h*.58))
            k = rng.uniform(.20,.74)*(1-.75*y/h)
            draw.point((x,y), fill=mix(sky.getpixel((x,y)),(225,235,254),k))
    else:
        haze = Image.new('RGBA',(w,h))
        pen = ImageDraw.Draw(haze)
        for _ in range(100 if cloudy else 29):
            x,y = rng.randrange(-170,w),rng.randrange(0,int(h*.64))
            width = rng.randrange(30,170)
            color = (105,112,130) if cloudy else mix(colors[2],(255,247,241),.50)
            pen.ellipse((x,y,x+width,y+rng.randint(4,22)),fill=(*color, rng.randrange(17,64)))
        sky.paste(haze.filter(ImageFilter.GaussianBlur(5)),(0,0),haze.getchannel('A').filter(ImageFilter.GaussianBlur(5)))
    return sky

def skyline(phase, idx):
    rng = random.Random(99 + idx)
    night = phase == 'night'
    wall = (25+idx*3,31+idx*3,45+idx*3) if night else (107+idx*4,98+idx*3,102+idx*3)
    image = Image.new('RGB',(256,320),wall)
    d = ImageDraw.Draw(image)
    for y in range(0,320,32):
        d.line((0,y,255,y),fill=mix(wall,(130,145,168),.18),width=2)
        for x in range(0,256,32):
            lit = rng.random() < (.47 if night else .23)
            swatches = ['FFCF83','F5E6C4','D79251','ADD0DA'] if lit else (['222C3D','303C50','394458'] if night else ['808392','8E8884','667482'])
            c = rgb(rng.choice(swatches))
            d.rectangle((x+6,y+5,x+25,y+25),fill=c)
            d.line((x+15,y+5,x+15,y+25),fill=mix(wall,c,.30))
            if lit and rng.random()<.32:
                d.rectangle((x+6,y+5,x+25,y+11),fill=mix(wall,c,.55))
    return image

def write_look(source, bin_length, phase, cloudy):
    doc = copy.deepcopy(source)
    # Keep the steel mullions and the modeled city visible through both window banks.
    # The mobile PBR glass pass can composite foreground room geometry over the skyline.
    for node in doc['nodes']:
        if node.get('name') == 'Clear architectural glazing':
            node.pop('mesh', None)
    extra = bytearray()
    def texture(mat, image):
        data = BytesIO()
        image.save(data,format='PNG')
        raw=data.getvalue()
        view = len(doc['bufferViews'])
        doc['bufferViews'].append({'buffer':0,'byteOffset':bin_length+len(extra),'byteLength':len(raw)})
        extra.extend(raw)
        extra.extend(bytes(-len(extra)%4))
        i = len(doc['images'])
        doc['images'].append({'name':mat['name']+' '+phase,'bufferView':view,'mimeType':'image/png'})
        t = len(doc['textures'])
        doc['textures'].append({'source':i,'sampler':0})
        mat['emissiveTexture']={'index':t}
        mat['pbrMetallicRoughness']['baseColorTexture']={'index':t}
    for mat in doc['materials']:
        name = mat['name']
        if name == 'Evening sky':
            texture(mat, sky_texture(phase, cloudy))
        elif name.startswith('City facade ') and phase != 'day':
            texture(mat, skyline(phase, int(name[-1])))
        elif name == 'Baked loft illumination':
            mat['emissiveFactor'] = [round(v*(.81 if cloudy else 1),3) for v in PHASES[phase][1]]
        elif name == 'Zinc roof' and phase=='night':
            mat['emissiveFactor']=[.012,.017,.028]
            mat['pbrMetallicRoughness']['baseColorFactor']=[.045,.057,.075,1]
    doc['buffers'][0]['byteLength'] = bin_length + len(extra)
    header = json.dumps(doc,separators=(',',':')).encode()
    header += b' ' * (-len(header)%4)
    key=phase+('-cloud' if cloudy else '')
    (ASSETS/'atmosphere'/f'{key}.look').write_bytes(struct.pack('<I',len(header))+header+extra)

with (ASSETS/'collection-room.glb').open('rb') as f:
    f.seek(12)
    n,_ = struct.unpack('<II',f.read(8))
    source = json.loads(f.read(n))
    bin_length, _ = struct.unpack('<II',f.read(8))
for phase in PHASES:
    for cloudy in [False,True]:
        write_look(source,bin_length,phase,cloudy)
