"""Actual control regression: independent camera handedness and real collision-clipped motion."""
from pathlib import Path
import argparse,collections,json,math,statistics
p=argparse.ArgumentParser();p.add_argument('directory',type=Path);p.add_argument('--closeup',action='store_true');a=p.parse_args();out=a.directory.resolve()
data=json.loads((out/'runtime-report.json').read_text());state=json.loads((out/'state.json').read_text());checks=[];metrics={};groups={k:collections.defaultdict(list) for k in ['client','server','render']}
for k in groups:
 for row in data[k]:groups[k][row['name']].append(row)
def mean(xs):return statistics.fmean(xs) if xs else None
def wheel_angle(r):
 # Actual +X axle after spin/raked steering. Ground rolling heading is perpendicular to its horizontal normal.
 m=r.get('palettes',{}).get('frontwheel')
 return math.degrees(math.atan2(m[2],m[0])) if m else r['model_wheel_angle']
def check(ok,name,**values):checks.append({'name':name,'passed':bool(ok),**values})
check(data.get('completed'),'isolated real client/integrated-server fixture completed')
check(state.get('source_unchanged'),'existing source profile unchanged')
expected_phases=5 if a.closeup else 21
check(len(groups['client'])==expected_phases,'all requested actual control phases sampled',phases=len(groups['client']),expected=expected_phases)
for name,allrows in groups['client'].items():
 rows=[r for r in allrows if r['tick']>=22];rend=groups['render'][name];remote=[r for r in groups['server'][name] if r['tick']>=22]
 vals={'samples':len(rows),'mean_yaw_step':mean([r['yaw_step'] for r in rows]),'max_yaw_step':max(abs(r['yaw_step']) for r in rows),'max_chord':max(r['chord'] for r in rows),'mean_chord':mean([r['chord'] for r in rows]),'mean_wheel':mean([r['wheel'] for r in rows]),'mean_requested':mean([r.get('input',0) for r in rows]),'final_x':allrows[-1]['x'],'final_z':allrows[-1]['z'],'final_yaw':allrows[-1]['yaw'],'final_signed_speed':allrows[-1]['signed_speed'],'final_hud_kmh':allrows[-1]['hud_kmh'],'collisions':sum(r['collision'] for r in rows),'water':sum(r['water'] for r in rows)}
 metrics[name]=vals
 check(len(rows)>=15 and len(rend)>=10 and len(remote)>=10,name+' actual client/server/GPU samples',client=len(rows),server=len(remote),render=len(rend))
 if name in ['stationary_neutral','stationary_left','stationary_right','snow_stopped_left','ice_stopped_right','water_stopped_left','hud_zero_left']:
  check(vals['max_yaw_step']<.001,name+' does not rotate the chassis at HUD zero',**vals)
  if name!='hud_zero_left':check(vals['max_chord']<.0002,name+' genuinely remains still without injected speed',**vals)
 if name.startswith('blocked_'):
  stationary=[r for r in rows if r['chord']<.0002]
  check(len(stationary)>=15,name+' fixture actually blocks progress',zero_progress_samples=len(stationary),**vals)
  check(stationary and max(abs(r['yaw_step']) for r in stationary)<.001,name+' blocked chassis cannot yaw without progress',**vals)
 if name in ['camera_left_forward','camera_right_forward','mouse_left_forward','mouse_right_forward','shoulder_left_forward','shoulder_right_forward','reverse_left','reverse_right']:
  left='left' in name;
  if not name.startswith('shoulder_') and not name.startswith('reverse_'):check(mean([r.get('screen_right_turn',0) for r in rows])*(-1 if left else 1)>0,name+' independent actual camera projection bends in the requested screen direction')
  reverse=name.startswith('reverse_');expected=(1 if reverse else -1)*(1 if left else -1)
  # Vanilla yaw0 camera's actual quaternion produces screen-right -X. Positive yaw bends forward towards -X/right.
  check(vals['mean_yaw_step']*expected>.005,name+' actual motion has correct screen-left/right handedness',expected_yaw_sign=expected,**vals)
  check(vals['mean_requested']*(-1 if left else 1)>20,name+' raw A/D or mouse asks for correct wheel direction',**vals)
  check(mean([wheel_angle(r) for r in rend])*(-1 if left else 1)>0,name+' actual front-wheel palette has matching semantic direction')
  if remote:
   match_errors=[]
   for r in remote:
    previous=[c['wheel'] for c in allrows if r['tick']-5<=c['tick']<=r['tick']]
    if previous:match_errors.append(min(abs(r['wheel']-wheel) for wheel in previous))
   check(match_errors and max(match_errors)<.001,name+' synchronized server wheel matches an actual client wheel within four-tick updates plus one processing tick',max_error=max(match_errors) if match_errors else None)
 if name=='s_rest_reverse':check(vals['final_signed_speed']<-.04 and vals['final_signed_speed']>=-.161,name+' S from rest drives bounded reverse',**vals)
 if name=='s_forward_brake_reverse':
  early=[r['signed_speed'] for r in allrows if r['tick']<12];late=[r['signed_speed'] for r in rows]
  check(any(x>.05 for x in early) and min(late)<-.04,name+' forward momentum brakes before bounded reverse',**vals)
 if name=='w_reverse_brake_forward':check(any(r['signed_speed']<-.02 for r in allrows[:4]) and vals['final_signed_speed']>.1,name+' W brakes reverse before forward',**vals)
 if name=='w_s_rest_brake_only':check(max(abs(r['signed_speed']) for r in rows)<.001,name+' W+S at rest does not accelerate',**vals)
 if rend:
  check(all(r.get('seam_ring_0_error',1)<.00001 and r.get('seam_ring_1_error',1)<.00001 for r in rend),name+' both actual raked headset seam rings remain attached to fixed body',max_ring_0=max(r.get('seam_ring_0_error',1) for r in rend),max_ring_1=max(r.get('seam_ring_1_error',1) for r in rend))
  errors=[]
  for r in rend:
   if 'frame_wheel' in r:errors.append(abs(wheel_angle(r)-r['frame_wheel']))
   else:
    # Broad fixture predates partial capture. Bound interpolation by its two observed endpoints.
    endpoints=[c['wheel'] for c in allrows if r['tick']-2<=c['tick']<r['tick']]
    endpoints.append(r['wheel']);lo=min(endpoints);hi=max(endpoints);angle=wheel_angle(r);errors.append(max(0,lo-angle,angle-hi))
  maxerr=max(errors)
  check(maxerr<.24,name+' submitted front wheel lies in the interpolated physical frame within measured pose quantization',max_error=maxerr,exact_partial_captured=all('frame_wheel' in r for r in rend))
  check(max(r.get('left_grip_error_blocks',0) for r in rend)<.025 and max(r.get('right_grip_error_blocks',0) for r in rend)<.025,name+' actual grips follow submitted stem')
# Concrete independent camera basis proof, not an implementation-derived sign label.
neutral=groups['client'].get('stationary_neutral',[])
check(neutral and mean([r['camera_right_x'] for r in neutral])<-.95 and abs(mean([r['camera_right_z'] for r in neutral]))<.02,'vanilla actual camera right basis at yaw zero is world -X')
report={'success':all(c['passed'] for c in checks),'candidate_sha256':state['candidate_sha256'],'passed':sum(c['passed'] for c in checks),'total':len(checks),'checks':checks,'metrics':metrics,'samples':{k:len(data[k]) for k in groups},'scope':'actual A/D/W/S and Shoulder mouse controls; no per-tick velocity injection; true stationary and collision-clipped movement; full submitted model palettes'}
(out/'analysis.json').write_text(json.dumps(report,indent=2));print(json.dumps({'success':report['success'],'passed':report['passed'],'total':report['total'],'samples':report['samples'],'failed':[c['name'] for c in checks if not c['passed']]}))
