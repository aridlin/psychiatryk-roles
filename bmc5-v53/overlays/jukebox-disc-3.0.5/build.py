#!/usr/bin/env python3
"""Compile the two jukebox families onto the exact approved Peeb checkpoint."""
from pathlib import Path
import argparse
import hashlib
import json
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
BASE_SHA = 'f03a1a2856e3ce9d62de6797ce2fcf49825373252d733e6f69d9f5f60b57fa53'
FROZEN_CANDIDATE_SHA = 'e0f6a9df96d249ff7a6d9477ddcc04bc90e5b446c3210f3cf47e8d887be7e66d'
PREFIXES = ('pl/aridlin/psychiatrykroles/jukebox/client/JukeboxCoverRenderer',
            'pl/aridlin/psychiatrykroles/jukebox/client/JukeboxCoverGeometry')

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True,
                        help='NeoForge 1.21.1 development classpath, separated with the platform path separator')
    parser.add_argument('--output', type=Path, default=ROOT / 'build')
    parser.add_argument('--verify-geometry', action='store_true')
    args = parser.parse_args()
    if digest(args.base) != BASE_SHA:
        raise SystemExit('Unexpected base SHA-256; use the exact f03a checkpoint.')
    sources = sorted((ROOT / 'src').rglob('*.java'))
    manifest = json.loads((ROOT / 'source-provenance.json').read_text())
    actual = {p.relative_to(ROOT).as_posix(): digest(p) for p in sources}
    expected = {entry['path']: entry['sha256'] for entry in manifest['sources']}
    if actual != expected:
        raise SystemExit('Source snapshot differs from source-provenance.json.')
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    classes = output / 'classes'
    if classes.exists():
        shutil.rmtree(classes)
    classes.mkdir()
    import os
    classpath = str(args.base.resolve()) + os.pathsep + args.classpath_file.read_text().strip()
    subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', classpath,
                    '-d', str(classes), *map(str, sources)], check=True)
    with zipfile.ZipFile(args.base) as archive:
        original = {entry.filename: archive.read(entry) for entry in archive.infolist() if not entry.is_dir()}
    entries = dict(original)
    for file in sorted(classes.rglob('*.class')):
        name = file.relative_to(classes).as_posix()
        if not name.startswith(PREFIXES) or name not in original:
            raise SystemExit(f'Unexpected class output: {name}')
        entries[name] = file.read_bytes()
    changed = sorted(name for name in original if entries[name] != original[name])
    if not all(name.startswith(PREFIXES) for name in changed):
        raise SystemExit('An unrelated checkpoint entry changed.')
    target = output / 'psychiatryk_roles-3.0.5-jukebox-disc.jar'
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            entry = zipfile.ZipInfo(name, (2026, 10, 8, 8, 0, 0))
            entry.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(entry, data)
    with zipfile.ZipFile(target) as archive:
        if archive.testzip() is not None:
            raise SystemExit('Candidate archive failed integrity validation.')
    proof = {'compiled': True, 'base_sha256': BASE_SHA,
             'candidate_sha256': digest(target), 'changed_entries': changed,
             'unchanged_entries': len(original) - len(changed),
             'no_entries_added_or_removed': set(entries) == set(original),
             'peeb_entries_byte_identical': all(entries[n] == original[n] for n in original
                                              if n.startswith(('pl/aridlin/psychiatrykroles/peeb/', 'assets/psychiatryk_peeb/'))),
             'geometry_verified': False, 'runtime_verified': False, 'native_visual_verified': False,
             'frozen_candidate_sha256': FROZEN_CANDIDATE_SHA,
             'matches_frozen_candidate': digest(target) == FROZEN_CANDIDATE_SHA}
    if args.verify_geometry:
        test_classes = output / 'test-classes'
        test_classes.mkdir(exist_ok=True)
        test_cp = str(classes) + os.pathsep + classpath
        subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', test_cp,
                        '-d', str(test_classes), str(ROOT / 'tests/JukeboxDiscGeometryQA.java')], check=True)
        test = subprocess.run(['java', '-cp', str(test_classes) + os.pathsep + test_cp,
                               'JukeboxDiscGeometryQA'], text=True, capture_output=True, check=True)
        result = json.loads(test.stdout)
        (output / 'geometry-report.json').write_text(json.dumps(result, indent=2) + '\n')
        proof['geometry_verified'] = result['success']
        proof['geometry_checks'] = result['checks']
    (output / 'build-proof.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps(proof, indent=2))

if __name__ == '__main__':
    main()
