from pathlib import Path
import subprocess,zipfile,os,shutil
root=Path(__file__).resolve().parent
cache=Path.home()/'.gradle/caches'
native=next((cache/'neoformruntime/intermediate_results').glob('compiledWithNeoForge_*_output.jar'))
libs=[p for p in (cache/'modules-2/files-2.1').rglob('*.jar') if not any(s in str(p) for s in ['/net.minecraftforge/','/org.spongepowered/mixin/','sources.jar','javadoc.jar'])]
cp=os.pathsep.join(map(str,[native,*libs]));(root/'build').mkdir(exist_ok=True)
(root/'build/classpath.txt').write_text(cp)
shutil.rmtree(root/"build/classes",ignore_errors=True)
subprocess.run(['/usr/lib/jvm/java-21-openjdk/bin/javac','-J-Xmx384m','--release','21','-proc:none','-cp',cp,'-d',str(root/'build/classes'),*map(str,(root/'src').rglob('*.java'))],check=True)
with zipfile.ZipFile(root/'build/psychiatryk-unlocks-1.0.0.jar','w',zipfile.ZIP_DEFLATED) as z:
 for folder in [root/'build/classes',root/'resources']:
  for p in sorted(folder.rglob('*')):
   if p.is_file():z.write(p,p.relative_to(folder))
print(root/'build/psychiatryk-unlocks-1.0.0.jar')
