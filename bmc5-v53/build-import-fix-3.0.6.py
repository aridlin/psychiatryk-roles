"""Rebuild the authored 3.0.6 delta from an externally supplied exact 53c6 JAR.

The source export supplies source-manifest-3.0.6.json and source folders. Neither
the runtime checkpoint nor Minecraft/NeoForge libraries are distributed here.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent
SHA = lambda data: hashlib.sha256(data).hexdigest()
ENTRY_CLIENT = 'pl/aridlin/psychiatrykroles/peeb/client/PeebClient.class'
ENTRY_MESH = 'pl/aridlin/psychiatrykroles/peeb/client/PeebMesh.class'

def run(args, log):
    result = subprocess.run(args, text=True, capture_output=True)
    log.write_text(result.stdout + result.stderr)
    if result.returncode: raise RuntimeError('Build step failed; see ' + str(log))
    return result.stdout

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--base', type=Path, required=True)
    p.add_argument('--classpath-file', type=Path, required=True)
    p.add_argument('--output-dir', type=Path, required=True)
    a = p.parse_args(); manifest = json.loads((ROOT/'source-manifest-3.0.6.json').read_text())
    base = a.base.resolve(); out = a.output_dir.resolve()
    if SHA(base.read_bytes()) != manifest['base_sha256']: raise ValueError('Exact external 53c6 baseline required')
    if out.exists(): raise ValueError('Use a fresh rebuild output directory')
    for name,digest in manifest['source_files_sha256'].items():
        if SHA((ROOT/name).read_bytes()) != digest: raise ValueError('Frozen exported source changed: '+name)
    out.mkdir(parents=True)
    libraries = a.classpath_file.read_text().strip()
    if not libraries: raise ValueError('Explicit Java21 Minecraft/NeoForge classpath required')
    external_cp = str(base) + os.pathsep + libraries
    asm_cp = os.pathsep.join(n for n in libraries.split(os.pathsep) if '/asm' in n and '/9.10.1/' in n)
    runtime_by_group, classes_by_group = {}, {}
    with zipfile.ZipFile(base) as z:
        original = {n:z.read(n) for n in z.namelist()}; info = {n:z.getinfo(n) for n in z.namelist()}
    groups = {row['name']:row for row in manifest['groups']}
    # Continuity classes precede controls only for compilation. Runtime order
    # remains the exact ordered composition manifest below.
    for name in ('ui','imports','head','grapple','continuity','controls'):
        group = groups[name]; source = ROOT/group['source_root']; folder = out/name; folder.mkdir()
        classes = folder/'classes'; classes.mkdir(); classes_by_group[name] = classes
        dependencies = []
        if name in ('imports','continuity','controls'): dependencies.append(str(classes_by_group['ui']))
        if name == 'controls': dependencies.insert(0,str(classes_by_group['continuity']))
        cp = os.pathsep.join(dependencies + [external_cp])
        sources = sorted((source/'src').rglob('*.java'))
        if not sources: raise ValueError('Source group empty: '+name)
        run(['javac','--release','21','-proc:none','-cp',cp,'-d',str(classes),*map(str,sources)],folder/'compile.log')
        values = {f.relative_to(classes).as_posix():f.read_bytes() for f in sorted(classes.rglob('*.class'))}
        if name in ('head','grapple'):
            if not asm_cp: raise ValueError('ASM9.10.1 required for bytecode method-preserving transplant')
            tools = folder/'tools'; tools.mkdir()
            run(['javac','--release','21','-proc:none','-cp',asm_cp,'-d',str(tools),str(source/'MethodOverlay.java')],folder/'tool-compile.log')
            entry = ENTRY_MESH if name == 'head' else ENTRY_CLIENT
            old = folder/'original.class'; old.write_bytes(original[entry]); patched = folder/'patched.class'
            run(['java','-Xmx128m','-cp',str(tools)+os.pathsep+asm_cp,'MethodOverlay',str(old),str(classes/entry),str(patched)],folder/'transplant.log')
            if name == 'head': values = {entry:patched.read_bytes()}
            else:
                values = {n:v for n,v in values.items() if not n.startswith('pl/aridlin/psychiatrykroles/peeb/client/PeebClient')}
                values[entry] = patched.read_bytes()
                mixins = json.loads(original['psychiatryk-peeb-client.mixins.json'])
                mixins['client'].append('PeebTravelMixin')
                values['psychiatryk-peeb-client.mixins.json'] = (json.dumps(mixins,indent=2)+'\n').encode()
        wanted = group['runtime_entries_sha256']
        if {n:SHA(v) for n,v in values.items()} != wanted:
            raise ValueError('Compiled runtime differs from frozen group: '+name)
        runtime_by_group[name] = values
    values = dict(original); seen = set(); superseded = set()
    for name in manifest['composition_order']:
        overlay = runtime_by_group[name]; superseded.update(set(overlay).intersection(seen)); seen.update(overlay)
        values.update(overlay)
    if sorted(superseded) != manifest['superseded_entries']: raise ValueError('Exact ordered overlay collisions differ')
    toml = values['META-INF/neoforge.mods.toml'].decode()
    if toml.count('version="3.0.5-bmc5"') != 6: raise ValueError('Six old common mod versions required')
    values['META-INF/neoforge.mods.toml'] = toml.replace('version="3.0.5-bmc5"','version="3.0.6-bmc5"').encode()
    candidate = out/'psychiatryk_roles-3.0.6-bmc5.jar'
    with zipfile.ZipFile(candidate,'w',zipfile.ZIP_DEFLATED) as z:
        for name,data in values.items():
            item = zipfile.ZipInfo(name,(2026,10,8,15,0,0)); item.compress_type = zipfile.ZIP_DEFLATED
            item.external_attr = info[name].external_attr if name in info else 0o644<<16
            z.writestr(item,data)
    actual = SHA(candidate.read_bytes())
    if actual != manifest['candidate_sha256']: raise ValueError('Rebuilt candidate differs from frozen full artifact')
    report = {'success':True,'candidate_sha256':actual,'base_sha256':manifest['base_sha256'],
              'portable_source_build_reproduces_frozen_artifact':True,'source_manifest_sha256':SHA((ROOT/'source-manifest-3.0.6.json').read_bytes()),
              'network_access_performed':False,'game_launch_performed':False,'git_mutation_performed':False}
    (out/'source-rebuild-report.json').write_text(json.dumps(report,indent=2)+'\n'); print(json.dumps(report))

if __name__ == '__main__': main()
