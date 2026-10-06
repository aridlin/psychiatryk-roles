"""Execute pure reverse outcomes against candidate bytecode, never shadow it with production source."""
from pathlib import Path
import argparse,hashlib,json,subprocess
qa=Path(__file__).resolve().parent
p=argparse.ArgumentParser();p.add_argument('--candidate',type=Path,required=True);a=p.parse_args();candidate=a.candidate.resolve()
classes=qa/'unit-classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','--release','21','-proc:none','-cp',str(candidate),'-d',str(classes),str(qa/'ReverseHandlingQA.java')],check=True)
java=(Path.home() / '.local/share/PrismLauncher/java/java-runtime-delta/bin/java')
run=subprocess.run([str(java),'-cp',str(candidate)+':'+str(classes),'ReverseHandlingQA'],text=True,capture_output=True)
(qa/'unit-output.txt').write_text(run.stdout+run.stderr);run.check_returncode()
checks=[{'name':line.partition('\t')[2],'passed':True} for line in run.stdout.splitlines() if line.startswith('CHECK\t')]
report={'success':True,'candidate_sha256':hashlib.sha256(candidate.read_bytes()).hexdigest(),'checks':checks,'passed':len(checks),'scope':'Actual candidate Handling bytecode: brake-before-direction-change, reverse cap, brake-only and signed zero-progress curvature'}
(qa/'unit-report.json').write_text(json.dumps(report,indent=2));print(json.dumps({'success':True,'passed':len(checks),'candidate_sha256':report['candidate_sha256']}))
