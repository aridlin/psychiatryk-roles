"""Compile authored streaming classes and apply exact supplied overlays to a frozen JAR.

Requires an installed Java21 toolchain and the caller's Minecraft/NeoForge compile
classpath. The original addon generator is never invoked. No network, game or
production action occurs. The caller supplies native/Loyalty overlay archives.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def sha(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', required=True, type=Path)
    parser.add_argument('--base-sha256', required=True)
    parser.add_argument('--classpath-file', required=True, type=Path)
    parser.add_argument('--vanilla-overlay', required=True, type=Path)
    parser.add_argument('--loyalty-overlay', required=True, type=Path)
    parser.add_argument('--output-dir', required=True, type=Path)
    args = parser.parse_args()
    base = args.base.resolve()
    if sha(base) != args.base_sha256:
        raise ValueError('Exact frozen commonPeeb base required')
    out = args.output_dir.resolve()
    out.mkdir(exist_ok=True)
    classes = out / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    sources = sorted((ROOT / 'src').rglob('*.java'))
    classpath = os.pathsep.join([str(args.vanilla_overlay.resolve()), str(base), args.classpath_file.read_text().strip()])
    compilation = subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', classpath,
                                  '-d', str(classes), *map(str, sources)], capture_output=True, text=True)
    (out / 'compile.log').write_text(compilation.stdout + compilation.stderr)
    if compilation.returncode:
        raise ValueError('Compilation failed; see build/compile.log')
    streaming = out / 'music-streaming-overlay.zip'
    runtime = {p.relative_to(classes).as_posix(): p.read_bytes() for p in sorted(classes.rglob('*.class'))}
    with zipfile.ZipFile(streaming, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in runtime.items():
            info = zipfile.ZipInfo(name, (2026, 10, 8, 10, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    proof = {'success': True, 'compiled': True, 'runtime_overlay_sha256': sha(streaming),
             'runtime_entries': sorted(runtime),
             'runtime_files_sha256': {n: hashlib.sha256(v).hexdigest() for n, v in runtime.items()},
             'source_sha256': {p.relative_to(ROOT).as_posix(): sha(p) for p in sources},
             'compiled_against_peeb_candidate_sha256': sha(base),
             'native_game_playback_verified': False, 'production_changed': False, 'evidence': []}
    for filename in ('stream-test.json', 'service-test.json'):
        path = out / filename
        if path.exists():
            value = json.loads(path.read_bytes())
            if value.get('success') is not True:
                raise ValueError('Existing stream/service fixture failed')
            proof['evidence'].append({'path': str(path.resolve()), 'sha256': sha(path)})
            proof[filename.replace('-', '_').replace('.json', '_assertions')] = value['assertions']
    (out / 'overlay-proof.json').write_text(json.dumps(proof, indent=2) + '\n')
    with zipfile.ZipFile(base) as archive:
        if archive.testzip() is not None or len(archive.namelist()) != len(set(archive.namelist())):
            raise ValueError('Base archive corrupt or duplicated')
        original = {n: archive.read(n) for n in archive.namelist()}
    entries = dict(original)
    overlays = [streaming, args.vanilla_overlay.resolve(), args.loyalty_overlay.resolve()]
    ownership = {}
    for overlay in overlays:
        with zipfile.ZipFile(overlay) as archive:
            if archive.testzip() is not None or len(archive.namelist()) != len(set(archive.namelist())):
                raise ValueError('Overlay corrupt or duplicated')
            for name in archive.namelist():
                if name in ownership or name.startswith('/') or '..' in name.split('/'):
                    raise ValueError('Overlay conflict or unsafe entry')
                entries[name] = archive.read(name)
                ownership[name] = str(overlay)
    candidate = out / 'psychiatryk_roles-3.0.5-streaming-vanilla-loyalty.jar'
    with zipfile.ZipFile(candidate, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2026, 10, 8, 10, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(info, data)
    candidate_sha = sha(candidate)
    snapshot = out / 'snapshots' / (candidate_sha + '.jar')
    snapshot.parent.mkdir(exist_ok=True)
    if snapshot.exists() and sha(snapshot) != candidate_sha:
        raise ValueError('Immutable snapshot collision')
    if not snapshot.exists():
        shutil.copyfile(candidate, snapshot)
    result = {'success': True, 'base_sha256': sha(base), 'base_candidate_path': str(base),
              'candidate_sha256': candidate_sha, 'candidate_path': str(snapshot.resolve()),
              'changed_entries': sorted(n for n in original if original[n] != entries[n]),
              'added_entries': sorted(set(entries) - set(original)), 'removed_entries': [],
              'unchanged_other_entries': sum(original[n] == entries[n] for n in original),
              'overlay_sha256': {str(p.resolve()): sha(p) for p in overlays},
              'overlay_entries_sha256': {n: hashlib.sha256(entries[n]).hexdigest() for n in ownership},
              'peeb_classes_byte_identical': all(original[n] == entries[n] for n in original if '/peeb/' in n),
              'disc_geometry_renderer_byte_identical': all(original[n] == entries[n] for n in original if '/jukebox/client/JukeboxCover' in n),
              'protected_legacy_recipe_exception': 'data/goplanska_kukirin/recipe/kukirin_scooter.json',
              'native_game_runtime_verified': False, 'production_changed': False}
    if not result['peeb_classes_byte_identical'] or not result['disc_geometry_renderer_byte_identical']:
        raise ValueError('Streaming/native overlays changed qualifiedPeeb/disc classes')
    (out / 'combined-proof.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({'candidate_sha256': candidate_sha, 'candidate_path': str(snapshot.resolve())}))


if __name__ == '__main__':
    main()
