"""Two original instrumental hardbass loops. No sampled recordings or existing melodies."""
from pathlib import Path
import numpy as np,wave,json
root=Path(__file__).resolve().parent/'resources/data/goplanska_kukirin/music';root.mkdir(parents=True,exist_ok=True)
rate=24000
for name,bpm,notes,seed in [('chiki_ride',150,[62,62,65,60,62,69,65,60],284),('night_motor',144,[57,60,57,64,62,60,64,57],592)]:
 beat=60/bpm;length=beat*64;n=int(length*rate);audio=np.zeros(n);rng=np.random.default_rng(seed)
 def add(start,sound):
  i=int(start*rate);end=min(n,i+len(sound));audio[i:end]+=sound[:end-i]
 for k in range(64):
  t=np.arange(int(beat*.75*rate))/rate
  kick=np.sin(2*np.pi*(48*t+105*(1-np.exp(-26*t))/26))*np.exp(-15*t)*.72;add(k*beat,kick)
  t=np.arange(int(beat*.38*rate))/rate;frequency=440*2**((notes[(k//2)%len(notes)]-69)/12)
  phase=frequency*t;waveform=.65*np.sin(2*np.pi*phase)+.18*(2*(phase%1)-1)+.13*np.sin(4*np.pi*phase)
  add((k+.5)*beat,np.tanh(waveform*1.8)*np.sin(np.pi*np.arange(len(t))/len(t))**.5*.25)
  for subdivision in [0,.5]:
   t=np.arange(int(.05*rate))/rate;noise=rng.normal(0,1,len(t));noise[1:]-=.8*noise[:-1];add((k+subdivision)*beat,noise*np.exp(-80*t)*.045)
  if k%2==1:
   t=np.arange(int(.11*rate))/rate;add(k*beat,rng.normal(0,1,len(t))*np.exp(-32*t)*.08)
 audio*=np.minimum(1,np.arange(n)/(rate*.02))*np.minimum(1,(n-np.arange(n))/(rate*.03))
 audio=np.clip(audio,-.95,.95)
 with wave.open(str(root/(name+'.wav')),'wb') as wav:
  wav.setnchannels(1);wav.setsampwidth(2);wav.setframerate(rate);wav.writeframes((audio*32767).astype('<i2').tobytes())
(root/'LICENSE.txt').write_text('Original instrumental compositions generated for Goplanska. No third-party recordings or copied melodies. Released under CC0 1.0.\n')
print('Generated Chiki Ride and Night Motor; original hardbass-style instrumentals')
