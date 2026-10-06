from pathlib import Path
import subprocess,zipfile,json
r=Path(__file__).resolve().parent;w=r.parent;c=Path.home()/'.gradle/caches'
cp=[w/'pointblank-v32/veil.jar',w.parent/'outputs/save-io-v32/psychiatryk_roles.jar',c/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar',*w.joinpath('release-v31/client/mods').glob('*.jar'),*c.joinpath('modules-2/files-2.1').rglob('*.jar')]
cp=[p for p in cp if not any(x in str(p) for x in ('-sources.jar','-javadoc.jar','/flywheel-fabric-','release-v31/client/mods/psychiatryk_roles'))]
(r/'classes').mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(r/'classes'),*map(str,r.joinpath('src').rglob('*.java'))],check=True)
with zipfile.ZipFile(r/'pointblank-qa.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('qa-pointblank-probes.mixins.json',json.dumps({'required':True,'minVersion':'0.8','package':'pl.aridlin.pbqa.mixin','compatibilityLevel':'JAVA_21','client':['ClientSystemProbeMixin','GameRendererProbeMixin','PortalPrepareProbeMixin'],'injectors':{'defaultRequire':1}}))
 z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mixins]]\nconfig="qa-pointblank-probes.mixins.json"\n[[mods]]\nmodId="goplanska_pointblank_qa"\nversion="1"\ndisplayName="Isolated scoped-weapon rendering observer"\n')
 for p in (r/'classes').rglob('*.class'):z.write(p,p.relative_to(r/'classes'))
print('Built read-only rendering observer')
