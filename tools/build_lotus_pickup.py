import sys, math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parent))
from build_world_hud import Drawing
# Reuse the project's colored-mesh exporter; radial, cupped petals make a lotus, not a leaf.
d=Drawing()
for ring,(n,length,width,base,rise) in enumerate([(10,.17,.067,-.04,.075),(8,.135,.06,-.015,.115),(6,.09,.04,.005,.13)]):
 for petal in range(n):
  angle=petal*math.tau/n+ring*.27
  def pt(t,v):
   r=.015+length*t
   lateral=math.sin(math.pi*t)**.8*width*v
   y=base+rise*t*t+.018*(1-v*v)*math.sin(math.pi*t)
   return (r*math.cos(angle)-lateral*math.sin(angle),y,r*math.sin(angle)+lateral*math.cos(angle))
  for i in range(12):
   for k in range(6):
    t=i/12;u=(i+1)/12;v=-1+k/3;w=v+1/3
    col=(1.,.48+.34*t,.65+.25*t)
    a,b,c,e=pt(t,v),pt(u,v),pt(u,w),pt(t,w)
    d.triangle(a,b,c,col);d.triangle(a,c,e,col)
# Golden seed heart, visible from above and the side.
for i in range(24):
 a=i*math.tau/24;b=(i+1)*math.tau/24
 d.triangle((0,.145,0),(.039*math.cos(a),.09,.039*math.sin(a)),(.039*math.cos(b),.09,.039*math.sin(b)),(1.,.78,.22))
import build_world_hud
build_world_hud.OUT=Path(__file__).resolve().parents[1] / 'app/src/main/assets/worlds/chill-guy'
d.vertices = [tuple(v*.75 for v in p) for p in d.vertices]
d.save('pickup')
