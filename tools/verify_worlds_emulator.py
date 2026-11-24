"""Inspect every rendered pickup, replay collision routes, and verify exit in a debuggable build.
Run after entering the room; requires the app's development receiver and an authorized PICO emulator.
No inventory or rewards are changed by inspection/replay.
"""
from pathlib import Path
import argparse,json,subprocess,time
from PIL import Image,ImageDraw
parser=argparse.ArgumentParser();parser.add_argument('--device',required=True);parser.add_argument('--worlds',nargs='*');args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
out=root/'verification/2026-11-23';out.mkdir(parents=True,exist_ok=True)
raw=Path('/tmp/blindbox-pickup-audit');raw.mkdir(exist_ok=True)
worlds=args.worlds or ['chill-guy','niu-lai','strawberlina','bananito','tung-tung-tung','tralalero-tralala','ballerina-capuccina','secret-capybara-toilet']
package='com.jenn.blindboxworld'
def run(*words):
 r=subprocess.run(['pico-cli',*map(str,words),'--device',args.device],text=True,capture_output=True,timeout=60)
 if r.returncode:raise RuntimeError(r.stdout+r.stderr)
 return r.stdout
def debug(cmd,*extra):return run('shell','am','broadcast','-a',package+'.DEBUG','-p',package,'--es','cmd',cmd,*extra)
def query():return json.loads(run('interact','query','--package',package,'--compact','--format','json'))
def wait_for(text,timeout=65):
 end=time.monotonic()+timeout
 while time.monotonic()<end:
  q=query()
  if text in json.dumps(q):return q
  time.sleep(1)
 raise RuntimeError('Timed out waiting for '+text)
def screenshot(path):
 run('capture','screenshot','--spatial','--size','1000x750','--out',path,'--replace')
 with Image.open(path) as im:im.verify()
reportPath=out/'world-report.json'
report=json.loads(reportPath.read_text()) if reportPath.exists() else []
for world in worlds:
 time.sleep(3)
 print('Opening '+world,flush=True)
 debug('verify-world','--es','figure',world)
 wait_for('0 / 30 collected')
 time.sleep(1)
 shots=[]
 for index in range(30):
  debug('inspect-pickup','--ei','index',index)
  time.sleep(.2)
  path=raw/f'{world}-{index+1:02}.png';screenshot(path);shots.append(path)
  if index%10==9: print(f'{world}: captured {index+1}/30 locations',flush=True)
 # Context surrounding each actual pickup center; full resolution captures are retained in /tmp.
 sheet=Image.new('RGB',(1500,1530),'#192026');draw=ImageDraw.Draw(sheet)
 for index,path in enumerate(shots):
  with Image.open(path) as im:
   tile=im.crop((280,180,700,600)).resize((250,230))
  x=(index%6)*250;y=(index//6)*300+30
  sheet.paste(tile,(x,y));draw.text((x+8,y+238),f'{world}  {index+1:02}/30',fill='white')
 sheet.save(out/f'{world}-pickups.jpg',quality=92)
 debug('verify-route')
 start=time.monotonic()
 wait_for('Result preview',timeout=125)
 elapsed=time.monotonic()-start
 screenshot(out/f'{world}-complete.png')
 logs=run('app','logcat','--package',package,'--tag','WorldVerification','--lines','400')
 (out/f'{world}-route.log').write_text(logs)
 import re
 contacts=sorted(set(map(int,re.findall(r'contact '+re.escape(world)+r' index=(\d+)',logs))))
 assert len(contacts)==30,(world,contacts)
 # Exercise the real exit control, including after completion.
 result=json.loads(run('interact','act','--package',package,'--text','Return to room','--action','click','--arguments','{}','--refresh','auto','--compact','--format','json'))
 assert result.get('performed'),result
 wait_for('room.floor')
 report=[entry for entry in report if entry['world']!=world]
 report.append(dict(world=world,renderedLocations=30,physicalContacts=len(contacts),routeWallSeconds=round(elapsed,1),returnedToRoom=True,contactIndices=contacts))
 (out/'world-report.json').write_text(json.dumps(report,indent=2)+'\n')
 print(f'{world}: 30 contacts, completed, returned to room ({elapsed:.1f}s)',flush=True)
print('All requested worlds inspected and traversed.',flush=True)
