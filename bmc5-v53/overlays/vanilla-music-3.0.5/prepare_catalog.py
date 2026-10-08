"""Generate metadata/aliases for all Minecraft 1.21.1 music and discs; copy no audio."""
from pathlib import Path
import argparse
import hashlib
import json
import math
import subprocess
import zipfile

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
sha = lambda data: hashlib.sha256(data).hexdigest()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-jar', type=Path, required=True)
    parser.add_argument('--music-source', type=Path, required=True, help='Externally supplied historical ScooterMusic source for the three catalog-selection edits')
    parser.add_argument('--assets', type=Path, default=Path.home() / '.local/share/PrismLauncher/assets')
    parser.add_argument('--minecraft-meta', type=Path, default=Path.home() / '.local/share/PrismLauncher/meta/net.minecraft/1.21.1.json')
    args = parser.parse_args()
    version = json.loads(args.minecraft_meta.read_text())
    spec = version['assetIndex']
    assert spec['id'] == '17'
    index_data = (args.assets / 'indexes/17.json').read_bytes()
    assert hashlib.sha1(index_data).hexdigest() == spec['sha1']
    objects = json.loads(index_data)['objects']
    sounds_object = objects['minecraft/sounds.json']
    sounds_data = (args.assets / 'objects' / sounds_object['hash'][:2] / sounds_object['hash']).read_bytes()
    assert hashlib.sha1(sounds_data).hexdigest() == sounds_object['hash']
    sound_events = json.loads(sounds_data)
    direct_paths = set()
    for event, definition in sound_events.items():
        if not (event.startswith('music.') or event.startswith('music_disc.')):
            continue
        for sound in definition['sounds']:
            row = {'name': sound} if isinstance(sound, str) else sound
            if row.get('type', 'file') == 'file':
                direct_paths.add(row['name'].removeprefix('minecraft:'))
    all_paths = {name[len('minecraft/sounds/'):-4] for name in objects if name.endswith('.ogg') and (name.startswith('minecraft/sounds/music/') or name.startswith('minecraft/sounds/records/'))}
    assert direct_paths <= all_paths
    # Include every native music resource, even one omitted from a random-play event.
    paths = sorted(all_paths)
    assert 'music/game/creative/aria_math' in paths and 'records/13' in paths and 'records/creator' in paths
    with zipfile.ZipFile(args.base_jar) as archive:
        aliases = json.loads(archive.read('assets/goplanska_kukirin/sounds.json'))
    original_aliases = dict(aliases)
    songs = []
    measured = []
    for path in paths:
        obj = objects['minecraft/sounds/' + path + '.ogg']
        local = args.assets / 'objects' / obj['hash'][:2] / obj['hash']
        assert local.is_file() and local.stat().st_size == obj['size']
        assert hashlib.sha1(local.read_bytes()).hexdigest() == obj['hash']
        info = subprocess.run(['ffprobe', '-v', 'error', '-show_entries', 'format=duration', '-of', 'json', str(local)], capture_output=True, text=True, check=True)
        seconds = float(json.loads(info.stdout)['format']['duration'])
        title = path.rsplit('/', 1)[-1].replace('_', ' ').title()
        title = {'Oxygene': 'Oxygène', 'Haggstrom': 'Haggstrom'}.get(title, title)
        # IDs obey the existing portable music-menu filename protocol.
        filename_title = path.rsplit('/', 1)[-1].replace('_', ' ').title()
        name = ('Minecraft Disc - ' if path.startswith('records/') else 'Minecraft - ') + filename_title + '.wav'
        event = 'vanilla_' + path
        url = 'minecraftsound:goplanska_kukirin:' + event
        aliases[event] = {'sounds': [{'name': 'minecraft:' + path, 'stream': True, 'attenuation_distance': 64}]}
        descriptor_sha = sha(url.encode())
        songs.append({'id': name, 'title': ('Minecraft Disc - ' if path.startswith('records/') else 'Minecraft - ') + title,
                      'url': url, 'sha256': descriptor_sha, 'bytes': obj['size'], 'durationTicks': math.ceil(seconds * 20), 'nativeAssetSha1': obj['hash']})
        measured.append({'path': path, 'nativeAssetSha1': obj['hash'], 'bytes': obj['size'], 'seconds': seconds})
    assert len({song['id'] for song in songs}) == len(songs)
    assert all(aliases[name] == value for name, value in original_aliases.items())
    resource = HERE / 'resources'
    for relative, value in [('data/goplanska_kukirin/vanilla-music.json', {'minecraft': '1.21.1', 'assetIndex': spec['id'], 'songs': songs}), ('assets/goplanska_kukirin/sounds.json', aliases)]:
        output = resource / relative
        output.parent.mkdir(parents=True, exist_ok=True)
        output.write_text(json.dumps(value, indent=2, ensure_ascii=False) + '\n')
    source_path = args.music_source
    music = source_path.read_text()
    assert music.count('TreeSet var0 = new TreeSet(ScooterMusicSources.catalog().keySet());') == 1
    music = music.replace('TreeSet var0 = new TreeSet(ScooterMusicSources.catalog().keySet());', 'TreeSet var0 = new TreeSet(ScooterMusicSources.catalog().keySet());\n      var0.addAll(VanillaMusicCatalog.songs());')
    assert music.count('Song var4 = (Song)ScooterMusicSources.catalog().get(var2);') == 1
    music = music.replace('Song var4 = (Song)ScooterMusicSources.catalog().get(var2);', 'Song var4 = VanillaMusicCatalog.get(var2);\n         if (var4 == null) var4 = (Song)ScooterMusicSources.catalog().get(var2);')
    music = music.replace('var0x -> ScooterMusicSources.catalog().containsKey(var0x)', 'var0x -> VanillaMusicCatalog.get(var0x) != null || ScooterMusicSources.catalog().containsKey(var0x)')
    music = music.replace('Choose a WAV from the server song list.', 'Choose a track from the server song list.')
    output = HERE / 'src/pl/aridlin/kukirin/ScooterMusic.java'
    output.write_text(music)
    report = {'success': True, 'minecraft': '1.21.1', 'native_music_resources': len(paths), 'native_music_tracks': sum(not p.startswith('records/') for p in paths),
              'native_music_discs': sum(p.startswith('records/') for p in paths), 'aria_math_present': True, 'all_music_asset_index_entries_present': True,
              'all_original_custom_sound_definitions_unchanged': True, 'copyrighted_audio_copied': False,
              'asset_index_sha1': spec['sha1'], 'native_sounds_json_sha1': sounds_object['hash'], 'measured_native_assets': measured,
              'source_sha256': sha(output.read_bytes()), 'base_jar_sha256': sha(args.base_jar.read_bytes()),
              'resources': {str(p.relative_to(resource)): sha(p.read_bytes()) for p in resource.rglob('*') if p.is_file()}}
    (HERE / 'catalog-proof.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({k: v for k, v in report.items() if k not in ('measured_native_assets', 'resources')}))


if __name__ == '__main__':
    main()
