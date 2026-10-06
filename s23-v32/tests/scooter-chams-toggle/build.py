"""Compile the three changed client sources without rebuilding the unified mod."""
from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
WORK = HERE.parent
CACHE = Path.home() / '.gradle/caches'
classes = HERE / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir(parents=True)
baseline = WORK / 'release-v31/client/mods/psychiatryk_roles-2.1.1.jar'
# The same compiler/classpath infrastructure as party-markers/build.py, with
# the actual v31 unified JAR first so unchanged helpers use shipped signatures.
cp = [baseline,
      CACHE / 'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar',
      *sorted((WORK / 'release-v31/client/mods').glob('*.jar')),
      WORK / 'roles-unified/accessories-api.jar',
      *sorted((CACHE / 'modules-2/files-2.1').rglob('*.jar'))]
cp = list(dict.fromkeys(path for path in cp if not any(value in str(path) for value in ['-sources.jar', '-javadoc.jar', '/flywheel-fabric-'])))
classpath = ':'.join(map(str, cp))
(HERE / 'classpath.txt').write_text(classpath)
sources = [WORK / 'party-markers/src/pl/aridlin/partymarkers' / name for name in ['ChamsCategories.java', 'HalftoneChams.java', 'PartyMarkers.java']]
subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', classpath, '-d', str(classes), *map(str, sources)], check=True)
with zipfile.ZipFile(baseline) as jar:
    changed = [str(path.relative_to(classes)) for path in sorted(classes.rglob('*.class')) if jar.read(str(path.relative_to(classes))) != path.read_bytes()]
result = {'baseline_sha256': hashlib.sha256(baseline.read_bytes()).hexdigest(),
          'compiled_classes': [str(path.relative_to(classes)) for path in sorted(classes.rglob('*.class'))],
          'changed_existing_classes': changed,
          'class_sha256': {str(path.relative_to(classes)): hashlib.sha256(path.read_bytes()).hexdigest() for path in sorted(classes.rglob('*.class'))}}
(HERE / 'build-report.json').write_text(json.dumps(result, indent=2))
print(json.dumps(result, indent=2))
