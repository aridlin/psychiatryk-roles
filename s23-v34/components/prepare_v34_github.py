"""Prepare authored v34 steering source and scrubbed QA; does not commit or push."""
from pathlib import Path
from public_v34_source import public_source_path,public_source_data,audit_public_tree
import argparse,json,shutil,zipfile
from build_v34 import WORK,OUT,QA,NEW,MOD,RELEASE,sha,verify_candidate,normalize_home_paths
from prepare_v32_github import public_report

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--deployment-record',type=Path);args=parser.parse_args()
 candidate=verify_candidate();proof=json.loads((QA/'build-report.json').read_text());manifest=json.loads((NEW/'goplanska-release.json').read_text())
 if manifest['release']!=RELEASE or manifest['client'][MOD]['sha256']!=sha(candidate):raise RuntimeError('Prepared v34 release differs from tested candidate')
 repo=WORK/'github-publish-v19';target=repo/'s23-v34';target.mkdir(exist_ok=True);components=target/'components'
 if components.exists():shutil.rmtree(components)
 components.mkdir(parents=True)
 for source in (repo/'s23-v33/components').rglob('*'):
  if source.is_file():
   relative=source.relative_to(repo/'s23-v33/components').as_posix()
   if public_source_path(relative):
    dest=components/relative;dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(public_source_data(relative,source.read_bytes()))
 # Preserve previously published features, replacing only our current steering
 # source and adding explicitly authored v34 helpers. Never scan QA clone roots.
 selected=set(proof['source_files'])|{'kukirin/resources/'+n for n in proof['resource_files']}|{'kukirin/build.py','kukirin/BoostDecayTest.java','build_steering_v34.py','build_v34.py','prepare_v34_github.py','public_v34_source.py','generators/repair_scooter_steering_rig.py','scooter-geometry-v34/continuity_qa.py','scooter-geometry-v34/continuity-report.json','scooter-geometry-v34/runtime_continuity_qa.py','scooter-geometry-v34/runtime-continuity-report.json','scooter-geometry-v34/README.md','scooter-geometry-v34/compiled-qa/SteeringRigQA.java','scooter-geometry-v34/compiled-qa/report.json','STEERING-v34.md','steering-v34-qa/build.py','steering-v34-qa/unit.py','steering-v34-qa/analyze.py','steering-v34-qa/HandlingGeometryQA.java','steering-v34-qa/SoulSpeedBoundQA.java','steering-v34-qa/ReverseHandlingQA.java','steering-v34-qa/reverse-handling-report.json','steering-v34-qa/spear-inheritance.json','steering-v34-qa/PrepareWorld.java','steering-v34-qa/README.md','scooter-steering-compat/build.py','scooter-steering-compat/README.md','scooter-steering-compat/qa/build_setup.py','scooter-steering-compat/qa/run.py','scooter-steering-compat/qa/README.md','scooter-steering-compat/qa/runtime-report.json'}
 with zipfile.ZipFile(NEW/'client/licenses/psychiatryk-roles-unified-source.zip')as z:
  required=set(proof['source_files'])|{'kukirin/resources/'+n for n in proof['resource_files']}|{'kukirin/build.py','kukirin/BoostDecayTest.java','build_steering_v34.py','build_v34.py','prepare_v34_github.py','public_v34_source.py','generators/repair_scooter_steering_rig.py','scooter-geometry-v34/continuity_qa.py','scooter-geometry-v34/continuity-report.json','scooter-geometry-v34/runtime_continuity_qa.py','scooter-geometry-v34/runtime-continuity-report.json','scooter-geometry-v34/README.md','scooter-geometry-v34/compiled-qa/SteeringRigQA.java','scooter-geometry-v34/compiled-qa/report.json','STEERING-v34.md'}
  if not required<=set(z.namelist()):raise RuntimeError('Source archive is missing reviewed v34 authored files')
  for n in z.namelist():
   if n not in selected and not n.startswith(('steering-v34-qa/src/','steering-v34-qa/resources/','steering-v34-qa/unit-src/','scooter-steering-compat/qa/src/')):continue
   if Path(n).is_absolute()or'..'in Path(n).parts:raise RuntimeError('Unsafe source path')
   if n.startswith('steering-v34-qa/')and(Path(n).suffix not in ['.java','.json','.toml','.py','.md']or Path(n).name.startswith('private_')):continue
   if not public_source_path(n):raise RuntimeError('Private source archive path: '+n)
   p=components/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(public_source_data(n,z.read(n)))
 shutil.copy2(candidate,target/'psychiatryk_roles-2.1.1.jar');shutil.copy2(NEW/'goplanska-release.json',target/'goplanska-release.json')
 for n in ['runtime-report.json','build-report.json']:public_report(QA/n,target/n)
 tests=target/'tests/steering-v34-qa'
 if tests.exists():shutil.rmtree(tests)
 tests.mkdir(parents=True)
 for folder in ['src','resources','unit-src']:
  for p in (WORK/'steering-v34-qa'/folder).rglob('*'):
   if p.is_file()and p.suffix in ['.java','.json','.toml','.py','.md']and not p.name.startswith('private_'):
    dest=tests/p.relative_to(WORK/'steering-v34-qa');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_bytes(public_source_data('steering-v34-qa/'+p.relative_to(WORK/'steering-v34-qa').as_posix(),p.read_bytes()))
 for n in ['build.py','unit.py','analyze.py','HandlingGeometryQA.java','SoulSpeedBoundQA.java','ReverseHandlingQA.java','PrepareWorld.java','README.md']:
  p=WORK/'steering-v34-qa'/n
  if p.is_file():(tests/n).write_bytes(public_source_data('steering-v34-qa/'+n,p.read_bytes()))
 compat=WORK/'scooter-steering-compat/qa/runtime-report.json'
 record=json.loads(compat.read_text())
 if record.get('success')is not True or record.get('candidate_sha256')!=sha(WORK/'release-v33/client'/MOD):raise RuntimeError('Historical v33 compatibility proof required for unchanged bridge classes')
 public_report(compat,target/'compatibility-runtime-v33-report.json')
 unit=WORK/'steering-v34-qa/unit-report.json'
 if unit.is_file():public_report(unit,tests/'unit-report.json')
 geometry=target/'tests/scooter-geometry-v34';geometry.mkdir(parents=True,exist_ok=True)
 (geometry/'continuity_qa.py').write_bytes(public_source_data('scooter-geometry-v34/continuity_qa.py',(WORK/'scooter-geometry-v34/continuity_qa.py').read_bytes()))
 public_report(WORK/'scooter-geometry-v34/continuity-report.json',geometry/'continuity-report.json')
 (geometry/'runtime_continuity_qa.py').write_bytes(public_source_data('scooter-geometry-v34/runtime_continuity_qa.py',(WORK/'scooter-geometry-v34/runtime_continuity_qa.py').read_bytes()))
 (geometry/'README.md').write_bytes(public_source_data('scooter-geometry-v34/README.md',(WORK/'scooter-geometry-v34/README.md').read_bytes()))
 public_report(WORK/'scooter-geometry-v34/runtime-continuity-report.json',geometry/'runtime-continuity-report.json')
 compiled=geometry/'compiled-qa';compiled.mkdir(parents=True,exist_ok=True)
 (compiled/'SteeringRigQA.java').write_bytes(public_source_data('scooter-geometry-v34/compiled-qa/SteeringRigQA.java',(WORK/'scooter-geometry-v34/compiled-qa/SteeringRigQA.java').read_bytes()))
 public_report(WORK/'scooter-geometry-v34/compiled-qa/report.json',compiled/'report.json')
 for name in ['reverse-handling-report.json','spear-inheritance.json']:public_report(QA/name,tests/name)
 soul=WORK/'steering-v34-qa/SoulSpeedBoundQA-report.json'
 if soul.is_file():public_report(soul,tests/'SoulSpeedBoundQA-report.json')
 deployment='Production deployment is not asserted by this preparation. runtime-report.json states the exact isolated steering tests performed.'
 receipt=target/'steering-deployment.json'
 if args.deployment_record:
  d=json.loads(args.deployment_record.read_text())
  if d.get('candidate_sha256')!=sha(candidate):raise RuntimeError('Activation receipt candidate mismatch')
  public_report(args.deployment_record,receipt);deployment='A verified production activation receipt is included in steering-deployment.json; consult its fields for the checks actually performed.'
 else:receipt.unlink(missing_ok=True)
 (target/'README.md').write_text('''# Goplanska S23 v34 — scooter direction and reversing

Control/model correction on the exact [v33 baseline](../s23-v33/README.md).
A/D use Minecraft's actual camera basis. The front steering assembly, trim and
grips share a pose tied to the rolling turn radius. The chassis stays fixed
at rest while the handlebars may turn. S brakes forward motion, then reverses
after stopping; backward motion reverses yaw while preserving wheel direction.
Existing mouse steering, Shoulder Surfing, body lean, spear Lunge and overspeed
features remain. build-report.json declares every changed class and model asset;
all unrelated unified-JAR entries and all other mods are preserved from v33.

runtime-report.json names the exact tested candidate and control/model cases.
It does not establish universal terrain/network behavior or production FPS.
The v33 downloads and per-file activation backup remain available for rollback.
Only authored source, model resources and synthetic helpers/reports are included.
The compatibility-runtime-v33-report.json remains historical evidence of the
unchanged spear bridge. The v33 direction assertion used an incorrect left/right
basis; v34's independently verified Minecraft-camera basis is authoritative.
Private account caches, worlds, decompilation, logs and server/profile exports
are filtered; Python helpers are parsed before publication.

'''+deployment+'\n')
 readme=repo/'README.md';old=readme.read_text();history=old[old.index('---'):]
 readme.write_text('''# Current release: Goplanska S23 v34

The NeoForge 1.21.1 release is in **[s23-v34](s23-v34/README.md)**.
Previous release: [s23-v33](s23-v33/README.md). The project below is the
historical Forge 1.20.1 edition.

'''+history)
 audit_public_tree(components)
 audit_public_tree(tests,prefix='steering-v34-qa/')
 audit_public_tree(geometry,prefix='scooter-geometry-v34/')
 print('Prepared public v34 authored source and scrubbed QA; no Git commit or push.')
if __name__=='__main__':main()
