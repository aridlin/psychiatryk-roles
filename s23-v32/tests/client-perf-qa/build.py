from pathlib import Path
import subprocess,zipfile,json
w=Path('work').resolve();r=w/'client-perf-qa';cache=(Path.home() / '.gradle/caches')
cp=[Path('outputs/save-io-v32/psychiatryk_roles.jar').resolve(),cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar',*cache.joinpath('modules-2/files-2.1').rglob('*.jar'),*w.joinpath('release-v31/client/mods').glob('*.jar')]
cp=[p for p in cp if not any(s in str(p) for s in ['-sources.jar','-javadoc.jar','/flywheel-fabric-','release-v31/client/mods/psychiatryk_roles'])]
(r/'classes').mkdir(parents=True,exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(r/'classes'),*map(str,r.joinpath('src').rglob('*.java'))],check=True)
with zipfile.ZipFile(r/'goplanska-client-perf-qa.jar','w') as z:
 z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="goplanska_client_perf_qa"\nversion="1.0"\ndisplayName="Isolated client performance QA"\n[[mixins]]\nconfig="goplanska-client-perf-qa.mixins.json"\n')
 z.writestr('goplanska-client-perf-qa.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'pl.aridlin.clientqa.mixin','compatibilityLevel':'JAVA_21','client':['CaptureProbeMixin'],'injectors':{'defaultRequire':1}}))
 for p in (r/'classes').rglob('*.class'):z.write(p,p.relative_to(r/'classes'))
print('Built test-only client helper')
