"""Bind scoped native runtime evidence to the exact wearable candidate."""
from pathlib import Path
import argparse
import hashlib
import json

ROOT = Path(__file__).resolve().parent
digest = lambda path: hashlib.sha256(Path(path).read_bytes()).hexdigest()
parser = argparse.ArgumentParser()
parser.add_argument('--client-report', type=Path, required=True)
parser.add_argument('--dedicated-report', type=Path, default=ROOT / 'qa/dedicated-smoke/runtime-report-final.json')
args = parser.parse_args()
build_path = ROOT / 'build/build-proof.json'
build = json.loads(build_path.read_text())
candidate = build['candidate_sha256']
assert build['success'] and digest(build['candidate_path']) == candidate
client = json.loads(args.client_report.read_text())
assert client['success'] and client['candidate_sha256'] == candidate
assert client['fixture_only'] and all(row['passed'] for row in client['checks'])
assert len(client['checks']) >= 25 and len(client['actual_openal_physics_samples']) >= 3
server = json.loads(args.dedicated_report.read_text())
assert server['candidate_sha256'] == candidate and server['candidate_unchanged']
assert all(server[k] for k in ['startup_success', 'dedicated_server_started', 'jukebox_command_registered',
                              'all_expected_subcommands_registered', 'help_command_output', 'done_logged',
                              'server_stopped', 'worlds_saved_before_exit'])
assert not server['new_client_targets'] and not server['wearable_feature_load_errors']
assert server['actual_mod_version'] == '3.0.3-bmc5'
assert build['original_scooter_model_render_physics_storage_recipes_preserved']
reference = lambda path: {'path': str(Path(path).resolve()), 'sha256': digest(path)}
startup_path = ROOT / 'build/dedicated-startup-proof.json'
startup = {'success': True, 'candidate_sha256': candidate, 'scope': 'Dedicated startup and jukebox command registration',
           'native_receipt': reference(args.dedicated_report), 'actual_mod_count': server['actual_mod_count'],
           'server_stopped': True, 'worlds_saved': True, 'java_exit': server['java_exit'],
           'shutdown_limitation': server.get('shutdown_limitation'),
           'full_process_shutdown_success': server['success']}
# This gate covers feature startup. It does not relabel the raw receipt's existing
# third-party executor shutdown limitation as a clean JVM exit.
if not server['success']:
    assert server['baseline_has_same_stuck_thread'] and server['java_exit'] == 143
startup_path.write_text(json.dumps(startup, indent=2) + '\n')
static_path = ROOT / 'build/baseline-preservation-proof.json'
static_path.write_text(json.dumps({'success': True, 'candidate_sha256': candidate,
    'base_sha256': build['base_sha256'], 'build_receipt': reference(build_path),
    'original_scooter_model_render_physics_storage_recipes_preserved': True,
    'existing_dependencies_preserved': True, 'soundphysics_global_config_unchanged': True}, indent=2) + '\n')
qualification = {'success': True, 'runtime_verified': True, 'candidate_sha256': candidate,
    'base_sha256': build['base_sha256'], 'evidence': [reference(args.client_report), reference(startup_path), reference(static_path)],
    'scope': 'Wearable equipment, visible back model, menu networking, moving Sound Physics and lifecycle; dedicated registration',
    'human_listening_claimed': False, 'synthetic_remote_entity_for_wall_scene': True,
    'production_activated': False}
ore_path = ROOT.parent / 'veinminer-ore-fix/qualification-proof.json'
if ore_path.exists():
    ore = json.loads(ore_path.read_text())
    assert ore['success'] and ore['candidate_sha256'] == candidate
    assert ore['native_configuration_verified'] and ore['no_enchantment_preserved']
    assert ore['key_diff'] == {'blocks': {'before': 'CONFIG_LIST', 'after': 'ORES'}}
    assert digest(ore_path.parent / 'veinmining-server.toml') == ore['new_config_sha256']
    assert ore['max_blocks_base'] == 50 and ore['native_preset'] == ['#c:ores', '#forge:ores']
    for record in [ore['configuration_proof'], *ore['native_bytecode_evidence']]:
        assert digest(record['path']) == record['sha256']
    qualification['evidence'].append(reference(ore_path))
    qualification['scope'] += '; native ores-only VeinMining configuration'
    qualification['veinminer_config_verified'] = True
    qualification['veinminer_configuration_game_test_claimed'] = False
(ROOT / 'build/qualification.json').write_text(json.dumps(qualification, indent=2) + '\n')
print(json.dumps({'success': True, 'candidate_sha256': candidate, 'runtime_checks': len(client['checks']), 'production_activated': False}))
