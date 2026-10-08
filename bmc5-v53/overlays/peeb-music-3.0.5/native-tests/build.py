"""Build only the test observer, against an exact frozen merged addon."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
BASE_WORK = ROOT.parents[2]
parser = argparse.ArgumentParser()
parser.add_argument('--candidate', type=Path, required=True)
parser.add_argument('--sha256', required=True)
parser.add_argument('--classpath-file', type=Path, required=True)
args = parser.parse_args()
candidate = args.candidate.resolve()
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
assert digest(candidate) == args.sha256, 'Exact merged candidate SHA gate failed'
classpath_file = args.classpath_file.resolve()
classpath = str(candidate) + ':' + classpath_file.read_text().strip()
classes = ROOT / 'build/classes'
shutil.rmtree(classes, ignore_errors=True)
classes.mkdir(parents=True)
compiled = subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', classpath,
                           '-d', str(classes), *map(str, (ROOT / 'src').rglob('*.java'))])
if compiled.returncode:
    raise SystemExit('QA observer compilation failed; diagnostics above.')
helper = ROOT / 'build/peeb-qa.jar'
metadata = '''modLoader="javafml"
loaderVersion="[4,)"
license="MIT"
[[mods]]
modId="psychiatryk_peeb_qa"
version="1"
displayName="Disposable Peeb rope and dither QA"
[[dependencies.psychiatryk_peeb_qa]]
modId="psychiatryk_peeb"
type="required"
versionRange="[3.0.5,)"
side="CLIENT"
'''
with zipfile.ZipFile(helper, 'w', zipfile.ZIP_DEFLATED) as archive:
    archive.writestr('META-INF/neoforge.mods.toml', metadata)
    archive.writestr('META-INF/MANIFEST.MF', 'Manifest-Version: 1.0\nMixinConfigs: peebqa.mixins.json\n\n')
    archive.writestr('peebqa.mixins.json', json.dumps({'required':True,'minVersion':'0.8','package':'pl.aridlin.peebqa.mixin','compatibilityLevel':'JAVA_21','client':['ShaderObserveMixin','RopeObserveMixin'],'injectors':{'defaultRequire':1}}))
    for path in sorted(classes.rglob('*.class')):
        archive.write(path, path.relative_to(classes))
proof = {
    'candidate': str(candidate), 'candidate_sha256': args.sha256,
    'helper_sha256': digest(helper), 'java_release': 21,
    'source_sha256': {str(path.relative_to(ROOT)): digest(path)
                      for path in sorted((ROOT / 'src').rglob('*.java'))},
    'runtime_verified': False,
}
(ROOT / 'build/helper-proof.json').write_text(json.dumps(proof, indent=2) + '\n')
(ROOT / 'build/classpath.txt').write_text(classpath)
print(json.dumps({'candidate_sha256': args.sha256, 'helper_sha256': proof['helper_sha256'],
                  'helper': str(helper), 'runtime_verified': False}))
