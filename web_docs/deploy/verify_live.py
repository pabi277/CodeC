#!/usr/bin/env python3
"""Verify public website bytes and preserved package hashes after Pages deployment."""
import argparse
from concurrent.futures import ThreadPoolExecutor
import hashlib
import json
from pathlib import Path
import time
from urllib.request import Request, urlopen
from preserve_packages import BASE, download, assert_hash

ROOT = Path(__file__).resolve().parents[2]


def verify(package_report: Path, report_path: Path) -> None:
    source = ROOT / 'website'
    expected = [{'path': p.relative_to(source).as_posix(), 'bytes': p.stat().st_size, 'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in sorted(source.rglob('*')) if p.is_file()]
    preserved = json.loads(package_report.read_text())['files']
    # Pages/CDN propagation can lag a successful deployment for a short time.
    for attempt in range(12):
        try:
            if download('index.html') == (source / 'index.html').read_bytes():
                break
        except OSError:
            pass
        if attempt == 11:
            raise ValueError('New homepage not publicly visible after propagation wait')
        time.sleep(10)
    def check(row):
        data = download(row['path'], max(row['bytes'], 1))
        assert_hash(data, row['sha256'], row['bytes'])
        return row['path']
    with ThreadPoolExecutor(max_workers=4) as pool:
        verified = list(pool.map(check, expected + preserved))
    # Verify directory entry URLs, content types and absence of noindex headers.
    checks = []
    for name in ('', 'learn/', 'about/', 'guides/codec-vs-termux.html', 'assets/css/style.css', 'sitemap.xml'):
        with urlopen(Request(BASE + name, headers={'Cache-Control': 'no-cache'}), timeout=60) as response:
            if response.status != 200 or 'noindex' in response.headers.get('X-Robots-Tag', '').lower():
                raise ValueError('Unexpected status or noindex header')
            content_type = response.headers.get_content_type()
            required = 'text/css' if name.endswith('.css') else None if name.endswith('.xml') else 'text/html'
            if required and content_type != required:
                raise ValueError(f'Wrong MIME type: {name}: {content_type}')
            checks.append({'url': BASE + name, 'status': response.status, 'content_type': content_type})
    report = {'status': 'PASS', 'base_url': BASE, 'website_files_exact': len(expected), 'protected_package_files_exact': len(preserved), 'verified_paths': verified, 'public_routes': checks, 'note': 'Public HTTP/bytes verified; no search-indexing or ranking claim.'}
    report_path.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('package_report', type=Path)
    parser.add_argument('report', type=Path)
    args = parser.parse_args()
    verify(args.package_report, args.report)
