"""Prepare the nine unboxing.js puff materials and optionally preview the live entrance shader.

The sky is shaded procedurally at runtime. No sky frames are packaged.
Usage: python design/entry_clouds.py [--preview /tmp/door-sky.jpg]
"""
import argparse
from pathlib import Path
import math
import gzip
import numpy as np
from PIL import Image

OUT = Path(__file__).resolve().parents[1] / 'app/src/main/assets/atmosphere/cirrus'


def smooth(a,b,x):
    t = np.clip((x-a)/(b-a),0,1)
    return t*t*(3-2*t)


def hash3(x,y,z):
    p,q,r = [((v*.3183099 + n) % 1.)*17. for v,n in zip((x,y,z),(.1,.2,.3))]
    return (p*q*r*(p+q+r))%1.


def field(size, bounds, z, output=None):
    """Exact five-octave value noise, tabulated for the shader's three fixed Z slices."""
    lo,hi = bounds
    x,y=np.meshgrid(np.linspace(lo,hi,size,dtype=np.float32),np.linspace(lo,hi,size,dtype=np.float32))
    total=np.zeros_like(x)
    for octave in range(5):
        ix=np.floor(x);iy=np.floor(y);iz=math.floor(z)
        fx=smooth(0,1,x-ix);fy=smooth(0,1,y-iy);fz=smooth(0,1,z-iz)
        value=np.zeros_like(x)
        for j in (0,1):
            for i in (0,1):
                v=hash3(ix+i,iy+j,iz)*(1-fz)+hash3(ix+i,iy+j,iz+1)*fz
                value+=v*(fx if i else 1-fx)*(fy if j else 1-fy)
        total+=value*(.5**(octave+1));x=x*2.03+13.2;y=y*2.03+13.2;z=z*2.03+13.2
    if output:
        output.write(np.uint8(total.clip(0,1)*255+.5).tobytes())
    def sample(a,b):
        a=np.clip((a-lo)*((size-1)/(hi-lo)),0,size-1.001)
        b=np.clip((b-lo)*((size-1)/(hi-lo)),0,size-1.001)
        i=a.astype(np.int32);j=b.astype(np.int32)
        fx=a-i;fy=b-j
        return ((total[j,i]*(1-fx)+total[j,i+1]*fx)*(1-fy)+
                (total[j+1,i]*(1-fx)+total[j+1,i+1]*fx)*fy).astype(np.float32)
    return sample


def camera_basis(ray):
    # The web camera looks from (0,3.8,12) towards (0,39,-30) when ascent reaches one.
    # Aim the front of the immersive dome there while retaining continuous coverage behind it.
    x,y,z=ray
    pitch=math.atan2(39.-3.8,42.)
    dy=y*math.cos(pitch)-z*math.sin(pitch);dz=y*math.sin(pitch)+z*math.cos(pitch)
    norm=np.maximum(dy,.04)+.45
    return x/norm,dz/norm,dy


def shape_puffs():
    rng=np.random.default_rng(404)
    u,v=np.meshgrid(np.linspace(-1,1,384,endpoint=False,dtype=np.float32),
                    np.linspace(-1,1,384,endpoint=False,dtype=np.float32))
    for num in range(9):
        seed=int(rng.integers(1_000_000))
        def noise(x,y,s):
            ix=np.floor(x).astype(np.int64);iy=np.floor(y).astype(np.int64)
            fx=smooth(0,1,x-ix);fy=smooth(0,1,y-iy)
            def h(i,j):
                n=((i*374761393)^(j*668265263)^s)&0xffffffff
                n=((n^(n>>13))*1274126177)&0xffffffff
                return ((n^(n>>16))&0xffffffff)/4294967295.
            return ((h(ix,iy)*(1-fx)+h(ix+1,iy)*fx)*(1-fy)+
                    (h(ix,iy+1)*(1-fx)+h(ix+1,iy+1)*fx)*fy)
        n=0.; amp=.54;x=u*4+10;y=v*4+10
        for octave in range(5):
            n+=noise(x,y,seed+octave*73)*amp
            x=x*2.07+7.1;y=y*2.03+3.7;amp*=.48
        wx=u+(n-.5)*.28;wy=v+(noise(u*3+8,v*3+8,seed+9)-.5)*.22
        body=np.zeros_like(u)
        for _ in range(6):
            cx=(rng.random()-.5)*.65;cy=(rng.random()-.5)*.45
            rx=.2+rng.random()*.2;ry=.18+rng.random()*.2
            body=np.maximum(body,np.exp(-(((wx-cx)/rx)**2+((wy-cy)/ry)**2)*1.2))
        density=np.maximum(0,body+(n-.52)*.6-.16)
        edge=np.clip((1-np.maximum(abs(u),abs(v)))*8,0,1)
        alpha=(1-np.exp(-density*5))*edge
        val=np.clip(.73-v*.2+(n-.5)*.28,0,1)
        alpha[[0,-1],:]=0;alpha[:,[0,-1]]=0
        data=np.uint8(np.dstack([205+50*val,222+33*val,237+18*val,255*alpha]).clip(0,255))
        Image.fromarray(data).save(OUT/f'puff-{num}.png',optimize=True)


def make_sky(slices,ray,seconds):
    warp,sheet,threads=slices
    u,v,h=camera_basis(ray)
    t=seconds*.077
    x=u+seconds*.004;y=v
    w=warp(2*x+.055*np.sin(t),2*y+.070*np.sin(t*.83))
    sx=x+w*.8;sy=y+np.sin(x*2.2)*.3+w*.4
    broad=sheet(sx*3,sy*3.8+.10*np.sin(t*.81))
    detail=threads(sx*11+.39*np.sin(t*1.6),sy*13+.31*np.cos(t*1.13))
    veil=smooth(.43,.70,broad)*(.65+detail*.45)*.8
    # The website post-processes linear RGB with a 27-level weave then the
    # Three.js ACES output transform. The endpoints below are its displayed
    # sRGB blue and white, sampled from the original unboxing view.
    s=np.maximum(h,0)**.7
    low=np.array([101,159,214],np.float32)/255
    high=np.array([65,108,175],np.float32)/255
    blue=low+(high-low)*s[...,None]
    # Tone-mapped cirrus follows the non-linear response of the website's output pass.
    lit=1-np.exp(-3.05*veil)
    return np.uint8(np.clip((blue+(np.array([.955,.969,.986])-blue)*lit[...,None])*255,0,255))



def main():
    ap=argparse.ArgumentParser();ap.add_argument('--preview',type=Path);ap.add_argument('--seconds',type=float,default=0.);args=ap.parse_args()
    OUT.mkdir(parents=True,exist_ok=True);shape_puffs()
    # Reusable noise fields for the dynamically shaded mesh on devices where
    # the native rendering surface is unavailable. They encode no scene or sky.
    with gzip.GzipFile(OUT/'noise.bin.gz','wb',mtime=0) as output:
        slices=[field(512,(-6,6),2.4,output),field(512,(-12,14),6.,output),field(512,(-40,44),1.,output)]
    if args.preview:
        n,m=800,400
        x,y=np.meshgrid(np.linspace(-.89,.89,n),np.linspace(.447,-.447,m));r=np.sqrt(1+x*x+y*y)
        t=args.seconds*.077
        preview_slices=[slices[0],field(512,(-12,14),6.+.065*np.sin(t*.47)),
                        field(512,(-40,44),1.+.13*np.sin(t*.66))]
        Image.fromarray(make_sky(preview_slices,(x/r,y/r,-1/r),args.seconds)).save(args.preview)
    print('Nine original puff materials and reference preview')

if __name__=='__main__':
    main()
