"""Original seamless electric-motor harmonics and low-pass wind, no sampled third-party audio."""
from pathlib import Path
import math, random, struct, wave, subprocess
root=Path(__file__).resolve().parent/'resources/assets/goplanska_kukirin'; rate=24000;n=rate*4
rng=random.Random(731);wind=[];last=0
for i in range(n):
 last=.92*last+.08*rng.uniform(-1,1);wind.append(last)
# Wrap crossfade so loop seam is inaudible.
blend=1200
for i in range(blend):
 a=i/blend;v=wind[i]*a+wind[n-blend+i]*(1-a);wind[i]=v;wind[n-blend+i]=v
for name in ['motor','wind']:
 samples=[]
 for i in range(n):
  t=i/rate
  v=(.25*math.sin(2*math.pi*120*t)+.09*math.sin(2*math.pi*240*t)+.035*math.sin(2*math.pi*480*t)) if name=='motor' else wind[i]*.7
  samples.append(struct.pack('<h',round(v*32767)))
 path=Path('/tmp')/f'goplanska-{name}.wav'
 with wave.open(str(path),'wb') as f:f.setparams((1,2,rate,0,'NONE','not compressed'));f.writeframes(b''.join(samples))
 subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(path),'-c:a','libvorbis','-q:a','4',str(root/'sounds'/f'{name}.ogg')],check=True)
(root/'sounds.json').write_text('{"motor":{"sounds":[{"name":"goplanska_kukirin:motor","stream":false}]},"wind":{"sounds":[{"name":"goplanska_kukirin:wind","stream":false}]}}\n')
