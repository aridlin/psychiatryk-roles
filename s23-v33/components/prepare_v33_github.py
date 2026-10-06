"""Prepare authored v33 steering source and scrubbed QA; does not commit or push."""
from pathlib import Path
import argparse,json,shutil,zipfile
from build_v33 import WORK,OUT,QA,NEW,MOD,RELEASE,sha,verify_candidate,normalize_home_paths
from prepare_v32_github import public_report

def main():
 parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--deployment-record',type=Path);args=parser.parse_args()
 candidate=verify_candidate();proof=json.loads((QA/'build-report.json').read_text());manifest=json.loads((NEW/'goplanska-release.json').read_text())
 if manifest['release']!=RELEASE or manifest['client'][MOD]['sha256']!=sha(candidate):raise RuntimeError('Prepared v33 release differs from tested candidate')
 repo=WORK/'github-publish-v19';target=repo/'s23-v33';target.mkdir(exist_ok=True);components=target/'components'
 if components.exists():shutil.rmtree(components)
 shutil.copytree(repo/'s23-v32/components',components)
 # Preserve previously published features, replacing only our current steering
 # source and adding explicitly authored v33 helpers. Never scan QA clone roots.
 selected=set(proof['source_files'])|{'kukirin/resources/'+n for n in proof['resource_files']}|{'kukirin/build.py','kukirin/BoostDecayTest.java','build_steering_v33.py','build_v33.py','prepare_v33_github.py','STEERING-v33.md','steering-v33-qa/build.py','steering-v33-qa/unit.py','steering-v33-qa/analyze.py','steering-v33-qa/HandlingGeometryQA.java','steering-v33-qa/SoulSpeedBoundQA.java','steering-v33-qa/PrepareWorld.java','steering-v33-qa/README.md','scooter-steering-compat/build.py','scooter-steering-compat/README.md','scooter-steering-compat/qa/build_setup.py','scooter-steering-compat/qa/run.py','scooter-steering-compat/qa/README.md','scooter-steering-compat/qa/runtime-report.json'}
 with zipfile.ZipFile(NEW/'client/licenses/psychiatryk-roles-unified-source.zip')as z:
  required=set(proof['source_files'])|{'kukirin/resources/'+n for n in proof['resource_files']}|{'kukirin/build.py','kukirin/BoostDecayTest.java','build_steering_v33.py','build_v33.py','prepare_v33_github.py','STEERING-v33.md'}
  if not required<=set(z.namelist()):raise RuntimeError('Source archive is missing reviewed v33 authored files')
  for n in z.namelist():
   if n not in selected and not n.startswith(('steering-v33-qa/src/','steering-v33-qa/resources/','steering-v33-qa/unit-src/','scooter-steering-compat/qa/src/')):continue
   if Path(n).is_absolute()or'..'in Path(n).parts:raise RuntimeError('Unsafe source path')
   if n.startswith('steering-v33-qa/')and(Path(n).suffix not in ['.java','.json','.toml','.py','.md']or Path(n).name.startswith('private_')):continue
   p=components/n;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes(z.read(n))
 shutil.copy2(candidate,target/'psychiatryk_roles-2.1.1.jar');shutil.copy2(NEW/'goplanska-release.json',target/'goplanska-release.json')
 for n in ['runtime-report.json','build-report.json']:public_report(QA/n,target/n)
 tests=target/'tests/steering-v33-qa'
 if tests.exists():shutil.rmtree(tests)
 tests.mkdir(parents=True)
 for folder in ['src','resources','unit-src']:
  for p in (WORK/'steering-v33-qa'/folder).rglob('*'):
   if p.is_file()and p.suffix in ['.java','.json','.toml','.py','.md']and not p.name.startswith('private_'):
    dest=tests/p.relative_to(WORK/'steering-v33-qa');dest.parent.mkdir(parents=True,exist_ok=True);dest.write_text(normalize_home_paths(p.read_text()))
 for n in ['build.py','unit.py','analyze.py','HandlingGeometryQA.java','SoulSpeedBoundQA.java','PrepareWorld.java','README.md']:
  p=WORK/'steering-v33-qa'/n
  if p.is_file():(tests/n).write_text(normalize_home_paths(p.read_text()))
 compat=WORK/'scooter-steering-compat/qa/runtime-report.json'
 record=json.loads(compat.read_text())
 if record.get('success')is not True or record.get('candidate_sha256')!=sha(candidate):raise RuntimeError('Exact compatibility runtime proof required')
 public_report(compat,target/'compatibility-runtime-report.json')
 unit=WORK/'steering-v33-qa/unit-report.json'
 if unit.is_file():public_report(unit,tests/'unit-report.json')
 soul=WORK/'steering-v33-qa/SoulSpeedBoundQA-report.json'
 if soul.is_file():public_report(soul,tests/'SoulSpeedBoundQA-report.json')
 deployment='Production deployment is not asserted by this preparation. runtime-report.json states the exact isolated steering tests performed.'
 receipt=target/'steering-deployment.json'
 if args.deployment_record:
  d=json.loads(args.deployment_record.read_text())
  if d.get('candidate_sha256')!=sha(candidate):raise RuntimeError('Activation receipt candidate mismatch')
  public_report(args.deployment_record,receipt);deployment='A verified production activation receipt is included in steering-deployment.json; consult its fields for the checks actually performed.'
 else:receipt.unlink(missing_ok=True)
 (target/'README.md').write_text('''# Goplanska S23 v33 — scooter steering

Scooter-control update on the exact [v32 baseline](../s23-v32/README.md).
Keyboard steering follows the requested direction, with curvature-bounded
low-speed turning instead of spinning in place. The previous high-speed yaw
envelope is preserved and the steering model stays within ±28 degrees.
Third-person input is corrected. Overspeed tapers linearly toward cruise over
five seconds. Direct model animation layers retain the full steering angle
and share the same hand/handlebar pose. Soul Speed raises the terrain speed cap
once instead of compounding velocity each tick. A spear with its own Lunge enchantment gives a one-shot 100 km/h
boost with a 40-tick taper. Mouse steering and the other v32 features remain. build-report.json lists the exact changed steering classes;
all unrelated existing unified-JAR entries are byte-identical to v32. No separate mod or world-save format is added or removed; the exact helper
classes and optional compatibility resources are listed in the build proof.

runtime-report.json identifies the tested candidate and actual checks. These
checks do not cover every terrain/network/input situation or establish a new
production FPS measurement. The v32 download and per-file activation backup
remain available for rollback. Existing public component source/history is
retained, with current steering source and synthetic helpers added. Private
launcher/account caches, worlds, screenshots, logs and profiles are excluded.

'''+deployment+'\n')
 readme=repo/'README.md';old=readme.read_text();history=old[old.index('---'):]
 readme.write_text('''# Current release: Goplanska S23 v33

The NeoForge 1.21.1 release is in **[s23-v33](s23-v33/README.md)**.
Previous release: [s23-v32](s23-v32/README.md). The project below is the
historical Forge 1.20.1 edition.

'''+history)
 print('Prepared public v33 authored source and scrubbed QA; no Git commit or push.')
if __name__=='__main__':main()
