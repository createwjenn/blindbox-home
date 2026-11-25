"""Supply real local conditions to the debug PICO emulator when its virtual network is offline."""
import argparse
import json
import subprocess
import time
from urllib.parse import urlencode

PACKAGE = 'com.jenn.blindboxworld'

def fetch(url):
    reply = subprocess.run(['curl','--fail','--silent','--show-error','--max-time','12',url], capture_output=True,check=True)
    return json.loads(reply.stdout)

def sample():
    query = urlencode(dict(latitude=37.79, longitude=-122.39, current='weather_code,is_day,rain,showers',
        daily='sunrise,sunset', timezone='America/Los_Angeles', timeformat='unixtime', forecast_days=2))
    forecast = fetch('https://api.open-meteo.com/v1/forecast?'+query)
    return {'city':'San Francisco','zone':'America/Los_Angeles','current':forecast['current'],'daily':forecast['daily']}

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--device',default='emulator-5554')
    parser.add_argument('--watch',action='store_true',help='Refresh while the emulator is connected and the app is running')
    args=parser.parse_args()
    adb=['adb','-s',args.device]
    def shell(*argv):
        return subprocess.check_output(adb+['shell',*argv],text=True,timeout=8).strip()
    if shell('getprop','ro.hardware') not in ('goldfish','ranchu'):
        raise SystemExit('A running Android emulator is required')
    data={}
    next_sample=0
    while True:
        if time.monotonic() >= next_sample:
            try:
                data=sample()
                next_sample=time.monotonic()+(1200 if 'current' in data else 300)
            except (subprocess.SubprocessError,ValueError,KeyError) as exc:
                if not args.watch:
                    raise SystemExit(f'Rincon Hill forecast unavailable: {exc}')
                if not data:
                    time.sleep(60)
                    continue
                next_sample=time.monotonic()+300
        data['boot']=int(shell('settings','get','global','boot_count'))
        data['elapsed']=int(float(shell('cat','/proc/uptime').split()[0])*1000)
        data['epoch']=int(time.time()*1000)
        # run-as limits the file to the debuggable app; no port, proxy or device setting changes.
        subprocess.run(adb+['shell','run-as',PACKAGE,'sh','-c',
            '"cat > files/loft-preview.tmp && mv files/loft-preview.tmp files/loft-preview.json"'],
            input=json.dumps(data).encode(),check=True,timeout=8)
        print('Updated emulator clock'+(', city and forecast' if 'current' in data else ''),flush=True)
        if not args.watch:
            break
        time.sleep(60)
        if subprocess.run(adb+['shell','pidof',PACKAGE],capture_output=True).returncode:
            break

if __name__=='__main__':
    main()
