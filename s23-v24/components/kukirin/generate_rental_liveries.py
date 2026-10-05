"""Paint new UV atlases from the mesh regions, without altering any original texture."""
from pathlib import Path
import struct,json,numpy as np
from PIL import Image,ImageDraw,ImageFont
root=Path(__file__).resolve().parent
raw=(root/'resources/assets/goplanska_kukirin/models/kukirin_g2.glb').read_bytes();n=struct.unpack_from('<I',raw,12)[0];g=json.loads(raw[20:20+n]);binary=raw[28+n:]
def accessor(i):
 a=g['accessors'][i];v=g['bufferViews'][a['bufferView']];dt={5126:'<f4',5125:'<u4',5123:'<u2',5121:'u1'}[a['componentType']];width={'SCALAR':1,'VEC2':2,'VEC3':3,'VEC4':4}[a['type']];return np.frombuffer(binary,dtype=dt,count=a['count']*width,offset=v.get('byteOffset',0)+a.get('byteOffset',0)).reshape(-1,width)
folder=root/'resources/assets/goplanska_kukirin/textures/model/rentals';folder.mkdir(parents=True,exist_ok=True)
for brand,green in [('lime',(132,210,0)),('bolt',(50,210,125)),('city',(249,128,29))]:
 label=Image.new('RGBA',(600,120),(0,0,0,0));ld=ImageDraw.Draw(label)
 font=ImageFont.truetype('/usr/share/fonts/TTF/DejaVuSans-Bold.ttf',95)
 ld.text((115,0),brand.capitalize(),font=font,fill=(255,255,255,255))
 if brand=='lime':
  ld.arc((8,8,100,100),0,360,fill='white',width=8)
  for angle in [0,60,120]:
   import math
   dx,dy=math.cos(math.radians(angle))*40,math.sin(math.radians(angle))*40;ld.line((54-dx,54-dy,54+dx,54+dy),fill='white',width=5)
 label=label.rotate(90,expand=True);la=np.asarray(label)
 image=Image.new('RGB' ,(1024,1024),(30,33,35));draw=ImageDraw.Draw(image)
 for mesh in g['meshes']:
  p=mesh['primitives'][0];uv=accessor(p['attributes']['TEXCOORD_0']);pos=accessor(p['attributes']['POSITION']);normal=accessor(p['attributes']['NORMAL']);indices=(accessor(p['indices']).ravel() if 'indices' in p else np.arange(len(pos))).reshape(-1,3)
  for tri in indices:
   center=pos[tri].mean(axis=0);name=mesh['name'];base=(30,33,35)
   if name=='steering':base=green if center[1]<.92 else (35,38,39)
   if name=='body':base=(232,235,231) if brand=='lime' else green if brand=='bolt' else (46,49,50)
   if name=='body' and center[1]>.32:base=(35,38,39)
   shade=.82+.18*max(0,float(normal[tri].mean(axis=0)[1]));color=tuple(round(c*shade) for c in base)
   points=[(float(uv[i,0]*1023),float(uv[i,1]*1023)) for i in tri];draw.polygon(points,fill=color);draw.line(points+[points[0]],fill=color,width=2)
   if name=='steering' and pos[tri,1].max()>.4 and pos[tri,1].min()<.9 and abs(float(normal[tri].mean(axis=0)[2]))>.45:
    # Project the brand onto the stem surface, then bake into that triangle's UV island.
    pts=np.array(points);lo=np.maximum(np.floor(pts.min(axis=0)).astype(int),0);hi=np.minimum(np.ceil(pts.max(axis=0)).astype(int),1023)
    v0,v1=pts[1]-pts[0],pts[2]-pts[0];det=v0[0]*v1[1]-v1[0]*v0[1]
    if abs(det)<1e-6:continue
    for yy in range(lo[1],hi[1]+1):
     for xx in range(lo[0],hi[0]+1):
      d=np.array([xx+.5,yy+.5])-pts[0];b=(d[0]*v1[1]-d[1]*v1[0])/det;c=(v0[0]*d[1]-v0[1]*d[0])/det
      if b<0 or c<0 or b+c>1:continue
      xyz=(1-b-c)*pos[tri[0]]+b*pos[tri[1]]+c*pos[tri[2]]
      u=(float(xyz[0])+.027)/.054;v=(.82-float(xyz[1]))/.36
      if 0<=u<1 and 0<=v<1:
       pixel=la[int(v*(la.shape[0]-1)),int(u*(la.shape[1]-1))]
       if pixel[3]:image.putpixel((xx,yy),tuple(map(int,pixel[:3])))
 image.resize((512,512),Image.Resampling.LANCZOS).save(folder/(brand+'.png'))
print('Generated Lime white/green, Bolt green/charcoal, City orange rental atlases')
