"""Deterministic fallback textures; live WebGL supplies the runtime sky and bay."""
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

def water_image(phase='day'):
    colors={'day':(40,91,111),'sunrise':(80,101,115),'sunset':(73,76,93),'night':(9,24,37)}
    y,x=np.mgrid[:512,:1024];rng=np.random.default_rng(919)
    ripple=np.sin(y*.38+np.sin(x*.063)*4)+.4*np.sin(y*.81+x*.09)+rng.normal(0,.3,x.shape)
    arr=np.array(colors[phase])+ripple[:,:,None]*(1.5 if phase=='night' else 3.5)
    reflection=np.exp(-((x-650)/190)**2)*np.maximum(0,np.sin(y*.49+np.sin(x*.02)))
    if phase!='day':arr+=reflection[:,:,None]*np.array((40,25,11))
    return Image.fromarray(np.uint8(np.clip(arr,0,255)))

def facade_image(phase='day',index=0):
    rng=np.random.default_rng(730+index);w,h=512,1024;y,x=np.mgrid[:h,:w]
    night=phase=='night';warm=phase in ('sunrise','sunset')
    # Continuous sky reflections and slim mullions, with many small office windows.
    glass=index<4
    walls=[(55,78,91),(96,116,124),(119,126,129),(72,94,104),(164,151,132),(184,178,164),(129,133,133),(153,137,122)]
    base=np.array(walls[index%8],float)
    if night:base*=.20
    arr=np.broadcast_to(base,(h,w,3)).copy()
    reflection=(np.sin(x*.012+index)*8+np.cos(y*.004+index)*9)[...,None]
    arr+=reflection*(.18 if night else 1)
    if warm:arr=arr*np.array([1.06,.90,.78])
    im=Image.fromarray(np.uint8(np.clip(arr,0,255)));d=ImageDraw.Draw(im)
    for row,yy in enumerate(range(0,h,32)):
        for col,xx in enumerate(range(0,w,26)):
            lit=rng.random()<(.35 if night else .025)
            if lit:
                c=rng.choice([(227,191,127),(189,161,113),(237,218,176),(153,180,187)])
            else:
                v=rng.uniform(.78,1.08)
                c=np.array((29,43,56) if night else (88,120,140) if glass else (59,75,84))*v
                if warm:c*=np.array([1.15,.88,.72])
                c=tuple(np.uint8(np.clip(c,0,255)))
            pad=2 if glass else 5
            d.rectangle((xx+pad,yy+3,xx+23-pad,yy+26),fill=tuple(c))
            d.line((xx+12,yy+3,xx+12,yy+26),fill=tuple(np.uint8(np.array(c)*.50)),width=1)
            if rng.random()<.22:d.rectangle((xx+pad,yy+3,xx+23-pad,yy+9),fill=tuple(np.uint8(np.array(c)*.60)))
        d.line((0,yy+29,w,yy+29),fill=(21,28,34) if night else (94,106,112),width=2)
    return im

def atmosphere_image(phase,cloudy):
    w,h=2048,768;rng=np.random.default_rng(231)
    palettes={'day':[(38,112,174),(107,167,207),(205,219,223)],'sunrise':[(45,74,125),(137,145,174),(248,187,131)],'sunset':[(30,48,90),(146,105,122),(252,150,73)],'night':[(4,10,23),(13,25,45),(43,51,67)]}
    top,mid,low=map(np.array,palettes[phase]);v=np.linspace(0,1,h)[:,None,None]
    arr=np.where(v<.62,top+(mid-top)*v/.62,mid+(low-mid)*(v-.62)/.38)
    arr=np.broadcast_to(arr,(h,w,3)).copy();noise=np.zeros((h,w))
    for scale,amp in [(12,.52),(24,.26),(48,.13),(96,.065)]:
        raw=np.uint8(rng.uniform(0,255,(scale//3,scale)))
        noise+=np.array(Image.fromarray(raw).resize((w,h),Image.Resampling.BICUBIC))/255*amp
    clouds=np.clip((noise-(.39 if cloudy else .51))*6,0,1)
    clouds*=np.clip(1-(np.arange(h)/h-.45)**2*6,0,1)[:,None]
    tint=np.array((35,42,56) if phase=='night' else (236,225,213) if phase!='day' else (239,244,244))
    arr=arr*(1-clouds[:,:,None]*.70)+tint*clouds[:,:,None]*.70
    if cloudy:arr=arr*.86+np.mean(arr,axis=2)[:,:,None]*.10
    return Image.fromarray(np.uint8(np.clip(arr,0,255)))
