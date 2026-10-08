#!/usr/bin/env python3
"""Reproduce the four source overlays from an externally supplied historical e0f6 checkpoint."""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import sys

ROOT = Path(__file__).resolve().parent
CHECKPOINT = '106f9337eb3f567e61e48a762c58e198331ccf95ca36a94e00e8695873469d85'
EXPECTED = '53c622c5b69e1d264eacee2e9b9d686e663d3e08d28a905523dd85fa07f5d509'

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base', type=Path, required=True, help='Exact external e0f6 JAR; this source tree distributes no runtime binaries')
    parser.add_argument('--classpath-file', type=Path, required=True, help='Minecraft 1.21.1 / NeoForge 21.1.250 Java compile classpath')
    parser.add_argument('--output-dir', type=Path, required=True)
    args = parser.parse_args()
    output = args.output_dir.resolve()
    output.mkdir(parents=True, exist_ok=True)
    common = output / 'peeb'
    vanilla = output / 'vanilla'
    loyalty = output / 'loyalty'
    music = output / 'music'
    def run(relative, extra):
        subprocess.run([sys.executable, str(ROOT / relative), *extra], check=True)
    run('overlays/peeb-followup-3.0.5/build.py', ['--base', str(args.base.resolve()), '--classpath-file', str(args.classpath_file.resolve()), '--output-dir', str(common)])
    focused = common / 'psychiatryk_roles-3.0.5-peeb-followup.jar'
    run('overlays/vanilla-music-3.0.5/build.py', ['--base', str(focused), '--classpath-file', str(args.classpath_file.resolve()), '--output-dir', str(vanilla)])
    run('overlays/elytra-loyalty-3.0.5/build.py', ['--base', str(focused), '--output-dir', str(loyalty)])
    run('overlays/music-streaming-3.0.5/build.py', ['--base', str(focused), '--base-sha256', hashlib.sha256(focused.read_bytes()).hexdigest(),
        '--classpath-file', str(args.classpath_file.resolve()), '--vanilla-overlay', str(vanilla / 'vanilla-music-overlay.zip'),
        '--loyalty-overlay', str(loyalty / 'elytra-loyalty-overlay.zip'), '--output-dir', str(music)])
    checkpoint = music / 'psychiatryk_roles-3.0.5-streaming-vanilla-loyalty.jar'
    assert hashlib.sha256(checkpoint.read_bytes()).hexdigest() == CHECKPOINT, 'Music checkpoint differs from the frozen artifact'
    restoration = output / 'restoration'
    run('overlays/scooter-upgrade-restoration-3.0.5/build.py', ['--base', str(checkpoint), '--classpath-file', str(args.classpath_file.resolve()), '--output-dir', str(restoration)])
    final = restoration / 'psychiatryk_roles-3.0.5-scooter-upgrades-restored.jar'
    actual = hashlib.sha256(final.read_bytes()).hexdigest()
    assert actual == EXPECTED, 'Final build differs from the qualified frozen artifact'
    print(json.dumps({'success': True, 'candidate_sha256': actual, 'candidate_path': str(final), 'game_or_production_launch_performed': False}))

if __name__ == '__main__':
    main()
