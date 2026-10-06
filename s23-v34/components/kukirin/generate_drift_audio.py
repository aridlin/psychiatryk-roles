"""Original tyre scrub loop; preserves all other sound definitions."""
from pathlib import Path
import math,random,struct,wave,subprocess,json
r=Path(__file__).resolve().parent/'resources/assets/goplanska_kukirin';rate=24000;n=rate*2;rng=random.Random(813);noise=0;rumble=0;data=[]
for i in range(n):
 noise=.60*noise+.40*rng.uniform(-1,1);rumble=.97*rumble+.03*noise;t=i/rate;sample=.75*(noise-rumble)*(.85+.15*math.sin(2*math.pi*7*t))+.35*rumble;data.append(sample)
for i in range(480):
 v=data[i]*(i/480)+data[n-480+i]*(1-i/480);data[i]=v;data[n-480+i]=v
p=Path('/tmp/goplanska-tyre-scrub.wav')
with wave.open(str(p),'wb') as f:f.setparams((1,2,rate,0,'NONE','not compressed'));f.writeframes(b''.join(struct.pack('<h',round(v*32767)) for v in data))
subprocess.run(['ffmpeg','-y','-loglevel','error','-i',str(p),'-c:a','libvorbis','-q:a','4',str(r/'sounds/drift.ogg')],check=True)
p=r/'sounds.json';s=json.loads(p.read_text());s['drift']={'sounds':[{'name':'goplanska_kukirin:drift','stream':False}]};p.write_text(json.dumps(s,indent=2)+'\n')
