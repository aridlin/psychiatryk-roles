#!/usr/bin/env python3
"""Compile the complete runtime source and run bounded, client-free signal/HUD tests."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import shutil
import subprocess


EXPECTED_BASE = "eb4b1685205ba1ac3432b1708572d829e9004193ca8857b8b05798bc48f6ce12"
TESTS = [
    "SignalProtocolTest", "SignalStateTest", "SignalValuesTest", "SignalRateTest",
    "SignalHudCompatibilityTest", "HudProtocolTest",
    "pl.aridlin.fixture.HudMotionTest", "pl.aridlin.fixture.VisualExpressionsTest",
]


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", type=Path, required=True, help="exact canonical 3.0.10 JAR")
    parser.add_argument("--sdk", type=Path, required=True, help="sdk-clean.txt classpath")
    parser.add_argument("--output", type=Path, default=Path(__file__).resolve().parents[1] / "build/signal-verification")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    if hashlib.sha256(args.base.read_bytes()).hexdigest() != EXPECTED_BASE:
        raise SystemExit("Expected the pinned canonical 3.0.10 base JAR")
    sdk = args.sdk.read_text().strip()
    if not sdk:
        raise SystemExit("Empty SDK classpath")
    java_home = Path("/usr/lib/jvm/java-21-openjdk/bin")
    javac = str(java_home / "javac") if (java_home / "javac").is_file() else "javac"
    java = str(java_home / "java") if (java_home / "java").is_file() else "java"
    classes = args.output / "classes"
    shutil.rmtree(classes, ignore_errors=True)
    classes.mkdir(parents=True)
    cp = f"{args.base.resolve()}:{sdk}"
    sources = sorted((root / "src").rglob("*.java"))
    tests = [root / "tests" / f"{name.rsplit('.', 1)[-1]}.java" for name in TESTS]
    subprocess.run([javac, "-J-Xmx384m", "--release", "21", "-proc:none", "-cp", cp,
                    "-d", str(classes), *map(str, sources + tests)], check=True)
    for test in TESTS:
        subprocess.run([java, "-Xmx256m", "-XX:-UsePerfData", "-cp", f"{classes}:{cp}", test], check=True)

    # The unchanged JAR must load first for this deliberately old-client parser test.
    legacy = args.output / "legacy"
    shutil.rmtree(legacy, ignore_errors=True)
    legacy.mkdir(parents=True)
    subprocess.run([javac, "-J-Xmx256m", "--release", "21", "-proc:none", "-cp", cp,
                    "-d", str(legacy), str(root / "tests/HudLegacyCompatibilityTest.java")], check=True)
    subprocess.run([java, "-Xmx256m", "-XX:-UsePerfData", "-cp", f"{cp}:{legacy}",
                    "HudLegacyCompatibilityTest"], check=True)
    print(f"SIGNAL_VERIFICATION_PASS sources={len(sources)} tests={len(TESTS) + 1}")


if __name__ == "__main__":
    main()
