"""Compile the authored compatibility plugins and reproduce the narrow loaderfix JAR.

Provide legally obtained baseline JAR and local Java/NeoForge/Mixin classpath.
No downloads, credentials, server actions or game launch occur.
"""
from pathlib import Path
import argparse
import hashlib
import json
import subprocess
import zipfile

sha = lambda data: hashlib.sha256(data).hexdigest()
ROOT = Path(__file__).resolve().parent
STAMP = (2026, 10, 8, 22, 0, 0)
BASE = 'de57e55ed0171644758b2c82689c1fcfa680ad6a8bc7d4ba72b86d3b4ae16c0f'


def archive(path, values):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED) as z:
        for n, data in sorted(values.items()):
            i = zipfile.ZipInfo(n, STAMP)
            i.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(i, data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--classpath-file', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if sha(args.baseline.read_bytes()) != BASE:
        raise ValueError('Exact original 3.0.8 baseline required')
    proof = json.loads((ROOT / 'native-proof.json').read_text())
    for section in ('sources_sha256', 'fixtures_sha256'):
        for n, h in proof[section].items():
            if sha((ROOT / n).read_bytes()) != h:
                raise ValueError('Frozen authored source differs')
    out = args.out.resolve()
    out.mkdir(parents=True, exist_ok=False)
    classes = out / 'classes'
    classes.mkdir()
    sources = sorted((ROOT / 'src').rglob('*.java'))
    cp = str(args.baseline.resolve()) + ':' + args.classpath_file.read_text().strip()
    subprocess.run(['javac', '-J-Xmx256m', '--release', '21', '-proc:none', '-cp', cp, '-d', str(classes), *map(str, sources)], check=True)
    runtime = {p.relative_to(classes).as_posix(): p.read_bytes() for p in classes.rglob('*.class')}
    if {n: sha(v) for n, v in runtime.items()} != proof['runtime_sha256']:
        raise ValueError('Native compiled plugin bytes differ')
    names = {'pl/aridlin/psychiatrykroles/compat/' + n + '.class' for n in ('JeiTransferCompatPlugin', 'FlywheelLegacyCompatPlugin')}
    overlay = {n: runtime[n] for n in names}
    archive(out / 'loader-compat-308-overlay.zip', overlay)
    with zipfile.ZipFile(args.baseline) as z:
        original = {n: z.read(n) for n in z.namelist() if not n.endswith('/')}
    final = dict(original)
    final.update(overlay)
    jar = out / 'psychiatryk_roles-3.0.8-bmc5-loaderfix.jar'
    archive(jar, final)
    if sha(jar.read_bytes()) != proof['candidate308_sha256']:
        raise ValueError('Candidate byte identity differs')
    result = {'success': True, 'byte_identical_candidate': True, 'candidate_sha256': sha(jar.read_bytes()), 'game_launched': False, 'remote_mutation_performed': False}
    (out / 'build-proof.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps(result))


if __name__ == '__main__':
    main()
