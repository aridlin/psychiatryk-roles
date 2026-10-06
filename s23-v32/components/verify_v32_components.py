"""Connect independently exercised components to the exact combined release."""
from pathlib import Path
import hashlib, json, tomllib, zipfile

root = Path(__file__).resolve().parent.parent
work, out = root / 'work', root / 'outputs/save-io-v32'
candidate = out / 'psychiatryk_roles.jar'
digest = lambda path: hashlib.sha256(path.read_bytes()).hexdigest()
load = lambda path: json.loads(path.read_text())

with zipfile.ZipFile(candidate) as current:
    def bridge(jar, names):
        with zipfile.ZipFile(jar) as tested:
            for name in names:
                assert tested.read(name) == current.read(name), name
        return {'reference_jar_sha256': digest(jar), 'byte_identical_entries': sorted(names)}

    io_report = load(out / 'save-io-only-runtime-report.json')
    assert io_report['success'] and io_report['real_dedicated_server_checks'] == 37
    io_names = load(out / 'io-byte-equivalence.json')['checked_entries']
    io = bridge(out / 'tested-save-io-only.jar', io_names)
    assert io['reference_jar_sha256'] == io_report['candidate_sha256']
    io['checks'] = 37

    client_report = load(out / 'client-runtime-report.json')
    assert client_report['success'] and all(v for k,v in client_report.items() if isinstance(v,bool))
    toggle = load(work / 'scooter-chams-toggle/build-report.json')
    with zipfile.ZipFile(out / 'tested-client-runtime-base.jar') as tested:
        client_names = [n for n in tested.namelist() if n.startswith('pl/aridlin/psychiatrykroles/clientperf/')]
    client_names += ['psychiatryk-client-perf.mixins.json', *toggle['changed_existing_classes']]
    client = bridge(out / 'tested-client-runtime-base.jar', client_names)
    assert client['reference_jar_sha256'] == 'c3d8c63a30c6f5f5392cb5f8d45eee9fa5ff677c01191480dae6cce2ffb33d5c'
    client['checks'] = sum(isinstance(v,bool) and k != 'success' for k,v in client_report.items())
    client['scope'] = 'actual title-screen client; no production game changes or world/FPS measurement'

    trade_report = load(out / 'trade-runtime-report.json')
    assert trade_report['success'] and trade_report['checks'] == 16
    trade_proof = load(work / 'trade-v32/build-proof.json')
    trade_names = [*trade_proof['changed_classes'], trade_proof['resource']]
    trade = bridge(work / 'trade-v32/server/mods/psychiatryk_roles-2.1.1.jar', trade_names)
    trade['real_mixin_dedicated_checks'] = 16
    trade['actual_merchant_offer_checks'] = trade_proof['qa_checks']
    trade['reference_scope'] = 'Fixture jar may subsequently contain additional overlays; these exact trade entries retain the independently exercised compiled bytes'
    for n,h in trade_proof['changed_classes'].items():
        assert hashlib.sha256(current.read(n)).hexdigest() == h['new_sha256'], n

    frost_report = load(work / 'frost-v32/server/frost-qa-report.json')
    assert frost_report['success'], frost_report
    frost_diff = load(work / 'frost-v32/class-diff.json')
    frost = bridge(work / 'frost-v32/server/mods/psychiatryk_roles-2.1.1.jar', [item['entry'] for item in frost_diff])
    for item in frost_diff:
        assert hashlib.sha256(current.read(item['entry'])).hexdigest() == item['after']
    frost['runtime_report'] = frost_report

    metadata = tomllib.loads(current.read('META-INF/neoforge.mods.toml').decode())
    registered = {item['config'] for item in metadata['mixins']}
    assert {'goplanska-villager.mixins.json', 'psychiatryk-save-io.mixins.json', 'psychiatryk-client-perf.mixins.json'} <= registered
    for name in registered:
        assert name in current.namelist(), name
    proof = load(out / 'build-report.json')
    assert proof['candidate_sha256'] == digest(candidate)
    pointblank = {}
    for component in ['pointblank-mount-v32', 'pointblank-stencil-v32']:
        folder = work / component
        component_proof = load(folder / 'build-proof.json')
        # A prepared patch alone never satisfies the release gate.
        runtime = load(folder / 'runtime-report.json')
        assert runtime['success'], component
        names = [*component_proof['classes'], component_proof['resource']]
        for name, expected in component_proof['classes'].items():
            assert hashlib.sha256(current.read(name)).hexdigest() == expected, name
        expected_resource = component_proof.get('resource_sha256', component_proof.get('resourceSha'))
        assert hashlib.sha256(current.read(component_proof['resource'])).hexdigest() == expected_resource
        assert component_proof['resource'] in registered
        pointblank[component] = {'exact_entries': names, 'runtime_report': runtime}

report = {
    'success': True,
    'candidate_sha256': digest(candidate),
    'verification_method': 'Actual runtime cases on component candidate JARs, connected by exact byte equivalence; final combined jar is not described as rerunning every historical case',
    'components': {'save_io': io, 'client_performance_and_toggle': client, 'villager_trades': trade, 'frost_walker': frost, 'pointblank': pointblank},
    'registered_mixin_resources': sorted(registered),
    'limits': ['Production base FPS has not been measured after client relaunch', 'Host filesystem latency remains; synchronous reads, allocation and explicit flush can still wait'],
}
(out / 'runtime-report.json').write_text(json.dumps(report, indent=2))
print('Verified exact combined v32 component bytes:', report['candidate_sha256'])
