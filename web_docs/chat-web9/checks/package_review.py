"""Build + verify organized30page website review ZIP (D31); never a website build step."""
from pathlib import Path, PurePosixPath
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
import hashlib
import json
import shutil
import tempfile

ROOT = Path(__file__).resolve().parents[3]
SITE = ROOT / 'website'
RECORD = ROOT / 'web_docs/chat-web9'
DEST = RECORD / 'CodeC-website-licensed.zip'
EXTRACTED = Path(tempfile.gettempdir()) / 'codec-website-licensed-extracted'
files = sorted(p for p in SITE.rglob('*') if p.is_file())
assert files and (SITE / 'index.html') in files
assert not any(p.is_symlink() for p in SITE.rglob('*')), 'No symlinks in review bundle'
for p in files:
    rel = p.relative_to(SITE)
    assert p.resolve().is_relative_to(SITE.resolve())
    assert not any(part.startswith('.') for part in rel.parts)
    assert p.suffix.lower() in {'.html', '.css', '.svg', '.js', '.png', '.jpg', '.jpeg', '.webp', '.ico', '.gif', '.woff', '.woff2', '.json', '.txt', '.xml'}
with ZipFile(DEST, 'w', compression=ZIP_DEFLATED, compresslevel=9) as z:
    for p in files:
        info = ZipInfo(p.relative_to(SITE).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
        info.compress_type = ZIP_DEFLATED
        info.external_attr = 0o100644 << 16
        z.writestr(info, p.read_bytes(), compresslevel=9)
with ZipFile(DEST) as z:
    assert z.testzip() is None
    assert z.namelist() == [p.relative_to(SITE).as_posix() for p in files]
    for name in z.namelist():
        path = PurePosixPath(name)
        assert not path.is_absolute() and '..' not in path.parts
        assert z.read(name) == (SITE / name).read_bytes()
    # Only our fixed scratch extraction directory is replaced, never a source path.
    if EXTRACTED.exists():
        shutil.rmtree(EXTRACTED)
    z.extractall(EXTRACTED)
report = {
    'archive': DEST.relative_to(ROOT).as_posix(),
    'bytes': DEST.stat().st_size,
    'sha256': hashlib.sha256(DEST.read_bytes()).hexdigest(),
    'uncompressed_bytes': sum(p.stat().st_size for p in files),
    'files': [{'path': p.relative_to(SITE).as_posix(), 'bytes': p.stat().st_size,
               'sha256': hashlib.sha256(p.read_bytes()).hexdigest()} for p in files],
    'crc_and_exact_tree_bytes': 'PASS',
}
(RECORD / 'ARCHIVE.json').write_text(json.dumps(report, indent=2) + '\n')
print(json.dumps(report, indent=2))
print(f'Extracted for offline browser test: {EXTRACTED}')
