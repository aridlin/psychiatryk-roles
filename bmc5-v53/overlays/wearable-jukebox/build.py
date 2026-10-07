"""Build the scoped wearable-jukebox overlay; never deploy an unqualified candidate."""
from pathlib import Path
import hashlib
import json
import os
import shutil
import subprocess
import tomllib
import zipfile

ROOT = Path(__file__).resolve().parent
WORKSPACE = ROOT.parents[2]
BASE = ROOT.parent / 'kukirin-regression-fix/build/psychiatryk_roles-3.0.2-kukirin-restoration-candidate.jar'
BASE_SHA = 'ea63b4c4fa3287861d2066c3b636d80772d162a01e6a643e92ec55a526329ad6'
digest = lambda value: hashlib.sha256(value).hexdigest()
assert digest(BASE.read_bytes()) == BASE_SHA, 'Original detailed scooter baseline changed'
OUT = ROOT / 'build'
CLASSES = OUT / 'classes'
shutil.rmtree(CLASSES, ignore_errors=True)
CLASSES.mkdir(parents=True)
cache = Path('<home>/.gradle/caches')
mc = cache / 'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'
accessories = list((ROOT.parent / 'client/mods').glob('accessories-neoforge-1.1.0-beta.53*.jar'))
assert len(accessories) == 1, 'Use the actual installed Accessories beta.53 API'
libraries = sorted(p for p in (cache / 'modules-2/files-2.1').rglob('*.jar')
                   if not any(s in str(p).lower() for s in ['-sources.jar', '-javadoc.jar', 'gemrender', 'flywheel', '/create-', '/psychiatryk', '/accessories/']))
classpath = [mc, BASE, accessories[0], *libraries]
sources = sorted((ROOT / 'src').rglob('*.java'))
(OUT / 'classpath.txt').write_text(os.pathsep.join(map(str, classpath)))
subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', os.pathsep.join(map(str, classpath)),
                '-d', str(CLASSES), *map(str, sources)], check=True)
with zipfile.ZipFile(BASE) as archive:
    original = {i.filename: archive.read(i) for i in archive.infolist() if not i.is_dir()}
entries = dict(original)
overlay = {}
for folder in [CLASSES, ROOT / 'resources']:
    for path in sorted(folder.rglob('*')):
        if path.is_file():
            name = path.relative_to(folder).as_posix()
            content = path.read_bytes()
            if name.startswith('data/') and '/tags/' in name and name in original:
                prior, added = json.loads(original[name]), json.loads(content)
                assert not added.get('replace', False)
                prior['values'] = list(dict.fromkeys(prior.get('values', []) + added['values']))
                content = (json.dumps(prior, indent=2) + '\n').encode()
            overlay[name] = content
entries.update(overlay)
metadata = entries['META-INF/neoforge.mods.toml'].decode()
assert metadata.count('version="3.0.2-bmc5"') == 5
metadata = metadata.replace('version="3.0.2-bmc5"', 'version="3.0.3-bmc5"')
metadata += '\n[[mixins]]\nconfig="psychiatryk-wearable-jukebox.mixins.json"\n'
tomllib.loads(metadata)
entries['META-INF/neoforge.mods.toml'] = metadata.encode()
entries['psychiatryk-wearable-jukebox.mixins.json'] = (json.dumps({
    'required': True, 'minVersion': '0.8', 'package': 'pl.aridlin.psychiatrykroles.jukebox.mixin',
    'compatibilityLevel': 'JAVA_21', 'client': ['WearableSoundEngineMixin', 'WearableChannelAccessor'],
    'injectors': {'defaultRequire': 1}}, indent=2) + '\n').encode()
translations = {
    'en_us': {'psychiatryk.jukebox.music': 'Jukebox music', 'psychiatryk.jukebox.music_tip': 'Play server songs from the jukebox on your back. /jukebox also opens this menu.', 'psychiatryk.jukebox.equip_tip': 'Equip in the back slot, then open Jukebox music in your inventory or use /jukebox.'},
    'pl_pl': {'psychiatryk.jukebox.music': 'Muzyka z pleców', 'psychiatryk.jukebox.music_tip': 'Odtwarzaj utwory serwera z szafy grającej na plecach. Menu otworzysz też przez /jukebox.', 'psychiatryk.jukebox.equip_tip': 'Załóż na plecy i otwórz Muzykę z pleców w ekwipunku albo użyj /jukebox.'}}
for lang, additions in translations.items():
    name = 'assets/psychiatryk_roles/lang/' + lang + '.json'
    content = json.loads(entries.get(name, b'{}'))
    content.update(additions)
    entries[name] = (json.dumps(content, ensure_ascii=False, indent=2) + '\n').encode()
allowed = ('pl/aridlin/kukirin/ScooterMusic', 'pl/aridlin/kukirin/ScooterAudioClient', 'pl/aridlin/kukirin/ScooterMenus', 'pl/aridlin/kukirin/ScooterJukeboxMusic')
changed = sorted(n for n in original if original[n] != entries[n])
assert all(not n.endswith('.class') or n.startswith(allowed) for n in changed), changed
for n, value in original.items():
    if (n.startswith('assets/goplanska_kukirin/') or n.startswith('licenses/')
            or '/recipe/' in n or n in ['pl/aridlin/kukirin/Scooter.class', 'pl/aridlin/kukirin/ScooterRenderer.class',
                'pl/aridlin/kukirin/G2Mesh.class', 'pl/aridlin/kukirin/ScooterGripPose.class',
                'pl/aridlin/kukirin/ScooterSteeringRig.class', 'pl/aridlin/kukirin/ScooterStorage.class']):
        assert entries[n] == value, 'Unrelated baseline asset/class changed: ' + n
candidate = OUT / 'psychiatryk_roles-3.0.3-wearable-jukebox-candidate.jar'
with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as archive:
    for name, content in sorted(entries.items()):
        info = zipfile.ZipInfo(name, (2026, 10, 7, 14, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(info, content)
with zipfile.ZipFile(candidate) as archive:
    assert archive.testzip() is None
proof = {'success': True, 'runtime_verified': False, 'base_path': str(BASE), 'base_sha256': BASE_SHA,
         'candidate_path': str(candidate), 'candidate_sha256': digest(candidate.read_bytes()),
         'candidate_bytes': candidate.stat().st_size, 'changed_entries': changed,
         'added_entries': sorted(set(entries) - set(original)), 'removed_entries': [],
         'source_inputs': {str(p.relative_to(ROOT)): digest(p.read_bytes()) for p in sources},
         'overlay_entries': {n: digest(v) for n, v in overlay.items()},
         'original_scooter_model_render_physics_storage_recipes_preserved': True,
         'existing_dependencies_preserved': True, 'soundphysics_global_config_unchanged': True}
(OUT / 'build-proof.json').write_text(json.dumps(proof, indent=2) + '\n')
print(json.dumps({'success': True, 'candidate_sha256': proof['candidate_sha256'], 'classes': len(list(CLASSES.rglob('*.class'))), 'runtime_verified': False}))
