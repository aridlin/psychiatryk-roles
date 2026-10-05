from pathlib import Path
import subprocess, zipfile
root=Path(__file__).resolve().parent
cache=(Path.home()/'.gradle/caches')
mc=cache/'neoformruntime/intermediate_results/compiledWithNeoForge_7b4a3a861b5f17ef1b7b0ecb17a482eaf112e6f0_output.jar'
cp=[str(root.parent.parent/'outputs/immersive-portals-sable-compat-0.5.1+ip-6.0.7-linux-native.jar'),str(mc),str(root.parent/'new-mods/way-neoforge-1.21.1-2.0.0.jar')]
cp.append(str(root.parent/"kukirin/goplanska-kukirin-1.0.0.jar"))
cp.append(str(root.parent/"client-only-mods/entityculling-neoforge-1.11.2-mc1.21.1.jar"))
cp.append(str(root.parent/"storagefinder-1.0.0.jar"))
cp.append(str(root.parent/"roles-unified/mark-sync-classes"))
cp.append(str(root.parent/"roles-unified/accessories-api.jar"))
cp += sorted(map(str,(cache/'modules-2/files-2.1').rglob('*.jar')))
sync=root.parent/'roles-unified/mark-sync-classes';sync.mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(cp),'-d',str(sync),str(root.parent/'roles-unified/src/pl/aridlin/psychiatrykroles/MarkSync.java'),str(root.parent/'roles-unified/src/pl/aridlin/psychiatrykroles/LootLens.java')],check=True)
cp=[p for p in cp if not any(x in str(p) for x in ['-sources.jar','-javadoc.jar','/flywheel-fabric-'])]
(root/'classes').mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',':'.join(cp),'-d',str(root/'classes'),*map(str,(root/'src').rglob('*.java'))],check=True)
with zipfile.ZipFile(root/'goplanska-party-markers-2.0.0.jar','w',zipfile.ZIP_DEFLATED) as z:
 for folder in ['classes','resources']:
  for p in (root/folder).rglob('*'):
   if p.is_file(): z.write(p,p.relative_to(root/folder))
print('Built', (root/'goplanska-party-markers-2.0.0.jar').stat().st_size, 'bytes')
