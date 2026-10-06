"""Run one isolated QA case and clean only native threads left after ServerStopped."""
from pathlib import Path
import json
import subprocess
import sys
import time

r=Path(__file__).resolve().parent
mode=sys.argv[1] if len(sys.argv)>1 else 'basic'
stopped=r/'server'/f'save-qa-{mode}-stopped.json'
report=r/'server'/f'save-qa-{mode}-report.json'
stopped.unlink(missing_ok=True)
report.unlink(missing_ok=True)
with (r/f'{mode}-console.log').open('w') as log:
    process=subprocess.Popen([sys.executable,str(r/'run.py'),f'-Dsaveqa.mode={mode}'],stdout=log,stderr=subprocess.STDOUT,stdin=subprocess.PIPE)
    deadline=time.monotonic()+180
    while time.monotonic()<deadline and process.poll() is None and not stopped.exists():
        time.sleep(0.25)
    if stopped.exists():
        # LOWEST-priority ServerStopped listener runs after candidate drain/close.
        status=json.loads(stopped.read_text())
        if any(value for key,value in status.items() if key.endswith('pending_jobs')):
            print('FAIL: storage jobs remain after ServerStopped:',status)
            sys.exit(2)
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            print('ServerStopped confirmed; cleaning stock Sable native UDP threads.')
            process.terminate()
            try: process.wait(timeout=10)
            except subprocess.TimeoutExpired:
                process.kill();process.wait()
    elif process.poll() is None:
        # A failed/hung startup cannot produce a success result.
        process.stdin.write(b'stop\n');process.stdin.flush()
        try: process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
        print('FAIL: timeout before completed server lifecycle')
        sys.exit(3)
    if not report.exists():
        print('FAIL: no QA report:',r/f'{mode}-console.log')
        sys.exit(4)
    result=json.loads(report.read_text())
    print(json.dumps(result,indent=2))
    sys.exit(0 if result['success'] else 1)
