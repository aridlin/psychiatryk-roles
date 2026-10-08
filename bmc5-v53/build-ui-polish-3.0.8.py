"""Rebuild the reviewed 3.0.8 source from an exact externally supplied 3.0.7 JAR."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,zipfile
ROOT=Path(__file__).resolve().parent
SHA=lambda b:hashlib.sha256(b).hexdigest()
ORDER=['music-hud-compact','music-menu','peeb-dye','roles-restore','peeb-step','roster-startup','music-stream-reliability','jei-compat','grapple-initiation','flywheel-compat']

def run(command,log):
    if shutil.which('nice'):command=['nice','-n','19',*command]
    result=subprocess.run(command,capture_output=True,text=True);log.write_text(result.stdout+result.stderr)
    if result.returncode:raise RuntimeError('Build failed: '+str(log))

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--base',type=Path,required=True);p.add_argument('--classpath-file',type=Path,required=True);p.add_argument('--output-dir',type=Path,required=True)
    a=p.parse_args();m=json.loads((ROOT/'source-manifest-3.0.8.json').read_text());out=a.output_dir.resolve();base=a.base.resolve()
    if SHA(base.read_bytes())!=m['base_sha256'] or m['base_sha256']!='ff773260893687f62a1c7dfd570a5c374110e972ce84f6fd51952620a7e7f303':raise ValueError('Reviewed exact external 3.0.7 baseline required')
    if out.exists():raise ValueError('Fresh rebuild directory required')
    for n,h in m['source_files_sha256'].items():
        if SHA((ROOT/n).read_bytes())!=h:raise ValueError('Frozen source changed: '+n)
    cp=a.classpath_file.read_text().strip()
    if not cp:raise ValueError('Explicit Java21 Minecraft/NeoForge classpath required')
    groups={g['name']:g for g in m['groups']}
    if set(groups)!=set(ORDER) or m['compilation_order']!=ORDER or m['composition_order']!=ORDER:raise ValueError('Reviewed build order required')
    with zipfile.ZipFile(base) as z:baseline={n:z.read(n) for n in z.namelist()}
    out.mkdir(parents=True);overlays={};dependencies=[]
    for name in ORDER:
        g=groups[name];folder=out/name;classes=folder/'classes';classes.mkdir(parents=True)
        sources=sorted((ROOT/g['source_root']/'src').rglob('*.java'))
        if not sources:raise ValueError('Empty source group')
        run(['javac','-J-Xmx256m','--release','21','-proc:none','-cp',os.pathsep.join(dependencies+[str(base),cp]),'-d',str(classes),*map(str,sources)],folder/'compile.log')
        if g['method_transplant']:
            if name not in ('peeb-dye','grapple-initiation'):raise ValueError('Only reviewed dye/initiation groups use method transplants')
            asmcp=os.pathsep.join(p for p in cp.split(os.pathsep) if '/asm' in p and '/9.10.1/' in p)
            if not asmcp:raise ValueError('Explicit ASM9.10.1 classpath required')
            toolclasses=folder/'tools';toolclasses.mkdir();tool=ROOT/g['source_root']/'MethodOverlay.java'
            run(['javac','-J-Xmx128m','--release','21','-proc:none','-cp',asmcp,'-d',str(toolclasses),str(tool)],folder/'transplant-compile.log')
            patched=folder/'patched';patched.mkdir();values={}
            methods=[('PeebMesh','mesh'),('PeebRenderer','renderer')] if name=='peeb-dye' else [('PeebClient',None)]
            for family,mode in methods:
                entry='pl/aridlin/psychiatrykroles/peeb/client/'+family+'.class';original=folder/('original-'+family+'.class');original.write_bytes(baseline[entry]);target=patched/entry;target.parent.mkdir(parents=True,exist_ok=True)
                command=['java','-Xmx128m','-cp',str(toolclasses)+os.pathsep+asmcp,'MethodOverlay',str(original),str(classes/entry),str(target)]+([] if mode is None else [mode])
                run(command,folder/(family+'-transplant.log'));values[entry]=target.read_bytes()
            additional=['pl/aridlin/psychiatrykroles/peeb/PeebDye.class','pl/aridlin/psychiatrykroles/peeb/client/PeebDyeClient.class'] if name=='peeb-dye' else ['pl/aridlin/psychiatrykroles/peeb/PeebGrapple.class','pl/aridlin/psychiatrykroles/peeb/PeebGrapple$Session.class']
            for entry in additional:values[entry]=(classes/entry).read_bytes()
        else:
            values={f.relative_to(classes).as_posix():f.read_bytes() for f in classes.rglob('*.class')}
        values.update({n:v.encode('utf-8') for n,v in g['resources_utf8'].items()})
        if {n:SHA(v) for n,v in values.items()}!=g['runtime_entries_sha256']:raise ValueError('Rebuilt delta differs: '+name)
        overlays[name]=values;dependencies.insert(0,str(classes))
    values=dict(baseline);seen=set();collisions=set()
    for name in ORDER:
        v=overlays[name];collisions.update(seen.intersection(v));seen.update(v);values.update(v)
    if sorted(collisions)!=m['superseded_entries']:raise ValueError('Overlay collisions differ')
    toml=values['META-INF/neoforge.mods.toml']
    if toml.count(b'version="3.0.7-bmc5"')!=6:raise ValueError('Six baseline mod versions required')
    values['META-INF/neoforge.mods.toml']=toml.replace(b'version="3.0.7-bmc5"',b'version="3.0.8-bmc5"')+b'\n[[mixins]]\nconfig="psychiatryk-jei-compat.mixins.json"\n\n[[mixins]]\nconfig="psychiatryk-flywheel-compat.mixins.json"\n'
    if set(values)!={i['name'] for i in m['zip_metadata']}:raise ValueError('Complete archive scope differs')
    target=out/'psychiatryk_roles-3.0.8-bmc5.jar'
    with zipfile.ZipFile(target,'w') as z:
        for record in m['zip_metadata']:
            i=zipfile.ZipInfo(record['name'],tuple(record['date_time']));i.compress_type=record['compress_type'];i.external_attr=record['external_attr'];z.writestr(i,values[i.filename])
    digest=SHA(target.read_bytes())
    if digest!=m['candidate_sha256']:raise ValueError('Complete rebuilt candidate differs')
    receipt={'success':True,'candidate_sha256':digest,'base_sha256':m['base_sha256'],'portable_source_build_reproduces_frozen_artifact':True,
             'compilation_order':ORDER,'only_reviewed_groups':True,'network_access_performed':False,'game_launch_performed':False,'git_mutation_performed':False}
    (out/'source-rebuild-report.json').write_text(json.dumps(receipt,indent=2)+'\n');print(json.dumps(receipt))
if __name__=='__main__':main()
