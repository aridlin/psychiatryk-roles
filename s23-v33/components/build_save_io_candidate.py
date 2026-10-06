from pathlib import Path
import subprocess, zipfile, json, hashlib, shutil

root=Path(__file__).resolve().parent.parent
work=root/'work'
out=root/'outputs/save-io-v32'
out.mkdir(parents=True,exist_ok=True)
classes=out/'classes'
if classes.exists():shutil.rmtree(classes)
classes.mkdir()
classpath=(work/'save-io/classpath.txt').read_text().strip()
sources=[*sorted((work/'save-io/src').rglob('*.java')),*sorted((work/'player-save-io/src').rglob('*.java'))]
subprocess.run(['javac','--release','21','-proc:none','-cp',classpath,'-d',str(classes),*map(str,sources)],check=True)
base=work/'release-v31/client/mods/psychiatryk_roles-2.1.1.jar'
with zipfile.ZipFile(base) as z:entries={n:z.read(n) for n in z.namelist() if not n.endswith('/')}
metadata=entries['META-INF/neoforge.mods.toml'].decode()
assert 'psychiatryk-save-io.mixins.json' not in metadata
entries['META-INF/neoforge.mods.toml']=(metadata+'\n[[mixins]]\nconfig="psychiatryk-save-io.mixins.json"\n').encode()
config=json.loads((work/'save-io/resources/psychiatryk-save-io.mixins.json').read_text())
config['mixins']+=['PlayerDataStorageMixin','SaveFlushMixin']
entries['psychiatryk-save-io.mixins.json']=json.dumps(config,indent=2).encode()
for p in sorted(classes.rglob('*.class')):entries[str(p.relative_to(classes))]=p.read_bytes()
trade=work/'trade-v32'
trade_proof=json.loads((trade/'build-proof.json').read_text())
for name,hashes in trade_proof['changed_classes'].items():
 data=(trade/'classes'/name).read_bytes()
 assert hashlib.sha256(data).hexdigest()==hashes['new_sha256'],name
 assert hashes['old_sha256'] is None or hashlib.sha256(entries[name]).hexdigest()==hashes['old_sha256'],name
 entries[name]=data
trade_resource=trade_proof['resource']
entries[trade_resource]=(work/'roles-unified/resources'/trade_resource).read_bytes()
assert hashlib.sha256(entries[trade_resource]).hexdigest()==trade_proof['resource_sha256']
frost=work/'frost-v32'
frost_diff=json.loads((frost/'class-diff.json').read_text())
for item in frost_diff:
 name=item['entry'];data=(frost/'classes'/name).read_bytes()
 assert hashlib.sha256(entries[name]).hexdigest()==item['before'],name
 assert hashlib.sha256(data).hexdigest()==item['after'],name
 entries[name]=data
clientperf=work/'client-perf'
if (clientperf/'resources/psychiatryk-client-perf.mixins.json').exists():
 for p in sorted((clientperf/'classes/pl/aridlin/psychiatrykroles/clientperf').rglob('*.class')):
  name=str(p.relative_to(clientperf/'classes'));assert name not in entries
  entries[name]=p.read_bytes()
 entries['psychiatryk-client-perf.mixins.json']=(clientperf/'resources/psychiatryk-client-perf.mixins.json').read_bytes()
 entries['META-INF/neoforge.mods.toml']+=b'\n[[mixins]]\nconfig="psychiatryk-client-perf.mixins.json"\n'
toggle=work/'scooter-chams-toggle'
if (toggle/'build-report.json').exists():
 for p in sorted((toggle/'classes/pl/aridlin/partymarkers').rglob('*.class')):
  name=str(p.relative_to(toggle/'classes'))
  assert name.startswith(('pl/aridlin/partymarkers/ChamsCategories','pl/aridlin/partymarkers/HalftoneChams','pl/aridlin/partymarkers/PartyMarkers')),name
  entries[name]=p.read_bytes()
pointblank_components={}
for component in ['pointblank-mount-v32','pointblank-stencil-v32']:
 folder=work/component;proof_path=folder/'build-proof.json'
 if not proof_path.exists():continue
 component_proof=json.loads(proof_path.read_text())
 for name,expected_hash in component_proof['classes'].items():
  assert name.startswith(('pl/aridlin/psychiatrykroles/pointblankfix/','pl/aridlin/psychiatrykroles/pointblankstencil/')) and name not in entries,name
  data=(folder/'classes'/name).read_bytes()
  assert hashlib.sha256(data).hexdigest()==expected_hash,name
  entries[name]=data
 resource=component_proof['resource']
 assert resource in ['psychiatryk-pointblank-mount.mixins.json','psychiatryk-pointblank-render.mixins.json'] and resource not in entries,resource
 data=(folder/'resources'/resource).read_bytes()
 assert hashlib.sha256(data).hexdigest()==component_proof.get('resource_sha256',component_proof.get('resourceSha'))
 entries[resource]=data
 entries['META-INF/neoforge.mods.toml']+=('\n[[mixins]]\nconfig="'+resource+'"\n').encode()
 pointblank_components[component]=component_proof
jar=out/'psychiatryk_roles.jar'
with zipfile.ZipFile(jar,'w',zipfile.ZIP_DEFLATED) as z:
 for name,data in entries.items():z.writestr(name,data)
with zipfile.ZipFile(base) as old:
 changed=[n for n in old.namelist() if not n.endswith('/') and old.read(n)!=entries[n]]
 expected=['META-INF/neoforge.mods.toml']
 expected += [n for n,h in trade_proof['changed_classes'].items() if h['old_sha256'] is not None]
 expected += [trade_resource]+[item['entry'] for item in frost_diff]
 if (toggle/'build-report.json').exists():expected+=json.loads((toggle/'build-report.json').read_text())['changed_existing_classes']
 assert sorted(changed)==sorted(expected),changed
with zipfile.ZipFile(base) as old:added=set(entries)-set(old.namelist())
proof={'candidate_sha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'base_sha256':hashlib.sha256(base.read_bytes()).hexdigest(),'changed_existing_entries':changed,'allowed_changed_existing_entries':expected,'added_entries':sorted(added),'new_classes':[n for n in sorted(added) if n.endswith('.class')],'pointblank_components':pointblank_components}
(out/'build-report.json').write_text(json.dumps(proof,indent=2))
print(jar,proof['candidate_sha256'])
