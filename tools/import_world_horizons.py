"""Bake opaque far-field environment spheres from each original Marble scan.

Only directions behind the splats use this backdrop. The sphere stays oriented
in the capture frame as the camera turns. The full-detail SPZ is still the foreground.
"""
import argparse
import gzip
import io
import json
import math
import struct
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter
from import_world_models import ASSETS, WORLDS, write_glb


def panorama(source, origin, size=(1024, 512)):
    raw = gzip.decompress(source.read_bytes())
    _, version, n, _, fraction, _, _ = struct.unpack_from('<IIIBBBB', raw)
    assert version == 2
    coords = np.frombuffer(raw, np.uint8, n*9, 16).reshape(n, 3, 3).astype(np.int32)
    packed = coords[:,:,0] | (coords[:,:,1] << 8) | (coords[:,:,2] << 16)
    xyz = ((packed ^ 0x800000) - 0x800000).astype(np.float32) / (1 << fraction)
    xyz[:,1:] *= -1
    xyz -= origin
    r = np.sqrt((xyz**2).sum(axis=1)).clip(.01)
    alpha = np.frombuffer(raw, np.uint8, n, 16+n*9)
    colors = np.frombuffer(raw, np.uint8, n*3, 16+n*10).reshape(n,3)
    colors = np.clip((colors.astype(np.float32)-127.5)*(.28209479/.15)+127.5,0,255)
    u = np.arctan2(xyz[:,0], -xyz[:,2])/(2*np.pi)+.5
    v = .5-np.arcsin((xyz[:,1]/r).clip(-1,1))/np.pi
    w,h=size
    pixel = np.floor(v*h).astype(int).clip(0,h-1)*w + np.floor(u*w).astype(int)%w
    # Prefer distant silhouettes over nearby walls/furniture. Use nearer radiance
    # only where the capture has no distant samples looking in that direction.
    weights = (alpha.astype(float)/255)**2 * np.minimum(r/8, 1)**4
    mass = np.bincount(pixel,weights,minlength=w*h).reshape(h,w)
    rgb = np.stack([np.bincount(pixel,weights*colors[:,i],minlength=w*h).reshape(h,w)
                    / np.maximum(.000001,mass) for i in range(3)],axis=2)
    # Coarse-to-fine normalized convolution fills undersampled angular bins.
    result = Image.new('RGB', (1,1), tuple(np.average(colors, axis=0, weights=weights).astype(int)))
    coverage = np.minimum(1,mass)
    for width in (16,32,64,128,256,512,w):
        small=(width,width//2)
        a=Image.fromarray(coverage.astype('float32')).resize(small,Image.Resampling.BOX)
        den=np.asarray(a).clip(.000001)
        small_rgb=np.stack([np.asarray(Image.fromarray((rgb[:,:,i]*coverage).astype('float32')).resize(small,Image.Resampling.BOX))/den for i in range(3)],2)
        color=Image.fromarray(np.uint8(small_rgb.clip(0,255)))
        result=Image.composite(color,result.resize(small,Image.Resampling.BILINEAR),
            Image.fromarray(np.uint8((den*6).clip(0,1)*255)))
    return result.filter(ImageFilter.GaussianBlur(.65))


def sphere(output, image):
    pos=[]; uv=[]; faces=[]
    sides=64; bands=32
    for j in range(bands+1):
        lat=math.pi*(.5-j/bands)
        for i in range(sides+1):
            lon=(i/sides-.5)*2*math.pi
            pos.append([45*math.cos(lat)*math.sin(lon),45*math.sin(lat),-45*math.cos(lat)*math.cos(lon)])
            uv.append([i/sides,j/bands])
    for j in range(bands):
        for i in range(sides):
            a=j*(sides+1)+i;b=a+sides+1
            faces.extend([a,b,a+1,a+1,b,b+1])
    photo=io.BytesIO();image.save(photo,'JPEG',quality=91)
    binary=bytearray(); views=[]
    for data in (np.array(pos,'<f4').tobytes(),np.array(uv,'<f4').tobytes(),
                 np.array(faces,'<u2').tobytes(),photo.getvalue()):
        binary.extend(bytes(-len(binary)%4));views.append(dict(buffer=0,byteOffset=len(binary),byteLength=len(data)));binary.extend(data)
    doc=dict(asset={'version':'2.0'},scene=0,scenes=[{'nodes':[0]}],nodes=[{'mesh':0}],
        meshes=[{'primitives':[{'attributes':{'POSITION':0,'TEXCOORD_0':1},'indices':2,'material':0}]}],
        materials=[{'doubleSided':True,'extensions':{'KHR_materials_unlit':{}},'pbrMetallicRoughness':{'baseColorTexture':{'index':0}}}],
        extensionsUsed=['KHR_materials_unlit'],textures=[{'source':0}],images=[{'bufferView':3,'mimeType':'image/jpeg'}],
        bufferViews=views,accessors=[{'bufferView':0,'componentType':5126,'type':'VEC3','count':len(pos),'min':[-45,-45,-45],'max':[45,45,45]},
            {'bufferView':1,'componentType':5126,'type':'VEC2','count':len(uv)}, {'bufferView':2,'componentType':5123,'type':'SCALAR','count':len(faces)}])
    write_glb(output,doc,binary)


if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('dist',type=Path);args=parser.parse_args()
    for slug,folder in WORLDS.items():
        src=args.dist/'assets'/folder
        sx,sy,sz=json.loads((src/'navigation-playable.json').read_text())['spawn']
        pic=panorama(src/'ceramic.spz',(sx,sy+.56,sz+1.08))
        sphere(ASSETS/'worlds'/slug/'horizon.glb',pic)
        srcpic=args.dist/'assets/world-wallpapers'/f'{slug}.png'
        art=ASSETS.parent/'res/drawable-nodpi'/('painting_'+slug.replace('-','_')+'.jpg')
        Image.open(srcpic).convert('RGB').save(art,quality=94,optimize=True)
        portrait=ASSETS.parent/'res/drawable-nodpi'/('world_portrait_'+slug.replace('-','_')+'.png')
        portrait.write_bytes((args.dist/'assets/world-portraits'/f'{slug}.png').read_bytes())
        print(slug,flush=True)
