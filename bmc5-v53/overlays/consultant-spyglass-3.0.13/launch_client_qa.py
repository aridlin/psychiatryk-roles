#!/usr/bin/env python3
"""Launch the isolated 3.0.13 Prism fixture on an independent X display."""

import os
import pathlib
import subprocess
import time

ROOT = pathlib.Path('/home/aridlin/psychiatryk-3012-static-import-20261009')
INSTANCE = 'psychiatryk-bmc5-3013-jukebox-qa'
QA = pathlib.Path('/home/aridlin/psychiatryk-qa-3013-spyglass-20261009')
LOG = QA / 'client-qa-prism.log'
DISPLAY = ':96'

if not (ROOT / 'instances' / INSTANCE / 'minecraft' / 'mods').is_dir():
    raise SystemExit('Missing isolated client instance')
display_running = subprocess.run(['pgrep', '-f', 'Xvfb :96'], capture_output=True).returncode == 0

QA.mkdir(parents=True, exist_ok=True)
with LOG.open('wb') as output:
    if not display_running:
        xvfb = subprocess.Popen(['/usr/bin/Xvfb', DISPLAY, '-screen', '0', '1280x720x24', '-nolisten', 'tcp'],
                                stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
        time.sleep(2)
        if xvfb.poll() is not None:
            raise SystemExit(f'Xvfb exited {xvfb.returncode}')
    env = dict(os.environ, DISPLAY=DISPLAY, QT_QPA_PLATFORM='xcb', LIBGL_ALWAYS_SOFTWARE='1')
    prism = subprocess.Popen(['/usr/bin/prismlauncher', '-d', str(ROOT), '-l', INSTANCE, '-a', 'aridlin'],
                             env=env, stdout=output, stderr=subprocess.STDOUT, start_new_session=True)
print(f'Xvfb existing={display_running}; Prism pid={prism.pid}; log={LOG}')
