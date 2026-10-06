"""Fit measured paths and compare them with submitted wheel palettes, not source formulas."""
from pathlib import Path
import argparse,collections,json,math,statistics
qa=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('directory',type=Path);a=p.parse_args();out=a.directory.resolve()
data=json.loads((out/'runtime-report.json').read_text());state=json.loads((out/'state.json').read_text());checks=[];metrics={}
def check(condition,name,**values):checks.append({'name':name,'passed':bool(condition),**values})
def average(values):return statistics.fmean(values) if values else float('nan')
def wrap(angle):return (angle+180)%360-180
def circle(rows):
 rows=[r for i,r in enumerate(rows) if not i or math.hypot(r['x']-rows[i-1]['x'],r['z']-rows[i-1]['z'])>1e-5]
 if len(rows)<6:return None
 mx=average([r['x'] for r in rows]);mz=average([r['z'] for r in rows]);points=[(r['x']-mx,r['z']-mz) for r in rows]
 matrix=[[0.0]*4 for _ in range(3)]
 for x,z in points:
  row=[2*x,2*z,1];rhs=x*x+z*z
  for i in range(3):
   for j in range(3):matrix[i][j]+=row[i]*row[j]
   matrix[i][3]+=row[i]*rhs
 for i in range(3):
  pivot=max(range(i,3),key=lambda j:abs(matrix[j][i]));matrix[i],matrix[pivot]=matrix[pivot],matrix[i]
  if abs(matrix[i][i])<1e-12:return None
  divisor=matrix[i][i];matrix[i]=[v/divisor for v in matrix[i]]
  for j in range(3):
   if j!=i:
    factor=matrix[j][i];matrix[j]=[matrix[j][k]-factor*matrix[i][k] for k in range(4)]
 cx,cz,c=[row[3] for row in matrix];radius=math.sqrt(max(0,c+cx*cx+cz*cz))
 residual=math.sqrt(average([(math.hypot(x-cx,z-cz)-radius)**2 for x,z in points]))
 return {'radius':radius,'fit_rms_blocks':residual,'points':len(points)}
groups={kind:collections.defaultdict(list) for kind in ['client','server','render']}
for kind in groups:
 for row in data[kind]:groups[kind][row['name']].append(row)
check(data.get('completed'), 'Real client fixture completed')
check(state.get('source_unchanged'),'Existing Prism source profile hashes unchanged')
check(len(groups['client'])==14,'All fourteen keyboard/camera phases collected',phases=len(groups['client']))
wheelbase=1.189106963466319
for name,client in groups['client'].items():
 server=groups['server'][name];render=groups['render'][name]
 check(len(client)>=15 and len(server)>=10 and len(render)>=10,name+' actual client/server/model observations',client=len(client),server=len(server),render=len(render))
 check(all(r['ground'] and abs(r['drift'])<1e-6 for r in client),name+' uses grounded rolling motion without drifting')
 if not render:continue
 check(all(r['shoulder_active']==name.startswith('shoulder') for r in render),name+' uses actual requested camera perspective')
 deltas=[wrap(client[i]['yaw']-client[i-1]['yaw']) for i in range(1,len(client))]
 yaw_step=average(deltas);distances=[math.hypot(client[i]['x']-client[i-1]['x'],client[i]['z']-client[i-1]['z']) for i in range(1,len(client))]
 mean_wheel=average([r['wheel'] for r in client]);model_angle=average([r['model_wheel_angle'] for r in render]);input_mean=average([r.get('input',r['wheel']) for r in client])
 values={'yaw_degrees_per_tick':yaw_step,'mean_traveled_chord':average(distances),'wheel_degrees':mean_wheel,'submitted_wheel_degrees':model_angle,'requested_input_degrees':input_mean}
 neutral='mouse_off' in name
 expected=1 if name.endswith('left') else -1
 if neutral:
  check(abs(yaw_step)<.005 and abs(mean_wheel)<.01 and abs(input_mean)<.01,name+' camera cannot steer with mouse steering disabled',**values)
 else:
  check(yaw_step*expected>.01 and mean_wheel*expected>.01 and input_mean*expected>25,name+' correct physical left/right input and yaw sign',**values)
  fit=circle(client);remote_fit=circle(server);values['client_circle']=fit;values['server_circle']=remote_fit
  if fit:
   geometric_angle=math.degrees(math.atan(wheelbase/fit['radius']))*expected;values['circle_wheel_degrees']=geometric_angle
   chord_radius=average(distances)/(2*math.sin(math.radians(abs(yaw_step))*.5));values['chord_radius']=chord_radius
   check(fit['fit_rms_blocks']<.02 and abs(fit['radius']/chord_radius-1)<.025,name+' measured trajectory is a stable circle',**values)
   check(abs(mean_wheel-geometric_angle)<.20,name+' physical wheel angle agrees with independently fitted radius',**values)
   # Real GPU pose cache rounds animation time to 1/128 second, or .4375 degrees.
   quantum=max(r['pose_quantum_seconds'] for r in render)*56
   check(model_angle*expected>0 and abs(model_angle-geometric_angle)<quantum*.5+.12,name+' actual submitted GLB wheel agrees with radius within pose quantization',angle_error=abs(model_angle-geometric_angle),quantum_degrees=quantum,**values)
   check(remote_fit and remote_fit['fit_rms_blocks']<.02 and abs(remote_fit['radius']/fit['radius']-1)<.025,name+' server receives same measured turn radius',**values)
  else:check(False,name+' circle could be fitted',**values)
 check(abs(model_angle-mean_wheel)<.35,name+' actual GPU palette angle agrees with current vehicle wheel',**values)
 check(all(r.get('left_grip_error_blocks',1)<.012 and r.get('right_grip_error_blocks',1)<.012 for r in render),name+' both actual grip targets follow submitted stem palette',max_left=max(r.get('left_grip_error_blocks',1) for r in render),max_right=max(r.get('right_grip_error_blocks',1) for r in render))
 check(server and abs(average([r['wheel'] for r in server])-mean_wheel)<.20,name+' model wheel angle is synchronized to server within steady window',server_wheel=average([r['wheel'] for r in server]),client_wheel=mean_wheel)
 metrics[name]=values
for mode in ['fp_low','fp_normal','fp_high','shoulder_low','shoulder_normal']:
 left=metrics.get(mode+'_left',{}).get('client_circle');right=metrics.get(mode+'_right',{}).get('client_circle')
 check(left and right and abs(left['radius']/right['radius']-1)<.02,mode+' mirror turn radius is symmetric',left=left,right=right)
for side in ['left','right']:
 for speed in ['low','normal']:
  first=metrics.get('fp_'+speed+'_'+side,{}).get('client_circle');shoulder=metrics.get('shoulder_'+speed+'_'+side,{}).get('client_circle')
  check(first and shoulder and abs(first['radius']/shoulder['radius']-1)<.02,'Shoulder camera offset does not change '+speed+' '+side+' keyboard radius',first=first,shoulder=shoulder)
report={'success':all(c['passed'] for c in checks),'candidate_sha256':state['candidate_sha256'],'passed':sum(c['passed'] for c in checks),'total':len(checks),'checks':checks,'metrics':metrics,'samples':{k:len(data[k]) for k in groups},'scope':'grounded non-drifting constant-speed actual A/D and Shoulder mouse controls, submitted GemRender model, integrated server synchronization'}
(out/'analysis.json').write_text(json.dumps(report,indent=2));print(json.dumps({'success':report['success'],'passed':report['passed'],'total':report['total'],'samples':report['samples'],'failed':[c['name'] for c in checks if not c['passed']]}))
