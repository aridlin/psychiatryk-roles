"""Offline long-track import and conversion checks; no provider/network calls."""
from pathlib import Path
import hashlib
import json
import math
import sys
import tempfile
import time
import wave

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'service'))
import common
import service
import worker

checks = 0


def check(condition, description):
    global checks
    checks += 1
    if not condition:
        raise AssertionError(description)


with tempfile.TemporaryDirectory(prefix='music-stream-offline-', dir=ROOT / 'build') as scratch:
    scratch = Path(scratch)
    source = scratch / 'over64MiB.wav'
    payload_bytes = 66 * 1024 * 1024
    with wave.open(str(source), 'wb') as output:
        output.setnchannels(1)
        output.setsampwidth(2)
        output.setframerate(22050)
        chunk = bytes(64 * 1024)
        for _ in range(payload_bytes // len(chunk)):
            output.writeframesraw(chunk)
    duration = payload_bytes / 44100
    target = scratch / 'converted.wav'
    converted = worker.audio_convert(source, target, duration)
    check(source.stat().st_size > 64 * 1024 * 1024, 'Input exceeds old64MiB limit')
    check(target.stat().st_size > 16 * 1024 * 1024, 'Output exceeds old16MiB limit')
    check(converted > 600, 'Output exceeds old10minute limit without truncation')
    check(abs(converted - duration) < 0.1, 'Entire input duration converted')
    metadata = common.metadata({'id': 'abcdefghijk', 'title': 'offline fixture', 'duration': 36000})
    check(metadata['duration'] == 36000, 'Ten-hour metadata accepted')
    check('max_filesize' not in worker.ydl_options(scratch), 'Provider input file cap removed')

    def fixture(request, work):
        media = work / 'fixture.wav'
        media.write_bytes(target.read_bytes())
        row = common.metadata({'id': 'abcdefghijk', 'title': 'long fixture', 'duration': converted})
        row.update(file='fixture.wav', bytes=media.stat().st_size,
                   sha256=hashlib.sha256(media.read_bytes()).hexdigest())
        return {'entries': [row], 'kind': 'track'}

    engine = service.Engine(scratch / 'state', 'synthetic-fixture-token-only',
                            'https://example.invalid/api', worker=fixture)
    try:
        check(engine.max_cache == 0 and engine.max_tracks == 0, 'No configured library duration/byte/track caps')
        queued = engine.submit({'url': 'https://music.youtube.com/watch?v=abcdefghijk', 'mode': 'import', 'maxEntries': 0})
        until = time.monotonic() + 20
        while time.monotonic() < until:
            receipt = engine.get(queued['jobId'])
            if receipt['status'] not in ('queued', 'running'):
                break
            time.sleep(0.05)
        check(receipt['status'] == 'complete', 'Long large import completed')
        entry = receipt['entries'][0]
        check(entry['bytes'] > 16 * 1024 * 1024, 'Published metadata retains large bytes')
        check(entry['durationTicks'] > 12000, 'Published metadata retains long duration')
        check((engine.state / 'audio' / (entry['sha256'] + '.wav')).stat().st_size == target.stat().st_size,
              'Complete audio remains on hosting service')
    finally:
        engine.pool.shutdown(wait=True)

report = {'success': True, 'assertions': checks, 'track_input_bytes': source.stat().st_size if source.exists() else payload_bytes + 44,
          'fixture_duration_seconds': converted, 'over64MiB_input_accepted': True,
          'over16MiB_WAV_accepted': True, 'over600_second_track_accepted': True,
          'real_provider_download_tested': False, 'production_changed': False}
(ROOT / 'build/service-test.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report))
