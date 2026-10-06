"""Original portable scooter primitives, GPL-3.0. No imported geometry/UVs/textures."""
from pathlib import Path
import math,struct,json,zlib,hashlib
r=Path(__file__).resolve().parents[1]/'src/main/resources/assets/goplanska_kukirin';parts=[[] for _ in range(4)]
BLACK=0xff272b2d; RUBBER=0xff111517; ORANGE=0xffe57813; METAL=0xff81898d; WHITE=0xffeeeeee
pivot=(.004729188461901814,.36550497783482205,-.3683804526267386);axis=(0,.9611505739343013,.27602458989329776)
front=(.0047632475897060045,.12631217025354627,-.4755584442795999);rear=(.004763247589706008,.12976315878939756,.47572712649345517)
def add(a,b):return tuple(x+y for x,y in zip(a,b))
def sub(a,b):return tuple(x-y for x,y in zip(a,b))
def mul(a,s):return tuple(x*s for x in a)
def cross(a,b):return(a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
def unit(a):return mul(a,1/math.sqrt(sum(x*x for x in a)))
def tri(part,col,a,b,c):
 n=unit(cross(sub(b,a),sub(c,a)));parts[part].append((col,n,a,b,c))
def quad(part,col,a,b,c,d):tri(part,col,a,b,c);tri(part,col,a,c,d)
def box(part,col,lo,hi):
 x,y,z=lo;X,Y,Z=hi
 quad(part,col,(x,y,z),(x,y,Z),(x,Y,Z),(x,Y,z));quad(part,col,(X,y,Z),(X,y,z),(X,Y,z),(X,Y,Z))
 quad(part,col,(x,y,Z),(x,y,z),(X,y,z),(X,y,Z));quad(part,col,(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z))
 quad(part,col,(X,y,z),(x,y,z),(x,Y,z),(X,Y,z));quad(part,col,(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z))
def tube(part,col,a,b,radius,segments=12):
 axis=unit(sub(b,a));u=unit(cross(axis,(1,0,0) if abs(axis[0])<.9 else (0,1,0)));v=cross(axis,u)
 for i in range(segments):
  t=2*math.pi*i/segments;T=2*math.pi*(i+1)/segments
  d=add(mul(u,math.cos(t)*radius),mul(v,math.sin(t)*radius));D=add(mul(u,math.cos(T)*radius),mul(v,math.sin(T)*radius))
  quad(part,col,add(a,d),add(a,D),add(b,D),add(b,d));tri(part,col,a,add(a,D),add(a,d));tri(part,col,b,add(b,d),add(b,D))
# Stationary deck and headset brace. Pivot lies inside overlapping bearing sleeves.
box(0,BLACK,(-.10,.13,-.22),(.11,.183,.41));box(0,ORANGE,(-.105,.151,-.20),(-.094,.179,.38));box(0,ORANGE,(.104,.151,-.20),(.115,.179,.38))
tube(0,BLACK,(.005,.16,-.18),pivot,.045);tube(0,METAL,add(pivot,mul(axis,-.04)),add(pivot,mul(axis,.04)),.040)
# Rotating steering mast, bar, grips and front fork.
top=add(pivot,mul(axis,.82));tube(1,BLACK,add(pivot,mul(axis,-.02)),top,.028)
tube(1,ORANGE,add(pivot,mul(axis,.075)),add(pivot,mul(axis,.66)),.029)
bar=(.005,1.168,-.308);tube(1,BLACK,top,bar,.026)
tube(1,BLACK,(-.265,bar[1],bar[2]),(.265,bar[1],bar[2]),.022)
tube(1,RUBBER,(-.290,bar[1],bar[2]),(-.205,bar[1],bar[2]),.026);tube(1,RUBBER,(.205,bar[1],bar[2]),(.290,bar[1],bar[2]),.026)
box(1,BLACK,(-.04,1.16,-.34),(.05,1.205,-.29));box(1,WHITE,(-.025,.90,-.28),(.035,.94,-.265))
for x in [-.040,.049]:tube(1,BLACK,add(pivot,(x,0,0)),add(front,(x,0,0)),.014)
# Rear axle attachments are fixed, wheel surfaces rotate separately around hubs.
for x in [-.04,.049]:tube(0,BLACK,(x,.17,.37),add(rear,(x,0,0)),.015)
for part,hub in [(2,front),(3,rear)]:
 tube(part,RUBBER,add(hub,(-.038,0,0)),add(hub,(.038,0,0)),.121,20)
 tube(part,METAL,add(hub,(-.040,0,0)),add(hub,(.040,0,0)),.069,12)
 tube(part,ORANGE,add(hub,(-.041,0,0)),add(hub,(.041,0,0)),.032,12)
# Authored stationary tail lamp.
box(0,0xffe32220,(-.035,.175,.397),(.045,.200,.423))
r.joinpath('models').mkdir(exist_ok=True,parents=True);out=bytearray(struct.pack('>4i',*[len(p) for p in parts]))
for part in parts:
 for col,*vectors in part:
  values=[q for v in vectors for q in (v[0],-v[1],-v[2])]
  out.extend(struct.pack('>I12f',col,*values))
(r/'models/kukirin_g2.mesh').write_bytes(out)
rig={'frontPivot':front,'rearPivot':rear,'steeringPivot':pivot,'steeringAxis':axis,'asset':'Original portable primitive scooter; no third-party model source.'}
(r/'models/scooter-rig.json').write_text(json.dumps(rig,indent=2)+'\n')
def chunk(k,data):return struct.pack('>I',len(data))+k+data+struct.pack('>I',zlib.crc32(k+data)&0xffffffff)
png=b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',1,1,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0\xff\xff\xff\xff'))+chunk(b'IEND',b'')
(r/'textures/model').mkdir(exist_ok=True,parents=True);(r/'textures/model/original.png').write_bytes(png)
report={'generator':'Original geometry authored for Portable Scooters; GPL-3.0','triangles_per_part':[len(p) for p in parts],'triangles':sum(map(len,parts)),'mesh_sha256':hashlib.sha256(out).hexdigest(),'protected_asset_inputs':[],'bearing_overlap':.06}
(r.parents[3]/'build').mkdir(exist_ok=True,parents=True)
Path(__file__).with_name('model-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
