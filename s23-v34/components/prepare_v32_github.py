"""Prepare public v32 source and QA evidence. Does not commit or push."""
from pathlib import Path
import argparse
import hashlib
import json
import re
import shutil
import zipfile

ROOT = Path(__file__).resolve().parent.parent
WORK, OUT = ROOT / 'work', ROOT / 'outputs'
QA = OUT / 'save-io-v32'
UUID = re.compile(r'\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b')


def digest(path):
    with path.open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def scrub(value):
    if isinstance(value, dict):
        return {UUID.sub('<redacted-uuid>', str(key)).replace('~/', '<local-home>/'): scrub(item) for key, item in value.items()
                if not any(word in str(key).lower() for word in ('password', 'token', 'secret', 'credential'))}
    if isinstance(value, list):
        return [scrub(item) for item in value]
    if isinstance(value, str):
        return UUID.sub('<redacted-uuid>', value).replace('~/', '<local-home>/')
    return value


def public_report(source, destination):
    destination.write_text(json.dumps(scrub(json.loads(source.read_text())), indent=2))


def public_test_source(source, destination):
    if source.suffix in {'.java', '.py'}:
        text = source.read_text()
        text = re.sub(r"Path\((['\"])\/home\/aridlin\/([^'\"]+)\1\)", lambda m: '(Path.home() / ' + repr(m.group(2)) + ')', text)
        text = re.sub(r'Path\.of\("~/([^\"]+)"\)', lambda m: 'Path.of(System.getProperty("user.home"), ' + json.dumps(m.group(1)) + ')', text)
        destination.write_text(text)
    else:
        shutil.copy2(source, destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--deployment-record', type=Path, help='optional actual deployment receipt supplied after production activation')
    args = parser.parse_args()
    report = json.loads((QA / 'runtime-report.json').read_text())
    candidate = QA / 'psychiatryk_roles.jar'
    manifest = json.loads((WORK / 'release-v32/goplanska-release.json').read_text())
    if report.get('success') is not True or report.get('candidate_sha256') != digest(candidate):
        raise RuntimeError('Successful runtime QA of this exact v32 candidate is required')
    if manifest['release'] != '2026-10-06-S23-v32' or manifest['client']['mods/psychiatryk_roles-2.1.1.jar']['sha256'] != digest(candidate):
        raise RuntimeError('Prepared public release does not match the tested candidate')
    repo = WORK / 'github-publish-v19'
    target = repo / 's23-v32'
    target.mkdir(exist_ok=True)
    components = target / 'components'
    if components.exists():
        shutil.rmtree(components)
    with zipfile.ZipFile(WORK / 'release-v32/client/licenses/psychiatryk-roles-unified-source.zip') as archive:
        for name in archive.namelist():
            if not name.startswith(('kukirin/', 'party-markers/', 'roles-unified/', 'restart-votes/', 'save-io/', 'player-save-io/', 'client-perf/', 'pointblank-fix/', 'pointblank-mount-v32/src/', 'pointblank-mount-v32/resources/', 'pointblank-stencil-v32/src/', 'pointblank-stencil-v32/resources/')) and name not in ['build_save_io_candidate.py', 'build_v32.py', 'prepare_v32_github.py', 'verify_v32_components.py', 'SAVE-IO-v32.md', 'pointblank-mount-v32/README.md', 'pointblank-mount-v32/build.py', 'pointblank-stencil-v32/README.md', 'pointblank-stencil-v32/build.py']:
                continue
            if '..' in Path(name).parts or Path(name).is_absolute():
                raise RuntimeError('Unsafe source archive path')
            destination = components / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(archive.read(name))
    shutil.copy2(candidate, target / 'psychiatryk_roles-2.1.1.jar')
    shutil.copy2(WORK / 'release-v32/goplanska-release.json', target / 'goplanska-release.json')
    public_report(QA / 'runtime-report.json', target / 'runtime-report.json')
    public_report(QA / 'build-report.json', target / 'build-report.json')
    for component, names in {
        'trade-v32': ['TradeQA.java', 'TradeServerQA.java', 'README.md', 'build-proof.json'],
        'frost-v32': ['build.py', 'class-diff.json'],
    }.items():
        destination = target / 'tests' / component
        destination.mkdir(parents=True, exist_ok=True)
        for name in names:
            source = WORK / component / name
            if not source.is_file():
                continue
            if source.suffix == '.json':
                public_report(source, destination / name)
            else:
                public_test_source(source, destination / name)
        helper = WORK / component / 'src'
        for source in helper.rglob('*.java'):
            path = destination / source.relative_to(WORK / component)
            path.parent.mkdir(parents=True, exist_ok=True)
            public_test_source(source, path)
        runtime = QA / 'trade-runtime-report.json' if component == 'trade-v32' else WORK / component / 'server/frost-qa-report.json'
        if runtime.exists():
            public_report(runtime, destination / 'runtime-report.json')
    # Publish only our helper sources and synthetic reports. Neither PointBlank
    # decompilation nor the disposable Prism clone/account cache belongs here.
    for component, names in {
        'pointblank-mount-v32': ['MountShotServerQA.java', 'README.md', 'build-proof.json', 'runtime-report.json'],
        'pointblank-stencil-v32': ['build-proof.json', 'runtime-report.json', 'README.md', 'build.py'],
        'pointblank-v32-qa': ['build.py'],
    }.items():
        destination = target / 'tests' / component
        if destination.exists():
            shutil.rmtree(destination)
        destination.mkdir(parents=True)
        for name in names:
            source = WORK / component / name
            if source.is_file():
                if source.suffix == '.json':
                    public_report(source, destination / name)
                else:
                    public_test_source(source, destination / name)
        if component == 'pointblank-v32-qa':
            for source in (WORK / component / 'src').rglob('*.java'):
                path = destination / source.relative_to(WORK / component)
                path.parent.mkdir(parents=True, exist_ok=True)
                public_test_source(source, path)
            runtime = QA / 'pointblank-qa/runtime-report.json'
            if runtime.is_file():
                public_report(runtime, destination / 'runtime-report.json')
            (destination / 'README.md').write_text('''# Isolated scoped-weapon rendering QA

Use only a disposable Java 21 / NeoForge client with the exact release mods.
The helper alters its private test world and inventory and observes allocator,
framebuffer, scope and GUI rendering. Do not install it in a production pack.
Supply the release dependencies locally; upstream jars and decompiled source
are not included. Set the report output path before building. The report lists
the exact observed phases: an allocator-only pass is not a complete scope test.
Private launcher state, account caches, worlds, screenshots and logs are excluded.
''')
    if (QA / 'sound-tuning-preflight.json').exists():
        public_report(QA / 'sound-tuning-preflight.json', target / 'sound-tuning-preflight.json')
    # These Java helpers use two invented FakePlayer UUIDs, not player/world exports.
    tests = target / 'tests/save-io-qa'
    if tests.exists():
        shutil.rmtree(tests)
    shutil.copytree(WORK / 'save-io-qa/src', tests / 'src')
    for name in ['build.py', 'run_case.py']:
        shutil.copy2(WORK / 'save-io-qa' / name, tests / name)
    for name in ['OwnerFailureQA.java', 'OwnerBoundQA.java']:
        source = WORK / 'save-io' / name
        if source.exists():
            shutil.copy2(source, tests / name)
    toggle = target / 'tests/scooter-chams-toggle'
    toggle.mkdir(parents=True, exist_ok=True)
    for name in ['ScooterToggleQA.java', 'README.md', 'build.py']:
        shutil.copy2(WORK / 'scooter-chams-toggle' / name, toggle / name)
    for name in ['build-report.json', 'predicate-test.json']:
        public_report(WORK / 'scooter-chams-toggle' / name, toggle / name)
    helper = WORK / 'client-perf-qa'
    if helper.exists():
        public_helper = target / 'tests/client-perf-qa'
        if public_helper.exists():
            shutil.rmtree(public_helper)
        public_helper.mkdir(parents=True)
        for name in ['src', 'resources']:
            if (helper / name).exists():
                for source in (helper / name).rglob('*'):
                    if not source.is_file() or source.name.startswith('private_'):
                        continue
                    destination = public_helper / source.relative_to(helper)
                    destination.parent.mkdir(parents=True, exist_ok=True)
                    public_test_source(source, destination)
        for name in ['build.py', 'test.py', 'README.md']:
            if (helper / name).is_file():
                public_test_source(helper / name, public_helper / name)
        for name in ['runtime-report.json', 'build-report.json', 'predicate-report.json']:
            if (helper / name).is_file():
                public_report(helper / name, public_helper / name)
        if (QA / 'client-runtime-report.json').is_file():
            public_report(QA / 'client-runtime-report.json', public_helper / 'runtime-report.json')
        (public_helper / 'README.md').write_text('''# Title-screen client performance QA

Test-only source: use a disposable client and the exact v32 JAR/NeoForge stack.
Do not ship the helper in the modpack. It generates synthetic payloads and
executes client commands at the title screen; it loads no world or server.
Configure the test client's Sound Physics file with:

    environment_evaluation_ray_count=24
    environment_evaluation_ray_bounces=2
    max_occlusion_rays=8

Retain update_moving_sounds=true, sound_update_interval=5,
sound_direction_evaluation=true, unsafe_level_access=false, and the existing
wall occlusion/smoothing settings. The helper verifies capture suppression only
for snapshot-ineligible idle PLAY payloads, retention of snapshot-eligible and
configuration payloads, unchanged buffer offsets/reference count, runtime
reflection precedence, /chams scooter off|on persistence and other categories.
Recording/paused-recording preservation is part of the separate predicate proof.

build.py is the workspace compiler recipe; provide its exact release dependency
paths locally. Set the helper's report output path for your environment before
building. The private Prism clone, account cache, launcher and game logs are
excluded; runtime-report.json contains only synthetic check outcomes.
''')
    (tests / 'README.md').write_text('''# Isolated save-I/O QA

Use Java 21 and the exact NeoForge 21.1.252 / Sable 2.0.5 mod stack from
the release manifest, with a disposable server/world. The helper creates two
invented FakePlayers and a two-block test assembly; no production data is here.
build.py builds the test-only mod and fault-injection mixins. Supply the local
server launcher run.py expected by run_case.py and install the tested candidate
only for candidate cases. Use the unchanged v31 JAR for the baseline case.
Do not install this QA mod on a production server: it injects delays/failures.
Check the runtime report for the cases actually executed and their results.
OwnerFailureQA and OwnerBoundQA exercise the ordered worker independently.
The production music library, server/world exports and credentials are excluded.
''')
    receipt = target / 'save-io-deployment.json'
    deployment = 'Production deployment has not been asserted by this preparation script. The runtime evidence is from an isolated server.'
    if args.deployment_record:
        data = json.loads(args.deployment_record.read_text())
        if data.get('candidate_sha256') not in (None, digest(candidate)):
            raise RuntimeError('Deployment receipt is for a different candidate')
        public_report(args.deployment_record, receipt)
        deployment = 'An actual deployment receipt is included as save-io-deployment.json. It records activation; consult its fields for the checks actually performed.'
    else:
        receipt.unlink(missing_ok=True)
    (target / 'README.md').write_text('''# S23 v32

Player NBT and Sable save I/O now uses ordered background workers. Detached
snapshots are captured on the server thread; the worker does not read live
players or mutable physics assemblies. Existing v31 client/server features and
unrelated mod entries are preserved; build-report.json lists every deliberate
bytecode/resource change. `/chams scooter off` hides the owner's
bound-scooter chams and top dots for that client; `on` restores them. Preferences
persist without changing other highlight categories or server ownership.

Client performance overlays skip expensive idle Flashback wire copies only for
PLAY payloads already rejected by its pre-roll snapshot filter. Active and
paused recording, configuration packets and snapshot-eligible packets retain
their capture behavior. Reflection discovery for packet snapshots is cached
per class, including absent fields/methods; live packet values are not cached.
This preserves pre-roll state while reducing avoidable allocations.

Client Sound Physics tracing budgets are 24 environment rays, 2 reflection
bounces and 8 occlusion rays (v31: 32 / 4 / 16). Moving sounds still update every
5 ticks; wall occlusion, directional evaluation and smoothing are unchanged.
The Prism update backs up each existing root/managed properties file and changes
only those three key lines, preserving the player's other sound preferences.

Ordinary villager prices and limits use their stored vanilla baseline. Wieśniuk
stock is unlimited without consuming ordinary stock or overflowing demand on
restock. Existing offer results, enchantments, gossip and demand are preserved.
Unknown historical random baselines stay intact rather than being rerolled.
Level-II scooter Frost Walker extends from existing crust and ice, and prepares
a continuous strip up to 12 blocks ahead. Its loaded-neighbor halo prevents
conversions from forcing chunk generation at the edge of loaded terrain.

PointBlank shots fired from a scooter ignore that same scooter and co-passenger
in aim selection, supplied-target validation and slow-projectile collision.
Outside targets, parked scooters, other vehicles and explosive splash behavior
remain. The actual dedicated-server fixture passes 14 checks. This fixes
own-mount interception; it does not establish direct mount-to-shooter damage.

PointBlank's mask, lens and glow pass cleanup restores neutral stencil state:
ALWAYS comparison, KEEP operations and all write bits enabled, synchronized in
both the OpenGL cache and actual state. The portal renderer enables the test
before opaque terrain without resetting its comparison; a leftover scope-only
EQUAL comparison against a cleared zero buffer rejected that terrain. Scopes
remain enabled. Consult tests/pointblank-stencil-v32/runtime-report.json and the
QA report for the actual tested render phases. Those results are not a universal
guarantee for every shader pack or weapon. Only our authored mixins and synthetic
QA are published;
PointBlank/Veil decompilation and private launcher state are excluded.

Vanilla .dat_old replacement and Sable DSYNC / force(true) operations remain.
Failures retain asynchronous snapshots in order for retry, with bounded queues
and visible errors. Loads, new allocations, explicit flushed saves and shutdown
barriers may still wait. The update reduces ordinary autosave stalls; it does
not repair the hosting disk. A process killed before queued work completes can
still lose that unfinished work. Persistent disk errors cannot be hidden or
made safe by queueing indefinitely.

runtime-report.json records the actual isolated cases and the tested candidate
checksum; build-report.json proves preservation of the existing mod entries.
The test-only sources include controlled disk-delay/failure injections and
synthetic FakePlayers. No production world/player data or credentials are
published. Private UUIDs and local home paths are scrubbed from JSON reports.

PlayerEvent.SaveToFile still fires on the server thread after enqueueing the
player snapshot, so future add-ons must not assume .dat has completed at that
event. No SaveToFile listener was found in the recursively scanned installed
v31 mod stack. Explicit load/flush barriers provide completed-save ordering.

''' + deployment + '\n')
    readme = repo / 'README.md'
    previous = readme.read_text()
    history = previous[previous.index('---'):]
    readme.write_text('''# Current release: Goplanska S23 v32

The NeoForge 1.21.1 release is in **[s23-v32](s23-v32/README.md)**.
Previous release: [s23-v31](s23-v31/README.md). The project below is the
historical Forge 1.20.1 edition.

''' + history)
    print('Prepared v32 public source and scrubbed isolated QA evidence; no Git commit or push performed.')


if __name__ == '__main__':
    main()
