"""Build the narrow Point Blank stencil overlay; requires a NeoForge dev classpath.

No Point Blank or Veil source is redistributed. Supply --classpath-file containing
Minecraft 1.21.1, NeoForge, Mixin, LWJGL and installed Point Blank 2.2.0 dependencies.
The resulting JAR is an overlay for the existing unified mod, not a standalone mod.
"""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--classpath-file', type=Path, required=True)
args = parser.parse_args()
classpath = args.classpath_file.read_text().strip()
classes = HERE / 'classes'
if classes.exists():
    shutil.rmtree(classes)
classes.mkdir()
sources = sorted((HERE / 'src').rglob('*.java'))
subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', classpath,
                '-d', str(classes), *map(str, sources)], check=True)
entries = sorted(classes.rglob('*.class'))
if len(entries) != 2:
    raise RuntimeError('Expected exactly two compatibility classes')
resource = HERE / 'resources/psychiatryk-pointblank-render.mixins.json'
jar = HERE / 'pointblank-stencil-component.jar'
with zipfile.ZipFile(jar, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in entries:
        info = zipfile.ZipInfo(str(path.relative_to(classes)), (2026, 10, 6, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, path.read_bytes())
    info = zipfile.ZipInfo(resource.name, (2026, 10, 6, 0, 0, 0))
    info.compress_type = zipfile.ZIP_DEFLATED
    archive.writestr(info, resource.read_bytes())
sha = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
proof = {
    'classes': {str(path.relative_to(classes)): sha(path) for path in entries},
    'resource': resource.name,
    'resourceSha': sha(resource),
    'component_jar': str(jar.relative_to(HERE.parent.parent)),
    'component_jar_sha256': sha(jar),
    'runtime_verified': False,
}
(HERE / 'build-proof.json').write_text(json.dumps(proof, indent=2) + '\n')
print(json.dumps(proof, indent=2))
