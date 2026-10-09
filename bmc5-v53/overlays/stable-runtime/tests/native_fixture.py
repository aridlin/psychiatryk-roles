from pathlib import Path
import subprocess,zipfile,sys
root=Path(__file__).resolve().parents[1]
workspace=root.parents[3]
staging=workspace/'migration/private_release/workstations-20261008'
# The fixture uses actual compiled runtime and configuration classes, with no full-pack memory load.
sdk=(staging/'sdk-clean.txt').read_text().strip()
out=root/'build/native';out.mkdir(parents=True,exist_ok=True)
jar=root/'build/psychiatryk_roles-3.0.10-bmc5.jar'
subprocess.run(['/usr/lib/jvm/java-21-openjdk/bin/javac','-J-Xmx256m','-proc:none','-cp',str(jar)+':'+sdk,'-d',str(out),str(root/'tests/NativeFixture.java'),str(root/'tests/FixtureProvider.java')],check=True)
run=staging/'runtime-smoke';(run/'mods').mkdir(parents=True,exist_ok=True)
lib=workspace/'migration/private_release/old-overworld-smoke/libraries'
if not (run/'libraries').exists():(run/'libraries').symlink_to(lib,True)
(run/'eula.txt').write_text('eula=true\n')
(run/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25590\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nlevel-type=minecraft:flat\ngenerate-structures=false\nmax-tick-time=90000\n')
meta='''modLoader="javafml"
loaderVersion="[4,)"
license="GPL-3.0"
[[mods]]
modId="psychiatryk_runtime"
version="1.0.0"
[[mods]]
modId="runtime_fixture"
version="1.0.0"
[[mixins]]
config="psychiatryk-runtime.mixins.json"
'''
with zipfile.ZipFile(jar) as source,zipfile.ZipFile(run/'mods/runtime.jar','w') as target:
 for name in source.namelist():
  if name.startswith('pl/aridlin/psychiatrykroles/runtime/') or name.startswith('pl/aridlin/kukirin/ScooterTuning') or name.startswith('pl/aridlin/psychiatrykroles/peeb/PeebConfig') or name.startswith('psychiatryk-runtime'):target.writestr(name,source.read(name))
 target.writestr('META-INF/neoforge.mods.toml',meta)
 target.writestr('META-INF/services/pl.aridlin.psychiatrykroles.runtime.ServerFeature','pl.aridlin.psychiatrykroles.runtime.server.FixtureProvider\n')
 for cls in out.rglob('*.class'):target.write(cls,cls.relative_to(out))
print(run)
