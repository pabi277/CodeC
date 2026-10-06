"""Full-site static validation. Run from anywhere; no app build or site build step."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlsplit, unquote
from collections import Counter
import re, json
R=Path(__file__).resolve().parents[3]; S=R/'website'
class Page(HTMLParser):
    def __init__(self,text):
        super().__init__(convert_charrefs=True);self.ids=[];self.links=[];self.resources=[];self.current=[];self.tags=Counter();self.title='';self.in_title=False;self.feed(text)
    def handle_starttag(self,t,attrs):
        a=dict(attrs);self.tags[t]+=1
        assert t not in {'script','iframe','object','embed','base','form','input'},t
        assert not any(k.startswith('on') for k in a),a
        if 'id' in a:self.ids.append(a['id'])
        if t=='title':self.in_title=True
        if t=='a':
            self.links.append(a['href'])
            if a.get('aria-current')=='page':self.current.append(a['href'])
        if 'src' in a:self.resources.append(a['src'])
        if t=='link':self.resources.append(a['href'])
        assert 'srcset' not in a
    def handle_endtag(self,t):
        if t=='title':self.in_title=False
    def handle_data(self,s):
        if self.in_title:self.title+=s
names={'index.html','install.html','start.html','engines.html','packages.html','faq.html','about.html','ai.html','privacy.html','learn.html'}|{f'ch-{i:02}.html' for i in range(1,20)}
texts={p.name:p.read_text() for p in S.glob('*.html')};assert set(texts)==names
pages={n:Page(t) for n,t in texts.items()};external=set();local_count=0
assert len({p.title for p in pages.values()})==29
canonical_header=re.search(r'<header class="site-header">.*?</header>',texts['index.html'],re.S)[0].replace(' aria-current="page"','')
canonical_footer=texts['index.html'].split('  <!-- Shared footer')[1]
for n,p in pages.items():
    assert p.tags['h1']==p.tags['main']==1,n
    assert len(p.ids)==len(set(p.ids)),n
    assert '<html lang="en">' in texts[n] and 'name="description"' in texts[n] and 'name="viewport"' in texts[n]
    expected=['privacy.html'] if n=='privacy.html' else ['learn.html']*2 if n.startswith('ch-') else [n]*2
    assert p.current==expected,(n,p.current,expected)
    header=re.search(r'<header class="site-header">.*?</header>',texts[n],re.S)[0].replace(' aria-current="page"','');assert header==canonical_header,n
    footer=texts[n].split('  <!-- Shared footer')[1].replace(' aria-current="page"','');assert footer==canonical_footer,n
    for href in p.links:
        u=urlsplit(href)
        if u.scheme:
            assert u.scheme=='https',href;external.add(href);continue
        assert not u.netloc and not href.startswith('/'),href
        target=unquote(u.path) or n;assert target in pages,(n,href)
        if u.fragment:assert unquote(u.fragment) in pages[target].ids,(n,href)
        local_count+=1
    for src in p.resources:
        u=urlsplit(src);assert not u.scheme and not u.netloc and not src.startswith('/'),src
        f=(S/unquote(u.path)).resolve();assert f.is_relative_to(S.resolve()) and f.is_file(),src
    if n.startswith('ch-'):
        num=int(n[3:5]);assert f'Chapter {num} of 19' in texts[n],n
        for required in ['What you will learn','Before you begin','Try it yourself','Common mistakes','Chapter navigation']:assert required in texts[n],(n,required)
        prev='learn.html' if num==1 else f'ch-{num-1:02}.html';nxt='learn.html' if num==19 else f'ch-{num+1:02}.html'
        nav=re.search(r'<nav class="prev-next".*?</nav>',texts[n],re.S)[0]
        assert f'href="{prev}"' in nav and f'href="{nxt}"' in nav,n
assert all(f'href="ch-{i:02}.html"' in texts['learn.html'] for i in range(1,20))
css=(S/'style.css').read_text();assert not re.search(r'@import|url\s*\(',css,re.I)
assert not list(S.glob('*.js'))
assert (S/'favicon.svg').read_bytes()==(R/'docs/brand/icon/codec-mark.svg').read_bytes()
roots=re.search(r'CODEC_REPOSITORY_PACKAGES="\n(.*?)\n"',(R/'codec-packages/properties.codec.sh').read_text(),re.S)[1].split()
assert len(roots)==33 and sorted(roots)==sorted(re.findall(r'data-package="([^"]+)"',texts['packages.html']))
for tool in ['list_files','search_project','read_file','read_files','find_files','outline_file','read_run_output','request_run']:
    assert tool in texts['ai.html'] and tool in texts['ch-19.html']
assert '<span class="syntax-keyword">' in texts['index.html']
assert '&lt;span class=' not in texts['index.html']
alltext='\n'.join(texts.values())
assert 'your code never leaves the device' not in alltext.lower()
assert not re.search(r'mailto:|\+91\s*62967',alltext)
assert not re.search(r'COMING IN W|W3 · upcoming|Course · coming next|W2 / 3',alltext)
report={'pages':29,'files':len(list(S.iterdir())),'local_links_and_anchors_checked':local_count,'missing_links':0,'external_hrefs':sorted(external),'author_js':0,'package_roots':len(roots),'bytes':sum(f.stat().st_size for f in S.rglob('*') if f.is_file()),'status':'PASS'}
(R/'web_docs/chat-web6/STATIC_REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
