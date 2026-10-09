#!/usr/bin/env python3
"""Focused, memory-bounded generic item and runtime-View regression checks."""
from __future__ import annotations

import hashlib
import json
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
BUILD = ROOT / "build"
JAR = BUILD / "psychiatryk_roles-3.0.10-bmc5.jar"
EXPECTED = "eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12"


def main() -> None:
    actual = hashlib.sha256(JAR.read_bytes()).hexdigest()
    if actual != EXPECTED:
        raise SystemExit(f"Canonical JAR hash changed: {actual}")
    output = BUILD / "tests-item-visuals"
    output.mkdir(parents=True, exist_ok=True)
    cp = f"{JAR}:{(BUILD / 'classpath.txt').read_text().strip()}"
    compile_result = subprocess.run(["/usr/lib/jvm/java-21-openjdk/bin/javac", "-J-Xmx256m",
        "--release", "21", "-proc:none", "-cp", cp, "-d", str(output),
        str(ROOT / "tests/RuntimeItemVisualsTest.java")], capture_output=True, text=True)
    (output / "compile.log").write_text(compile_result.stdout + compile_result.stderr)
    if compile_result.returncode:
        raise SystemExit(compile_result.stderr)
    model = ROOT / "resources/assets/psychiatryk_runtime/models/item/runtime_item.json"
    test = subprocess.run(["/usr/lib/jvm/java-21-openjdk/bin/java", "-Xms32m", "-Xmx128m",
        "-cp", f"{output}:{cp}", "pl.aridlin.psychiatrykroles.runtime.RuntimeItemVisualsTest",
        str(model)], capture_output=True, text=True)
    (output / "test.log").write_text(test.stdout + test.stderr)
    success = test.returncode == 0 and "RUNTIME_ITEM_VISUALS_PASS checks=11" in test.stdout
    proof = {"success": success, "canonicalJarSha256": actual, "checks": 11 if success else None,
             "exitCode": test.returncode, "clientRenderVerified": False, "deployed": False}
    (output / "results.json").write_text(json.dumps(proof, indent=2) + "\n")
    print(test.stdout + test.stderr, end="")
    if not success:
        raise SystemExit(1)


if __name__ == "__main__":
    main()
