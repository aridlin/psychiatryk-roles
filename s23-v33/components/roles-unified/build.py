from pathlib import Path
import subprocess,zipfile,tomllib,json,hashlib
r=Path(__file__).resolve().parent;w=r.parent;cache=(Path.home() / '.gradle/caches');base=w/'release-v16.2/client/mods/psychiatryk_roles-2.0.0-dev.jar'
mc=cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'
cp=[w/'release-v18/client/mods/jei-1.21.1-neoforge-19.51.0.418.jar',w/'release-v18/client/mods/psychiatryk_roles-2.1.1.jar',r/'accessories-api.jar',w/'release-v31/client/mods/Flashback-0.39.7-for-MC1.21.1.jar',w/'release-v31/client/mods/flashback_neoforge_fixed-1.0.14.jar',w/'release-v31/server/mods/sable-neoforge-1.21.1-2.0.5.jar',w/'party-markers/goplanska-party-markers-2.0.0.jar',mc,base,*w.joinpath('physics-test/deps').glob('*.jar'),*w.joinpath('restore-test/mods').glob('*.jar'),*cache.joinpath('modules-2/files-2.1').rglob('*.jar')]
cp=[p for p in cp if not any(x in str(p) for x in ['-sources.jar','-javadoc.jar','/flywheel-fabric-'])]
cp.insert(0,w/'release-v31/client/mods/pointblank-neoforge-1.21-2.2.0.jar')
veil=r/'build-deps/veil-neoforge-1.21.1-4.3.2.jar';veil.parent.mkdir(exist_ok=True)
with zipfile.ZipFile(w/'release-v31/server/mods/sable-neoforge-1.21.1-2.0.5.jar') as sable:
 veil.write_bytes(sable.read('META-INF/jarjar/veil-neoforge-1.21.1-4.3.2.jar'))
cp=[veil,*[p for p in cp if 'veil-neoforge' not in p.name]]
classes=r/'classes';classes.mkdir(exist_ok=True)
sources=[w/'void-portals/src/pl/aridlin/psychiatrykroles'/n for n in ['VoidDoors.java','VoidTrapdoors.java','ImmersiveVoidPortals.java','VoidExitDirections.java','VoidGive.java']]
sources+=list(r.joinpath('src').rglob('*.java'))
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(map(str,cp)),'-d',str(classes),*map(str,sources)],check=True)
replacements={str(p.relative_to(classes)):p.read_bytes() for p in classes.joinpath('pl').rglob('*.class')}
custom=[w/'kukirin/goplanska-kukirin-1.0.0.jar',w/'admin-spectate/goplanska-admin-spectate-1.0.0.jar',w/'party-markers/goplanska-party-markers-2.0.0.jar',*sorted(w.joinpath('release-v16.2/client/mods').glob('goplanska-*.jar'))]
custom=[p for p in custom if ('kukirin' not in p.name or p.parent==w/'kukirin') and ('admin-spectate' not in p.name or p.parent==w/'admin-spectate') and ('party-markers' not in p.name or p.parent==w/'party-markers')]
entries={};tomls=[];collisions=[]
for p in [base,*custom]:
 with zipfile.ZipFile(p) as z:
  text=z.read('META-INF/neoforge.mods.toml').decode();start=text.index('[[mods]]');tomls.append(text[start:])
  for n in z.namelist():
   if n.endswith('/') or n in ['META-INF/neoforge.mods.toml','META-INF/MANIFEST.MF','LICENSE.txt','LICENSE']:continue
   b=z.read(n)
   if n in entries and entries[n]!=b:collisions.append(n)
   entries[n]=b
assert not collisions,collisions
entries.update(replacements)
for p in r.joinpath('resources').rglob('*'):
 if p.is_file():entries[str(p.relative_to(r/'resources'))]=p.read_bytes()
for lang,label in [('en_us','Marking Torch'),('pl_pl','Pochodnia znacząca')]:
 n='assets/psychiatryk_roles/lang/'+lang+'.json';data=json.loads(entries.get(n,b'{}'));data['block.psychiatryk_roles.marking_torch']=label;data['item.psychiatryk_roles.loot_lens']='Loot Lens' if lang=='en_us' else 'Soczewka łupów';entries[n]=json.dumps(data,ensure_ascii=False).encode()

asm=[w/'restore-test/libraries/org/ow2/asm/asm/9.10.1/asm-9.10.1.jar',w/'restore-test/libraries/org/ow2/asm/asm-tree/9.10.1/asm-tree-9.10.1.jar']
subprocess.run(['javac','-cp',':'.join(map(str,asm)),'-d',str(classes),str(r/'PatchSleep.java')],check=True)
original=r/'roles-original.class';patched=r/'roles-sleep50.class';original.write_bytes(entries['pl/aridlin/psychiatrykroles/PsychiatrykRoles.class'])
subprocess.run(['java','-cp',':'.join(map(str,[classes,*asm])),'PatchSleep',str(original),str(patched)],check=True)
entries['pl/aridlin/psychiatrykroles/PsychiatrykRoles.class']=patched.read_bytes()
metadata='modLoader="javafml"\nloaderVersion="[4,)"\nlicense="GPL-3.0-or-later; see bundled component licenses"\n'+'\n'.join(tomls)
metadata=metadata.replace('version="2.0.0-dev"','version="2.1.1"').replace('Example mod description.','Unified Goplanska roles, void portals, parties and chams, Kinker Starter, KuKirin and admin spectate.')
metadata+='\n[[mixins]]\nconfig="goplanska-villager.mixins.json"\n\n[[mixins]]\nconfig="psychiatryk-save-io.mixins.json"\n\n[[mixins]]\nconfig="psychiatryk-client-perf.mixins.json"\n'
metadata+='\n[[mixins]]\nconfig="psychiatryk-pointblank-mount.mixins.json"\n\n[[mixins]]\nconfig="psychiatryk-pointblank-render.mixins.json"\n'
metadata=metadata.replace('[[mods]]','[[mods]]\nlogoFile="pack-icon.png"')
parsed=tomllib.loads(metadata);entries['META-INF/neoforge.mods.toml']=metadata.encode();entries['META-INF/MANIFEST.MF']=b'Manifest-Version: 1.0\r\n\r\n'
entries['licenses/admin-spectate-GPL-3.0.txt']=(w/'admin-spectate/resources/LICENSE.txt').read_bytes()
entries['goplanska-unified-components.json']=json.dumps({'mods':[m['modId'] for m in parsed['mods']],'inputs':{p.name:hashlib.sha256(p.read_bytes()).hexdigest() for p in [base,*custom]}},indent=2).encode()
out=r/'psychiatryk_roles-2.1.1.jar'
with zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for n,b in entries.items():z.writestr(n,b)
with zipfile.ZipFile(base) as old:
 changed=[n for n in old.namelist() if n.endswith('.class') and old.read(n)!=entries[n]]
 assert all(n.startswith(('pl/aridlin/psychiatrykroles/VoidDoors','pl/aridlin/psychiatrykroles/VoidTrapdoors','pl/aridlin/psychiatrykroles/ImmersiveVoidPortals','pl/aridlin/psychiatrykroles/PsychiatrykRoles.class','pl/aridlin/psychiatrykroles/RestartManager','pl/aridlin/psychiatrykroles/RestartVote','pl/aridlin/psychiatrykroles/VillagerTradeRebalance','pl/aridlin/psychiatrykroles/Wiesniuk.class')) for n in changed),changed
print('Unified jar',out.stat().st_size,'bytes; mods:',[m['modId'] for m in parsed['mods']]);print('Preserved all unrelated roles bytecode; replaced',len(changed),'door classes')
