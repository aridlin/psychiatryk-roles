#!/usr/bin/env python3
"""Compile four focused Peeb source families onto the externally supplied e0f6 checkpoint."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
BASE_SHA = 'e0f6a9df96d249ff7a6d9477ddcc04bc90e5b446c3210f3cf47e8d887be7e66d'
EXPECTED_SHA = '2f83776f209b598bc3b684b8c46c0d40fc3400002bcf26f253e50395ae493fc9'
digest = lambda file: hashlib.sha256(file.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    assert digest(args.base) == BASE_SHA, 'Supply the exact approved e0f6 disc/Peeb checkpoint'
    provenance = json.loads((ROOT / 'source-provenance.json').read_text())
    sources = sorted((ROOT / 'src').rglob('*.java'))
    assert len(sources) == 4
    assert {source.relative_to(ROOT).as_posix(): digest(source) for source in sources} == provenance['source_sha256']
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    classes = output / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    classpath = str(args.base.resolve()) + os.pathsep + args.classpath_file.read_text().strip()
    subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', classpath, '-d', str(classes), *map(str, sources)], check=True)
    with zipfile.ZipFile(args.base) as archive:
        assert archive.testzip() is None and len(archive.namelist()) == len(set(archive.namelist()))
        original = {row.filename: archive.read(row) for row in archive.infolist() if not row.is_dir()}
    entries = dict(original)
    prefixes = tuple(source.relative_to(ROOT / 'src').as_posix()[:-5] for source in sources)
    for file in sorted(classes.rglob('*.class')):
        name = file.relative_to(classes).as_posix()
        assert name.startswith(prefixes) and name in original, 'Unexpected class family or new entry'
        entries[name] = file.read_bytes()
    changed = sorted(name for name in original if entries[name] != original[name])
    assert changed and all(name.startswith(prefixes) for name in changed)
    assert entries.keys() == original.keys()
    target = output / 'psychiatryk_roles-3.0.5-peeb-followup.jar'
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            row = zipfile.ZipInfo(name, (2026, 10, 8, 8, 0, 0))
            row.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(row, data)
    assert digest(target) == EXPECTED_SHA, 'Compiled output differs from the qualified frozen source snapshot'
    proof = {'success': True, 'base_sha256': BASE_SHA, 'candidate_sha256': digest(target), 'changed_entries': changed,
             'added_entries': [], 'removed_entries': [], 'unchanged_other_entries': len(original) - len(changed),
             'runtime_verified_by_this_build': False, 'production_changed': False}
    (output / 'build-proof.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps({'candidate_sha256': digest(target), 'candidate_path': str(target)}))

if __name__ == '__main__':
    main()
