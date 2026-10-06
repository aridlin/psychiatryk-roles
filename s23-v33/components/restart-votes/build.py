from pathlib import Path
import subprocess,zipfile
r=Path(__file__).resolve().parent;cache=Path('/home/aridlin/.gradle/caches'); original=r.parent/'release-v15/server/mods/psychiatryk_roles-2.0.0-dev.jar'
cp=[original,cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar',*cache.joinpath('modules-2/files-2.1').rglob('*.jar')]
(r/'classes').mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(r/'classes'),*map(str,r.joinpath('src').rglob('*.java'))],check=True)
with zipfile.ZipFile(original) as old,zipfile.ZipFile(r/'psychiatryk_roles-2.0.0-dev.jar','w',zipfile.ZIP_DEFLATED) as out:
 for n in old.namelist():
  if not (n.startswith('pl/aridlin/psychiatrykroles/RestartManager') or n.startswith('pl/aridlin/psychiatrykroles/RestartVote') or n.startswith('pl/aridlin/psychiatrykroles/VillagerTradeRebalance') or n.startswith('pl/aridlin/psychiatrykroles/Wiesniuk')):out.writestr(n,old.read(n))
 for p in r.joinpath('classes').rglob('*.class'):out.write(p,p.relative_to(r/'classes'))
print('Built restart vote roles JAR')
