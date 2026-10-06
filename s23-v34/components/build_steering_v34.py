"""Build the focused steering/rig correction on the frozen deployed v33 JAR."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile

ROOT=Path(__file__).resolve().parent.parent
WORK=ROOT/'work'; OUT=ROOT/'outputs/steering-v34'; OUT.mkdir(exist_ok=True)
BASE=WORK/'release-v33/client/mods/psychiatryk_roles-2.1.1.jar'
EXPECTED_BASE='fb6794deda990eb9df51e409728b308bfc5376d0fd5a01b1b59c82d81a68418b'
digest=lambda data:hashlib.sha256(data).hexdigest()
assert digest(BASE.read_bytes())==EXPECTED_BASE
names=['Scooter','ScooterHandling','ScooterGripPose','ScooterVisual','ScooterRenderer','G2Mesh','ScooterSteeringRig']
sources=[WORK/'kukirin/src/pl/aridlin/kukirin'/(name+'.java')for name in names]
cache=Path.home()/'.gradle/caches'
mc=cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'
cp=[mc,BASE,WORK/'model-render-research/GemRender/versions/1.21.1/build/libs/gemrender-0.1.6.jar',*WORK.joinpath('release-v33/client/mods').glob('*.jar'),*WORK.joinpath('kukirin/light-research').glob('*.jar'),*cache.joinpath('modules-2/files-2.1').rglob('*.jar')]
cp=[path for path in cp if not any(s in str(path)for s in ['-sources.jar','-javadoc.jar','/flywheel-fabric-'])]
classpath=':'.join(map(str,cp));(OUT/'classpath.txt').write_text(classpath)
classes=OUT/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
subprocess.run(['javac','--release','21','-proc:none','-sourcepath','','-cp',classpath,'-d',str(classes),*map(str,sources)],check=True)
with zipfile.ZipFile(BASE)as archive:original={n:archive.read(n)for n in archive.namelist()if not n.endswith('/')}
entries=original.copy();compiled={str(path.relative_to(classes)):path.read_bytes()for path in classes.rglob('*.class')}
roots=['pl/aridlin/kukirin/'+name for name in names]
assert all(any(n==root+'.class'or n.startswith(root+'$')for root in roots)for n in compiled)
entries.update(compiled)
resource_names=['assets/goplanska_kukirin/models/'+name for name in ['kukirin_g2.glb','kukirin_g2.mesh','scooter-rig.json']]
resources={name:(WORK/'kukirin/resources'/name).read_bytes()for name in resource_names}
entries.update(resources)
changed=sorted(n for n in original if entries[n]!=original[n]);added=sorted(set(entries)-set(original))
assert set(added)<={"pl/aridlin/kukirin/ScooterSteeringRig.class"} and set(changed)<=set(compiled)|set(resources)
jar=OUT/'psychiatryk_roles.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED)as archive:
 for name,data in entries.items():archive.writestr(zipfile.ZipInfo(name,(2026,10,6,0,0,0)),data,compress_type=zipfile.ZIP_DEFLATED)
proof={'candidate_sha256':digest(jar.read_bytes()),'base_sha256':EXPECTED_BASE,'changed_existing_entries':changed,'added_entries':added,'classes':{name:digest(data)for name,data in compiled.items()},'source_files':{str(path.relative_to(WORK)):digest(path.read_bytes())for path in sources},'resource_files':{name:digest(data)for name,data in resources.items()},'unrelated_v33_entries_preserved':True}
(OUT/'build-report.json').write_text(json.dumps(proof,indent=2)+'\n')
print('Built focused v34 candidate',proof['candidate_sha256'],'; changed entries:',changed)
