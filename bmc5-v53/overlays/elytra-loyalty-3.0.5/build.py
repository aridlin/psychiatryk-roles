#!/usr/bin/env python3
"""Package the one frozen Elytra scooter recipe; no code or general scooter enchantment change."""
from pathlib import Path
import argparse
import hashlib
import json
import zipfile

ROOT = Path(__file__).resolve().parent
RECIPE = 'data/goplanska_kukirin/recipe/kukirin_scooter.json'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True)
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    manifest = json.loads((ROOT / 'source-provenance.json').read_text())
    with zipfile.ZipFile(args.base) as archive:
        before = archive.read(RECIPE)
    after = (ROOT / 'resources' / RECIPE).read_bytes()
    assert hashlib.sha256(before).hexdigest() == manifest['recipe_before_sha256']
    assert hashlib.sha256(after).hexdigest() == manifest['recipe_after_sha256']
    original = json.loads(before)
    updated = json.loads(after)
    compare = json.loads(after)
    assert compare['result']['components'].pop('minecraft:enchantments') == {'levels': {'minecraft:loyalty': 1}}
    assert compare == original, 'Ingredient grid/count/prebound result changed beyond Loyalty I'
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    target = output / 'elytra-loyalty-overlay.zip'
    with zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
        row = zipfile.ZipInfo(RECIPE, (2026, 10, 8, 10, 0, 0))
        row.compress_type = zipfile.ZIP_DEFLATED
        archive.writestr(row, after)
    print(json.dumps({'success': True, 'overlay': str(target), 'modified_paths': [RECIPE], 'default_loyalty_level': 1}))

if __name__ == '__main__':
    main()
