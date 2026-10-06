"""Original soft air-thrust boost loop; no imported recordings."""
from pathlib import Path
import random,math,struct,wave,subprocess
r=Path(__file__).resolve().parent/'resources/assets/goplanska_kukirin/sounds';rng=random.Random(934);rate=24000;n=rate*4;slow=0;fast=0;data=[]
for i in range(n):
 noise=rng.uniform(-1,1);slow=.98*slow+.02*noise;fast=.65*fast+.35*noise;t=i/rate
 data.append(.55*fast+.55*slow+.045*math.sin(2*math.pi*90*t))
for i in range(1200):
 a=i/1200;v=data[i]*a+data[n-1200+i]*(1-a);data[i]=v;data[n-1200+i]=v
p=Path('/tmp/goplanska-nitro-soft.wav')
with wave.open(str(p),'wb') as f:f.setparams((1,2,rate,0,'NONE','not compressed'));f.writeframes(b''.join(struct.pack('<h',int(v*32767)) for v in data))
subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(p),'-c:a','libvorbis','-q:a','4',str(r/'nitro.ogg')],check=True)
