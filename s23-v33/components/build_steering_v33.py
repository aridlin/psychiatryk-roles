"""Compile only the authored v33 scooter overlay, preserving every other v32 entry."""
from pathlib import Path
import hashlib,json,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parent.parent;WORK=ROOT/'work';OUT=ROOT/'outputs/steering-v33';OUT.mkdir(exist_ok=True)
BASE=WORK/'release-v32/client/mods/psychiatryk_roles-2.1.1.jar';sha=lambda data:hashlib.sha256(data).hexdigest()
assert sha(BASE.read_bytes())=='ac32a763e5ed3c54dd5a5197424d24d2b2b9f73f0d610aba4aeda20832dcb25a'
source_names=['Scooter','ScooterHandling','ScooterOverspeed','ScooterFlight','ScooterControl','ScooterClient','ScooterVisual','ScooterRenderer','ScooterGripPose','ScooterShoulderPlugin','ScooterSteeringInput']
sources=[WORK/'kukirin/src/pl/aridlin/kukirin'/(n+'.java') for n in source_names]
# A later optional spear component may append only its explicitly authored helper.
for name in ['ScooterSpearLunge','mixin/ScooterSpearStabMixin','mixin/ScooterSpearEffectsMixin','mixin/ScooterSpearCompatPlugin']:
 p=WORK/'kukirin/src/pl/aridlin/kukirin'/(name+'.java')
 if p.exists():sources.append(p)
cache=(Path.home() / '.gradle/caches');mc=cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'
cp=[mc,BASE,WORK/'model-render-research/GemRender/versions/1.21.1/build/libs/gemrender-0.1.6.jar',*WORK.joinpath('release-v32/client/mods').glob('*.jar'),*WORK.joinpath('kukirin/light-research').glob('*.jar'),*cache.joinpath('modules-2/files-2.1').rglob('*.jar')]
cp=[p for p in cp if not any(x in str(p) for x in ['-sources.jar','-javadoc.jar','/flywheel-fabric-'])]
classpath=':'.join(map(str,cp));(OUT/'classpath.txt').write_text(classpath)
classes=OUT/'classes';shutil.rmtree(classes,ignore_errors=True);classes.mkdir()
result=subprocess.run(['javac','--release','21','-proc:none','-sourcepath','','-cp',classpath,'-d',str(classes),*map(str,sources)])
if result.returncode:raise SystemExit(result.returncode)
with zipfile.ZipFile(BASE) as z:original={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
entries=original.copy();compiled={str(p.relative_to(classes)):p.read_bytes() for p in classes.rglob('*.class')}
allowed_roots=['pl/aridlin/kukirin/'+n for n in source_names]+['pl/aridlin/kukirin/ScooterSpearLunge','pl/aridlin/kukirin/mixin/ScooterSpearStabMixin','pl/aridlin/kukirin/mixin/ScooterSpearEffectsMixin','pl/aridlin/kukirin/mixin/ScooterSpearCompatPlugin']
assert all(any(n==r+'.class' or n.startswith(r+'$') for r in allowed_roots) for n in compiled),compiled.keys()
entries.update(compiled)
resources=['shouldersurfing_plugin.json']
for n in ['psychiatryk-scooter-spears.mixins.json']:
 if (WORK/'kukirin/resources'/n).exists():resources.append(n)
for n in resources:entries[n]=(WORK/'kukirin/resources'/n).read_bytes()
if 'psychiatryk-scooter-spears.mixins.json' in resources:
 n='META-INF/neoforge.mods.toml';text=entries[n].decode();assert 'psychiatryk-scooter-spears.mixins.json' not in text
 entries[n]=(text+'\n[[mixins]]\nconfig="psychiatryk-scooter-spears.mixins.json"\n').encode()
changed=sorted(n for n in original if entries[n]!=original[n]);added=sorted(set(entries)-set(original))
jar=OUT/'psychiatryk_roles.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for n,b in entries.items():z.writestr(zipfile.ZipInfo(n,(2026,10,6,0,0,0)),b,compress_type=zipfile.ZIP_DEFLATED)
proof={'candidate_sha256':sha(jar.read_bytes()),'base_sha256':sha(BASE.read_bytes()),'changed_existing_entries':changed,'added_entries':added,'classes':{n:sha(b) for n,b in compiled.items()},'source_files':{str(p.relative_to(WORK)):sha(p.read_bytes()) for p in sources},'resource_files':{n:sha(entries[n]) for n in resources}}
(OUT/'build-report.json').write_text(json.dumps(proof,indent=2)+'\n');print(json.dumps(proof,indent=2))
