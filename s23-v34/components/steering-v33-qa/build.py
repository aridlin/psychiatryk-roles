"""Build only the QA observer; no production implementation is compiled or changed."""
from pathlib import Path
import argparse, hashlib, json, shutil, subprocess, zipfile

qa=Path(__file__).resolve().parent
root=qa.parent.parent
p=argparse.ArgumentParser()
p.add_argument('--candidate',type=Path,required=True)
a=p.parse_args()
candidate=a.candidate.resolve()
cache=Path.home()/'.gradle/caches'
cp=[candidate,cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar',*root.joinpath('work/release-v32/client/mods').glob('*.jar'),*cache.joinpath('modules-2/files-2.1').rglob('*.jar')]
cp=[x for x in cp if not any(s in str(x) for s in ('-sources.jar','-javadoc.jar','/flywheel-fabric-','release-v32/client/mods/psychiatryk_roles'))]
classes=qa/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
compiled=subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(classes),*map(str,qa.joinpath('src').rglob('*.java'))])
if compiled.returncode:raise SystemExit(compiled.returncode)
with zipfile.ZipFile(qa/'steering-qa.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('qa-steering-observers.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'pl.aridlin.steeringqa.mixin','compatibilityLevel':'JAVA_21','client':['ScooterDrawObserver','DirectDrawObserver'],'injectors':{'defaultRequire':1}}))
 z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mixins]]\nconfig="qa-steering-observers.mixins.json"\n[[mods]]\nmodId="goplanska_steering_qa"\nversion="1"\ndisplayName="Disposable scooter steering geometry observer"\n')
 for x in classes.rglob('*.class'):z.write(x,x.relative_to(classes))
(qa/'build-proof.json').write_text(json.dumps({'candidate':str(candidate),'candidate_sha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'helper_sha256':hashlib.sha256((qa/'steering-qa.jar').read_bytes()).hexdigest()},indent=2))
print('Built steering observer only')
