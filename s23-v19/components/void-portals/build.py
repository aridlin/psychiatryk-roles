from pathlib import Path
import subprocess,zipfile,hashlib
r=Path(__file__).resolve().parent;cache=Path('/home/aridlin/.gradle/caches');base=r.parent/'chatbook-reset/psychiatryk_roles-2.0.0-dev.jar'
mc=cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'
cp=[mc,base,*r.parent.joinpath('physics-test/deps').glob('*.jar'),*r.parent.joinpath('restore-test/mods').glob('*.jar'),*cache.joinpath('modules-2/files-2.1').rglob('*.jar')]
(r/'classes').mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(r/'classes'),*map(str,(r/'src').rglob('*.java'))],check=True)
replacements={str(p.relative_to(r/'classes')):p.read_bytes() for p in (r/'classes').rglob('*.class')}
replacements.update({str(p.relative_to(r/'resources')):p.read_bytes() for p in (r/'resources').rglob('*') if p.is_file()})
out=r/'psychiatryk_roles-2.0.0-dev.jar'
with zipfile.ZipFile(base) as old,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for e in old.infolist():z.writestr(e,replacements.pop(e.filename,old.read(e.filename)))
 for name,data in replacements.items():z.writestr(name,data)
with zipfile.ZipFile(out) as z:assert z.testzip() is None
print(out,hashlib.sha256(out.read_bytes()).hexdigest())
