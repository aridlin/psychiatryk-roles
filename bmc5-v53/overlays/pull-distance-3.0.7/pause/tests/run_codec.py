from pathlib import Path
import subprocess,json,hashlib
r=Path(__file__).resolve().parent.parent;parts=Path('work/peeb-update-20261008/jukebox-disc/build/classpath.txt').read_text().strip().split(':');base=Path('work/peeb-update-20261008/deployment/import-fix-20261008/release/build/psychiatryk_roles-3.0.6-bmc5.jar');pins=['/datafixerupper/8.0.16/','/authlib/6.0.54/','/brigadier/1.3.10/','/logging/1.2.7/','/gson/2.10.1/','/guava/32.1.2-jre/','/slf4j-api/2.0.9/'];cp=':'.join([str(r/'build/classes'),str(base)]+[p for p in parts if any(pin in p for pin in pins)]+parts);out=r/'build/test-classes';out.mkdir(exist_ok=True)
x=subprocess.run(['nice','-n','19','javac','-J-Xmx256m','--release','21','-proc:none','-cp',cp,'-d',str(out),str(r/'tests/PauseCodecCheck.java')],capture_output=True,text=True);(r/'build/test-compile.log').write_text(x.stdout+x.stderr)
if x.returncode:print(x.stderr[-1500:]);raise SystemExit(x.returncode)
x=subprocess.run(['nice','-n','19','java','-Xmx256m','-cp',str(out)+':'+cp,'pl.aridlin.kukirin.PauseCodecCheck'],capture_output=True,text=True);(r/'build/codec-test.log').write_text(x.stdout+x.stderr)
if x.returncode:print(x.stderr[-2500:]);raise SystemExit(x.returncode)
data=json.loads(x.stdout[x.stdout.rfind('{'):]);data['source_sha256']={str(p.relative_to(r)):hashlib.sha256(p.read_bytes()).hexdigest() for p in (r/'src').rglob('*.java')};(r/'build/codec-test.json').write_text(json.dumps(data,indent=2)+'\n');print(json.dumps({k:v for k,v in data.items() if k!='source_sha256'}))
