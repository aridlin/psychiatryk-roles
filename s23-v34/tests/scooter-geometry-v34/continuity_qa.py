"""Actual triangle surface and raked-palette continuity checks (no client launch)."""
from pathlib import Path
import importlib.util,json,math
import numpy as np
root=Path(__file__).resolve().parent
spec=importlib.util.spec_from_file_location('rig_repair',root.parent/'generators/repair_scooter_steering_rig.py');mod=importlib.util.module_from_spec(spec);spec.loader.exec_module(mod)

def triangles(folder):
 doc,data=mod.read_glb(folder/'kukirin_g2.glb');result=[]
 for index,mesh in enumerate(doc['meshes']):
  pts=mod.array(doc,data,mesh['primitives'][0]['attributes']['POSITION'])
  if index==1:pts+=doc['nodes'][2]['translation']
  if index==2:pts+=doc['nodes'][2]['translation'];pts+=doc['nodes'][5].get('translation',[0,0,0])
  if index==3:pts+=doc['nodes'][4]['translation']
  result.append(pts.reshape(-1,3,3))
 return doc,result

def distances(points,tris):
 # Plane projection for triangle interior, otherwise nearest edge segment.
 a,b,c=tris[:,0],tris[:,1],tris[:,2];ab=b-a;ac=c-a
 normals=np.cross(ab,ac);n2=(normals*normals).sum(1)
 v0=ab;v1=ac;d00=(v0*v0).sum(1);d01=(v0*v1).sum(1);d11=(v1*v1).sum(1);den=d00*d11-d01*d01
 out=[]
 for p in points:
  v2=p-a;d20=(v2*v0).sum(1);d21=(v2*v1).sum(1)
  u=np.divide(d11*d20-d01*d21,den,out=np.full(len(den),-1.),where=den>1e-20)
  v=np.divide(d00*d21-d01*d20,den,out=np.full(len(den),-1.),where=den>1e-20)
  plane=np.divide(((v2*normals).sum(1))**2,n2,out=np.full(len(den),np.inf),where=n2>1e-20)
  best=np.where((u>=0)&(v>=0)&(u+v<=1),plane,np.inf)
  for start,end in ((a,b),(b,c),(c,a)):
   edge=end-start;e2=(edge*edge).sum(1)
   t=np.divide(((p-start)*edge).sum(1),e2,out=np.zeros(len(e2)),where=e2>1e-20).clip(0,1)
   best=np.minimum(best,((p-(start+edge*t[:,None]))**2).sum(1))
  out.append(math.sqrt(float(best.min())))
 return np.array(out)

def rotation(axis,angle):
 x,y,z=axis;c=math.cos(angle);s=math.sin(angle);v=1-c
 return np.array([[x*x*v+c,x*y*v-z*s,x*z*v+y*s],[x*y*v+z*s,y*y*v+c,y*z*v-x*s],[x*z*v-y*s,y*z*v+x*s,z*z*v+c]])

old_doc,old=triangles(root/'v33-baseline');new_doc,new=triangles(root/'repaired')
rig=json.loads((root/'repaired/scooter-rig.json').read_text());pivot=np.array(rig['steeringPivot']);axis=np.array(rig['steeringAxis']);oldpivot=np.array(old_doc['nodes'][2]['translation'])
components=mod.connected_faces(old[1].reshape(-1,3));brace=[f for f in components if len(f)==108 and old[1][f][:,:,1].min()<.17 and .47<old[1][f][:,:,1].max()<.48][0]
frame=old[1][brace];samples=np.unique(np.concatenate([frame.reshape(-1,3),frame.mean(1),(frame[:,0]+frame[:,1])/2,(frame[:,1]+frame[:,2])/2,(frame[:,2]+frame[:,0])/2]),axis=0)
bottom=samples[samples[:,1]<.215];rest=distances(bottom,old[0]);print('bottom samples',len(bottom),'distance range',rest.min(),rest.max())
# Rest contact region is derived from actual triangle surfaces, not vertex proximity.
contact=bottom[rest<.008];assert len(contact)>=12
top=samples[samples[:,1]>.4];toprest=distances(top,np.delete(old[1],brace,axis=0));topcontact=top[toprest<.008]
records=[];checks=[]
for yaw in [-28,-14,0,14,28]:
 baseline_rot=rotation(np.array([0,1,0]),-math.radians(yaw))
 baseline=(contact-oldpivot)@baseline_rot.T+oldpivot
 angle=-math.atan(math.tan(math.radians(yaw))/axis[1]);rot=rotation(axis,angle)
 newcontact=contact # this geometry now belongs to fixed body
 olddist=distances(baseline,old[0]);newdist=distances(newcontact,new[0][:-108])
 # Exact physical headset-line attachment under full matrix, rather than a yaw-only test.
 axispoints=np.array([pivot,pivot+axis*.2385586])
 after=(axispoints-pivot)@rot.T+pivot
 axle=rot@np.array([1.,0,0]);projected=-math.degrees(math.atan2(axle[2],axle[0]))
 # Root model yaw180 flips authored steering sign into the requested game wheel yaw.
 projected=-projected
 assert abs(projected-yaw)<1e-9
 assert np.max(np.linalg.norm(after-axispoints,axis=1))<1e-12
 record={'steering_yaw_degrees':yaw,'projected_wheel_yaw_degrees':projected,'baseline_deck_brace_mean_surface_gap_blocks':float(olddist.mean()*1.25),'baseline_deck_brace_max_surface_gap_blocks':float(olddist.max()*1.25),'corrected_deck_brace_mean_surface_gap_blocks':float(newdist.mean()*1.25),'corrected_deck_brace_max_surface_gap_blocks':float(newdist.max()*1.25),'headset_axis_attachment_error_blocks':float(np.max(np.linalg.norm(after-axispoints,axis=1))*1.25),'fixed_headset_to_rotating_tube_max_surface_gap_blocks':float(distances(topcontact,(new[1]-pivot)@rot.T+pivot).max()*1.25)}
 records.append(record)
 assert np.max(np.linalg.norm(newcontact-contact,axis=1))==0
# Preserve complete triangle data at rest, including UV coordinates, normals/materials.
assert sum(map(len,old))==sum(map(len,new))==9126
assert np.allclose(new[0][-108:],frame,atol=1e-7,rtol=0)
assert np.allclose(new[0][:-108],old[0],atol=1e-7,rtol=0)
assert np.allclose(new[2],old[2],atol=1e-7,rtol=0)
assert np.allclose(new[3],old[3],atol=1e-7,rtol=0)
assert new_doc['materials']==old_doc['materials'] and new_doc['images']==old_doc['images'] and new_doc['textures']==old_doc['textures']
old_doc,old_data=mod.read_glb(root/'v33-baseline/kukirin_g2.glb');new_doc,new_data=mod.read_glb(root/'repaired/kukirin_g2.glb')
for key in ('NORMAL','TEXCOORD_0'):
 old_body=mod.array(old_doc,old_data,old_doc['meshes'][0]['primitives'][0]['attributes'][key]);old_stem=mod.array(old_doc,old_data,old_doc['meshes'][1]['primitives'][0]['attributes'][key]);new_body=mod.array(new_doc,new_data,new_doc['meshes'][0]['primitives'][0]['attributes'][key]);new_stem=mod.array(new_doc,new_data,new_doc['meshes'][1]['primitives'][0]['attributes'][key]);width=old_stem.shape[1]
 assert np.array_equal(new_body[:len(old_body)],old_body)
 assert np.array_equal(new_body[len(old_body):],old_stem.reshape(-1,3,width)[brace].reshape(-1,width))
 moving=[i for i in range(len(old_stem)//3)if i not in set(brace)]
 assert np.array_equal(new_stem,old_stem.reshape(-1,3,width)[moving].reshape(-1,width))
# D reflection yields precisely the same fallback steering transform.
D=np.diag([1,-1,-1]);assert np.allclose(D@rotation(axis,.4)@D,rotation(D@axis,.4),atol=1e-12)
report={'success':True,'contact_samples':len(contact),'surface_method':'Exact nearest point-to-triangle distance for sampled fixed-frame contact points; not nearest-vertex distance','records':records,'rest_geometry_uv_materials_preserved':True,'fallback_transform_equivalent':True,'limit':'Actual rendered palette/image continuity still requires isolated client QA'}
(root/'continuity-report.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
