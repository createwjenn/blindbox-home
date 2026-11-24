"""Exercise the four sky phases using the existing emulator-only preview seam.
Preserves the exact original preview file; never changes the Android clock or user inventory.
"""
from pathlib import Path
import base64,json,shlex,subprocess,time
from datetime import datetime
from zoneinfo import ZoneInfo
root=Path(__file__).resolve().parents[1];out=root/'verification/2026-11-24-sf-webgl';out.mkdir(parents=True,exist_ok=True)
pkg='com.jenn.blindboxworld';device='emulator-5554'
def run(*words,check=True):
 r=subprocess.run(['pico-cli',*map(str,words),'--device',device],capture_output=True,text=True,timeout=45)
 if check and r.returncode:raise RuntimeError(r.stderr+r.stdout)
 return r.stdout
def shell(*words,**kw):return run('shell',*words,**kw)
def write(data):
 encoded=base64.b64encode(data.encode()).decode()
 shell('run-as',pkg,'sh','-c',shlex.quote('printf %s '+encoded+' | base64 -d > files/loft-preview.json'))
def enter():
 shell('am','force-stop',pkg);run('app','launch',pkg)
 for _ in range(15):
  q=json.loads(run('interact','query','--package',pkg,'--compact','--format','json'))
  if any(c.get('semanticId')=='landing.front-door' for c in q.get('controls',[])):break
  time.sleep(1)
 r=json.loads(run('interact','act','--package',pkg,'--semantic-id','landing.front-door','--action','click','--arguments','{}','--refresh','auto','--compact','--format','json'))
 assert r.get('performed'),r
 pid=shell('pidof',pkg).strip()
 for _ in range(25):
  logs=run('log','--pid',pid,'--tag','LoftGpu','--lines','15')
  if 'PRESENTED sky' in logs and 'PRESENTED bay' in logs:break
  time.sleep(1)
 else:raise RuntimeError('GPU did not present frames')
 return logs
original=shell('run-as',pkg,'cat','files/loft-preview.json')
(out/'preview-original.json').write_text(original)
try:
 for phase,hour,minute in [('sunrise',7,0),('day',12,0),('sunset',16,50),('night',22,0)]:
  epoch=int(datetime(2026,11,24,hour,minute,tzinfo=ZoneInfo('America/Los_Angeles')).timestamp()*1000)
  data={'city':'San Francisco','zone':'America/Los_Angeles','located':epoch//1000,'boot':int(shell('settings','get','global','boot_count')),'elapsed':int(float(shell('cat','/proc/uptime').split()[0])*1000),'epoch':epoch}
  write(json.dumps(data));logs=enter();time.sleep(1)
  expected={'sunrise':0,'day':1,'sunset':2,'night':3}[phase]
  assert f'phase={expected}.0' in logs,(phase,logs)
  shell('input','keyevent','--duration','850','21');time.sleep(.5)
  run('capture','screenshot','--spatial','--size','1200x900','--out',out/f'{phase}-runtime.png','--replace')
  (out/f'{phase}-gpu.log').write_text(logs)
  print('Captured '+phase,flush=True)
finally:
 write(original)
 print('Restored original emulator preview',flush=True)
 enter()
 print('Returned to normal room clock',flush=True)
