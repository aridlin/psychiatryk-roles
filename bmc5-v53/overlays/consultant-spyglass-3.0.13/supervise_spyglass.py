#!/usr/bin/env python3
"""Bounded, isolated full-pack NeoForge dedicated-server startup smoke."""

from __future__ import annotations

import hashlib
import json
import os
from pathlib import Path
import re
import signal
import socket
import subprocess
import time


ROOT = Path(__file__).resolve().parent
JAVA = Path("/home/aridlin/.local/share/PrismLauncher/java/java-runtime-delta/bin/java")
CANDIDATE = "psychiatryk_roles-3.0.13-bmc5.jar"
CANDIDATE_SHA256 = "58968efafbfb46a3d929b5fe2ebbf3baacd495fe5c94802448483d4bcf01014c"
HELPER_SHA256 = "4e57e91706e38a6839a327d44312ca3358a40a53ccfd238f5b8aac18db4f9d1c"
PORT = 25596
MAX_SECONDS = 600
MAX_POST_DONE_SECONDS = 180
MIN_START_KIB = 8_000_000
STOP_KIB = 4_000_000
ADDRESS_SPACE_BYTES = 6_500_000_000
DONE = re.compile(r"\[minecraft/DedicatedServer\]: Done \([0-9.]+s\)!")
RUNTIME = "[Psychiatryk Runtime] schema=1"


def available_kib() -> int:
    for line in Path("/proc/meminfo").read_text(encoding="ascii").splitlines():
        if line.startswith("MemAvailable:"):
            return int(line.split()[1])
    raise RuntimeError("MemAvailable unavailable")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def validate_fixture() -> None:
    expected = json.loads((ROOT / "source-mod-sha256.json").read_text())
    found = {path.name for path in (ROOT / "mods").glob("*.jar")}
    if len(expected) != 297 or found != set(expected) | {CANDIDATE, "goplanska-spyglass-check.jar"}:
        raise RuntimeError(f"Unexpected mods: expected 299 JARs, found {len(found)}")
    for name, digest in expected.items():
        if sha256(ROOT / "mods" / name) != digest:
            raise RuntimeError(f"Mod hash mismatch: {name}")
    if sha256(ROOT / "mods" / CANDIDATE) != CANDIDATE_SHA256:
        raise RuntimeError("Candidate hash mismatch")
    if sha256(ROOT / "mods/goplanska-spyglass-check.jar") != HELPER_SHA256:
        raise RuntimeError("Test helper hash mismatch")
    props = (ROOT / "server.properties").read_text()
    for line in ("server-ip=127.0.0.1", f"server-port={PORT}",
                 "level-name=qa-3013-spyglass-world", "enable-rcon=false",
                 "enable-query=false"):
        if line not in props.splitlines():
            raise RuntimeError(f"Missing server property: {line}")
    web = json.loads((ROOT / "kubejs/config/web_server.json").read_text())
    automodpack = json.loads((ROOT / "automodpack/automodpack-server.json").read_text())
    if web.get("enabled") is not False or automodpack.get("modpackHost") is not False:
        raise RuntimeError("Extra pack network services must be disabled")
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", PORT))


def stop_group(server: subprocess.Popen) -> None:
    try:
        os.killpg(server.pid, signal.SIGTERM)
    except ProcessLookupError:
        return
    try:
        server.wait(timeout=15)
    except subprocess.TimeoutExpired:
        try:
            os.killpg(server.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        server.wait(timeout=5)


def save_status(**fields: object) -> None:
    (ROOT / "status.json").write_text(json.dumps(fields, indent=2) + "\n")


def main() -> int:
    validate_fixture()
    first = available_kib()
    if first < MIN_START_KIB:
        raise RuntimeError(f"Host MemAvailable too low: {first // 1024} MiB")
    cpus = sorted(os.sched_getaffinity(0))[-2:]
    qa_home = ROOT / "qa-home"
    cmd = ["/usr/bin/nice", "-n", "10", "/usr/bin/ionice", "-c", "3",
           "/usr/bin/taskset", "-c", ",".join(map(str, cpus)),
           "/usr/bin/prlimit", f"--as={ADDRESS_SPACE_BYTES}", "--", str(JAVA),
           f"-Duser.home={qa_home}",
           "-Xms256m", "-Xmx3G", "-Xss768k", "-XX:ActiveProcessorCount=2",
           "-XX:MaxMetaspaceSize=768m", "-XX:CompressedClassSpaceSize=256m",
           "-XX:MaxDirectMemorySize=256m", "-XX:ReservedCodeCacheSize=128m",
           "@libraries/net/neoforged/neoforge/21.1.252/unix_args.txt", "nogui"]
    env = dict(os.environ, HOME=str(qa_home))
    qa_home.mkdir(exist_ok=True)
    print(f"Starting full-pack QA: candidate={CANDIDATE_SHA256}, mods=299, "
          f"port=127.0.0.1:{PORT}, Xmx=3G, AS<=6.5GB, CPUs={cpus}, "
          f"available={first // 1024} MiB", flush=True)
    start = time.monotonic()
    with (ROOT / "console.log").open("wb") as output:
        server = subprocess.Popen(cmd, cwd=ROOT, env=env, stdin=subprocess.PIPE,
                                  stdout=output, stderr=subprocess.STDOUT,
                                  start_new_session=True)
        save_status(state="running", pid=server.pid, candidate_sha256=CANDIDATE_SHA256,
                    startup_available_kib=first, port=PORT, cpu_affinity=cpus)
        done_at = None
        runtime_seen = False
        spyglass_seen = False
        network_ok = False
        auxiliary_udp: list[str] = []
        stop_sent = False
        reason = ""
        try:
            while server.poll() is None:
                elapsed = time.monotonic() - start
                available = available_kib()
                log = (ROOT / "console.log").read_text(errors="replace")
                if done_at is None and DONE.search(log):
                    done_at = elapsed
                    print(f"DONE at {elapsed:.1f}s", flush=True)
                    network = subprocess.run(["/usr/bin/ss", "-ltnup"], capture_output=True,
                                             text=True, check=False)
                    listeners = [line for line in network.stdout.splitlines()
                                 if f"pid={server.pid}," in line]
                    (ROOT / "network-at-done.txt").write_text("\n".join(listeners) + "\n")
                    network_ok = network.returncode == 0 and any(
                        line.startswith("tcp") and f"127.0.0.1:{PORT}" in line
                        for line in listeners)
                    for line in listeners:
                        address = line.split()[4]
                        if line.startswith("udp"):
                            auxiliary_udp.append(address)
                        elif not (address.startswith("127.") or address.startswith("[::1]:")
                                  or address.startswith("::1:")):
                            network_ok = False
                            reason = f"non-loopback TCP listener: {address}"
                    if not network_ok and not reason:
                        reason = "QA listener missing or not proven on loopback"
                    if reason:
                        break
                if "SPYGLASS_CHECK PASS" in log and not spyglass_seen:
                    spyglass_seen = True
                    print(f"SPYGLASS CHECK at {elapsed:.1f}s", flush=True)
                if "SPYGLASS_CHECK FAIL" in log:
                    reason = "functional spyglass assertion failed"
                    break
                if RUNTIME in log and not runtime_seen:
                    runtime_seen = True
                    print(f"RUNTIME MARKER at {elapsed:.1f}s", flush=True)
                if available < STOP_KIB:
                    reason = f"host memory guard: {available // 1024} MiB"
                    break
                if elapsed >= MAX_SECONDS:
                    reason = "900-second timeout"
                    break
                if done_at is not None and not stop_sent and (
                    (runtime_seen and spyglass_seen and elapsed >= done_at + 10)
                    or elapsed >= done_at + MAX_POST_DONE_SECONDS
                ):
                    server.stdin.write(b"stop\n")
                    server.stdin.flush()
                    stop_sent = True
                    print(f"SENT STOP at {elapsed:.1f}s", flush=True)
                if stop_sent and elapsed >= (done_at or elapsed) + MAX_POST_DONE_SECONDS + 90:
                    reason = "clean-stop timeout"
                    break
                time.sleep(2)
            if reason:
                print(f"FORCED STOP: {reason}", flush=True)
                stop_group(server)
            else:
                server.wait()
        except BaseException:
            stop_group(server)
            raise
    log = (ROOT / "console.log").read_text(errors="replace")
    clean = stop_sent and server.returncode == 0 and "Stopping server" in log and "Saving worlds" in log
    passed = bool(DONE.search(log) and runtime_seen and spyglass_seen and clean and network_ok and not reason)
    save_status(state="finished", passed=passed, exit_code=server.returncode,
                done=bool(DONE.search(log)), runtime_marker=runtime_seen, spyglass_check=spyglass_seen,
                clean_stop=clean, minecraft_tcp_bound_loopback=network_ok,
                auxiliary_udp_listeners=auxiliary_udp,
                stop_sent=stop_sent, reason=reason,
                elapsed_seconds=round(time.monotonic() - start, 1),
                candidate_sha256=CANDIDATE_SHA256, port=PORT, cpu_affinity=cpus)
    print(f"RESULT passed={passed} done={bool(DONE.search(log))} "
          f"runtime={runtime_seen} spyglass={spyglass_seen} clean_stop={clean} loopback={network_ok} "
          f"exit={server.returncode} "
          f"reason={reason or 'none'}", flush=True)
    return 0 if passed else 1


if __name__ == "__main__":
    raise SystemExit(main())
