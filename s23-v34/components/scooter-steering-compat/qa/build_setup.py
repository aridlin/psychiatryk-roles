from pathlib import Path
import hashlib
import json
import shutil
import subprocess
import sys
import zipfile

root = Path(__file__).resolve().parent
workspace = root.parents[2]
candidate = Path(sys.argv[1]) if len(sys.argv) > 1 else workspace / "outputs/steering-v33/psychiatryk_roles.jar"
cache = (Path.home() / '.gradle/caches')
classpath = [candidate, cache / "neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar",
             *workspace.joinpath("work/release-v31/server/mods").glob("*.jar"),
             *cache.joinpath("modules-2/files-2.1").rglob("*.jar")]
classpath = [p for p in classpath if not any(s in str(p) for s in ("-sources.jar", "-javadoc.jar", "flywheel-fabric"))]
classes = root / "classes"
classes.mkdir(exist_ok=True)
subprocess.run(["javac", "--release", "21", "-proc:none", "-cp", ":".join(map(str, classpath)),
                "-d", str(classes), *map(str, (root / "src").rglob("*.java"))], check=True)
helper = root / "scooter-compat-qa.jar"
with zipfile.ZipFile(helper, "w", zipfile.ZIP_DEFLATED) as archive:
    archive.writestr("META-INF/neoforge.mods.toml", 'modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="goplanska_scooter_compat_qa"\nversion="1"\ndisplayName="Isolated scooter compatibility QA"\n')
    for path in classes.rglob("*.class"):
        archive.write(path, path.relative_to(classes))
server = root / "server"
if not server.exists():
    source = workspace / "work/save-io-qa/server"
    server.mkdir()
    (server / "libraries").symlink_to((source / "libraries").resolve(), target_is_directory=True)
    (server / "mods").mkdir()
    for path in (source / "mods").glob("*.jar"):
        if path.name in ("save-io-qa.jar", "psychiatryk_roles-2.1.1.jar"):
            continue
        (server / "mods" / path.name).symlink_to(path.resolve())
    for name in ("config", "defaultconfigs"):
        shutil.copytree(source / name, server / name)
    (server / "eula.txt").write_text("eula=true\n")
    properties = (source / "server.properties").read_text().replace("server-port=25593", "server-port=25599").replace("query.port=25593", "query.port=25599").replace("ISOLATED SAVE I/O QA - NOT PRODUCTION", "ISOLATED SCOOTER COMPAT QA - NOT PRODUCTION")
    (server / "server.properties").write_text(properties)
    voice = server / "config/voicechat/voicechat-server.properties"
    if voice.exists():
        voice.write_text(voice.read_text().replace("port=25594", "port=25600"))
    (server / "config/spark/config.json").write_text(json.dumps({"backgroundProfiler": False}))
shutil.copy2(candidate, server / "mods/psychiatryk_roles-2.1.1.jar")
shutil.copy2(helper, server / "mods/scooter-compat-qa.jar")
digest = hashlib.sha256(candidate.read_bytes()).hexdigest()
(root / "candidate-sha256.txt").write_text(digest + "\n")
print("Prepared isolated fixture", digest)
