from pathlib import Path
import hashlib
import json
import subprocess

root = Path(__file__).resolve().parent
workspace = root.parent.parent
source_root = root.parent / "kukirin/src"
cache = (Path.home() / '.gradle/caches')
mods = root.parent / "release-v31/client/mods"
classpath = [
    root.parent / "kukirin/classes",
    mods / "ShoulderSurfing-NeoForge-1.21.1-5.2.0.jar",
    mods / "spear-backport-neoforge-1.8.0-1.21.1.jar",
    cache / "neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar",
    root.parent / "roles-unified/psychiatryk_roles-2.1.1.jar",
    root.parent / "model-render-research/GemRender/versions/1.21.1/build/libs/gemrender-0.1.6.jar",
]
classpath += sorted(p for p in (cache / "modules-2/files-2.1").rglob("*.jar")
                    if not any(word in p.name for word in ("-sources.jar", "-javadoc.jar", "flywheel-fabric")))
names = ["ScooterShoulderPlugin.java", "ScooterSteeringInput.java", "ScooterSpearLunge.java",
         "mixin/ScooterSpearStabMixin.java", "mixin/ScooterSpearEffectsMixin.java",
         "mixin/ScooterSpearCompatPlugin.java"]
sources = [source_root / "pl/aridlin/kukirin" / name for name in names]
classes = root / "classes"
classes.mkdir(exist_ok=True)
subprocess.run(["javac", "--release", "21", "-proc:none", "-sourcepath", str(source_root),
                "-implicit:none", "-cp", ":".join(map(str, classpath)), "-d", str(classes),
                *map(str, sources)], check=True)
resources = root.parent / "kukirin/resources"
proof = {
    "classes": {str(p.relative_to(classes)): hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(classes.rglob("*.class"))},
    "resources": {name: hashlib.sha256((resources / name).read_bytes()).hexdigest()
                  for name in ("shouldersurfing_plugin.json", "psychiatryk-scooter-spears.mixins.json")},
    "sources": [str(p.relative_to(workspace)) for p in sources],
    "runtime_verified": False,
}
(root / "build-proof.json").write_text(json.dumps(proof, indent=2) + "\n")
print(json.dumps(proof, indent=2))
