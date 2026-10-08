#!/usr/bin/env python3
"""Restore exact existing scooter modification recipes with a typed policy allowlist."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'restoration-manifest.json').read_text())
    assert digest(args.base) == manifest['base_sha256'], 'Frozen base mismatch'
    source = ROOT / 'src/pl/aridlin/psychiatrykroles/migration/MigrationPolicy.java'
    assert digest(source) == manifest['policy_source_sha256']
    peeb_source = ROOT / 'src/pl/aridlin/psychiatrykroles/peeb/PeebMode.java'
    assert digest(peeb_source) == manifest['held_peeb_source_sha256']
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    assert not list(classes.rglob('*.class')), 'Use an empty output directory'
    subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', os.pathsep.join([
        str(args.base.resolve()), args.classpath_file.read_text().strip()]), '-d', str(classes), str(source), str(peeb_source)], check=True)
    files = {file.relative_to(classes).as_posix(): file.read_bytes() for file in classes.rglob('*.class')}
    policy_name = 'pl/aridlin/psychiatrykroles/migration/MigrationPolicy.class'
    allowed = lambda name: any(name == family + '.class' or name.startswith(family + '$') for family in manifest['authorized_class_families'])
    assert policy_name in files and all(allowed(name) for name in files), 'Only the two authored class families may compile'
    with zipfile.ZipFile(args.base) as jar:
        base = {name: jar.read(name) for name in jar.namelist() if not name.endswith('/')}
    assert policy_name in base
    class_entries = dict(files)
    assert all(name in base for name in class_entries)
    for name, row in manifest['lang_changes'].items():
        file = ROOT / 'resources' / name
        assert digest(file) == row['sha256']
        before = json.loads(base[name])
        after = json.loads(file.read_text())
        assert before[row['key']] == row['before'] and after[row['key']] == row['after']
        after[row['key']] = row['before']
        assert before == after, 'Only the authorized tooltip value may change'
        files[name] = file.read_bytes()
    for row in manifest['recipes'].values():
        file = ROOT / 'resources' / row['path']
        assert digest(file) == row['sha256']
        assert row['path'] not in base, 'Recipe unexpectedly already exists'
        files[row['path']] = file.read_bytes()
    assert len(files) == len(class_entries) + 2 + 23
    def archive(path, entries):
        with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as jar:
            for name, data in sorted(entries.items()):
                info = zipfile.ZipInfo(name, (2026, 10, 8, 14, 0, 0))
                info.compress_type = zipfile.ZIP_DEFLATED
                info.external_attr = 0o644 << 16
                jar.writestr(info, data)
    overlay = output / 'scooter-upgrade-restoration-overlay.zip'
    archive(overlay, files)
    combined = dict(base)
    combined.update(files)
    candidate = output / 'psychiatryk_roles-3.0.5-scooter-upgrades-restored.jar'
    archive(candidate, combined)
    candidate_sha = digest(candidate)
    snapshots = output / 'snapshots'
    snapshots.mkdir(exist_ok=True)
    frozen = snapshots / (candidate_sha + '.jar')
    if frozen.exists(): assert digest(frozen) == candidate_sha
    else: frozen.write_bytes(candidate.read_bytes())
    changed = sorted(name for name in base if base[name] != combined[name])
    added = sorted(set(combined) - set(base))
    expected_changed = sorted([policy_name, 'pl/aridlin/psychiatrykroles/peeb/PeebMode.class', 'pl/aridlin/psychiatrykroles/peeb/PeebMode$PeebItem.class', *manifest['lang_changes']])
    assert changed == expected_changed and added == sorted(row['path'] for row in manifest['recipes'].values())
    endgame = manifest['unchanged_endgame_recipe']
    assert base[endgame] == combined[endgame]
    proof = {
        'success': True, 'scope': manifest['scope'], 'base_sha256': manifest['base_sha256'],
        'base_path': str(args.base.resolve()), 'candidate_sha256': candidate_sha, 'candidate_path': str(frozen),
        'overlay_path': str(overlay), 'overlay_sha256': digest(overlay),
        'files_sha256': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(files.items())},
        'restored_json_entries_sha256': {row['path']: row['sha256'] for row in manifest['recipes'].values()},
        'policy_class_entries_sha256': {policy_name: hashlib.sha256(files[policy_name]).hexdigest()},
        'peeb_mode_class_entries_sha256': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(class_entries.items()) if '/peeb/PeebMode' in name},
        'authored_class_entries_sha256': {name: hashlib.sha256(data).hexdigest() for name, data in sorted(class_entries.items())},
        'authored_class_families': manifest['authorized_class_families'],
        'lang_changes': manifest['lang_changes'],
        'typed_recipe_ids': manifest['typed_recipe_ids'], 'changed_entries': changed,
        'added_entries': added, 'removed_entries': [], 'unchanged_entries_count': len(base) - len(changed),
        'endgame_recipe_sha256': hashlib.sha256(combined[endgame]).hexdigest(),
        'endgame_recipe_unchanged': True, 'all_other_base_entries_unchanged': True,
        'native_recipe_matching_verified': False, 'production_mutated': False,
        'evidence': [{'path': str(path), 'sha256': digest(path)} for path in [source, peeb_source, ROOT / 'restoration-manifest.json', ROOT / 'build.py']],
    }
    (output / 'build-proof.json').write_text(json.dumps(proof, indent=2) + '\n')
    print(json.dumps({'success': True, 'candidate_sha256': candidate_sha, 'candidate_path': str(frozen), 'restored_recipe_count': len(added)}))

if __name__ == '__main__':
    main()
