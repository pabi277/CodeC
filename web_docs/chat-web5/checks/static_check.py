"""W2 static-site preflight. Stdlib only; run from any directory. Not a site build."""
from collections import Counter
from html.parser import HTMLParser
from pathlib import Path
from urllib.parse import unquote, urlsplit
import re

ROOT = Path(__file__).resolve().parents[3]
SITE = ROOT / 'website'
EXPECTED_FUTURE = {'engines.html', 'packages.html', 'ai.html', 'learn.html', 'faq.html', 'about.html', 'privacy.html'}

class Page(HTMLParser):
    def __init__(self, text):
        super().__init__(convert_charrefs=True)
        self.ids, self.links, self.resources = [], [], []
        self.tags = Counter()
        self.current = []
        self.feed(text)
    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        self.tags[tag] += 1
        if 'id' in a:
            self.ids.append(a['id'])
        if tag == 'a' and 'href' in a:
            self.links.append(a['href'])
            if a.get('aria-current') == 'page':
                self.current.append(a['href'])
        assert tag not in {'script', 'iframe', 'object', 'embed', 'base'}, tag
        assert not any(k.startswith('on') for k in a), 'Inline handler'
        if 'src' in a:
            self.resources.append(a['src'])
        if tag == 'link':
            self.resources.append(a['href'])
        assert 'srcset' not in a, 'Review srcset manually if added'

files = sorted(SITE.glob('*.html'))
assert {p.name for p in files} == {'index.html', 'install.html', 'start.html'}
texts = {p.name: p.read_text() for p in files}
pages = {name: Page(text) for name, text in texts.items()}
future, external = set(), set()
for name, page in pages.items():
    assert page.tags['h1'] == page.tags['main'] == 1, name
    assert len(page.ids) == len(set(page.ids)), f'Duplicate IDs: {name}'
    assert page.current == [name, name], f'Active nav: {name}'
    text = texts[name]
    assert '<html lang="en">' in text and 'name="viewport"' in text
    assert 'href="privacy.html"' in text
    for resource in page.resources:
        url = urlsplit(resource)
        assert not url.scheme and not url.netloc and not resource.startswith('/'), resource
        target = (SITE / unquote(url.path)).resolve()
        assert target.is_relative_to(SITE.resolve()) and target.is_file(), resource
    for href in page.links:
        url = urlsplit(href)
        if url.scheme:
            assert url.scheme == 'https', href
            external.add(href)
            continue
        assert not url.netloc and not href.startswith('/'), href
        target_name = unquote(url.path) or name
        target = SITE / target_name
        if not target.exists():
            future.add(target_name)
            assert target_name in EXPECTED_FUTURE and not url.fragment, href
        elif url.fragment:
            assert unquote(url.fragment) in pages[target_name].ids, f'{name}: {href}'
    header = re.search(r'<header class="site-header">.*?</header>', text, re.S)[0]
    normalized = header.replace(' aria-current="page"', '')
    canonical = re.search(r'<header class="site-header">.*?</header>', texts['index.html'], re.S)[0].replace(' aria-current="page"', '')
    assert normalized == canonical, f'Header drift: {name}'
    assert text.split('  <!-- Shared footer')[1] == texts['index.html'].split('  <!-- Shared footer')[1], f'Footer drift: {name}'
assert future == EXPECTED_FUTURE, future
css = (SITE / 'style.css').read_text()
assert not re.search(r'@import|url\s*\(', css, re.I), 'Review new CSS resource loading'
assert not list(SITE.rglob('*.js'))
assert (SITE / 'favicon.svg').read_bytes() == (ROOT / 'docs/brand/icon/codec-mark.svg').read_bytes()
assert '549a8c54cf9ff3fff4f6d985997bbb887f6b3b3098f4ab6ba68fefe7028a60d8' in texts['install.html']
assert '7,217,532' in texts['install.html']
assert '-74%' not in ''.join(texts.values())
assert 'cc hello.c -o a.out\n./a.out' in texts['start.html']
assert 'cc double.c -o a.out\n./a.out' in texts['start.html']
print(f'PASS: {len(pages)} pages, local resources, metadata, headings, IDs, implemented links/anchors, chrome, release pins, no JS.')
print('Deferred paths (expected, NOT passing built links):', ', '.join(sorted(future)))
print(f'{len(external)} outbound URLs (not fetched by this script):')
print('\n'.join(sorted(external)))
print('Site files:', ', '.join(p.name for p in SITE.iterdir()))
print('Uncompressed bytes:', sum(p.stat().st_size for p in SITE.rglob('*') if p.is_file()))
