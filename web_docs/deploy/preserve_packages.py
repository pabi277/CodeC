#!/usr/bin/env python3
"""Read-only recovery of the currently published signed CodeC package tree.

No private keys, package builds, installs, metadata generation or re-signing.
Fail closed on any missing file, changed signature, unsafe path or hash mismatch.
"""
from __future__ import annotations
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import subprocess
import sys
import time
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
BASE = 'https://pabi277.github.io/CodeC/'
KEYS = ROOT / 'codec-packages/keys'
SPEC = importlib.util.spec_from_file_location('codec_repository_validator', ROOT / 'codec-packages/scripts/validate-repository.py')
sys.path.insert(0, str(ROOT / 'codec-packages/scripts'))
validator = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(validator)


def safe_path(value: str) -> str:
    if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_+~./-]+', value):
        raise ValueError(f'Unsafe path: {value!r}')
    if value.startswith('/') or any(p in ('', '.', '..') for p in value.split('/')):
        raise ValueError(f'Unsafe path: {value!r}')
    return value


def digest(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def download(relative: str, limit: int = 16_000_000) -> bytes:
    url = BASE + safe_path(relative)
    for attempt in range(3):
        try:
            req = Request(url, headers={'User-Agent': 'CodeC-pages-preservation/1', 'Cache-Control': 'no-cache'})
            with urlopen(req, timeout=90) as response:
                if response.status != 200 or response.url != url:
                    raise ValueError(f'Unexpected HTTP response/redirect: {url}')
                data = response.read(limit + 1)
            if len(data) > limit:
                raise ValueError(f'File exceeds expected size limit: {relative}')
            return data
        except OSError:
            if attempt == 2:
                raise
            time.sleep(2 ** attempt)
    raise AssertionError('unreachable')


def assert_hash(data: bytes, expected: str, size: int | None = None) -> None:
    if not re.fullmatch(r'[0-9a-f]{64}', expected) or digest(data) != expected:
        raise ValueError('SHA256 mismatch')
    if size is not None and len(data) != size:
        raise ValueError('Size mismatch')


def package_records(root: Path) -> dict[str, tuple[str, int]]:
    """Use verified APT indexes, not the unsigned JSON manifest, as authority."""
    records = {}
    for arch in ('all', 'aarch64', 'x86_64'):
        index = root / f'dists/stable/main/binary-{arch}/Packages'
        for stanza in validator.parse_packages(index.read_text()):
            name = safe_path(stanza['Filename'])
            if not re.fullmatch(r'dists/stable/main/binary-(all|aarch64|x86_64)/[A-Za-z0-9_+.~-]+\.deb', name):
                raise ValueError(f'Unexpected package location: {name}')
            value = (stanza['SHA256'], int(stanza['Size']))
            if not re.fullmatch(r'[0-9a-f]{64}', value[0]) or not 0 < value[1] <= 512_000_000:
                raise ValueError('Invalid package digest/size')
            if name in records and records[name] != value:
                raise ValueError('Conflicting index records')
            records[name] = value
    if not records or sum(size for _, size in records.values()) > 950_000_000:
        raise ValueError('Empty repository or Pages size budget exceeded')
    return records


def preserve(output: Path) -> dict:
    if output.exists():
        raise ValueError('Output must be a new directory; never overwrite an existing tree')
    output.mkdir(parents=True)
    def save(name: str, data: bytes) -> None:
        target = output / safe_path(name)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)

    # The deployed trust files must exactly match the repository-pinned public keys.
    for name in ('codec-archive-keyring-v1.gpg', 'codec-archive-keyring-v1.asc', 'codec-archive-keyring-v1.fingerprints'):
        data = download('keys/' + name)
        if data != (KEYS / name).read_bytes():
            raise ValueError('Live public key differs from the pinned trust material')
        save('keys/' + name, data)
    for name in ('Release', 'Release.sha256', 'InRelease', 'Release.gpg'):
        save('dev/dists/stable/' + name, download('dev/dists/stable/' + name))
    repo = output / 'dev'
    fingerprint = next(line.split('=', 1)[1] for line in (KEYS / 'codec-archive-keyring-v1.fingerprints').read_text().splitlines() if line.startswith('signing='))
    validator.verify_release_signatures(repo / 'dists/stable/Release', KEYS / 'codec-archive-keyring-v1.gpg', fingerprint)
    validator.check_sidecar(repo / 'dists/stable/Release', 'Release')
    hashes = validator.parse_release_hashes((repo / 'dists/stable/Release').read_text())
    expected = {f'main/binary-{arch}/Packages{suffix}' for arch in ('all', 'aarch64', 'x86_64') for suffix in ('', '.gz')}
    if set(hashes) != expected:
        raise ValueError('Unexpected signed index layout; review before publication')
    for name, sha in hashes.items():
        data = download('dev/dists/stable/' + safe_path(name))
        assert_hash(data, sha)
        save('dev/dists/stable/' + name, data)
    records = package_records(repo)
    def fetch_package(item):
        name, (sha, size) = item
        data = download('dev/' + name, size)
        assert_hash(data, sha, size)
        save('dev/' + name, data)
        print('Preserved', name, size, flush=True)
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(fetch_package, sorted(records.items())))
    for name in ('CODEC-REPOSITORY', 'repository.json', 'repository.json.sha256'):
        save('dev/' + name, download('dev/' + name))
    manifest = json.loads((repo / 'repository.json').read_text())
    declared = {r['filename']: (r['sha256'], r['size']) for r in manifest['packages']}
    if len(declared) != len(manifest['packages']) or declared != records:
        raise ValueError('Manifest and signed indexes disagree')
    validator.validate(repo, ['aarch64', 'x86_64'], KEYS / 'codec-archive-keyring-v1.gpg', fingerprint)
    # Catch a package publication during the snapshot, even if each download worked.
    for relative in ('dev/dists/stable/InRelease', 'dev/repository.json'):
        if download(relative) != (output / relative).read_bytes():
            raise ValueError('Live repository changed during snapshot; retry from a fresh tree')
    files = [{'path': p.relative_to(output).as_posix(), 'bytes': p.stat().st_size, 'sha256': digest(p.read_bytes())} for p in sorted(output.rglob('*')) if p.is_file()]
    return {'source': BASE, 'status': 'PASS', 'package_count': len(records), 'signing_fingerprint': fingerprint, 'files': files}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    report = preserve(args.output)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print('Signed package preservation PASS:', report['package_count'], 'packages')
