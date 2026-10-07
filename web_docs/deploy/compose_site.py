#!/usr/bin/env python3
"""Add the static website to an already validated package artifact, never replace it."""
import argparse
import hashlib
from pathlib import Path
import shutil


def manifest(root: Path, prefixes=('dev', 'keys')) -> dict:
    result = {}
    for prefix in prefixes:
        folder = root / prefix
        if not folder.is_dir() or folder.is_symlink():
            raise ValueError(f'Missing real package directory: {prefix}')
        for p in sorted(folder.rglob('*')):
            if p.is_symlink():
                raise ValueError('Symlinks are forbidden')
            if p.is_file():
                result[p.relative_to(root).as_posix()] = hashlib.sha256(p.read_bytes()).hexdigest()
    if not result:
        raise ValueError('Empty package snapshot')
    return result


def compose(site: Path, output: Path) -> None:
    before = manifest(output)
    for required in ('dev/CODEC-REPOSITORY', 'dev/dists/stable/InRelease', 'dev/dists/stable/Release.gpg', 'keys/codec-archive-keyring-v1.gpg'):
        if not (output / required).is_file() or not (output / required).stat().st_size:
            raise ValueError(f'Missing required package file: {required}')
    if not (site / 'index.html').is_file() or site.is_symlink():
        raise ValueError('Website entry point missing')
    files = sorted(site.rglob('*'))
    # Validate the entire source before copying even its first byte.
    for p in files:
        rel = p.relative_to(site)
        if p.is_symlink() or any(part.startswith('.') for part in rel.parts) or rel.parts[0].lower() in ('dev', 'keys'):
            raise ValueError(f'Forbidden website path: {rel}')
        target = output / rel
        if target.exists() and (target.is_file() or target.is_symlink()):
            raise ValueError(f'Artifact collision: {rel}')
    for p in files:
        if p.is_file():
            target = output / p.relative_to(site)
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(p, target)
    (output / '.nojekyll').write_text('')
    if manifest(output) != before:
        raise ValueError('Package tree changed during website composition')
    print(f'Composed website with {len(before)} byte-identical protected package/key files')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('website', type=Path)
    parser.add_argument('artifact', type=Path)
    args = parser.parse_args()
    compose(args.website, args.artifact)
