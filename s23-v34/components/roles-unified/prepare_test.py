from pathlib import Path
import shutil,subprocess,zipfile
r=Path(__file__).resolve().parent;w=r.parent;test=r/'server-test';test.mkdir(exist_ok=True)
for folder in ['config']:shutil.copytree(w/'release-v16.2/server'/folder,test/folder,dirs_exist_ok=True)
(test/'mods').mkdir(exist_ok=True)
for p in (w/'release-v16.2/server/mods').glob('*.jar'):
 if p.name.startswith(('goplanska-','psychiatryk_roles','automodpack-')):continue
 shutil.copy2(p,test/'mods'/p.name)
for p in (test/'mods').glob('psychiatryk_roles-*.jar'):p.unlink()
shutil.copy2(r/'psychiatryk_roles-2.1.1.jar',test/'mods/psychiatryk_roles-2.1.1.jar')
if not (test/'libraries').exists():(test/'libraries').symlink_to((w/'restore-test/libraries').resolve(),target_is_directory=True)
shutil.copy2(w/'physics-baseline/eula.txt',test/'eula.txt')
(test/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25617\nonline-mode=true\nlevel-name=world\nlevel-type=minecraft:normal\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
cp=[r/'accessories-api.jar',r/'psychiatryk_roles-2.1.1.jar',(Path.home() / '.gradle/caches/neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'),*test.joinpath('mods').glob('*.jar'),*(Path.home() / '.gradle/caches/modules-2/files-2.1').rglob('*.jar')]
cls=r/'test-classes';cls.mkdir(exist_ok=True);subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(cls),str(r/'UnifiedCheck.java')],check=True)
with zipfile.ZipFile(test/'mods/goplanska-unified-check.jar','w') as z:
 z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="goplanska_unified_check"\nversion="1.0"\ndisplayName="Local Unified Check"\n')
 for p in cls.rglob('*.class'):z.write(p,p.relative_to(cls))
print('Prepared isolated server test')
