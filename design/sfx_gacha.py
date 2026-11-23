"""Synthesize the gachapon sounds (mono 48 kHz) into app/src/main/assets/sfx/."""
import sys, wave
from pathlib import Path
import numpy as np
here = Path(__file__).resolve().parent
out = Path(sys.argv[sys.argv.index('--out') + 1]) if '--out' in sys.argv else here.parent / "app/src/main/assets/sfx"
out.mkdir(parents=True, exist_ok=True); SR = 48000; rng = np.random.default_rng(21)
def t(d): return np.arange(int(SR * d)) / SR
def save(n, x):
    x = x / (np.abs(x).max() + 1e-9) * .85
    with wave.open(str(out / n), 'wb') as w: w.setnchannels(1); w.setsampwidth(2); w.setframerate(SR); w.writeframes((x * 32767).astype('<i2').tobytes())
def ping(f, d, decay, partials=((1, 1), (2.76, .5), (5.4, .25))):
    tt = t(d); return sum(a * np.sin(2 * np.pi * f * k * tt) for k, a in partials) * np.exp(-tt / decay) * np.minimum(1, tt / .001)
def click(d=.03, f=2400): tt = t(d); return (np.sin(2 * np.pi * f * tt) + .5 * rng.normal(size=tt.size)) * np.exp(-tt / .004)
def place(x, s, at): i = int(at * SR); x[i:i + s.size] += s[:max(0, x.size - i)]; return x
x = np.zeros(int(SR * .9)); place(x, click(.02, 3000) * .6, 0); place(x, ping(3100, .5, .09) * .5, .12); place(x, ping(2300, .5, .08) * .4, .22)
place(x, ping(1500, .5, .1) * .5, .36); save('coin_insert.wav', x)
save('coin_spawn.wav', ping(2600, .6, .18) + .4 * ping(3900, .6, .1))
save('crank_click.wav', click(.05, 1800) + .4 * click(.05, 900))
x = np.zeros(int(SR * 1.2))
for k in range(9): place(x, click(.04, 1400 + 120 * k) * (1 - k * .06), k * .1)
save('crank_turn.wav', x)
x = np.zeros(int(SR * 1.4)); tt = t(.7); roll = np.convolve(rng.normal(size=tt.size), np.ones(30) / 30, 'same') * (1 + np.sin(2 * np.pi * 9 * tt)) * np.sin(np.pi * tt / .7)
place(x, roll * 1.4, 0); place(x, ping(180, .5, .12, ((1, 1), (1.6, .6))), .72); place(x, .5 * click(.06, 600), .72); place(x, ping(260, .4, .08, ((1, 1), (2.1, .4))) * .5, .95)
save('capsule_drop.wav', x)
tt = t(.9); save('vitrine_slide.wav', np.convolve(rng.normal(size=tt.size), np.ones(60) / 60, 'same') * np.sin(np.pi * tt / .9) ** 2 + .3 * ping(880, .9, .3) * (tt > .75)[:int(SR*.9)])
x = np.zeros(int(SR * .5)); place(x, ping(220, .3, .1, ((1, 1), (1.5, .5))), 0); place(x, ping(180, .3, .1, ((1, 1), (1.5, .5))), .14); save('no_coin.wav', x)
print('gachapon sfx in', out)
