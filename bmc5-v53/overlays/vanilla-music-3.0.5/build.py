#!/usr/bin/env python3
"""Compile the frozen native music catalog and package metadata aliases; copy no native audio."""
from pathlib import Path
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
digest = lambda file: hashlib.sha256(file.read_bytes()).hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'source-provenance.json').read_text())
    sources = sorted((ROOT / 'src').rglob('*.java'))
    resources = sorted(file for file in (ROOT / 'resources').rglob('*') if file.is_file())
    assert {file.relative_to(ROOT).as_posix(): digest(file) for file in sources + resources} == manifest['source_resource_sha256']
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    classes = output / 'classes'
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir()
    classpath = str(args.base.resolve()) + os.pathsep + args.classpath_file.read_text().strip()
    subprocess.run(['javac', '--release', '21', '-proc:none', '-cp', classpath, '-d', str(classes), *map(str, sources)], check=True)
    entries = {file.relative_to(classes).as_posix(): file.read_bytes() for file in sorted(classes.rglob('*.class'))}
    entries.update({file.relative_to(ROOT / 'resources').as_posix(): file.read_bytes() for file in resources})
    assert {name: hashlib.sha256(data).hexdigest() for name, data in entries.items()} == manifest['runtime_files_sha256']
    overlay = output / 'vanilla-music-overlay.zip'
    with zipfile.ZipFile(overlay, 'w', zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries.items()):
            row = zipfile.ZipInfo(name, (2026, 10, 8, 10, 0, 0))
            row.compress_type = zipfile.ZIP_DEFLATED
            archive.writestr(row, data)
    print(json.dumps({'success': True, 'overlay': str(overlay), 'native_tracks': 79, 'native_audio_copied': False}))

if __name__ == '__main__':
    main()
