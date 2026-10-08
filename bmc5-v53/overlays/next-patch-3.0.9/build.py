"""Source-only reproducible seven-overlay build against an explicit baseline and MC SDK."""
from pathlib import Path
import argparse,hashlib,json,shutil,subprocess,zipfile,tomllib
GROUPS=['global-tuning','party-pull','music-background-pause','emf-options-compat','loader-compat','peeb-turn-dye','peeb-terrain-step']
BASE_SHA='56cc13966c2ed202ff54ac9e0b401fce32325842d93fff2be22b1ee14de9b614'
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
def entries(path):
 with zipfile.ZipFile(path) as z:return {n:z.read(n) for n in z.namelist() if not n.endswith('/')}
def build(source_root,base,sdk,out):
 assert sha(base)==BASE_SHA,'Wrong baseline'
 receipt=json.loads((source_root/'source-manifest.json').read_text())
 for name,digest in receipt['sources'].items():assert sha(source_root/name)==digest,'Exported source changed: '+name
 sdk=sdk.read_text().strip().split(':')
 pins=['/datafixerupper/8.0.16/','/authlib/6.0.54/','/brigadier/1.3.10/','/logging/1.2.7/','/gson/2.10.1/','/guava/32.1.2-jre/','/slf4j-api/2.0.9/','/fastutil/8.5.12/']
 libs=[s for s in sdk if Path(s).exists()]
 cp=':'.join([str(base)]+[s for s in libs if any(pin in s for pin in pins)]+libs)
 out.mkdir(parents=True,exist_ok=True);runtime=out/'runtime';shutil.rmtree(runtime,ignore_errors=True);runtime.mkdir()
 baseline=entries(base);final=dict(baseline)
 def run(args,name):
  result=subprocess.run(['nice','-n','19',*args],capture_output=True,text=True);(out/(name+'.log')).write_text(result.stdout+result.stderr)
  if result.returncode:raise RuntimeError(name+': '+result.stderr[-5000:])
  return result.stdout
 for group in GROUPS:
  folder=source_root/'groups'/group;classes=out/group/'classes';classes.mkdir(parents=True,exist_ok=True)
  src=sorted((folder/'src').rglob('*.java'))
  # Group APIs remain binary compatible; no cross-group donor recompilation is needed.
  run(['javac','-J-Xmx256m','--release','21','-proc:none','-cp',cp,'-d',str(classes),*map(str,src)],group+'-compile')
  created={p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
  if group=='global-tuning':
   tools=out/group/'tools';tools.mkdir();run(['javac','-J-Xmx192m','--release','21','-proc:none','-cp',cp,'-d',str(tools),str(folder/'MethodOverlay.java')],group+'-tool')
   for name in ['Scooter','ScooterHandling']:
    entry='pl/aridlin/kukirin/'+name+'.class';original=out/group/(name+'-original.class');original.write_bytes(baseline[entry]);target=out/group/(name+'-final.class')
    run(['java','-Xmx192m','-cp',str(tools)+':'+cp,'MethodOverlay',str(original),str(classes/entry),str(target),name],group+'-'+name)
    created[entry]=target.read_bytes()
   created={n:v for n,v in created.items() if not n.startswith('pl/aridlin/kukirin/Scooter$') and not n.startswith('pl/aridlin/kukirin/ScooterHandling$')}
  elif group=='party-pull':
   tools=out/group/'tools';tools.mkdir();run(['javac','-J-Xmx192m','--release','21','-proc:none','-cp',cp,'-d',str(tools),str(folder/'MethodOverlay.java')],group+'-tool')
   entry='pl/aridlin/psychiatrykroles/peeb/client/PeebClient.class';original=out/group/'PeebClient-original.class';original.write_bytes(baseline[entry]);target=out/group/'PeebClient-final.class'
   run(['java','-Xmx192m','-cp',str(tools)+':'+cp,'MethodOverlay',str(original),str(classes/entry),str(target)],group+'-PeebClient')
   created[entry]=target.read_bytes();created={n:v for n,v in created.items() if not n.startswith('pl/aridlin/psychiatrykroles/peeb/client/PeebClient$') or n.endswith('/PeebClient$Grapple.class')}
  elif group in ['peeb-turn-dye','peeb-terrain-step']:
   tools=out/group/'tools';tools.mkdir();run(['javac','-J-Xmx192m','--release','21','-proc:none','-cp',cp,'-d',str(tools),str(folder/'MethodOverlay.java')],group+'-tool')
   names=[('pl/aridlin/psychiatrykroles/peeb/client/PeebMesh.class','mesh'),('pl/aridlin/psychiatrykroles/peeb/client/PeebRenderer.class','renderer')] if group=='peeb-turn-dye' else [('pl/aridlin/psychiatrykroles/peeb/PeebAdventuresPhysics.class','physics')]
   selected={}
   for entry,mode in names:
    original=out/group/(Path(entry).stem+'-original.class');original.write_bytes(baseline[entry]);target=out/group/(Path(entry).stem+'-final.class')
    run(['java','-Xmx192m','-cp',str(tools)+':'+cp,'MethodOverlay',str(original),str(classes/entry),str(target),mode],group+'-'+mode)
    selected[entry]=target.read_bytes()
   if group=='peeb-turn-dye':
    entry='pl/aridlin/psychiatrykroles/peeb/client/PeebTurning.class';selected[entry]=created[entry]
   created=selected
  for path in sorted((folder/'resources').rglob('*')) if (folder/'resources').exists() else []:
   if path.is_file():created[path.relative_to(folder/'resources').as_posix()]=path.read_bytes()
  expected=receipt['runtime_sha256'][group]
  assert {n:hashlib.sha256(v).hexdigest() for n,v in created.items()}==expected,'Source rebuild differs: '+group
  for n,v in created.items():
   final[n]=v;target=runtime/n;target.parent.mkdir(parents=True,exist_ok=True);target.write_bytes(v)
 meta='META-INF/neoforge.mods.toml';raw=baseline[meta].decode();assert raw.count('version="3.0.8-bmc5"')==6
 parsed=tomllib.loads(raw);assert len(parsed['mods'])==6 and all(m['version']=='3.0.8-bmc5' for m in parsed['mods'])
 raw=raw.replace('version="3.0.8-bmc5"','version="3.0.9-bmc5"')
 for config in ['psychiatryk-music-pause.mixins.json','psychiatryk-emf-options-compat.mixins.json']:raw+='\n[[mixins]]\nconfig="'+config+'"\n'
 final[meta]=raw.encode();updated=tomllib.loads(raw)
 assert all(sum(m['config']==c for m in updated['mixins'])==1 for c in ['psychiatryk-music-pause.mixins.json','psychiatryk-emf-options-compat.mixins.json'])
 destination=out/'psychiatryk_roles-3.0.9-bmc5.jar'
 with zipfile.ZipFile(destination,'w',zipfile.ZIP_DEFLATED) as z:
  for n,v in sorted(final.items()):
   info=zipfile.ZipInfo(n,(2026,10,8,22,0,0));info.compress_type=zipfile.ZIP_DEFLATED;z.writestr(info,v)
 assert sha(destination)==receipt['candidate_sha256'],'Portable candidate differs'
 proof={'success':True,'source_groups_rebuilt':GROUPS,'candidate_sha256':sha(destination),'baseline_sha256':BASE_SHA,'byte_identical':True,'game_launched':False}
 (out/'portable-proof.json').write_text(json.dumps(proof,indent=2)+'\n');return proof
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--source-root',type=Path,default=Path(__file__).resolve().parent);p.add_argument('--base',type=Path,required=True);p.add_argument('--sdk-classpath',type=Path,required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();print(json.dumps(build(a.source_root,a.base,a.sdk_classpath,a.output)))
