"""Qualify only the completed acoustic scope; preserve the failed auxiliary raw run."""
from pathlib import Path
import hashlib
import json
import re
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parent
BASE=ROOT.parent/'wearable-jukebox'
CURRENT='9d5e4271882a6ca112b7cdc859afb962cf3e42921f83a5fec05fba88ad11919e'
PREVIOUS='daa891b2f6e7c50fa9663efd13fc66e43bb9c1b6682c66833f0a30d4d1ca51a4'
OUT=ROOT/'qa/outputs/placed-acoustics3-0-4-held-item'
digest=lambda path:hashlib.sha256(Path(path).read_bytes()).hexdigest()
load=lambda path:json.loads(Path(path).read_text())
evidence=lambda path:{'path':str(Path(path).resolve()),'sha256':digest(path)}
raw_path=OUT/'runtime-report.json'
raw=load(raw_path);state=load(OUT/'state.json');build=load(ROOT/'build/build-proof.json')
inherited_path=BASE/'build/qualification.json';inherited=load(inherited_path)
assert raw['candidate_sha256']==state['candidate_sha256']==build['candidate_sha256']==CURRENT
assert raw['success'] is False and raw['phase']==12 and raw['error']=='java.lang.AssertionError: Phase 12 exceeded500ticks'
assert state['source_unchanged'] and state['candidate_unchanged'] and state['fixture_only'] and not state['credential_files_used']
assert inherited['success'] and inherited['runtime_verified'] and inherited['candidate_sha256']==PREVIOUS
assert build['success'] and build['base_sha256']==PREVIOUS and not build['added_entries'] and not build['removed_entries']
for item in inherited['evidence']:
    assert digest(item['path'])==item['sha256'], 'Inherited qualification evidence drifted'

expected=[
 'Actual full BMC client loaded',
 'Actual global SPR moving sound updates remain disabled',
 'Actual vanilla jukebox block entity exists',
 'Native crouch-right-click receives protected placed-jukebox catalog',
 'Actual long WAV uses block-backed Moving sound with no entity',
 'Placed custom audio is positional BLOCKS source',
 'Placed native OpenAL source is PLAYING with no error',
 'Scoped adapter repeatedly processes stationary placed custom source at global false',
 'Actual keyboard walking moves listener behind wall around its end',
 'Actual placed-source EFX high-frequency gain decreases behind wall',
 'Stationary placed source advances real SPR and temporal smoothing while listener walks',
 'Native OpenAL source remains at placed jukebox centre',
 'Walking listener back to clear path recovers actual placed EFX at global false',
]
assert [x['name'] for x in raw['checks']]==expected and all(x['passed'] is True for x in raw['checks'])
samples=raw['actual_openal_physics_samples']
assert [x['label'] for x in samples]==['placed-open-before-walk','placed-behind-wall-after-walk','placed-open-after-return-walk']
for row in samples:
    assert row['state']==4114 and row['al_error']==0 and row['actual_spr_filter_setter_tail']['al_error']==0
    assert row['actual_spr_filter_setter_tail']['source']==row['source']
    assert all(abs(row[axis]-value)<.01 for axis,value in [('x',8.5),('y',100.5),('z',8.5)])
assert samples[1]['direct_hf']<samples[0]['direct_hf']-.01 and samples[2]['direct_hf']>samples[1]['direct_hf']+.01
assert samples[0]['listener_x']>7 and samples[1]['listener_x']<3 and samples[2]['listener_x']>7
assert all(samples[i]['spr_applications']<samples[i+1]['spr_applications'] and samples[i]['smoothing_applications']<samples[i+1]['smoothing_applications'] for i in [0,1])

before=BASE/'build/psychiatryk_roles-3.0.3-wearable-jukebox-candidate.jar'
after=ROOT/'build/psychiatryk_roles-3.0.4-jukebox-acoustics-candidate.jar'
assert digest(before)==PREVIOUS and digest(after)==CURRENT
mixin='pl/aridlin/psychiatrykroles/jukebox/mixin/WearableSoundEngineMixin.class'
with zipfile.ZipFile(before) as old,zipfile.ZipFile(after) as new:
    assert set(old.namelist())==set(new.namelist())
    changed=sorted(name for name in old.namelist() if old.read(name)!=new.read(name))
    assert changed==sorted(['META-INF/neoforge.mods.toml',mixin])==sorted(build['changed_entries'])
    common_identical=all(old.read(name)==new.read(name) for name in old.namelist() if name not in changed)
    mixin_hashes={'before':hashlib.sha256(old.read(mixin)).hexdigest(),'after':hashlib.sha256(new.read(mixin)).hexdigest()}

def instructions(jar,label):
    result=subprocess.run(['javap','-c','-p','-classpath',str(jar),'pl.aridlin.psychiatrykroles.jukebox.mixin.WearableSoundEngineMixin'],check=True,capture_output=True,text=True).stdout
    (ROOT/'qa'/f'{label}-budget-bytecode.txt').write_text(result)
    section=result[result.index('private void jukebox$movingEnvironment'):]
    parsed=[]
    for line in section.splitlines():
        match=re.match(r'\s*(\d+):\s+(\S+)\s*(.*)',line)
        if match:parsed.append((int(match[1]),match[2],match[3]))
    return parsed

previous=instructions(before,'before');current=instructions(after,'after')
index=next(i for i,row in enumerate(previous) if '.isEntitySource:()Z' in row[2])
assert previous[index-1][1]=='aload' and previous[index+1][1]=='ifeq'
previous=previous[:index-1]+previous[index+2:]
def normalized(rows):
    targets={row[0]:i for i,row in enumerate(rows)};answer=[]
    for _,op,arg in rows:
        if '//' in arg:arg=arg.split('//',1)[1].strip()
        elif op.startswith('if') or op=='goto':arg='instruction '+str(targets[int(arg.strip())])
        else:arg=' '.join(arg.split())
        answer.append((op,arg))
    return answer
assert normalized(previous)==normalized(current), 'Beyond removal of entity-only predicate, actual compiled hook changed'
assert any(op=='bipush' and arg.strip()=='10' for _,op,arg in current)
assert any(op=='iconst_4' for _,op,arg in current) and any(op=='iinc' and arg.strip()=='2, 1' for _,op,arg in current)
budget={'success':True,'source':'Compiled hook instruction/control-flow equality after deleting only isEntitySource predicate',
        'reevaluation_interval_ticks':10,'maximum_evaluated_sources_per_native_tick':4,'runtime_four_source_claimed':False,
        'mixin_sha256':mixin_hashes,'base_qualification':evidence(inherited_path),
        'bytecode_evidence':[evidence(ROOT/'qa/before-budget-bytecode.txt'),evidence(ROOT/'qa/after-budget-bytecode.txt')]}
budget_path=ROOT/'qa/budget-preservation-proof.json';budget_path.write_text(json.dumps(budget,indent=2)+'\n')
scoped={
 'success':True,'runtime_verified':True,'candidate_sha256':CURRENT,'base_sha256':PREVIOUS,
 'scope':'Completed native placed vanilla-jukebox custom-WAV acoustic regression under global update_moving_sounds=false; unchanged compiled10tick/4source budget inherited from qualified3.0.3',
 'raw_runtime_receipt':evidence(raw_path),'raw_runtime_success':False,'raw_runtime_error':raw['error'],
 'scope_passed_checks':raw['checks'],'actual_openal_physics_samples':samples,'four_source_budget':budget,
 'unfinished_excluded':['Auxiliary second/fourth placed menu and fifth-source runtime rejection','Auxiliary four-source runtime tick window','Native teardown completion'],
 'client_exit':{'self_exited_after_fixture_error':True,'native_teardown_verified':False},
 'human_listening_claimed':False,'fresh_server_startup_claimed':False,'public_server_join_claimed':False,
 'integrated_server_fixture_only':True,'null_audio_output_only':True,
 'vanilla_RECORDS_disc_acoustics_claimed':False,'private_catalog_used':False,
 'all_other_client_and_common_server_classes_resources_identical':common_identical,
 'build_preservation':evidence(ROOT/'build/build-proof.json'),'base_qualification':evidence(inherited_path),
 'state_evidence':evidence(OUT/'state.json'),'budget_evidence':evidence(budget_path),
}
scoped_path=OUT/'placed-acoustic-scoped-report.json';scoped_path.write_text(json.dumps(scoped,indent=2)+'\n')
qualification={'success':True,'runtime_verified':True,'candidate_sha256':CURRENT,'base_sha256':PREVIOUS,
 'scope':scoped['scope'],'evidence':[evidence(scoped_path),evidence(raw_path),evidence(ROOT/'build/build-proof.json'),evidence(budget_path),evidence(inherited_path)],
 'raw_runtime_success':False,'auxiliary_fixture_complete':False,'unfinished_excluded':scoped['unfinished_excluded'],
 'human_listening_claimed':False,'fresh_server_startup_claimed':False,'public_server_join_claimed':False,
 'production_activated':False,'private_catalog_used':False,'runtime_four_source_claimed':False}
(ROOT/'build/qualification.json').write_text(json.dumps(qualification,indent=2)+'\n')
print(json.dumps({'qualification':str(ROOT/'build/qualification.json'),'qualification_sha256':digest(ROOT/'build/qualification.json'),'scoped_receipt':str(scoped_path),'scoped_sha256':digest(scoped_path),'candidate_sha256':CURRENT,'completed_checks':len(expected),'native_efx_samples':len(samples),'raw_runtime_success':False}))
