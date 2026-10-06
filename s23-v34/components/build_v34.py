"""Package a tested scooter-control v34 overlay on the exact v33 release. No deployment."""
from pathlib import Path
from public_v34_source import public_source_path,public_source_data,audit_source_zip
import argparse, hashlib, json, re, shutil, tempfile, zipfile
ROOT=Path(__file__).resolve().parent.parent
WORK,OUT=ROOT/'work',ROOT/'outputs'
OLD,NEW=WORK/'release-v33',WORK/'release-v34'
QA=OUT/'steering-v34'
RELEASE='2026-10-06-S23-v34'
MOD='mods/psychiatryk_roles-2.1.1.jar'
APPROVED_CANDIDATE_SHA256='965392449a2b91432712d4f844b078074d6b660a3f3c00e4915e5f5904dc0a20'
APPROVED_BASE_SHA256='fb6794deda990eb9df51e409728b308bfc5376d0fd5a01b1b59c82d81a68418b'
# Exact reviewed frozen component; successful runtime QA remains mandatory.
CHANGED_CLASSES={
 'assets/goplanska_kukirin/models/kukirin_g2.glb',
 'assets/goplanska_kukirin/models/kukirin_g2.mesh',
 'assets/goplanska_kukirin/models/scooter-rig.json',
 'pl/aridlin/kukirin/G2Mesh.class',
 'pl/aridlin/kukirin/Scooter.class',
 'pl/aridlin/kukirin/ScooterGripPose.class',
 'pl/aridlin/kukirin/ScooterHandling$Steering.class',
 'pl/aridlin/kukirin/ScooterHandling.class',
 'pl/aridlin/kukirin/ScooterRenderer.class',
 'pl/aridlin/kukirin/ScooterVisual.class',
}
ADDED_ENTRIES={'pl/aridlin/kukirin/ScooterSteeringRig.class'}
APPROVED_SOURCE_FILES={'kukirin/src/pl/aridlin/kukirin/'+name+'.java' for name in ['Scooter','ScooterHandling','ScooterGripPose','ScooterVisual','ScooterRenderer','G2Mesh','ScooterSteeringRig']}
APPROVED_RESOURCE_FILES={'assets/goplanska_kukirin/models/'+name for name in ['kukirin_g2.glb','kukirin_g2.mesh','scooter-rig.json']}
DOC='''
## v34 scooter direction and reversing
Keyboard A/D follow Minecraft's actual camera basis. The visible steering
assembly, grips and trim use the same pose, and the front wheel follows the
rolling turn radius. The chassis does not pivot at rest; the handlebars can.
S brakes forward motion first, then reverses after stopping. Backward rolling
reverses chassis yaw while preserving the requested handlebar direction.
Mouse steering, Shoulder Surfing, body lean, overspeed taper and spear Lunge
remain. Runtime reports describe only the actual control/model cases exercised.
Every unrelated existing unified-JAR entry and every other mod is preserved
from v33. Keep the v33 pack and the activation backup for rollback.
The earlier v33 direction assertion used an incorrect left/right basis; v34's
Minecraft-camera basis checks are the authoritative direction evidence.
'''
def sha(path):
 with Path(path).open('rb')as f:return hashlib.file_digest(f,'sha256').hexdigest()
def verify_candidate():
 if not APPROVED_CANDIDATE_SHA256 or not CHANGED_CLASSES or not APPROVED_SOURCE_FILES:
  raise RuntimeError('v34 is a blocked draft: finalize the reviewed candidate hash and explicit entry/source allowlists after QA')
 candidate=QA/'psychiatryk_roles.jar';checksum=sha(candidate)
 if checksum!=APPROVED_CANDIDATE_SHA256 or sha(OLD/'client'/MOD)!=APPROVED_BASE_SHA256:raise RuntimeError('Candidate/baseline differs from the explicitly approved v34 overlay')
 runtime=json.loads((QA/'runtime-report.json').read_text());proof=json.loads((QA/'build-report.json').read_text())
 if runtime.get('success')is not True or runtime.get('runtime_verified')is not True or runtime.get('candidate_sha256')!=checksum:raise RuntimeError('Successful runtime QA of this exact v34 candidate is required')
 if proof.get('candidate_sha256')!=checksum or proof.get('base_sha256')!=sha(OLD/'client'/MOD):raise RuntimeError('v34 candidate proof must match the exact v33 baseline')
 declared=set(proof.get('changed_existing_entries',proof.get('changed_entries',[])))
 if declared!=CHANGED_CLASSES:raise RuntimeError('v34 proof must declare exactly the reviewed steering classes: '+repr(declared))
 with zipfile.ZipFile(OLD/'client'/MOD)as a,zipfile.ZipFile(candidate)as b:
  an={n for n in a.namelist()if not n.endswith('/')};bn={n for n in b.namelist()if not n.endswith('/')}
  if an-bn or bn-an!=ADDED_ENTRIES:raise RuntimeError('v34 removed entries or added entries outside the reviewed whitelist')
  if set(proof.get('added_entries',[]))!=ADDED_ENTRIES:raise RuntimeError('v34 added-entry proof differs from reviewed whitelist')
  changed={n for n in an if a.read(n)!=b.read(n)}
  if changed!=CHANGED_CLASSES:raise RuntimeError('Unexpected v34 byte changes: '+repr(changed))
  expected={**proof.get('classes',{}),**proof.get('resource_files',{}),**proof.get('entry_sha256',{})}
  if not ((CHANGED_CLASSES|ADDED_ENTRIES)-{'META-INF/neoforge.mods.toml'})<=set(expected):raise RuntimeError('Per-entry tested hashes are required')
  for n in (CHANGED_CLASSES|ADDED_ENTRIES)-{'META-INF/neoforge.mods.toml'}:
   if hashlib.sha256(b.read(n)).hexdigest()!=expected[n]:raise RuntimeError('Steering class differs from build proof: '+n)
  inherited=json.loads((QA/'spear-inheritance.json').read_text())
  if inherited.get('verified')is not True or inherited.get('candidate_sha256')!=checksum or inherited.get('base_sha256')!=APPROVED_BASE_SHA256 or inherited.get('lunge_source_methods_unchanged')is not True:raise RuntimeError('Exact honest v33 spear inheritance proof required')
  for n,h in inherited['unchanged_entries'].items():
   if a.read(n)!=b.read(n)or hashlib.sha256(b.read(n)).hexdigest()!=h:raise RuntimeError('Inherited spear/boost entry changed: '+n)
 if set(proof['source_files'])!=APPROVED_SOURCE_FILES:raise RuntimeError('Source proof differs from approved scooter source whitelist')
 if set(proof['resource_files'])!=APPROVED_RESOURCE_FILES:raise RuntimeError('Resource proof differs from approved scooter resources')
 for n,h in proof['source_files'].items():
  if Path(n).is_absolute()or'..'in Path(n).parts or sha(WORK/n)!=h:raise RuntimeError('Compiled source differs from release source: '+n)
 for n,h in proof['resource_files'].items():
  if Path(n).is_absolute()or'..'in Path(n).parts or sha(WORK/'kukirin/resources'/n)!=h:raise RuntimeError('Compiled resource differs from source: '+n)
 return candidate

def normalize_home_paths(text):
 text=re.sub(r"Path\((['\"])/home/[^/'\"]+/([^'\"]+)\1\)",lambda m:'(Path.home() / '+repr(m.group(2))+')',text)
 text=re.sub(r'''(?m)^(\s*)["'](/home/[^/]+/[^"']*/bin/java)["'](,\s*)$''',lambda m:m.group(1)+'str(Path.home() / '+repr(m.group(2).split('/',3)[3])+')'+m.group(3),text)
 text=re.sub(r'/home/[^/]+/Documents/Codex/[^/]+/[^/]+/outputs/steering-v34-qa','outputs/steering-v34-qa',text)
 text=re.sub(r'/home/[a-zA-Z0-9_.-]+/','~/',text)
 return text

def source_zip(client,host):
 source=OLD/'client/licenses/psychiatryk-roles-unified-source.zip';target=client/'licenses'/source.name
 proof=json.loads((QA/'build-report.json').read_text())
 replacement={name:WORK/name for name in proof['source_files']}
 for name in proof['resource_files']:replacement['kukirin/resources/'+name]=WORK/'kukirin/resources'/name
 for folder in ['src','resources','unit-src']:
  for path in (WORK/'steering-v34-qa'/folder).rglob('*'):
   if path.is_file()and path.suffix in ['.java','.json','.toml','.py','.md']and not path.name.startswith('private_'):replacement[str(path.relative_to(WORK))]=path
 for name in ['build.py','unit.py','analyze.py','HandlingGeometryQA.java','SoulSpeedBoundQA.java','ReverseHandlingQA.java','PrepareWorld.java','README.md']:
  path=WORK/'steering-v34-qa'/name
  if path.is_file():replacement[str(path.relative_to(WORK))]=path
 for name in ['scooter-steering-compat/build.py','scooter-steering-compat/README.md','scooter-steering-compat/qa/build_setup.py','scooter-steering-compat/qa/run.py','scooter-steering-compat/qa/README.md']:
  replacement[name]=WORK/name
 for path in (WORK/'scooter-steering-compat/qa/src').rglob('*.java'):replacement[str(path.relative_to(WORK))]=path
 compat_report=WORK/'scooter-steering-compat/qa/runtime-report.json'
 compat=json.loads(compat_report.read_text())
 if compat.get('success')is not True or compat.get('candidate_sha256')!=APPROVED_BASE_SHA256:raise RuntimeError('Historical compatibility report must verify the exact preserved v33 baseline')
 replacement['scooter-steering-compat/qa/runtime-report.json']=compat_report
 replacement['generators/repair_scooter_steering_rig.py']=WORK/'generators/repair_scooter_steering_rig.py'
 replacement['scooter-geometry-v34/continuity_qa.py']=WORK/'scooter-geometry-v34/continuity_qa.py'
 replacement['scooter-geometry-v34/continuity-report.json']=WORK/'scooter-geometry-v34/continuity-report.json'
 replacement['scooter-geometry-v34/runtime_continuity_qa.py']=WORK/'scooter-geometry-v34/runtime_continuity_qa.py'
 replacement['scooter-geometry-v34/runtime-continuity-report.json']=WORK/'scooter-geometry-v34/runtime-continuity-report.json'
 replacement['scooter-geometry-v34/README.md']=WORK/'scooter-geometry-v34/README.md'
 replacement['scooter-geometry-v34/compiled-qa/SteeringRigQA.java']=WORK/'scooter-geometry-v34/compiled-qa/SteeringRigQA.java'
 replacement['scooter-geometry-v34/compiled-qa/report.json']=WORK/'scooter-geometry-v34/compiled-qa/report.json'
 replacement['steering-v34-qa/reverse-handling-report.json']=QA/'reverse-handling-report.json'
 replacement['steering-v34-qa/spear-inheritance.json']=QA/'spear-inheritance.json'
 continuity=json.loads(replacement['scooter-geometry-v34/continuity-report.json'].read_text())
 if continuity.get('success')is not True:raise RuntimeError('Successful authored rig continuity proof required')
 geometry=json.loads(replacement['scooter-geometry-v34/compiled-qa/report.json'].read_text())
 if geometry.get('success')is not True or geometry.get('candidate_sha256')!=APPROVED_CANDIDATE_SHA256:raise RuntimeError('Exact compiled rig helper proof required')
 geometry_runtime=json.loads(replacement['scooter-geometry-v34/runtime-continuity-report.json'].read_text())
 if geometry_runtime.get('success')is not True or geometry_runtime.get('candidate_sha256')!=APPROVED_CANDIDATE_SHA256:raise RuntimeError('Exact actual submitted rig continuity proof required')
 reverse=json.loads(replacement['steering-v34-qa/reverse-handling-report.json'].read_text())
 if reverse.get('success')is not True or reverse.get('candidate_sha256')!=APPROVED_CANDIDATE_SHA256:raise RuntimeError('Exact reverse handling proof required')
 for name in ['build_v34.py','prepare_v34_github.py','build_steering_v34.py','public_v34_source.py','kukirin/build.py','kukirin/BoostDecayTest.java']:replacement[name]=WORK/name
 with zipfile.ZipFile(source)as a,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED)as b:
  for entry in a.infolist():
   if entry.filename not in replacement and not entry.filename.startswith(('steering-v34/','steering-v34-qa/')) and public_source_path(entry.filename):
    b.writestr(entry.filename,public_source_data(entry.filename,a.read(entry.filename)))
  for name,path in sorted(replacement.items()):
   if not public_source_path(name):raise RuntimeError('Private/unsupported source selected: '+name)
   b.writestr(name,public_source_data(name,path.read_bytes()))
  b.writestr('STEERING-v34.md',DOC.lstrip()+'\nThe spear compatibility report is historical v33 evidence. Those bridge classes are byte-identical in v34; v34 runtime-report.json covers the changed scooter core/control/model cases.\n')
 audit_source_zip(target)
 shutil.copy2(target,host/'licenses'/source.name)

def inventory(folder):
 result={}
 for p in sorted((folder/'mods').glob('*.jar')):
  data=p.read_bytes();result['mods/'+p.name]={'size':len(data),'sha1':hashlib.sha1(data).hexdigest(),'sha256':hashlib.sha256(data).hexdigest()}
 return result

def archives(client,manifest):
 result=[]
 for ext,indexname in [('curseforge.zip','manifest.json'),('mrpack','modrinth.index.json')]:
  filename=lambda version:f'psychiatryk-s23-20261006-klient-{version}'+('-curseforge.zip'if ext=='curseforge.zip'else'.mrpack')
  with zipfile.ZipFile(OUT/filename('v33'))as previous:index=json.loads(previous.read(indexname))
  index['name']='Goplanska S23 v34';index['version'if ext=='curseforge.zip'else'versionId']=RELEASE
  external=set()
  if ext=='mrpack':
   index['files']=[entry for entry in index['files']if entry['path']!=MOD]
   for entry in index['files']:
    if entry['path']not in manifest['client']or entry['hashes']['sha1']!=manifest['client'][entry['path']]['sha1']:raise RuntimeError('External archive inventory changed: '+entry['path'])
   external={entry['path']for entry in index['files']}
  dest=OUT/filename('v34');temp=dest.with_name(dest.name+'.building')
  with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED)as z:
   z.writestr(indexname,json.dumps(index,indent=2))
   for p in sorted(client.rglob('*')):
    n=p.relative_to(client).as_posix()
    if p.is_file()and n not in external:z.write(p,'overrides/'+n)
  with zipfile.ZipFile(temp)as z:
   if z.testzip()is not None or hashlib.sha256(z.read('overrides/'+MOD)).hexdigest()!=manifest['client'][MOD]['sha256']:raise RuntimeError('Invalid packaged v34 archive')
  temp.replace(dest);result.append(dest)
 return result

def package_report(candidate,packs,activation_names):
 """Audit the completed local artifacts only; records no external activation."""
 source=NEW/'client/licenses/psychiatryk-roles-unified-source.zip'
 audit_source_zip(source)
 with zipfile.ZipFile(OLD/'client/licenses'/source.name)as a,zipfile.ZipFile(source)as b:
  old={n:a.read(n)for n in a.namelist()if not n.endswith('/')}
  new={n:b.read(n)for n in b.namelist()if not n.endswith('/')}
  added=sorted(set(new)-set(old));removed=sorted(set(old)-set(new))
  changed=sorted(n for n in set(old)&set(new)if old[n]!=new[n])
  python_count=sum(n.endswith('.py')for n in new)
 manifest=json.loads((NEW/'goplanska-release.json').read_text())
 for side in ['client','server']:
  if inventory(NEW/side)!=manifest[side]:raise RuntimeError('Completed package inventory mismatch: '+side)
 host=NEW/'server/automodpack/host-modpack/main'
 if sha(NEW/'client'/MOD)!=sha(NEW/'server'/MOD)or sha(host/MOD)!=sha(candidate):raise RuntimeError('Completed unified-JAR sides differ')
 activation=OUT/'goplanska-s23-v34-activate.zip'
 with zipfile.ZipFile(activation)as z:
  names=[n for n in z.namelist()if n!='goplanska-deployment-v34.properties']
  if len(names)!=7 or set(names)!=set(activation_names):raise RuntimeError('Completed activation archive is not exactly seven reviewed files')
  for n in names:
   if hashlib.sha256(z.read(n)).hexdigest()!=sha(NEW/'server'/n):raise RuntimeError('Activation entry differs from packaged server: '+n)
 runtime=json.loads((QA/'runtime-report.json').read_text())
 compat=json.loads((WORK/'scooter-steering-compat/qa/runtime-report.json').read_text())
 def record(path):return {'file':path.name,'size':path.stat().st_size,'sha256':sha(path)}
 result={'success':True,'candidate_sha256':sha(candidate),'base_sha256':APPROVED_BASE_SHA256,'release':RELEASE,
  'client_mods':len(manifest['client']),'server_mods':len(manifest['server']),'activation_files':len(activation_names),
  'activation_entry_sha256':{n:sha(NEW/'server'/n)for n in activation_names},'artifacts':[record(p)for p in packs],
  'activation_zip':record(activation),'source_archive':{**record(source),'new_files':added,'changed_files':changed,
   'filtered_private_or_binary_files':removed,'private_exports_found':False,'python_helpers_ast_checked':python_count,
   'inherited_history_privacy_normalized':True,'historical_v33_release_unchanged':True},
  'runtime_client_and_unit_checks':runtime.get('passed'),'runtime_scope':runtime.get('checks_scope',runtime.get('scope')),
  'historical_spear_report':{'release':'v33','candidate_sha256':compat['candidate_sha256'],'checks':len(compat['checks']),
    'unchanged_bridge_bytecode_proven':True,'rerun_on_v34':False},'external_actions_performed':False}
 (QA/'package-report.json').write_text(json.dumps(result,indent=2)+'\n')

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--replace',action='store_true');args=parser.parse_args()
 candidate=verify_candidate()
 if NEW.exists()and not args.replace:raise RuntimeError('release-v34 exists; deliberate local replacement needs --replace')
 baseline=json.loads((OLD/'goplanska-release.json').read_text())
 if baseline['release']!='2026-10-06-S23-v33':raise RuntimeError('Unexpected baseline release')
 with tempfile.TemporaryDirectory(prefix='.release-v34-',dir=WORK)as temp:
  release=Path(temp)
  for side in ['client','server']:shutil.copytree(OLD/side,release/side)
  client,server=release/'client',release/'server';host=server/'automodpack/host-modpack/main'
  for folder in [client,server,host]:shutil.copy2(candidate,folder/MOD)
  for folder in [client,host]:
   (folder/'README-SCOOTER.txt').write_text((folder/'README-SCOOTER.txt').read_text()+DOC)
   (folder/'README-STEERING.txt').write_text(DOC.lstrip())
  source_zip(client,host)
  manifest=json.loads(json.dumps(baseline));manifest['release']=RELEASE;manifest['features'].append('v34 scooter controls: correct keyboard direction and synchronized steering model; S brakes then reverses; unrelated v33 bytecode and all other mods preserved')
  for side in ['client','server']:
   actual=inventory(release/side)
   if actual.keys()!=baseline[side].keys()or any(v!=baseline[side][k]for k,v in actual.items()if k!=MOD):raise RuntimeError('Unrelated mod change in '+side)
   manifest[side]=actual
  if inventory(host)!={n:h for n,h in manifest['client'].items()if not n.startswith('mods/automodpack-')}:raise RuntimeError('Host/client mod inventory mismatch')
  for p in [release/'goplanska-release.json',client/'goplanska-release.json',server/'goplanska-release.json',host/'goplanska-release.json']:p.write_text(json.dumps(manifest,indent=2))
  packs=archives(client,manifest);pointer={'release':RELEASE,'curseforge':packs[0].name,'prism':packs[1].name,'previousRelease':'v33','manifest':'goplanska-release-v34.json','sha256':{p.name:sha(p)for p in packs}}
  (release/'current-release.json').write_text(json.dumps(pointer,indent=2))
  changed=[p.relative_to(server).as_posix()for p in sorted(server.rglob('*'))if p.is_file()and(not(OLD/'server'/p.relative_to(server)).is_file()or sha(p)!=sha(OLD/'server'/p.relative_to(server)))]
  allowed={MOD,'goplanska-release.json','automodpack/host-modpack/main/'+MOD,'automodpack/host-modpack/main/goplanska-release.json','automodpack/host-modpack/main/README-SCOOTER.txt','automodpack/host-modpack/main/README-STEERING.txt','automodpack/host-modpack/main/licenses/psychiatryk-roles-unified-source.zip'}
  if set(changed)!=allowed:raise RuntimeError('Unexpected v34 activation files: '+repr(changed))
  with zipfile.ZipFile(OUT/'goplanska-s23-v34-activate.zip','w',zipfile.ZIP_DEFLATED)as z:
   for n in changed:z.write(server/n,n)
   z.writestr('goplanska-deployment-v34.properties',''.join('sha256.'+n+'='+sha(server/n)+'\n'for n in changed))
  if NEW.exists():shutil.rmtree(NEW)
  shutil.copytree(release,NEW)
 package_report(candidate,packs,changed)
 print('Prepared tested scooter-control v34; 7 activation files, v33 rollback preserved. No deployment or publication.')
if __name__=='__main__':main()
