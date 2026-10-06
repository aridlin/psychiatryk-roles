"""Check actual submitted render palettes against triangle-surface contact geometry.

Usage: python runtime_continuity_qa.py baseline_jar baseline_runtime.json candidate_jar candidate_runtime.json output.json
Requires NumPy. Reads only supplied fixture archives/reports; no game launch.
"""
from pathlib import Path
import argparse, hashlib, importlib.util, json, math, tempfile, zipfile
import numpy as np


def main():
    parser=argparse.ArgumentParser()
    for name in ('baseline_jar','baseline_runtime','candidate_jar','candidate_runtime','output'):
        parser.add_argument(name,type=Path)
    options=parser.parse_args()
    root=Path(__file__).resolve().parent
    # Load only geometry utility definitions; the static audit's standalone execution is not needed.
    utilities={'__file__':str(root/'continuity_qa.py')}
    exec((root/'continuity_qa.py').read_text().split('old_doc,old=triangles(')[0],utilities)
    distance=utilities['distances'];triangles=utilities['triangles'];connected=utilities['mod'].connected_faces
    with tempfile.TemporaryDirectory() as temporary:
        folders=[]
        for label,archive in (('baseline',options.baseline_jar),('candidate',options.candidate_jar)):
            folder=Path(temporary)/label;folder.mkdir();folders.append(folder)
            with zipfile.ZipFile(archive) as z:
                for name in ('kukirin_g2.glb','scooter-rig.json'):
                    (folder/name).write_bytes(z.read('assets/goplanska_kukirin/models/'+name))
        olddoc,old=triangles(folders[0]);newdoc,new=triangles(folders[1])
        oldpivot=np.array(olddoc['nodes'][2]['translation']);newpivot=np.array(newdoc['nodes'][2]['translation'])
        rig=json.loads((folders[1]/'scooter-rig.json').read_text());axis=np.array(rig['steeringAxis'])
        components=connected(old[1].reshape(-1,3))
        brace=[f for f in components if len(f)==108 and old[1][f][:,:,1].min()<.17 and .47<old[1][f][:,:,1].max()<.48][0]
        frame=old[1][brace];samples=np.unique(np.concatenate([frame.reshape(-1,3),frame.mean(1),(frame[:,0]+frame[:,1])/2,(frame[:,1]+frame[:,2])/2,(frame[:,2]+frame[:,0])/2]),axis=0)
        bottom=samples[samples[:,1]<.215];contact=bottom[distance(bottom,old[0])<.008]
        def matrix(row,name):return np.array(row['palettes'][name]).reshape(4,4).T
        def transformed(points,m):
            flat=np.asarray(points).reshape(-1,3);return(np.c_[flat,np.ones(len(flat))]@m.T)[:,:3].reshape(np.asarray(points).shape)
        records=[];checks=0
        for label,runtime in (('baseline',options.baseline_runtime),('candidate',options.candidate_runtime)):
            rows=json.loads(runtime.read_text())['render']
            for phase in ('stationary_neutral','stationary_left','stationary_right','reverse_left','reverse_right'):
                selected=[r for r in rows if r['name']==phase and r['tick']>=25]
                assert selected,('missing settled fixture phase',label,phase)
                row=selected[-1];body=matrix(row,'body');stem=matrix(row,'stem')
                target=transformed(old[0],body)
                posed_contact=transformed(contact-oldpivot,stem) if label=='baseline' else transformed(contact,body)
                gap=distance(posed_contact,target)*1.25
                entry={'release':label,'phase':phase,'requested_wheel_yaw':row['wheel'],'surface_gap_mean_blocks':float(gap.mean()),'surface_gap_max_blocks':float(gap.max()),'path':row['render_path']}
                if label=='candidate':
                    axispoints=np.array([[0,0,0],axis*.2385586]);expected=axispoints+newpivot
                    error=np.linalg.norm(transformed(axispoints,stem)-transformed(expected,body),axis=1).max()*1.25
                    assert error<1e-6,('headset axis disconnected',error);checks+=1;entry['headset_axis_error_blocks']=float(error)
                    front=matrix(row,'frontwheel');front_hub=transformed(np.array([[0,0,0]]),front)[0]
                    expected_hub=transformed(np.array([np.array(rig['frontPivot'])-newpivot]),stem)[0]
                    huberror=float(np.linalg.norm(front_hub-expected_hub)*1.25)
                    assert huberror<1e-6,('frontwheel spin moved hub',huberror);checks+=1;entry['front_hub_error_blocks']=huberror
                    assert np.linalg.norm(front[:3,0]-stem[:3,0])<1e-6,'Front wheel axle spin must share steered X basis';checks+=1
                    axle=stem[:3,0];projected=math.degrees(math.atan2(axle[2],axle[0]));entry['projected_yaw_degrees']=projected
                    assert abs(projected-row['wheel'])<.24,('wrong wheel yaw',projected,row['wheel']);checks+=1
                    assert max(row['left_grip_error_blocks'],row['right_grip_error_blocks'])<1e-5,'hand sockets not following actual mesh';checks+=1
                    assert gap.max()<.01001,'fixed brace detached from actual fixed deck';checks+=1
                records.append(entry)
        comparison=[]
        for phase in ('stationary_left','stationary_right'):
            baseline=next(r for r in records if r['release']=='baseline' and r['phase']==phase)
            candidate=next(r for r in records if r['release']=='candidate' and r['phase']==phase)
            assert candidate['surface_gap_mean_blocks']<baseline['surface_gap_mean_blocks']/5,'Actual palette attachment must measurably improve';checks+=1
            comparison.append({'phase':phase,'mean_gap_reduction_ratio':baseline['surface_gap_mean_blocks']/candidate['surface_gap_mean_blocks']})
        report={'success':True,'checks':checks,'candidate_sha256':hashlib.sha256(options.candidate_jar.read_bytes()).hexdigest(),'baseline_sha256':hashlib.sha256(options.baseline_jar.read_bytes()).hexdigest(),'records':records,'comparison':comparison,'method':'Actual submitted full 4x4 bone palettes transform real authored brace contact points, measured against triangle surfaces; actual axle/hub/grip invariants.','limit':'Numeric submitted-palette proof complements isolated first-person screenshot inspection; it does not replace it.'}
        options.output.write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))

if __name__=='__main__':main()
