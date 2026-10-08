"""Rebuild focused 3.0.7 source from an externally supplied exact3.0.6 checkpoint."""
from pathlib import Path
import argparse,hashlib,json,os,subprocess,zipfile
ROOT=Path(__file__).resolve().parent
SHA=lambda b:hashlib.sha256(b).hexdigest()
def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True)
    a=p.parse_args();m=json.loads((ROOT/'source-manifest-3.0.7.json').read_text());out=a.output_dir.resolve();base=a.base.resolve()
    if SHA(base.read_bytes())!=m['base_sha256']:raise ValueError('Exact external3.0.6 baseline required')
    if out.exists():raise ValueError('Fresh rebuild directory required')
    for n,h in m['source_files_sha256'].items():
        if SHA((ROOT/n).read_bytes())!=h:raise ValueError('Frozen source changed: '+n)
    cp=a.classpath_file.read_text().strip()
    if not cp:raise ValueError('ExplicitJava21 Minecraft/NeoForge classpath required')
    out.mkdir(parents=True);groups={g['name']:g for g in m['groups']};overlays={};dependencies=[]
    for name in m['compilation_order']:
        g=groups[name];folder=out/name;classes=folder/'classes';classes.mkdir(parents=True)
        sources=sorted((ROOT/g['source_root']/'src').rglob('*.java'))
        if not sources:raise ValueError('Empty source group')
        result=subprocess.run(['javac','-J-Xmx256m','--release','21','-proc:none','-cp',os.pathsep.join(dependencies+[str(base),cp]),'-d',str(classes),*map(str,sources)],capture_output=True,text=True)
        (folder/'compile.log').write_text(result.stdout+result.stderr)
        if result.returncode:raise RuntimeError('Compile failed: '+str(folder/'compile.log'))
        values={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
        if name=='physics':
            # Reproduce only the three reviewed method changes in the exact
            # external baseline; retain its unrelated methods and inner classes.
            entry='pl/aridlin/psychiatrykroles/peeb/client/PeebClient.class'
            tool=ROOT/g['method_transplant_tools']['MethodOverlay.java'];toolclasses=folder/'tools';toolclasses.mkdir()
            asmcp=os.pathsep.join(n for n in cp.split(os.pathsep) if '/asm' in n and '/9.10.1/' in n)
            if not asmcp:raise ValueError('Explicit ASM9.10.1 classpath required for reviewed method transplantation')
            result=subprocess.run(['javac','-J-Xmx128m','--release','21','-proc:none','-cp',asmcp,'-d',str(toolclasses),str(tool)],capture_output=True,text=True)
            (folder/'transplant-compile.log').write_text(result.stdout+result.stderr)
            if result.returncode:raise RuntimeError('Transplant tool compile failed')
            original=folder/'original-PeebClient.class'
            with zipfile.ZipFile(base) as z:original.write_bytes(z.read(entry))
            patched=folder/'runtime'/entry;patched.parent.mkdir(parents=True)
            result=subprocess.run(['java','-Xmx128m','-cp',str(toolclasses)+os.pathsep+asmcp,'MethodOverlay',str(original),str(classes/entry),str(patched)],capture_output=True,text=True)
            (folder/'transplant.log').write_text(result.stdout+result.stderr)
            if result.returncode:raise RuntimeError('Reviewed method transplantation failed')
            transplant=json.loads(result.stdout)
            if not transplant['success'] or transplant['unchanged_methods']!=37 or sorted(transplant['changed_methods'])!=['exit','predict','release']:raise ValueError('Transplant scope differs')
            (folder/'transplant-proof.json').write_text(json.dumps(transplant,indent=2)+'\n')
            values={n:v for n,v in values.items() if not n.startswith(entry[:-6])}
            values[entry]=patched.read_bytes()
            for compiled in classes.rglob('PeebClient*.class'):compiled.unlink()
            (classes/entry).write_bytes(patched.read_bytes())
        values.update({n:v.encode() for n,v in g['resources_utf8'].items()})
        if {n:SHA(v) for n,v in values.items()}!=g['runtime_entries_sha256']:raise ValueError('Compiled delta differs: '+name)
        overlays[name]=values
        # Later groups resolve the transplanted class rather than raw authored
        # PeebClient bytecode or newly compiled historical inner classes.
        dependencies.insert(0,str(classes))
    with zipfile.ZipFile(base) as z:values={n:z.read(n) for n in z.namelist()}
    seen=set();collisions=set()
    for name in m['composition_order']:
        v=overlays[name];collisions.update(seen.intersection(v));seen.update(v);values.update(v)
    if sorted(collisions)!=m['superseded_entries']:raise ValueError('Overlay collisions differ')
    toml=values['META-INF/neoforge.mods.toml']
    if toml.count(b'version="3.0.6-bmc5"')!=6:raise ValueError('Six baseline mod versions required')
    values['META-INF/neoforge.mods.toml']=toml.replace(b'version="3.0.6-bmc5"',b'version="3.0.7-bmc5"')
    if set(values)!={i['name'] for i in m['zip_metadata']}:raise ValueError('Complete archive scope differs')
    target=out/'psychiatryk_roles-3.0.7-bmc5.jar'
    with zipfile.ZipFile(target,'w') as z:
        for record in m['zip_metadata']:
            i=zipfile.ZipInfo(record['name'],tuple(record['date_time']));i.compress_type=record['compress_type'];i.external_attr=record['external_attr'];z.writestr(i,values[i.filename])
    digest=SHA(target.read_bytes())
    if digest!=m['candidate_sha256']:raise ValueError('Complete rebuilt candidate differs')
    receipt={'success':True,'candidate_sha256':digest,'portable_source_build_reproduces_frozen_artifact':True,'network_access_performed':False,'game_launch_performed':False,'git_mutation_performed':False}
    (out/'source-rebuild-report.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
if __name__=='__main__':main()
