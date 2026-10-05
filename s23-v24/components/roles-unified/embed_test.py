from pathlib import Path
import zipfile
r=Path(__file__).resolve().parent;out=r/'server-test/mods/psychiatryk_roles-2.1.1.jar'
with zipfile.ZipFile(r/'psychiatryk_roles-2.1.1.jar') as old,zipfile.ZipFile(out,'w',zipfile.ZIP_DEFLATED) as z:
 for e in old.infolist():
  b=old.read(e.filename)
  if e.filename=='META-INF/neoforge.mods.toml':b+=b'\n[[mods]]\nmodId="goplanska_unified_check"\nversion="1.0"\ndisplayName="Isolated Unified Check"\n'
  z.writestr(e,b)
 for p in (r/'test-classes').rglob('*.class'):z.write(p,p.relative_to(r/'test-classes'))
p=r/'server-test/mods/goplanska-unified-check.jar'
if p.exists():p.rename(r/'test-helper-unused.jar')
