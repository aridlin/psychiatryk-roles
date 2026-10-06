"""Package the tested v32 overlays on the exact v31 release. No deployment."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent.parent
WORK, OUT = ROOT / 'work', ROOT / 'outputs'
OLD, NEW = WORK / 'release-v31', WORK / 'release-v32'
RELEASE = '2026-10-06-S23-v32'
MOD = 'mods/psychiatryk_roles-2.1.1.jar'
QA = OUT / 'save-io-v32'
TOGGLE = WORK / 'scooter-chams-toggle'
TOGGLE_CHANGED = {
    'pl/aridlin/partymarkers/ChamsCategories.class',
    'pl/aridlin/partymarkers/ChamsCategories$Kind.class',
    'pl/aridlin/partymarkers/ChamsCategories$Hover.class',
    'pl/aridlin/partymarkers/ChamsCategories$Target.class',
    'pl/aridlin/partymarkers/HalftoneChams$Events.class',
    'pl/aridlin/partymarkers/PartyMarkers.class',
}
CLIENT_PERF_CLASSES = {
    'pl/aridlin/psychiatrykroles/clientperf/SnapshotReflectionCache.class',
    'pl/aridlin/psychiatrykroles/clientperf/SnapshotReflectionCache$1.class',
    'pl/aridlin/psychiatrykroles/clientperf/SnapshotReflectionCache$2.class',
    'pl/aridlin/psychiatrykroles/clientperf/SnapshotReflectionCache$EntityAccess.class',
    'pl/aridlin/psychiatrykroles/clientperf/mixin/IdlePayloadCaptureMixin.class',
    'pl/aridlin/psychiatrykroles/clientperf/mixin/SnapshotCacheAccess.class',
    'pl/aridlin/psychiatrykroles/clientperf/mixin/SnapshotReflectionMixin.class',
}
CLIENT_PERF_RESOURCE = 'psychiatryk-client-perf.mixins.json'
POINTBLANK_COMPONENTS = {
    'pointblank-mount-v32': ({
        'pl/aridlin/psychiatrykroles/pointblankfix/MountShotFilter.class',
        'pl/aridlin/psychiatrykroles/pointblankfix/mixin/HitScanMountMixin.class',
        'pl/aridlin/psychiatrykroles/pointblankfix/mixin/SlowProjectileMountMixin.class',
    }, 'psychiatryk-pointblank-mount.mixins.json'),
    'pointblank-stencil-v32': ({
        'pl/aridlin/psychiatrykroles/pointblankstencil/mixin/ScopeStencilMixin.class',
        'pl/aridlin/psychiatrykroles/pointblankstencil/mixin/ScopeGlowStencilMixin.class',
    }, 'psychiatryk-pointblank-render.mixins.json'),
}
SOUND_PROPERTIES = Path('config/sound_physics_remastered/soundphysics.properties')
SOUND_TUNING = {
    'environment_evaluation_ray_count': 24,
    'environment_evaluation_ray_bounces': 2,
    'max_occlusion_rays': 8,
}
DOC = '''
## v32 save I/O
Player NBT and Sable saves run on ordered background workers after detached
snapshots are captured on the server thread. Existing gameplay and rendering
features from v31 are preserved. Vanilla .dat_old replacement and Sable DSYNC /
force(true) durability are retained; this does not remove filesystem flushes.
Failed asynchronous snapshots remain queued in order for retry. Queue limits
apply before accepting new work. Persistent storage failures are reported and
can reject further saves; an unavailable disk cannot be made reliable by this
update. Player loads, Sable reads/new allocations, explicit flushed saves and
shutdown barriers may still wait. This reduces ordinary autosave stalls, not
the hosting disk's underlying latency. An abrupt process kill can still lose
work that has not completed, as with other asynchronous Minecraft saves.
Client command /chams scooter off hides bound-scooter chams and dots only for
you; /chams scooter on restores them. This preference persists per client and
does not change other chams categories or server ownership.
Flashback skips unnecessary idle wire copies for PLAY payloads already excluded
from its pre-roll snapshots. Recording, paused recording, configuration packets
and snapshot-eligible packets retain their capture behavior. Snapshot reflection
lookups are cached per class; current packet values and pre-roll state stay live.
Sound Physics tracing uses 24 environment rays, 2 bounces and 8 occlusion rays
(previously 32 / 4 / 16). Moving-sound evaluation remains enabled every 5 ticks;
wall occlusion, direction evaluation and existing smoothing are unchanged.
Only these three property values are tuned; Prism retains other user settings
and backs up each existing root/managed properties file before atomic updates.
Ordinary villager trades use their stored vanilla baseline costs and limits.
Wieśniuk has unlimited stock; its purchases preserve ordinary stock counters
and avoid sentinel arithmetic corrupting demand. Existing offers, enchantments,
discounts and demand stay intact. Unknown historical random baselines cannot
be reconstructed exactly without rerolling and are preserved.
Level-II scooter Frost Walker extends its path from existing crust/ice and
prepares a continuous bounded strip up to 12 blocks ahead. Conversions require
a loaded neighbor halo, so planning the strip does not force chunk generation.
PointBlank shots fired while mounted ignore the shooter's own scooter and
co-passenger in aim selection, server validation and slow-projectile collisions.
Outside targets, other vehicles and explosive splash are unchanged. This fixes
own-mount interception; it is not evidence of direct mount-to-shooter damage.
PointBlank's mask, lens and glow pass cleanup restores neutral stencil state:
ALWAYS comparison, KEEP operations and all stencil write bits enabled. Both the
OpenGL state cache and actual state are synchronized. This prevents a later
portal world pass from re-enabling a leftover scope-only comparison. Scopes
remain enabled. Runtime reports state the exact render phases verified; they
do not establish a universal fix for every shader or scoped weapon.
'''


def sha256(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def tune_sound_properties(data):
    """Change exactly the three tracing-budget lines, preserving other bytes."""
    text = data.decode('utf-8')
    for key, value in SOUND_TUNING.items():
        pattern = r'^([ \t]*' + re.escape(key) + r'[ \t]*=[ \t]*)[^\r\n]*?([ \t]*)(?=\r?$)'
        text, count = re.subn(pattern, lambda match: match.group(1) + str(value) + match.group(2), text, flags=re.MULTILINE)
        if count != 1:
            raise RuntimeError('Expected exactly one Sound Physics property: ' + key)
    return text.encode('utf-8')


def verify_candidate():
    report = json.loads((QA / 'runtime-report.json').read_text())
    candidate = QA / 'psychiatryk_roles.jar'
    if report.get('success') is not True or report.get('candidate_sha256') != sha256(candidate):
        raise RuntimeError('v32 packaging requires successful runtime QA of this exact candidate')
    proof = json.loads((QA / 'build-report.json').read_text())
    if proof.get('candidate_sha256') != sha256(candidate) or proof.get('base_sha256') != sha256(OLD / 'client' / MOD):
        raise RuntimeError('Candidate build proof does not match the current v31 baseline')
    toggle_proof = json.loads((TOGGLE / 'build-report.json').read_text())
    toggle_test = json.loads((TOGGLE / 'predicate-test.json').read_text())
    if toggle_test.get('success') is not True or set(toggle_proof['changed_existing_classes']) != TOGGLE_CHANGED:
        raise RuntimeError('Scooter-toggle class proof and passing predicate test are required')
    declared_changes = set(proof.get('allowed_changed_existing_entries', ['META-INF/neoforge.mods.toml']))
    trade = json.loads((WORK / 'trade-v32/build-proof.json').read_text())
    frost = json.loads((WORK / 'frost-v32/class-diff.json').read_text())
    trade_existing = {n for n,h in trade['changed_classes'].items() if h['old_sha256'] is not None}
    trade_added = {n for n,h in trade['changed_classes'].items() if h['old_sha256'] is None}
    pointblank_proofs = {}
    pointblank_added = set()
    for component, (classes, resource) in POINTBLANK_COMPONENTS.items():
        component_proof = json.loads((WORK / component / 'build-proof.json').read_text())
        if set(component_proof['classes']) != classes or component_proof['resource'] != resource:
            raise RuntimeError('Unexpected PointBlank component whitelist: ' + component)
        pointblank_proofs[component] = component_proof
        pointblank_added.update(classes | {resource})
    expected_changes = {'META-INF/neoforge.mods.toml', *TOGGLE_CHANGED, *trade_existing,
                        trade['resource'], *(item['entry'] for item in frost)}
    if declared_changes != expected_changes:
        raise RuntimeError('Build proof must declare exactly the known v32 component changes')
    with zipfile.ZipFile(OLD / 'client' / MOD) as previous, zipfile.ZipFile(candidate) as current:
        removed = set(previous.namelist()) - set(current.namelist())
        changed = [name for name in previous.namelist() if not name.endswith('/') and previous.read(name) != current.read(name)]
        added = set(current.namelist()) - set(previous.namelist())
        if removed or set(changed) != declared_changes:
            raise RuntimeError(f'Unexpected existing mod changes: removed={removed}, changed={changed}')
        for name in TOGGLE_CHANGED:
            if hashlib.sha256(current.read(name)).hexdigest() != toggle_proof['class_sha256'][name]:
                raise RuntimeError('Candidate scooter-toggle class disagrees with tested bytecode: ' + name)
        for name, hashes in trade['changed_classes'].items():
            if hashlib.sha256(current.read(name)).hexdigest() != hashes['new_sha256']:
                raise RuntimeError('Candidate trade class disagrees with tested bytecode: ' + name)
        if hashlib.sha256(current.read(trade['resource'])).hexdigest() != trade['resource_sha256']:
            raise RuntimeError('Candidate trade mixin resource differs from its tested source')
        for item in frost:
            if hashlib.sha256(current.read(item['entry'])).hexdigest() != item['after']:
                raise RuntimeError('Candidate Frost Walker differs from its tested bytecode')
        declared_client = CLIENT_PERF_CLASSES | {CLIENT_PERF_RESOURCE}
        if not declared_client.issubset(added):
            raise RuntimeError('Candidate lacks the declared client performance component')
        for name in CLIENT_PERF_CLASSES:
            if hashlib.sha256(current.read(name)).hexdigest() != sha256(WORK / 'client-perf/classes' / name):
                raise RuntimeError('Candidate client performance class disagrees with compiled bytecode: ' + name)
        if current.read(CLIENT_PERF_RESOURCE) != (WORK / 'client-perf/resources' / CLIENT_PERF_RESOURCE).read_bytes():
            raise RuntimeError('Candidate client performance mixin resource does not match its source')
        if not pointblank_added.issubset(added):
            raise RuntimeError('Candidate lacks the declared PointBlank components')
        metadata = current.read('META-INF/neoforge.mods.toml').decode('utf-8')
        for component, component_proof in pointblank_proofs.items():
            for name, expected_hash in component_proof['classes'].items():
                if hashlib.sha256(current.read(name)).hexdigest() != expected_hash:
                    raise RuntimeError('Candidate PointBlank class differs from proof: ' + name)
            resource = component_proof['resource']
            expected_hash = component_proof.get('resource_sha256', component_proof.get('resourceSha'))
            if expected_hash is None or hashlib.sha256(current.read(resource)).hexdigest() != expected_hash:
                raise RuntimeError('Candidate PointBlank resource differs from proof: ' + resource)
            if current.read(resource) != (WORK / component / 'resources' / resource).read_bytes() or resource not in metadata:
                raise RuntimeError('Candidate PointBlank mixin source/registration mismatch: ' + resource)
        if any(not (name.startswith('pl/aridlin/psychiatrykroles/io/') or name == 'psychiatryk-save-io.mixins.json' or name in declared_client or name in trade_added or name in pointblank_added) for name in added):
            raise RuntimeError('Candidate contains additions outside the declared I/O and client performance components')
    return candidate


def public_source_zip(client, main):
    # Preserve original component source/licenses; add this release's overlays.
    previous = OLD / 'client/licenses/psychiatryk-roles-unified-source.zip'
    target = client / 'licenses/psychiatryk-roles-unified-source.zip'
    components = ['roles-unified', 'party-markers', 'save-io', 'player-save-io', 'client-perf']
    replacement = {}
    allowed = {'.java', '.json', '.toml', '.py', '.txt', '.vsh', '.fsh', '.md'}
    excluded = {'classes', 'test-classes', 'server', 'server-test', 'light-research', 'model-research', 'mark-sync-classes'}
    for component in components:
        folder = WORK / component
        for path in folder.rglob('*'):
            if not path.is_file() or any(part in excluded for part in path.relative_to(folder).parts):
                continue
            if path.name.startswith('private_') or path.name == 'classpath.txt' or path.name.endswith('-qa.txt'):
                continue
            if path.suffix in allowed or folder / 'resources' in path.parents:
                replacement[str(path.relative_to(WORK))] = path
    replacement['build_save_io_candidate.py'] = WORK / 'build_save_io_candidate.py'
    for name in ['build_v32.py', 'prepare_v32_github.py', 'verify_v32_components.py']:
        replacement[name] = WORK / name
    # These folders also contain private ARR decompilation and disposable clients.
    # Only our authored source/resources are eligible, never an entire folder scan.
    for component in POINTBLANK_COMPONENTS:
        for folder_name in ['src', 'resources']:
            for path in (WORK / component / folder_name).rglob('*'):
                if path.is_file() and path.suffix in allowed and not path.name.startswith('private_'):
                    replacement[str(path.relative_to(WORK))] = path
        for name in ['build.py', 'README.md']:
            path = WORK / component / name
            if path.is_file():
                replacement[str(path.relative_to(WORK))] = path
    for name in ['kukirin/src/pl/aridlin/kukirin/ScooterFrostWalker.java',
                 'restart-votes/src/pl/aridlin/psychiatrykroles/Wiesniuk.java']:
        replacement[name] = WORK / name
    # Test source only: no test instances, screenshots, profiles or raw reports.
    helper = WORK / 'client-perf-qa'
    if helper.exists():
        for folder_name in ['src', 'resources']:
            for path in (helper / folder_name).rglob('*'):
                if path.is_file() and path.suffix in allowed and not path.name.startswith('private_'):
                    replacement[str(path.relative_to(WORK))] = path
        for name in ['build.py', 'test.py', 'README.md']:
            if (helper / name).is_file():
                replacement[str((helper / name).relative_to(WORK))] = helper / name
    with zipfile.ZipFile(previous) as source, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as dest:
        for entry in source.infolist():
            if entry.filename in replacement or Path(entry.filename).name.startswith('private_'):
                continue
            if any(entry.filename.startswith(component + '/') for component in [*POINTBLANK_COMPONENTS, 'pointblank-v32']):
                continue
            if any(entry.filename.startswith(component + '/') for component in components) and Path(entry.filename).suffix in allowed:
                continue
            dest.writestr(entry, source.read(entry.filename))
        for name, path in sorted(replacement.items()):
            data = path.read_bytes()
            if path.suffix in allowed:
                text = data.decode()
                text = re.sub(r"Path\((['\"])\/home\/aridlin\/([^'\"]+)\1\)", lambda m: '(Path.home() / ' + repr(m.group(2)) + ')', text)
                text = re.sub(r'Path\.of\("~/([^\"]+)"\)', lambda m: 'Path.of(System.getProperty("user.home"), ' + json.dumps(m.group(1)) + ')', text)
                data = text.encode()
            dest.writestr(name, data)
        dest.writestr('SAVE-IO-v32.md', '# Save I/O overlay v32\n' + DOC + '\nCompile the Java 21 source against the exact NeoForge 21.1.252 / Sable 2.0.5 stack. The local classpath.txt is deliberately excluded. build_save_io_candidate.py overlays the compiled classes and mixin registration onto the unchanged v31 unified JAR.\n')
    shutil.copy2(target, main / 'licenses' / target.name)


def mod_manifest(folder):
    result = {}
    for path in sorted((folder / 'mods').glob('*.jar')):
        data = path.read_bytes()
        result['mods/' + path.name] = {'size': len(data), 'sha1': hashlib.sha1(data).hexdigest(), 'sha256': hashlib.sha256(data).hexdigest()}
    return result


def package_archives(client, manifest):
    result = []
    for suffix, index_name in [('curseforge.zip', 'manifest.json'), ('mrpack', 'modrinth.index.json')]:
        stem = 'psychiatryk-s23-20261006-klient-'
        previous = OUT / (stem + 'v31-' + suffix if suffix == 'curseforge.zip' else stem + 'v31.mrpack')
        dest = OUT / (stem + 'v32-' + suffix if suffix == 'curseforge.zip' else stem + 'v32.mrpack')
        with zipfile.ZipFile(previous) as source:
            index = json.loads(source.read(index_name))
        index['name'] = 'Goplanska S23 v32'
        index['version' if suffix == 'curseforge.zip' else 'versionId'] = RELEASE
        if suffix == 'mrpack':
            index['files'] = [entry for entry in index['files'] if entry['path'] in manifest['client'] and entry['path'] not in [MOD, 'mods/lambdynamiclights-4.8.11+1.21.1.jar']]
            for entry in index['files']:
                if entry['hashes']['sha1'] != manifest['client'][entry['path']]['sha1']:
                    raise RuntimeError('External Modrinth index hash disagrees with release: ' + entry['path'])
        external = set() if suffix == 'curseforge.zip' else {entry['path'] for entry in index['files']}
        temporary = dest.with_name(dest.name + '.building')
        with zipfile.ZipFile(temporary, 'w', zipfile.ZIP_DEFLATED) as archive:
            archive.writestr(index_name, json.dumps(index, indent=2))
            for path in sorted(client.rglob('*')):
                relative = str(path.relative_to(client))
                if path.is_file() and relative not in external:
                    archive.write(path, 'overrides/' + relative)
        with zipfile.ZipFile(temporary) as archive:
            if archive.testzip() is not None or hashlib.sha256(archive.read('overrides/' + MOD)).hexdigest() != manifest['client'][MOD]['sha256']:
                raise RuntimeError('Pack verification failed: ' + dest.name)
        temporary.replace(dest)
        result.append(dest)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--replace', action='store_true', help='replace a previously prepared local v32 directory')
    args = parser.parse_args()
    candidate = verify_candidate()  # All release writes follow this exact-candidate gate.
    if NEW.exists() and not args.replace:
        raise RuntimeError('release-v32 already exists; use --replace for a deliberate local rebuild')
    OUT.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='.release-v32-', dir=WORK) as staging:
        release = Path(staging)
        for side in ['client', 'server']:
            shutil.copytree(OLD / side, release / side)
        client, server = release / 'client', release / 'server'
        main_pack = server / 'automodpack/host-modpack/main'
        for folder in [client, server, main_pack]:
            shutil.copy2(candidate, folder / MOD)
        # The server's own config remains untouched. These are client overrides.
        for folder in [client, main_pack]:
            properties = folder / SOUND_PROPERTIES
            properties.write_bytes(tune_sound_properties(properties.read_bytes()))
        doc = (client / 'README-SCOOTER.txt').read_text() + DOC
        for folder in [client, main_pack]:
            (folder / 'README-SCOOTER.txt').write_text(doc)
            (folder / 'README-SAVE-IO.txt').write_text(DOC.lstrip())
        public_source_zip(client, main_pack)
        manifest = json.loads((OLD / 'goplanska-release.json').read_text())
        if manifest['release'] != '2026-10-06-S23-v31':
            raise RuntimeError('Unexpected baseline release')
        manifest['release'] = RELEASE
        manifest['features'] += ['Ordered background player-NBT and Sable saves; existing durable replacement/DSYNC/force operations retained; load/allocation/explicit flush may still wait']
        manifest['features'] += ['/chams scooter off|on: persistent per-client bound-scooter chams and dot visibility; other categories preserved']
        manifest['features'] += ['Flashback idle capture skips only snapshot-ineligible PLAY packets; recording/pre-roll state preserved; packet snapshot reflection discovery cached']
        manifest['features'] += ['Sound Physics client tracing budgets 24 rays / 2 bounces / 8 occlusion rays; moving updates remain at 5 ticks and other settings preserved']
        manifest['features'] += ['Villager vanilla baseline costs/stock for ordinary players; unlimited Wieśniuk stock without consuming ordinary stock or corrupting demand']
        manifest['features'] += ['Level-II Frost Walker extends continuous lava/ice paths up to 12 blocks ahead with bounded work and a loaded-neighbor halo']
        manifest['features'] += ['PointBlank mounted shots ignore the shooter\'s own scooter/co-passenger; outside targets and ordinary vehicle behavior preserved']
        manifest['features'] += ['PointBlank mask/lens/glow cleanup restores neutral stencil function, operations and write bits for subsequent portal world rendering; scopes preserved and verified phases documented in QA']
        for side in ['client', 'server']:
            current = mod_manifest(release / side)
            expected = json.loads((OLD / 'goplanska-release.json').read_text())[side]
            if current.keys() != expected.keys() or any(current[key] != value for key, value in expected.items() if key != MOD):
                raise RuntimeError('v32 must preserve every existing mod except the save-I/O overlay: ' + side)
            manifest[side] = current
        # AutoModpack itself stays in the launcher bootstrap, not its managed pack.
        host_expected = {name: data for name, data in manifest['client'].items() if not name.startswith('mods/automodpack-')}
        if mod_manifest(main_pack) != host_expected:
            raise RuntimeError('AutoModpack host and client mod inventories disagree')
        for path in [release / 'goplanska-release.json', client / 'goplanska-release.json', server / 'goplanska-release.json', main_pack / 'goplanska-release.json']:
            path.write_text(json.dumps(manifest, indent=2))
        archives = package_archives(client, manifest)
        pointer = {'release': RELEASE, 'curseforge': archives[0].name, 'prism': archives[1].name, 'previousRelease': 'v31', 'manifest': 'goplanska-release-v32.json', 'sha256': {path.name: sha256(path) for path in archives}}
        (release / 'current-release.json').write_text(json.dumps(pointer, indent=2))
        changed = [str(path.relative_to(server)) for path in sorted(server.rglob('*')) if path.is_file() and (not (OLD / 'server' / path.relative_to(server)).exists() or sha256(path) != sha256(OLD / 'server' / path.relative_to(server)))]
        with zipfile.ZipFile(OUT / 'goplanska-s23-v32-activate.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
            properties = []
            for name in changed:
                archive.write(server / name, name)
                properties.append('sha256.' + name + '=' + sha256(server / name))
            archive.writestr('goplanska-deployment-v32.properties', '\n'.join(properties) + '\n')
        if NEW.exists():
            shutil.rmtree(NEW)
        shutil.copytree(release, NEW)
    print('Prepared v32:', len(manifest['client']), 'client mods;', len(manifest['server']), 'server mods;', len(changed), 'deployment files. No production action performed.')


if __name__ == '__main__':
    main()
