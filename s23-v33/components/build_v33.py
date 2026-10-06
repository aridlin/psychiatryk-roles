"""Package a tested scooter-control v33 overlay on the exact v32 release. No deployment."""
from pathlib import Path
import argparse, hashlib, json, re, shutil, tempfile, zipfile
ROOT=Path(__file__).resolve().parent.parent
WORK,OUT=ROOT/'work',ROOT/'outputs'
OLD,NEW=WORK/'release-v32',WORK/'release-v33'
QA=OUT/'steering-v33'
RELEASE='2026-10-06-S23-v33'
MOD='mods/psychiatryk_roles-2.1.1.jar'
APPROVED_CANDIDATE_SHA256='fb6794deda990eb9df51e409728b308bfc5376d0fd5a01b1b59c82d81a68418b'
APPROVED_BASE_SHA256='ac32a763e5ed3c54dd5a5197424d24d2b2b9f73f0d610aba4aeda20832dcb25a'
# Finalize only from the reviewed steering component before release QA.
CHANGED_CLASSES={
 'pl/aridlin/kukirin/'+name+'.class' for name in
 ['Scooter','ScooterHandling','ScooterFlight','ScooterControl','ScooterClient','ScooterVisual','ScooterRenderer','ScooterGripPose']
}|{'META-INF/neoforge.mods.toml','pl/aridlin/kukirin/ScooterRenderer$Events.class'}
ADDED_ENTRIES={
 'pl/aridlin/kukirin/ScooterHandling$Steering.class',
 'pl/aridlin/kukirin/ScooterOverspeed.class',
 'pl/aridlin/kukirin/ScooterShoulderPlugin.class',
 'pl/aridlin/kukirin/ScooterSteeringInput.class',
 'pl/aridlin/kukirin/ScooterSteeringInput$Client.class',
 'pl/aridlin/kukirin/ScooterSteeringInput$Shoulder.class',
 'pl/aridlin/kukirin/ScooterSpearLunge.class',
 'pl/aridlin/kukirin/mixin/ScooterSpearStabMixin.class',
 'pl/aridlin/kukirin/mixin/ScooterSpearEffectsMixin.class',
 'pl/aridlin/kukirin/mixin/ScooterSpearCompatPlugin.class',
 'shouldersurfing_plugin.json','psychiatryk-scooter-spears.mixins.json',
}
APPROVED_SOURCE_FILES={
 'kukirin/src/pl/aridlin/kukirin/'+name+'.java' for name in
 ['Scooter','ScooterHandling','ScooterOverspeed','ScooterFlight','ScooterControl','ScooterClient','ScooterVisual','ScooterRenderer','ScooterGripPose','ScooterShoulderPlugin','ScooterSteeringInput','ScooterSpearLunge','mixin/ScooterSpearStabMixin','mixin/ScooterSpearEffectsMixin','mixin/ScooterSpearCompatPlugin']
}
APPROVED_RESOURCE_FILES={'shouldersurfing_plugin.json','psychiatryk-scooter-spears.mixins.json'}
DOC='''
## v33 scooter controls
Keyboard steering follows the requested direction reliably, with low-speed
turning bounded by curvature instead of spinning in place. High-speed turning
keeps its existing yaw limit; the steering model is bounded to ±28 degrees. Third-person
control is corrected. Overspeed returns linearly toward cruising speed over
5 seconds. Direct model animation layers preserve the full steering angle,
including shared handlebar/hand poses. Soul Speed raises the terrain cap once
instead of multiplying speed each tick. A spear's own Lunge triggers one 100 km/h boost with a 40-tick taper;
it requires that spear enchantment. Mouse steering and existing upgrades remain.
The build proof lists the exact changed classes and optional helper additions;
every other existing JAR entry is byte-identical to v32. Runtime reports describe the cases actually exercised, not every terrain,
network or input situation. Keep the v32 pack and activation backup for rollback.
'''
def sha(path):
 with Path(path).open('rb')as f:return hashlib.file_digest(f,'sha256').hexdigest()
def verify_candidate():
 candidate=QA/'psychiatryk_roles.jar';checksum=sha(candidate)
 if checksum!=APPROVED_CANDIDATE_SHA256 or sha(OLD/'client'/MOD)!=APPROVED_BASE_SHA256:raise RuntimeError('Candidate/baseline differs from the explicitly approved v33 overlay')
 runtime=json.loads((QA/'runtime-report.json').read_text());proof=json.loads((QA/'build-report.json').read_text())
 if runtime.get('success')is not True or runtime.get('candidate_sha256')!=checksum:raise RuntimeError('Successful runtime QA of this exact v33 candidate is required')
 if proof.get('candidate_sha256')!=checksum or proof.get('base_sha256')!=sha(OLD/'client'/MOD):raise RuntimeError('v33 candidate proof must match the exact v32 baseline')
 declared=set(proof.get('changed_existing_entries',proof.get('changed_entries',[])))
 if declared!=CHANGED_CLASSES:raise RuntimeError('v33 proof must declare exactly the reviewed steering classes: '+repr(declared))
 with zipfile.ZipFile(OLD/'client'/MOD)as a,zipfile.ZipFile(candidate)as b:
  an={n for n in a.namelist()if not n.endswith('/')};bn={n for n in b.namelist()if not n.endswith('/')}
  if an-bn or bn-an!=ADDED_ENTRIES:raise RuntimeError('v33 removed entries or added entries outside the reviewed whitelist')
  if set(proof.get('added_entries',[]))!=ADDED_ENTRIES:raise RuntimeError('v33 added-entry proof differs from reviewed whitelist')
  changed={n for n in an if a.read(n)!=b.read(n)}
  if changed!=CHANGED_CLASSES:raise RuntimeError('Unexpected v33 byte changes: '+repr(changed))
  expected={**proof.get('classes',{}),**proof.get('resource_files',{}),**proof.get('entry_sha256',{})}
  if not ((CHANGED_CLASSES|ADDED_ENTRIES)-{'META-INF/neoforge.mods.toml'})<=set(expected):raise RuntimeError('Per-entry tested hashes are required')
  for n in (CHANGED_CLASSES|ADDED_ENTRIES)-{'META-INF/neoforge.mods.toml'}:
   if hashlib.sha256(b.read(n)).hexdigest()!=expected[n]:raise RuntimeError('Steering class differs from build proof: '+n)
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
 text=re.sub(r'/home/[^/]+/Documents/Codex/[^/]+/[^/]+/outputs/steering-v33-qa','outputs/steering-v33-qa',text)
 text=re.sub(r'/home/[a-zA-Z0-9_.-]+/','~/',text)
 return text

def source_zip(client,host):
 source=OLD/'client/licenses/psychiatryk-roles-unified-source.zip';target=client/'licenses'/source.name
 proof=json.loads((QA/'build-report.json').read_text())
 replacement={name:WORK/name for name in proof['source_files']}
 for name in proof['resource_files']:replacement['kukirin/resources/'+name]=WORK/'kukirin/resources'/name
 for folder in ['src','resources','unit-src']:
  for path in (WORK/'steering-v33-qa'/folder).rglob('*'):
   if path.is_file()and path.suffix in ['.java','.json','.toml','.py','.md']and not path.name.startswith('private_'):replacement[str(path.relative_to(WORK))]=path
 for name in ['build.py','unit.py','analyze.py','HandlingGeometryQA.java','SoulSpeedBoundQA.java','PrepareWorld.java','README.md']:
  path=WORK/'steering-v33-qa'/name
  if path.is_file():replacement[str(path.relative_to(WORK))]=path
 for name in ['scooter-steering-compat/build.py','scooter-steering-compat/README.md','scooter-steering-compat/qa/build_setup.py','scooter-steering-compat/qa/run.py','scooter-steering-compat/qa/README.md']:
  replacement[name]=WORK/name
 for path in (WORK/'scooter-steering-compat/qa/src').rglob('*.java'):replacement[str(path.relative_to(WORK))]=path
 compat_report=WORK/'scooter-steering-compat/qa/runtime-report.json'
 compat=json.loads(compat_report.read_text())
 if compat.get('success')is not True or compat.get('candidate_sha256')!=APPROVED_CANDIDATE_SHA256:raise RuntimeError('Public compatibility report must verify this exact candidate')
 from prepare_v32_github import scrub
 for name in ['build_v33.py','prepare_v33_github.py','build_steering_v33.py','kukirin/build.py','kukirin/BoostDecayTest.java']:replacement[name]=WORK/name
 with zipfile.ZipFile(source)as a,zipfile.ZipFile(target,'w',zipfile.ZIP_DEFLATED)as b:
  for entry in a.infolist():
   if entry.filename not in replacement and not entry.filename.startswith(('steering-v33/','steering-v33-qa/')):b.writestr(entry,a.read(entry.filename))
  for name,path in sorted(replacement.items()):
   data=path.read_text()
   data=normalize_home_paths(data)
   b.writestr(name,data.encode())
  b.writestr('scooter-steering-compat/qa/runtime-report.json',json.dumps(scrub(compat),indent=2))
  b.writestr('STEERING-v33.md',DOC.lstrip())
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
  with zipfile.ZipFile(OUT/filename('v32'))as previous:index=json.loads(previous.read(indexname))
  index['name']='Goplanska S23 v33';index['version'if ext=='curseforge.zip'else'versionId']=RELEASE
  external=set()
  if ext=='mrpack':
   index['files']=[entry for entry in index['files']if entry['path']!=MOD]
   for entry in index['files']:
    if entry['path']not in manifest['client']or entry['hashes']['sha1']!=manifest['client'][entry['path']]['sha1']:raise RuntimeError('External archive inventory changed: '+entry['path'])
   external={entry['path']for entry in index['files']}
  dest=OUT/filename('v33');temp=dest.with_name(dest.name+'.building')
  with zipfile.ZipFile(temp,'w',zipfile.ZIP_DEFLATED)as z:
   z.writestr(indexname,json.dumps(index,indent=2))
   for p in sorted(client.rglob('*')):
    n=p.relative_to(client).as_posix()
    if p.is_file()and n not in external:z.write(p,'overrides/'+n)
  with zipfile.ZipFile(temp)as z:
   if z.testzip()is not None or hashlib.sha256(z.read('overrides/'+MOD)).hexdigest()!=manifest['client'][MOD]['sha256']:raise RuntimeError('Invalid packaged v33 archive')
  temp.replace(dest);result.append(dest)
 return result

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--replace',action='store_true');args=parser.parse_args()
 candidate=verify_candidate()
 if NEW.exists()and not args.replace:raise RuntimeError('release-v33 exists; deliberate local replacement needs --replace')
 baseline=json.loads((OLD/'goplanska-release.json').read_text())
 if baseline['release']!='2026-10-06-S23-v32':raise RuntimeError('Unexpected baseline release')
 with tempfile.TemporaryDirectory(prefix='.release-v33-',dir=WORK)as temp:
  release=Path(temp)
  for side in ['client','server']:shutil.copytree(OLD/side,release/side)
  client,server=release/'client',release/'server';host=server/'automodpack/host-modpack/main'
  for folder in [client,server,host]:shutil.copy2(candidate,folder/MOD)
  for folder in [client,host]:
   (folder/'README-SCOOTER.txt').write_text((folder/'README-SCOOTER.txt').read_text()+DOC)
   (folder/'README-STEERING.txt').write_text(DOC.lstrip())
  source_zip(client,host)
  manifest=json.loads(json.dumps(baseline));manifest['release']=RELEASE;manifest['features'].append('v33 scooter controls: reliable first/third-person steering, curvature-bounded low-speed turning; linear overspeed taper and spear-Lunge one-shot boost; unrelated v32 bytecode preserved')
  for side in ['client','server']:
   actual=inventory(release/side)
   if actual.keys()!=baseline[side].keys()or any(v!=baseline[side][k]for k,v in actual.items()if k!=MOD):raise RuntimeError('Unrelated mod change in '+side)
   manifest[side]=actual
  if inventory(host)!={n:h for n,h in manifest['client'].items()if not n.startswith('mods/automodpack-')}:raise RuntimeError('Host/client mod inventory mismatch')
  for p in [release/'goplanska-release.json',client/'goplanska-release.json',server/'goplanska-release.json',host/'goplanska-release.json']:p.write_text(json.dumps(manifest,indent=2))
  packs=archives(client,manifest);pointer={'release':RELEASE,'curseforge':packs[0].name,'prism':packs[1].name,'previousRelease':'v32','manifest':'goplanska-release-v33.json','sha256':{p.name:sha(p)for p in packs}}
  (release/'current-release.json').write_text(json.dumps(pointer,indent=2))
  changed=[p.relative_to(server).as_posix()for p in sorted(server.rglob('*'))if p.is_file()and(not(OLD/'server'/p.relative_to(server)).is_file()or sha(p)!=sha(OLD/'server'/p.relative_to(server)))]
  allowed={MOD,'goplanska-release.json','automodpack/host-modpack/main/'+MOD,'automodpack/host-modpack/main/goplanska-release.json','automodpack/host-modpack/main/README-SCOOTER.txt','automodpack/host-modpack/main/README-STEERING.txt','automodpack/host-modpack/main/licenses/psychiatryk-roles-unified-source.zip'}
  if set(changed)!=allowed:raise RuntimeError('Unexpected v33 activation files: '+repr(changed))
  with zipfile.ZipFile(OUT/'goplanska-s23-v33-activate.zip','w',zipfile.ZIP_DEFLATED)as z:
   for n in changed:z.write(server/n,n)
   z.writestr('goplanska-deployment-v33.properties',''.join('sha256.'+n+'='+sha(server/n)+'\n'for n in changed))
  if NEW.exists():shutil.rmtree(NEW)
  shutil.copytree(release,NEW)
 print('Prepared tested scooter-control v33; 7 activation files, v32 rollback preserved. No deployment or publication.')
if __name__=='__main__':main()
