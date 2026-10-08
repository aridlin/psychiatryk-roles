"""Run on ProLiant as root; default checks only. --activate switches the API release.

No credentials are read. Existing audio, catalogs, import receipts and backups
remain in place. This script does not control Minecraft.
"""
from pathlib import Path
import argparse
import datetime as dt
import hashlib
import json
import os
import shutil
import subprocess

UNIT = 'goplanska-ytmusic.service'
OLD = Path('/opt/goplanska-ytmusic/releases/caf33999a66e1fcb22a7')
STATE = Path('/var/lib/goplanska-ytmusic')
DROPIN = Path('/etc/systemd/system/goplanska-ytmusic.service.d/20-streaming-release.conf')
FILES = {'common.py', 'service.py', 'worker.py', 'requirements.txt'}
BASE = {'common.py': '0cd124b5d89a2cd74b2d8da37455de991592ada8049f089844f3e35f9ee4269b',
        'service.py': 'd9126d0c14ccc04d5dcb9975498d88a1116e4403168f40ff189dcd4ecf256d48',
        'worker.py': '2a1b64a41a7685e98006dce3fd53e5abc24c888819c3e230a2a77459d7f0ce7f'}


def run(*arguments):
    return subprocess.run(arguments, check=True, capture_output=True, text=True).stdout.strip()


def digest(path):
    with Path(path).open('rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def atomic(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_name(path.name + '.writing')
    with temporary.open('wb') as output:
        output.write(data)
        output.flush()
        os.fsync(output.fileno())
    temporary.chmod(0o644)
    temporary.replace(path)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stage', type=Path, required=True)
    parser.add_argument('--activate', action='store_true')
    args = parser.parse_args()
    if os.geteuid() != 0:
        raise ValueError('Root required for systemd release preflight')
    stage = args.stage.resolve()
    manifest_path = stage / 'service-manifest.json'
    manifest = json.loads(manifest_path.read_bytes())
    if (manifest.get('success') is not True or set(manifest['files_sha256']) != FILES
            or {n: digest(stage / 'service' / n) for n in FILES} != manifest['files_sha256']):
        raise ValueError('Frozen four-file service package differs')
    if any(digest(OLD / name) != value for name, value in BASE.items()):
        raise ValueError('Current reviewed service source baseline changed')
    working = run('systemctl', 'show', UNIT, '--property=WorkingDirectory', '--value')
    if working != str(OLD) or run('systemctl', 'show', UNIT, '--property=User', '--value') != 'goplanska-ytmusic':
        raise ValueError('Current service working directory/user differs')
    if run('systemctl', 'is-active', UNIT) != 'active':
        raise ValueError('Current service is not active')
    active = 0
    for path in (STATE / 'jobs').glob('*.json'):
        if json.loads(path.read_bytes()).get('status') in ('queued', 'running'):
            active += 1
    if active:
        raise ValueError('Wait for current imports to finish before switching the service')
    release_id = digest(manifest_path)[:20]
    release = OLD.parent / release_id
    result = {'success': True, 'checked': True, 'active_import_jobs': active,
              'prior_release': str(OLD), 'new_release': str(release),
              'release_manifest_sha256': digest(manifest_path), 'activated': False,
              'credentials_read_or_copied': False, 'existing_music_library_rewritten': False,
              'minecraft_restarted': False}
    if not args.activate:
        print(json.dumps(result))
        return
    if release.exists() or DROPIN.exists():
        raise ValueError('Fresh release/drop-in names required; inspect earlier deployment first')
    attempt = dt.datetime.now(dt.timezone.utc).strftime('%Y%m%dT%H%M%SZ') + '-' + release_id
    backup = Path('/opt/goplanska-ytmusic/backups') / ('streaming-' + attempt)
    backup.mkdir(parents=True, exist_ok=False)
    atomic(backup / 'before.json', (json.dumps(result, indent=2) + '\n').encode())
    release.mkdir(mode=0o755)
    for name in sorted(FILES):
        atomic(release / name, (stage / 'service' / name).read_bytes())
    atomic(release / 'service-manifest.json', manifest_path.read_bytes())
    # Keep the installed extractor runtime exactly; neither download nor upgrade it.
    if (OLD / 'vendor').is_dir():
        (release / 'vendor').symlink_to(OLD / 'vendor', target_is_directory=True)
    configuration = ('[Service]\nWorkingDirectory=' + str(release) + '\nExecStart=\n'
                     'ExecStart=/usr/bin/python3 ' + str(release / 'service.py')
                     + ' --state /var/lib/goplanska-ytmusic'
                     + ' --credential /run/credentials/goplanska-ytmusic.service/music-hosting\n')
    atomic(backup / 'candidate-dropin.conf', configuration.encode())
    atomic(DROPIN, configuration.encode())
    try:
        run('systemctl', 'daemon-reload')
        run('systemctl', 'restart', UNIT)
        if run('systemctl', 'is-active', UNIT) != 'active':
            raise ValueError('New service failed to become active')
        if run('systemctl', 'show', UNIT, '--property=WorkingDirectory', '--value') != str(release):
            raise ValueError('New service did not select frozen release')
        result.update(activated=True, activated_at_utc=dt.datetime.now(dt.timezone.utc).isoformat(),
                      backup_directory=str(backup), service_restarted=True,
                      client_incremental_stream_runtime_requires_separate_verification=True)
        atomic(backup / 'activation-report.json', (json.dumps(result, indent=2) + '\n').encode())
    except Exception:
        # The original drop-in was absent. Retain the attempted file in this backup.
        DROPIN.replace(backup / 'failed-dropin.conf')
        run('systemctl', 'daemon-reload')
        run('systemctl', 'restart', UNIT)
        raise
    print(json.dumps(result))


if __name__ == '__main__':
    main()
