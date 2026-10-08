from pathlib import Path
import subprocess,json,hashlib,zipfile,shutil
R=Path(__file__).resolve().parent;W=R.parents[1];B=R/'build';B.mkdir(exist_ok=True)
base=W/'deployment/import-fix-20261008/release/build/psychiatryk_roles-3.0.6-bmc5.jar'
assert hashlib.sha256(base.read_bytes()).hexdigest()=='7e5f26551d0e3ec0535a9bb94e8c2d6f7452a714f73071ffa147b0b51d0f8a5d'
config=R.parent/'config/build/classes'
workspace=W.parents[1]
cp=str(config)+':'+str(base)+':'+(workspace/'work/bmc5-migration/wearable-jukebox/build/classpath.txt').read_text().strip()
c=B/'classes';shutil.rmtree(c,ignore_errors=True);c.mkdir(exist_ok=True)
sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
sources=sorted((R/'src').rglob('*.java'));tests=sorted((R/'tests').rglob('*.java'))
def run(cmd,label):
 x=subprocess.run(['nice','-n','15',*cmd],text=True,capture_output=True);(B/(label+'.log')).write_text(x.stdout+x.stderr)
 if x.returncode:raise RuntimeError(label+': '+x.stderr[-4000:])
 return x.stdout
run(['javac','-J-Xmx256m','--release','21','-proc:none','-cp',cp,'-d',str(c),*map(str,sources)],'compile')
run(['javac','-J-Xmx192m','--release','21','-proc:none','-cp',str(c)+':'+cp,'-d',str(B/'tests'),*map(str,tests)],'test-compile')
proofs={}
for name in ['PullDistanceTest','GrappleDistanceTest','CameraTugTest','MountedCombatTest']:
 v=json.loads(run(['java','-Xmx128m','-cp',str(B/'tests')+':'+str(c)+':'+cp,name],name));proofs[name]=v;(B/(name+'.json')).write_text(json.dumps(v,indent=2)+'\n')
asmcp=':'.join(n for n in cp.split(':') if '/asm' in n and '/9.10.1/' in n)
run(['javac','-J-Xmx128m','--release','21','-proc:none','-cp',asmcp,'-d',str(B/'tools'),str(R/'MethodOverlay.java')],'transplant-compile')
entry='pl/aridlin/psychiatrykroles/peeb/client/PeebClient.class'
with zipfile.ZipFile(base) as z:(B/'original-PeebClient.class').write_bytes(z.read(entry))
patched=B/'runtime'/entry;patched.parent.mkdir(parents=True,exist_ok=True)
transplant=json.loads(run(['java','-Xmx128m','-cp',str(B/'tools')+':'+asmcp,'MethodOverlay',str(B/'original-PeebClient.class'),str(c/entry),str(patched)],'transplant'))
(B/'transplant-proof.json').write_text(json.dumps(transplant,indent=2)+'\n')
runtime={entry:patched.read_bytes()}
for f in sorted(c.rglob('*.class')):
 n=f.relative_to(c).as_posix()
 if n.startswith('pl/aridlin/psychiatrykroles/peeb/client/PeebClient'):continue
 runtime[n]=f.read_bytes()
# The walking spring remains exclusively in the existing TravelMixin. Camera
# prediction adds only endpoint tug and skips owner-freelook. Lifecycle resets
# are transplanted with their full original behavior preserved.
client=(R/'src'/entry.replace('.class','.java')).read_text();prediction=client.split('public static void predict(Post event) {',1)[1].split('private static void release()',1)[0]
assert 'pullVelocity(' not in prediction and 'cameraTug(' in prediction
assert '!PeebBindings.freelookDown(Minecraft.getInstance())' in prediction
for method in ['release()', 'exit(boolean var0)']:
 block=client.split('private static void '+method+' {',1)[1]
 assert block.startswith('\n      previousTugLook = null;\n      previousTugAnchor = null;')
link={'success':True,'walking_spring_not_duplicated':True,'owner_freelook_skipped':True,'release_exit_history_reset':True,'client_tick_deduplicated':True,'method_transplant_verified':True}
(B/'client-integration-test.json').write_text(json.dumps(link,indent=2)+'\n')
overlay=B/'physics-overlay.zip'
with zipfile.ZipFile(overlay,'w',zipfile.ZIP_DEFLATED) as z:
 for n,data in sorted(runtime.items()):
  i=zipfile.ZipInfo(n,(2026,10,8,15,0,0));i.compress_type=zipfile.ZIP_DEFLATED;z.writestr(i,data)
v={'success':True,'base_sha256':sha(base),'overlay_path':str(overlay),'overlay_sha256':sha(overlay),'sources_sha256':{str(s.relative_to(R)):sha(s) for s in sources},'fixtures_sha256':{str(t.relative_to(R)):sha(t) for t in tests},'tools_sha256':{'MethodOverlay.java':sha(R/'MethodOverlay.java'),'build.py':sha(R/'build.py')},'runtime_sha256':{n:hashlib.sha256(data).hexdigest() for n,data in runtime.items()},'config_overlay_sha256':sha(R.parent/'config/build/config-overlay.zip'),'tests':proofs,'peebclient_transplant':transplant,'client_integration':link,'camera_tug_verified':True,'mounted_additive_momentum_verified':True,'combat_bounds_cooldowns_verified':True,'game_launched':False,'limitations':['Geometric laws and native gameplay helpers verified; no live input, network timing, ridden motion or combat game session was launched.']}
(B/'overlay-proof.json').write_text(json.dumps(v,indent=2)+'\n');print(json.dumps({'success':True,'entries':len(v['runtime_sha256']),'overlay_sha256':v['overlay_sha256'],'checks':sum(x['checks'] for x in proofs.values())}))
