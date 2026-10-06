"""Check candidate methods against the actual authored GLB, then finite boost decay."""
from pathlib import Path
import argparse,hashlib,json,math,struct,subprocess,zipfile
qa=Path(__file__).resolve().parent;root=qa.parent.parent
p=argparse.ArgumentParser();p.add_argument('--candidate',type=Path,required=True);a=p.parse_args();candidate=a.candidate.resolve()
classes=qa/'unit-classes';classes.mkdir(exist_ok=True)
java=(Path.home() / '.local/share/PrismLauncher/java/java-runtime-delta/bin/java')
subprocess.run(['javac','--release','21','-cp',str(candidate),'-d',str(classes),str(qa/'HandlingGeometryQA.java')],check=True)
run=subprocess.run([str(java),'-cp',str(classes)+':'+str(candidate),'HandlingGeometryQA'],text=True,capture_output=True)
(qa/'unit-output.txt').write_text(run.stdout+run.stderr);run.check_returncode()
with zipfile.ZipFile(candidate) as z:
 glb=z.read('assets/goplanska_kukirin/models/kukirin_g2.glb');rig=json.loads(z.read('assets/goplanska_kukirin/models/scooter-rig.json'))
magic,version,total=struct.unpack_from('<III',glb);assert magic==0x46546c67 and version==2 and total==len(glb)
pos=12;document=None;binary=None
while pos<len(glb):
 size,kind=struct.unpack_from('<II',glb,pos);chunk=glb[pos+8:pos+8+size];pos+=8+size
 if kind==0x4e4f534a:document=json.loads(chunk)
 elif kind==0x004e4942:binary=chunk
def accessor(index):
 a=document['accessors'][index];v=document['bufferViews'][a['bufferView']];assert a['componentType']==5126
 width={'SCALAR':1,'VEC4':4}[a['type']];offset=v.get('byteOffset',0)+a.get('byteOffset',0);stride=v.get('byteStride',width*4)
 return [struct.unpack_from('<'+'f'*width,binary,offset+i*stride) for i in range(a['count'])]
clip=next(x for x in document['animations'] if x['name']=='steering');channel=clip['channels'][0]
assert document['nodes'][channel['target']['node']]['name']=='steering_pivot'
sampler=clip['samplers'][channel['sampler']];times=[x[0] for x in accessor(sampler['input'])];quaternions=accessor(sampler['output']);assert sampler.get('interpolation','LINEAR')=='LINEAR'
def authored_angle(time):
 lo=0
 while lo+1<len(times)-1 and times[lo+1]<time:lo+=1
 t=(time-times[lo])/(times[lo+1]-times[lo]);angles=[2*math.atan2(q[1],q[3]) for q in quaternions]
 # The authored endpoints are simple rotations around one common Y axis.
 return math.degrees(angles[lo]+t*(angles[lo+1]-angles[lo]))
checks=[]
for line in run.stdout.splitlines():
 if line.startswith('CHECK\t'):checks.append({'name':line.split('\t',1)[1],'passed':True})
 elif line.startswith('TURN\t'):
  distance,wheel,yaw,time=map(float,line.split('\t')[1:]);world_wheel=-authored_angle(time)
  assert abs(world_wheel-wheel)<1e-4,(wheel,world_wheel)
  assert world_wheel*yaw>0
  checks.append({'name':f'GLB front-wheel world direction and angle at distance {distance:g}, wheel {wheel:g}','passed':True,'world_wheel_degrees':world_wheel})
wheelbase=math.hypot(rig['frontPivot'][0]-rig['rearPivot'][0],rig['frontPivot'][2]-rig['rearPivot'][2])*1.25
assert abs(wheelbase-1.189106963466319)<1e-10
checks.append({'name':'Candidate rig wheelbase after exact render scale','passed':True,'blocks':wheelbase})
report={'success':True,'candidate_sha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'glb_sha256':hashlib.sha256(glb).hexdigest(),'passed':len(checks),'checks':checks,'scope':'Candidate bytecode and actual authored GLB; live steering remains a separate gate'}
(qa/'unit-report.json').write_text(json.dumps(report,indent=2));print(json.dumps({'success':True,'passed':len(checks),'wheelbase_blocks':wheelbase,'candidate_sha256':report['candidate_sha256']}))
