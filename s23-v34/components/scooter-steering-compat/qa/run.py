from pathlib import Path
import json
import subprocess
import time

root = Path(__file__).resolve().parent
server = root / "server"
report = server / "scooter-compat-report.json"
stopped = server / "scooter-compat-stopped.json"
for path in (report, stopped):
    path.unlink(missing_ok=True)
with (root / "console-private.log").open("w") as log:
    process = subprocess.Popen([
        str(Path.home() / '.local/share/PrismLauncher/java/java-runtime-delta/bin/java'),
        "-Xms1G", "-Xmx4G", "@libraries/net/neoforged/neoforge/21.1.252/unix_args.txt", "nogui",
    ], cwd=server, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.PIPE)
    deadline = time.monotonic() + 210
    while time.monotonic() < deadline and process.poll() is None and not stopped.exists():
        time.sleep(.25)
    if stopped.exists():
        try:
            process.wait(timeout=3)
        except subprocess.TimeoutExpired:
            process.terminate()
            try:
                process.wait(timeout=15)
            except subprocess.TimeoutExpired:
                process.kill()
                process.wait()
    elif process.poll() is None:
        process.stdin.write(b"stop\n")
        process.stdin.flush()
        try:
            process.wait(timeout=15)
        except subprocess.TimeoutExpired:
            process.kill()
            process.wait()
    if not report.exists():
        raise RuntimeError("No fixture report; inspect the private console log")
    data = json.loads(report.read_text())
    data["normal_server_stopped"] = stopped.exists()
    data["candidate_sha256"] = (root / "candidate-sha256.txt").read_text().strip()
    (root / "runtime-report.json").write_text(json.dumps(data, indent=2) + "\n")
    print(json.dumps(data, indent=2))
    assert data["success"] and stopped.exists(), "Actual scooter server compatibility fixture failed"
