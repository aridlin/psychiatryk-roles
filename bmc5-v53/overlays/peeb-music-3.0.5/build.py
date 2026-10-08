#!/usr/bin/env python3
"""Exact checkpoint overlay; source-check never packages recovered baseline classes."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parent
BMC=ROOT.parents[1]
JAVA=BMC/'src/peeb-music-3.0.5/main/java'
RESOURCES=BMC/'src/peeb-music-3.0.5/main/resources'
CHECKPOINT_SHA='4173799b5497e2f7c3183b96a3550adcc7a3893968ff3311265d6c2d06e49a29'
EXPECTED_SHA=json.loads((BMC/'patch-evidence/peeb-rope-dither-3.0.5-build.json').read_text())['candidate_sha256']
NAMES=['pl/aridlin/psychiatrykroles/peeb/PeebGrapple.java',
 'pl/aridlin/psychiatrykroles/peeb/client/PeebClient.java',
 'pl/aridlin/psychiatrykroles/peeb/client/PeebRenderer.java',
 'pl/aridlin/psychiatrykroles/peeb/client/PeebStyle.java',
 'pl/aridlin/psychiatrykroles/peeb/client/PeebHud.java',
 'pl/aridlin/psychiatrykroles/peeb/client/PeebRopeGeometry.java']
SHADERS=['assets/psychiatryk_peeb/shaders/core/peeb_ps1.'+n for n in ['json','vsh','fsh']]
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--checkpoint',type=Path,required=True,help='Externally supplied exact 4173 JAR; never downloaded by this tool')
parser.add_argument('--classpath-file',type=Path,required=True,help='Named Minecraft 1.21.1 / NeoForge 21.1.250 classpath, separated with os.pathsep')
parser.add_argument('--output-dir',type=Path,default=ROOT/'build')
parser.add_argument('--javac',default='javac')
parser.add_argument('--source-check',action='store_true',help='Compile all 58 recovered/edited sources solely to verify compilability; no runtime JAR')
args=parser.parse_args()
checkpoint=args.checkpoint.resolve();assert sha(checkpoint)==CHECKPOINT_SHA,'Wrong checkpoint fingerprint'
out=args.output_dir.resolve();out.mkdir(parents=True,exist_ok=True)
classes=out/('source-check-classes' if args.source_check else 'classes')
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
cp_items=args.classpath_file.read_text().strip().split(os.pathsep)
assert all(p and Path(p).exists() for p in cp_items),'Missing classpath input'
classpath=os.pathsep.join([str(checkpoint),*cp_items])
sources=sorted(JAVA.rglob('*.java')) if args.source_check else [JAVA/n for n in NAMES]
assert len(sources)==(58 if args.source_check else 6)
assert all(p.is_file() and not p.is_symlink() for p in sources)
provenance=json.loads((BMC/'patch-evidence/peeb-music-3.0.5-source-provenance.json').read_text())
for p in sources:assert sha(p)==provenance['source_sha256'][p.relative_to(BMC).as_posix()],'Source fingerprint drift: '+str(p.relative_to(BMC))
subprocess.run([args.javac,'--release','21','-proc:none','-cp',classpath,'-d',str(classes),*map(str,sources)],check=True)
if args.source_check:
 report={'success':True,'source_files':len(sources),'compiled_class_files':len(list(classes.rglob('*.class'))),'checkpoint_sha256':CHECKPOINT_SHA,
  'runtime_packaging':False,'reconstructed_sources_not_original':True,'byte_identity_claimed':False}
 (out/'source-check-proof.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report));raise SystemExit(0)
with zipfile.ZipFile(checkpoint) as z:original={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
entries=dict(original)
for p in sorted(classes.rglob('*.class')):entries[p.relative_to(classes).as_posix()]=p.read_bytes()
for n in SHADERS:
 p=RESOURCES/n;assert sha(p)==provenance['resource_sha256'][p.relative_to(BMC).as_posix()]
 assert n in original;entries[n]=p.read_bytes()
changed=sorted(n for n in original if original[n]!=entries[n]);added=sorted(set(entries)-set(original))
allowed_prefixes=tuple(n[:-5] for n in NAMES)
assert all(n.startswith(allowed_prefixes) or n in SHADERS for n in changed),changed
assert set(original)<=set(entries),'Existing entries removed'
assert added==['pl/aridlin/psychiatrykroles/peeb/client/PeebRopeGeometry$Curve.class','pl/aridlin/psychiatrykroles/peeb/client/PeebRopeGeometry.class']
unchanged_prefixes=('pl/aridlin/kukirin/','pl/aridlin/portablechams/','pl/aridlin/psychiatrykroles/jukebox/','assets/goplanska_kukirin/',
 'pl/aridlin/psychiatrykroles/peeb/client/PeebBindings','pl/aridlin/psychiatrykroles/peeb/client/PeebClientBootstrap')
assert all(entries[n]==original[n] for n in original if n.startswith(unchanged_prefixes))
candidate=out/'psychiatryk_roles-3.0.5-peeb-rope-dither.jar'
with zipfile.ZipFile(candidate,'w',zipfile.ZIP_DEFLATED) as z:
 for n,data in sorted(entries.items()):
  i=zipfile.ZipInfo(n,(2026,10,8,7,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,data)
with zipfile.ZipFile(candidate) as z:assert z.testzip() is None
assert sha(candidate)==EXPECTED_SHA,'Output differs from frozen candidate; do not silently publish it'
report={'success':True,'checkpoint_sha256':CHECKPOINT_SHA,'candidate_sha256':sha(candidate),'changed_entries':changed,'added_entries':added,
 'unchanged_other_entries':len(original)-len(changed),'runtime_verified':False,'no_entries_removed':True,
 'music_scooter_chams_and_bindings_byte_identical':True,'full_recovered_source_repackaged':False}
(out/'build-proof.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
