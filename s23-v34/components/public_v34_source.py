"""Filter and syntax-check public authored source; never inspect live profiles."""
from pathlib import Path
import ast,json,re,zipfile

PUBLIC_COMPONENTS={
 'admin-spectate','kinker-starter','void-portals','restart-votes','chatbook-fix',
 'chatbook-reset','original-roles-source','kukirin','party-markers','roles-unified',
 'save-io','player-save-io','client-perf','client-perf-qa','pointblank-fix',
 'pointblank-mount-v32','pointblank-stencil-v32','pointblank-v32-qa',
 'scooter-steering-compat','steering-v33-qa','steering-v34-qa',
}
PUBLIC_TOP={
 'build_save_io_candidate.py','build_v32.py','prepare_v32_github.py',
 'verify_v32_components.py','SAVE-IO-v32.md','build_steering_v33.py',
 'build_v33.py','prepare_v33_github.py','STEERING-v33.md',
 'build_steering_v34.py','build_v34.py','prepare_v34_github.py',
 'public_v34_source.py','STEERING-v34.md',
 'generators/repair_scooter_steering_rig.py',
 'scooter-geometry-v34/continuity_qa.py','scooter-geometry-v34/continuity-report.json',
 'scooter-geometry-v34/runtime_continuity_qa.py',
 'scooter-geometry-v34/runtime-continuity-report.json','scooter-geometry-v34/README.md',
 'scooter-geometry-v34/compiled-qa/SteeringRigQA.java',
 'scooter-geometry-v34/compiled-qa/report.json',
}
TEXT_SUFFIXES={'.java','.py','.json','.toml','.md','.txt','.properties','.mcmeta','.fsh','.vsh','.glsl'}
ASSET_SUFFIXES={'.png','.ogg','.wav','.mesh','.glb'}
PRIVATE_PARTS={'.git','.ssh','.aws','accounts.json','usercache.json','ops.json',
 'whitelist.json','server.properties','launcher-private.log','console-private.log',
 'decompiled','audit','upstream','libraries','classes','logs','crash-reports',
 'world','worlds','server','config','configs','backups','build-inputs'}
UUID=re.compile(r'\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\b')
HOME_LITERAL=re.compile(r'/home/[A-Za-z0-9_.-]+/')
CREDENTIAL_FILES={'sitemanager.xml','known_hosts','credentials.json','.netrc'}


def public_source_path(name):
 p=Path(name)
 if p.is_absolute() or '..' in p.parts or '\\' in name:return False
 if any(part in PRIVATE_PARTS or part.startswith(('private_','prism-root','launcher-private','console-private')) for part in p.parts):return False
 if name in PUBLIC_TOP:return True
 if not p.parts or p.parts[0] not in PUBLIC_COMPONENTS:return False
 if p.suffix in TEXT_SUFFIXES:return True
 # Only model/texture/audio resources are exported, never QA screenshots/binaries.
 if p.suffix in ASSET_SUFFIXES:return 'resources' in p.parts
 return name=='original-roles-source/gameTest/resources/data/psychiatryk_roles/structures/empty.nbt'


def normalize_home_paths(text):
 text=re.sub(r"Path\((['\"])/home/[^/'\"]+/([^'\"]+)\1\)",lambda m:'(Path.home() / '+repr(m.group(2))+')',text)
 text=re.sub(r'Path\.of\("/home/[^/]+/([^\"]+)"\)',lambda m:'Path.of(System.getProperty("user.home"), '+json.dumps(m.group(1))+')',text)
 text=re.sub(r'''(?m)^(\s*)["'](/home/[^/]+/[^"']*/bin/java)["'](,\s*)$''',lambda m:m.group(1)+'str(Path.home() / '+repr(m.group(2).split('/',3)[3])+')'+m.group(3),text)
 return HOME_LITERAL.sub('~/',text)


def scrub_report(value):
 if isinstance(value,dict):
  return {UUID.sub('<synthetic-uuid>',str(k)):scrub_report(v) for k,v in value.items()
          if not any(word in str(k).lower() for word in ('password','token','secret','credential'))}
 if isinstance(value,list):return [scrub_report(v) for v in value]
 if isinstance(value,str):return UUID.sub('<synthetic-uuid>',HOME_LITERAL.sub('<local-home>/',value))
 return value


def audit_python(name,text):
 tree=ast.parse(text,filename=name)
 for node in ast.walk(tree):
  if isinstance(node,ast.Constant) and isinstance(node.value,str):
   value=node.value
   if HOME_LITERAL.search(value):raise RuntimeError('Private home literal in '+name)
   # This checker declares denied filenames; its own literals are not I/O.
   if name!='public_v34_source.py' and any(filename in value for filename in CREDENTIAL_FILES):raise RuntimeError('Credential-store reference in public source '+name)
   if re.search(r'https://(?:discord(?:app)?\.com)/api/webhooks/\d{16,}/\S+',value):raise RuntimeError('Private webhook in '+name)
  if isinstance(node,(ast.Assign,ast.AnnAssign)):
   value=node.value
   targets=node.targets if isinstance(node,ast.Assign) else [node.target]
   names=[t.id.lower() for t in targets if isinstance(t,ast.Name)]
   if isinstance(value,ast.Constant) and isinstance(value.value,str) and len(value.value)>16 and any(n in {'password','secret','api_token','access_token','refresh_token'} for n in names):
    raise RuntimeError('Hard-coded secret assignment in '+name)


def public_source_data(name,data):
 if not public_source_path(name):raise RuntimeError('Private/unsupported public source path '+name)
 if Path(name).suffix not in TEXT_SUFFIXES:return data
 text=normalize_home_paths(data.decode('utf-8'))
 if Path(name).suffix=='.json' and any(word in Path(name).name for word in ('report','proof','inheritance')):
  text=json.dumps(scrub_report(json.loads(text)),indent=2)+'\n'
 if Path(name).suffix=='.py':audit_python(name,text)
 if HOME_LITERAL.search(text):raise RuntimeError('Private home remains in public source '+name)
 return text.encode('utf-8')


def audit_source_zip(path):
 with zipfile.ZipFile(path) as archive:
  names=archive.namelist()
  if len(names)!=len(set(names)):raise RuntimeError('Duplicate public source archive entry')
  for name in names:
   if name.endswith('/'):continue
   data=archive.read(name)
   audit_public_data(name,data)


def audit_public_tree(root,prefix=''):
 for path in Path(root).rglob('*'):
  if path.is_file():
   name=prefix+path.relative_to(root).as_posix()
   data=path.read_bytes()
   audit_public_data(name,data)


def audit_public_data(name,data):
 sanitized=public_source_data(name,data)
 if Path(name).suffix=='.json' and any(word in Path(name).name for word in ('report','proof','inheritance')):
  # Formatting is irrelevant; private values must already have been scrubbed.
  if json.loads(sanitized)!=json.loads(data):raise RuntimeError('Unsanitized public report '+name)
 elif sanitized!=data:raise RuntimeError('Unsanitized public source '+name)
