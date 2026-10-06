from pathlib import Path
import hashlib,json,subprocess,zipfile
r=Path(__file__).resolve().parent;w=r.parent;root=w.parent;c=Path.home()/'.gradle/caches'
cp=[w/'release-v31/server/mods/psychiatryk_roles-2.1.1.jar',c/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar',*w.joinpath('release-v31/server/mods').glob('*.jar'),*c.joinpath('modules-2/files-2.1').rglob('*.jar')]
cp=[p for p in cp if not any(x in str(p) for x in ('-sources.jar','-javadoc.jar','/flywheel-fabric-'))]
(r/'classes').mkdir(exist_ok=True)
source=w/'kukirin/src/pl/aridlin/kukirin/ScooterFrostWalker.java'
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(r/'classes'),str(source)],check=True)
(r/'qa-classes').mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',str(r/'classes')+':'+':'.join(map(str,cp)),'-d',str(r/'qa-classes'),*map(str,r.joinpath('src').rglob('*.java'))],check=True)
base=root/'outputs/save-io-v32/psychiatryk_roles.jar'
with zipfile.ZipFile(base) as z:entries={n:z.read(n) for n in z.namelist()}
with zipfile.ZipFile(w/'release-v31/server/mods/psychiatryk_roles-2.1.1.jar') as z:original={n:z.read(n) for n in z.namelist()}
diffs=[]
for p in (r/'classes').rglob('*.class'):
 name=p.relative_to(r/'classes').as_posix();old=original[name];new=p.read_bytes();entries[name]=new
 diffs.append({'entry':name,'before':hashlib.sha256(old).hexdigest(),'after':hashlib.sha256(new).hexdigest()})
with zipfile.ZipFile(r/'psychiatryk_roles-frost-candidate.jar','w',zipfile.ZIP_DEFLATED) as z:
 for n,b in entries.items():z.writestr(n,b)
with zipfile.ZipFile(r/'frost-qa.jar','w',zipfile.ZIP_DEFLATED) as z:
 z.writestr('META-INF/neoforge.mods.toml','modLoader="javafml"\nloaderVersion="[4,)"\nlicense="MIT"\n[[mods]]\nmodId="goplanska_frost_qa"\nversion="1"\ndisplayName="Isolated high-speed Frost Walker QA"\n')
 for p in (r/'qa-classes').rglob('*.class'):z.write(p,p.relative_to(r/'qa-classes'))
(r/'class-diff.json').write_text(json.dumps(diffs,indent=2));print(json.dumps(diffs,indent=2))
