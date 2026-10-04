from pathlib import Path
import subprocess,zipfile,hashlib,json
root=Path(__file__).resolve().parent
baseline=Path('/home/aridlin/Documents/Codex/psychiatryk-migration-staging/release-v8-20261002/psychiatryk_roles-2.0.0-dev.jar')
cache=Path('/home/aridlin/.gradle/caches')
mc=cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'
meta=json.loads(Path('/home/aridlin/.local/share/PrismLauncher/meta/net.minecraft/1.21.1.json').read_text())
vanilla=[]
for lib in meta['libraries']:
 parts=lib['name'].split(':'); group,artifact,version=parts[:3]; classifier='-'+parts[3] if len(parts)>3 else ''
 path=Path('/home/aridlin/.local/share/PrismLauncher/libraries')/group.replace('.','/')/artifact/version/(artifact+'-'+version+classifier+'.jar')
 if path.exists(): vanilla.append(str(path))
cp=':'.join([str(mc),*vanilla,str(baseline),*sorted(map(str,(cache/'modules-2/files-2.1').rglob('*.jar')))])
(root/'classes').mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',cp,'-d',str(root/'classes'),*map(str,(root/'src').rglob('*.java')),*map(str,(root/'test').rglob('*.java'))],check=True)
testmc=cache/'neoformruntime/intermediate_results/rename_efa20955c81d1ac56b640006e618f58ef4dd804d_output.jar'
runtimecp=':'.join([str(root/'classes'),str(testmc),str(cache/'neoformruntime/artifacts/minecraft_1.21.1_client.jar'),*vanilla])
result=subprocess.run(['java','-cp',runtimecp,'ChatTransportTest'],capture_output=True,text=True)
print(result.stdout)
if result.returncode: print(result.stderr[:5000]); raise SystemExit(result.returncode)
entry='pl/aridlin/psychiatrykroles/ChatBook.class'
out=root/'psychiatryk_roles-2.0.0-dev.jar'
with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as new:
 for item in old.infolist(): new.writestr(item,(root/'classes'/entry).read_bytes() if item.filename==entry else old.read(item.filename))
with zipfile.ZipFile(baseline) as old,zipfile.ZipFile(out) as new:
 assert new.testzip() is None
 changed=[n for n in old.namelist() if old.read(n)!=new.read(n)]
 assert changed==[entry],changed
print('Only ChatBook.class changed')
print('Built',out.stat().st_size,'bytes SHA256',hashlib.sha256(out.read_bytes()).hexdigest())
