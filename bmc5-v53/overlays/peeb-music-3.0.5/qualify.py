#!/usr/bin/env python3
"""Focused actual-class math/bytecode checks; never launches a game."""
from pathlib import Path
import argparse,json,os,re,subprocess,sys,zipfile
ROOT=Path(__file__).resolve().parent
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--checkpoint',type=Path,required=True)
parser.add_argument('--classpath-file',type=Path,required=True)
parser.add_argument('--output-dir',type=Path,default=ROOT/'build')
parser.add_argument('--glslang',help='Optional glslangValidator executable; omitted means GLSL stages not rerun')
args=parser.parse_args();out=args.output_dir.resolve()
subprocess.run([sys.executable,str(ROOT/'build.py'),'--checkpoint',str(args.checkpoint),'--classpath-file',str(args.classpath_file),'--output-dir',str(out)],check=True)
classes=out/'classes';qa=out/'test-classes';qa.mkdir(exist_ok=True)
cp=os.pathsep.join([str(classes),str(args.checkpoint.resolve()),args.classpath_file.read_text().strip()])
subprocess.run(['javac','--release','21','-proc:none','-cp',cp,'-d',str(qa),*map(str,sorted((ROOT/'tests').glob('*.java')))],check=True)
qcp=os.pathsep.join([str(qa),cp])
subprocess.run(['java','-cp',qcp,'RopeHookQA',str(out/'physics-report.json')],check=True)
geometry=subprocess.run(['java','-cp',qcp,'PeebRopeGeometryQA'],check=True,capture_output=True,text=True)
curve=json.loads(geometry.stdout);assert curve['success'] and curve['checks']==28513
(out/'geometry-report.json').write_text(json.dumps(curve,indent=2)+'\n')
physics=json.loads((out/'physics-report.json').read_text());assert physics['success'] and physics['checks']==8218
client=subprocess.check_output(['javap','-classpath',cp,'-c','-p','pl.aridlin.psychiatrykroles.peeb.client.PeebClient'],text=True)
server=subprocess.check_output(['javap','-classpath',cp,'-c','-p','pl.aridlin.psychiatrykroles.peeb.PeebGrapple'],text=True)
def method(text,name):
 match=re.search(r'^  (?:public |private |protected )?(?:static )?[^\n]*\b'+re.escape(name)+r'\([^\n]*\);\n',text,re.M)
 assert match,'Missing compiled method '+name
 end=re.search(r'^  (?:public |private |protected )?(?:static )?[^\n]*\([^\n]*\);\n',text[match.end():],re.M)
 return text[match.start():match.end()+end.start()] if end else text[match.start():]
server_tick=method(server,'tick');prediction=method(client,'predict')
with zipfile.ZipFile(args.checkpoint) as z:base={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
with zipfile.ZipFile(out/'psychiatryk_roles-3.0.5-peeb-rope-dither.jar') as z:candidate={i.filename:z.read(i) for i in z.infolist() if not i.is_dir()}
payloads=[n for n in base if n.startswith('pl/aridlin/psychiatrykroles/peeb/') and ('Payload' in n or n.endswith('PeebPackets.class'))]
checks={
 'server_has_no_additive_pull':'Method pullVelocity:' not in server_tick,
 'server_uses_shared_safety_clamp_once':server_tick.count('Method constrainVelocity:')==1,
 'server_authoritative_reel_once':server_tick.count('Method reelLength:')==1,
 'server_tick_guard_present':'getfield' in server_tick and 'Session.lastPhysicsTick:J' in server_tick and 'putfield' in server_tick,
 'client_tick_guard_present':prediction.count('Field lastPredictionTick:J')==2 and 'lcmp' in prediction,
 'client_single_post_tick_pull_call':client.count('PeebGrapple.pullVelocity:')==1,
 'client_has_no_second_safety_force_call':'PeebGrapple.constrainVelocity:' not in client,
 'client_prediction_is_bound_to_ticks_not_frame':'ClientTickEvent$Post' in prediction and 'Field ticks:J' in prediction,
 'nonelastic_hook_has_no_spring_coefficients':'SPRING_GAIN' not in server and 'RADIAL_DAMPING' not in server,
 'wire_payload_unchanged':bool(payloads) and all(base[n]==candidate[n] for n in payloads),
}
assert all(checks.values()),checks
(out/'timing-report.json').write_text(json.dumps({'success':True,'checks':checks,'native_runtime_verified':False},indent=2)+'\n')
shader_rerun=False
if args.glslang:
 shaders=ROOT.parents[1]/'src/peeb-music-3.0.5/main/resources/assets/psychiatryk_peeb/shaders/core'
 for ext,stage in [('vsh','vert'),('fsh','frag')]:subprocess.run([args.glslang,'-S',stage,str(shaders/('peeb_ps1.'+ext))],check=True)
 shader_rerun=True
report={'success':True,'physics_checks':physics['checks'],'geometry_checks':curve['checks'],'event_bytecode_checks':len(checks),
 'glsl_stages_rerun':shader_rerun,'native_game_launched':False,'human_grapple_feel_verified':False}
(out/'qualification.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report))
