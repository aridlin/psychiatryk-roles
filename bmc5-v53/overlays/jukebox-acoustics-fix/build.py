"""One client-mixin fix on the exact qualified 3.0.3 baseline."""
from pathlib import Path
import hashlib, json, os, subprocess, tomllib, zipfile

ROOT = Path(__file__).resolve().parent
BASE = ROOT.parent / 'wearable-jukebox/build/psychiatryk_roles-3.0.3-wearable-jukebox-candidate.jar'
BASE_SHA = 'daa891b2f6e7c50fa9663efd13fc66e43bb9c1b6682c66833f0a30d4d1ca51a4'
sha = lambda data: hashlib.sha256(data).hexdigest()
assert sha(BASE.read_bytes()) == BASE_SHA
out = ROOT / 'build'; classes = out / 'classes'; classes.mkdir(parents=True, exist_ok=True)
classpath = (ROOT.parent / 'wearable-jukebox/build/classpath.txt').read_text().split(os.pathsep)
classpath = [str(BASE), *classpath]
source = ROOT / 'src/pl/aridlin/psychiatrykroles/jukebox/mixin/WearableSoundEngineMixin.java'
subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', os.pathsep.join(classpath), '-d', str(classes), str(source)], check=True)
name = 'pl/aridlin/psychiatrykroles/jukebox/mixin/WearableSoundEngineMixin.class'
with zipfile.ZipFile(BASE) as archive: original = {i.filename: archive.read(i) for i in archive.infolist() if not i.is_dir()}
entries = dict(original); entries[name] = (classes / name).read_bytes()
metadata = entries['META-INF/neoforge.mods.toml'].decode()
assert metadata.count('version="3.0.3-bmc5"') == 5
metadata = metadata.replace('version="3.0.3-bmc5"', 'version="3.0.4-bmc5"'); tomllib.loads(metadata)
entries['META-INF/neoforge.mods.toml'] = metadata.encode()
changed = sorted(n for n in original if original[n] != entries[n])
assert changed == ['META-INF/neoforge.mods.toml', name]
candidate = out / 'psychiatryk_roles-3.0.4-jukebox-acoustics-candidate.jar'
with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, data in sorted(entries.items()):
        info = zipfile.ZipInfo(name, (2026, 10, 7, 16, 30, 0)); info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, data)
with zipfile.ZipFile(candidate) as archive: assert archive.testzip() is None
proof = {'success': True, 'runtime_verified': False, 'base_sha256': BASE_SHA, 'candidate_sha256': sha(candidate.read_bytes()),
    'candidate_path': str(candidate), 'changed_entries': changed, 'added_entries': [], 'removed_entries': [],
    'common_server_classes_and_networking_byte_identical': True, 'all_other_client_classes_resources_recipes_assets_identical': True,
    'soundphysics_global_config_unchanged': True, 'source_sha256': sha(source.read_bytes())}
(out / 'build-proof.json').write_text(json.dumps(proof, indent=2) + '\n'); print(json.dumps(proof))
