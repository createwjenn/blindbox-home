"""Bake the world controls and welcome glyphs as true geometry so they do not cut rectangular holes in splats."""
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import numpy as np
from import_world_models import ASSETS, write_glb

OUT = ASSETS / "world-hud"
OUT.mkdir(exist_ok=True)
font_file = "/System/Library/Fonts/Supplemental/Arial Bold.ttf"
white = (.98, .97, .89)
shadow = (.028, .014, .018)
items = {"ballerina-capuccina":"coffee beans", "bananito":"bananas", "chill-guy":"lotus flowers",
         "niu-lai":"pink flowers", "secret-capybara-toilet":"little poops", "strawberlina":"strawberries",
         "tralalero-tralala":"fish", "tung-tung-tung":"wood chunks"}


class Drawing:
    def __init__(self):
        self.vertices, self.colors = [], []

    def triangle(self, a,b,c, color):
        self.vertices.extend([a,b,c])
        self.colors.extend([color]*3)

    def disk(self, x, y, z, radius, color):
        for i in range(80):
            a, b = i*math.tau/80,(i+1)*math.tau/80
            self.triangle((x,y,z),(x+radius*math.cos(a),y+radius*math.sin(a),z),
                          (x+radius*math.cos(b),y+radius*math.sin(b),z),color)

    def text(self, value, height, cy, color, max_width, drop_shadow=False):
        # Generate triangles only where a glyph has ink. Native 3DGS correctly
        # remains visible between letters and inside their counters.
        ppm=900
        font=ImageFont.truetype(font_file,round(height*ppm*1.4))
        left,top,right,bottom=font.getbbox(value)
        width,h=right-left,bottom-top
        mask=Image.new("L",(width,h))
        ImageDraw.Draw(mask).text((-left,-top),value,font=font,fill=255)
        scale=min(height/h,max_width/width)
        pixels=np.asarray(mask)>110
        active={}
        def flush(span, start, end):
            l,r=span
            for ox,oy,oz,col in ((.0028,-.0045,.0003,shadow),(0.,0.,.0014,color)) if drop_shadow else ((0.,0.,.0014,color),):
                x0=(l-width/2)*scale+ox;x1=(r-width/2)*scale+ox
                y0=(h/2-end)*scale+cy+oy;y1=(h/2-start)*scale+cy+oy
                a=(x0,y0,oz);b=(x1,y0,oz);c=(x1,y1,oz);d=(x0,y1,oz)
                self.triangle(a,b,c,col);self.triangle(a,c,d,col)
        for y in range(h+1):
            cuts=np.flatnonzero(np.diff(np.r_[False,pixels[y],False])) if y<h else []
            spans=set(zip(cuts[::2],cuts[1::2]))
            for span in active.keys()-spans: flush(span,active.pop(span),y)
            for span in spans-active.keys(): active[span]=y

    def save(self,name):
        pos=np.array(self.vertices,'<f4'); color=np.array(self.colors,'<f4')
        doc=dict(asset={'version':'2.0'},scene=0,scenes=[{'nodes':[0]}],nodes=[{'mesh':0}],
            meshes=[{'primitives':[{'attributes':{'POSITION':0,'COLOR_0':1},'material':0}]}],
            materials=[{'doubleSided':True,'extensions':{'KHR_materials_unlit':{}}}],extensionsUsed=['KHR_materials_unlit'],
            bufferViews=[{'buffer':0,'byteOffset':0,'byteLength':pos.nbytes},{'buffer':0,'byteOffset':pos.nbytes,'byteLength':color.nbytes}],
            accessors=[{'bufferView':0,'componentType':5126,'type':'VEC3','count':len(pos),'min':pos.min(axis=0).tolist(),'max':pos.max(axis=0).tolist()},
                       {'bufferView':1,'componentType':5126,'type':'VEC3','count':len(pos)}])
        write_glb(OUT/(name+'.glb'),doc,bytearray(pos.tobytes()+color.tobytes()))


for label in ("Walk","Jump","Turn"):
    draw=Drawing()
    if label=="Jump":
        draw.disk(0,0,0,.060,(.072,.065,.063))
        draw.text(label,.025,0,white,.085)
    else:
        draw.disk(0,0,0,.084,(.087,.078,.078))
        draw.disk(0,.014,.0001,.051,(.138,.127,.118))
        # The plus / minus indicates whether the axis is directional or horizontal.
        for dx,dy in ((.031,.0018), (.0018,.031)) if label=="Walk" else ((.031,.0018),):
            a=(-dx,.014-dy,.0006);b=(dx,.014-dy,.0006);c=(dx,.014+dy,.0006);d=(-dx,.014+dy,.0006)
            draw.triangle(a,b,c,white); draw.triangle(a,c,d,white)
        draw.text(label,.018,-.053,white,.1)
    draw.save(label.lower())

for slug,item in items.items():
    draw=Drawing()
    name=' '.join(t.capitalize() for t in slug.split('-'))
    draw.text(name+"’s World",.105,.080,white,1.56,True)
    draw.text("Collect 30 "+item,.062,-.046,white,1.32,True)
    draw.save("welcome-"+slug)
for i in range(1,4):
    draw=Drawing()
    draw.text(f"Starting in {i}",.049,0,white,.8,True)
    draw.save("count-"+str(i))
