from pathlib import Path
import argparse,subprocess,os,shutil,zipfile,hashlib,json
p=argparse.ArgumentParser();p.add_argument('--base',type=Path,required=True);p.add_argument('--sdk',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args()
EXPECTED_BASE_SHA256='a513a1e338f0651648dfa32ecf9f3049daab312c2304183083337ecbc43572de'
EXPECTED_OUTPUT_SHA256='eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12'
base_hash=hashlib.sha256(a.base.read_bytes()).hexdigest()
if base_hash!=EXPECTED_BASE_SHA256:raise SystemExit('Expected the pinned 3.0.9 baseline JAR, got '+base_hash)
root=Path(__file__).resolve().parent;a.output.mkdir(parents=True,exist_ok=True);classes=a.output/'classes';shutil.rmtree(classes,ignore_errors=True)
cp=str(a.base.resolve())+':'+a.sdk.read_text().strip();(a.output/'classpath.txt').write_text(cp)
r=subprocess.run(['/usr/lib/jvm/java-21-openjdk/bin/javac','-J-Xmx384m','--release','21','-proc:none','-cp',cp,'-d',str(classes),*map(str,(root/'src').rglob('*.java'))],capture_output=True,text=True);(a.output/'compile.log').write_text(r.stdout+r.stderr)
if r.returncode:print(r.stderr);raise SystemExit(r.returncode)
with zipfile.ZipFile(a.base) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
for folder in [classes,root/'resources']:
 for f in folder.rglob('*'):
  if f.is_file():entries[f.relative_to(folder).as_posix()]=f.read_bytes()
meta=entries['META-INF/neoforge.mods.toml'].decode()
if 'version="3.0.9-bmc5"' not in meta or 'modId="psychiatryk_runtime"' in meta:
 raise SystemExit('Baseline metadata is not the expected pre-runtime 3.0.9 release')
meta=meta.replace('version="3.0.9-bmc5"','version="3.0.10-bmc5"')
meta+='''\n[[mods]]
modId="psychiatryk_runtime"
version="1.0.0"
displayName="Psychiatryk Stable Runtime"
description="Versioned optional menus and server-controlled item variants."
[[mixins]]
config="psychiatryk-runtime.mixins.json"
''';entries['META-INF/neoforge.mods.toml']=meta.encode()
jar=a.output/'psychiatryk_roles-3.0.10-bmc5.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for name,data in sorted(entries.items()):
  entry=zipfile.ZipInfo(name,(2026,10,9,1,0,0));entry.compress_type=zipfile.ZIP_DEFLATED;z.writestr(entry,data)
built_hash=hashlib.sha256(jar.read_bytes()).hexdigest()
if built_hash!=EXPECTED_OUTPUT_SHA256:raise SystemExit('Canonical JAR differs from native-tested item candidate: '+built_hash)
print(json.dumps({'jar':str(jar),'sha256':built_hash,'base_sha256':base_hash}))
