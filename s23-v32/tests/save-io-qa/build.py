from pathlib import Path
import json
import shutil
import subprocess
import zipfile

r = Path(__file__).resolve().parent
w = r.parent
c = Path.home() / '.gradle/caches'
cp = [w/'release-v31/server/mods/psychiatryk_roles-2.1.1.jar',
      c/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar',
      *w.joinpath('release-v31/server/mods').glob('*.jar'),
      *c.joinpath('modules-2/files-2.1').rglob('*.jar')]
cp = [p for p in cp if not any(x in str(p) for x in ('-sources.jar', '-javadoc.jar', '/flywheel-fabric-'))]
(r/'classes').mkdir(exist_ok=True)
subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', ':'.join(map(str, cp)),
                '-d', str(r/'classes'), *map(str, r.joinpath('src').rglob('*.java'))], check=True)
with zipfile.ZipFile(r/'save-io-qa.jar', 'w', zipfile.ZIP_DEFLATED) as z:
    z.writestr('META-INF/neoforge.mods.toml',
                'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n'
                '[[mods]]\nmodId="goplanska_save_qa"\nversion="1"\ndisplayName="Isolated save I/O QA"\n'
                '[[mixins]]\nconfig="goplanska-save-qa.mixins.json"\n')
    z.writestr('goplanska-save-qa.mixins.json', json.dumps({
        'required': True, 'minVersion': '0.8', 'compatibilityLevel': 'JAVA_21',
        'package': 'pl.aridlin.saveqa.mixin',
        'mixins': ['PlayerDiskFaultMixin', 'SableDiskFaultMixin'],
        'injectors': {'defaultRequire': 1}}))
    for p in r.joinpath('classes').rglob('*.class'):
        z.write(p, p.relative_to(r/'classes'))
shutil.copy2(r/'save-io-qa.jar', r/'server/mods/save-io-qa.jar')
print('Built test-only storage latency and failure injections')
