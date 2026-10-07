"""Recursive offline/resource, navigation and SEO audit. No network or site build."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlsplit,unquote
from collections import Counter
from xml.etree import ElementTree as ET
import json,re,posixpath,subprocess
R=Path(__file__).resolve().parents[3];S=R/'website';D=R/'web_docs/chat-web7'
config=json.loads((D/'SITE_MAP.json').read_text());routes=config['routes'];BASE=config['base_url']
mapping={r['legacy']:r['path'] for r in routes}
def canonical(p):return BASE+(p[:-10] if p.endswith('index.html') else p)
def resolve(page,href):return posixpath.normpath(posixpath.join(posixpath.dirname(page),unquote(urlsplit(href).path))) if urlsplit(href).path else page
class Page(HTMLParser):
 def __init__(self,text):
  super().__init__(convert_charrefs=True);self.ids=[];self.links=[];self.resources=[];self.current=[];self.tags=Counter();self.title='';self.in_title=False;self.metas={};self.canon=[];self.schemas=[];self.script=None;self.feed(text)
 def handle_starttag(self,t,attrs):
  a=dict(attrs);self.tags[t]+=1
  assert t not in {'iframe','object','embed','base','form','input'},t
  assert not any(k.startswith('on') for k in a)
  if t=='script':
   assert a=={'type':'application/ld+json'},a;self.script=''
  if 'id' in a:self.ids.append(a['id'])
  if t=='title':self.in_title=True
  if t=='meta':
   key=a.get('name') or a.get('property')
   if key:assert key not in self.metas;self.metas[key]=a['content']
  if t=='a':
   self.links.append(a['href'])
   if a.get('aria-current')=='page':self.current.append(a['href'])
  if 'src' in a:self.resources.append(a['src'])
  if t=='link':
   if a.get('rel')=='canonical':self.canon.append(a['href'])
   else:self.resources.append(a['href'])
  assert 'srcset' not in a
 def handle_endtag(self,t):
  if t=='title':self.in_title=False
  if t=='script' and self.script is not None:self.schemas.append(json.loads(self.script));self.script=None
 def handle_data(self,s):
  if self.in_title:self.title+=s
  if self.script is not None:self.script+=s
texts={p.relative_to(S).as_posix():p.read_text() for p in S.rglob('*.html')}
assert set(texts)==set(mapping.values()) and len(texts)==30
assert sorted(p.name for p in S.glob('*.html'))==['index.html']
pages={n:Page(t) for n,t in texts.items()};links=0;external=set();titles=set();descriptions=set();canonicals=set();incoming=Counter()
def normalized(fragment,page):
 def change(m):
  a,q,v=m.groups();u=urlsplit(v)
  return m.group(0) if u.scheme or not u.path else f'{a}={q}{resolve(page,v)}{q}'
 return re.sub(r'\b(href|src)=([\"\'])([^\"\']+)\2',change,fragment).replace(' aria-current="page"','')
header0=normalized(re.search(r'<header class="site-header">.*?</header>',texts['index.html'],re.S)[0],'index.html')
footer0=normalized(re.search(r'<footer class="site-footer">.*?</footer>',texts['index.html'],re.S)[0],'index.html')
for row in routes:
 n=row['path'];p=pages[n];text=texts[n];url=canonical(n)
 assert p.tags['h1']==p.tags['main']==1,n
 assert len(p.ids)==len(set(p.ids)),n
 assert '<html lang="en">' in text and p.metas['viewport']=='width=device-width, initial-scale=1'
 assert p.title==row['title'] and p.title not in titles;n_title=len(p.title);assert 15<=n_title<=100,(n,n_title);titles.add(p.title)
 assert p.metas['description']==row['description'] and row['description'] not in descriptions;descriptions.add(row['description'])
 assert 55<=len(row['description'])<=220,(n,len(row['description']))
 assert p.canon==[url] and url not in canonicals;canonicals.add(url)
 assert p.metas['robots']=='index, follow, max-image-preview:large'
 assert p.metas['og:title']==p.title and p.metas['og:description']==row['description'] and p.metas['og:url']==url
 assert p.metas['og:image']==BASE+'assets/images/social-card.png'
 assert p.metas['twitter:image']==p.metas['og:image'] and p.metas['twitter:card']=='summary_large_image'
 assert p.metas['twitter:title']==p.title and p.metas['twitter:description']==row['description']
 assert len(p.schemas)==1 and p.tags['script']==1
 data=p.schemas[0];assert data['@context']=='https://schema.org';graph=data['@graph'];types={x['@type'] for x in graph}
 node=next(x for x in graph if x['@id']==url+'#page');assert node['name']==p.title and node['url']==url
 if row['legacy']!='index.html':
  assert 'aria-label="Breadcrumb"' in text
  crumb=next(x for x in graph if x['@type']=='BreadcrumbList')
  assert crumb['itemListElement']==[{'@type':'ListItem','position':i+1,'name':c['name'],'item':canonical(c['path'])} for i,c in enumerate(row['breadcrumbs'])]
 if row['legacy']=='learn.html':assert len(next(x for x in graph if x['@type']=='Course')['hasPart'])==19
 if row['legacy']=='index.html':assert 'SoftwareApplication' in types
 assert not re.search(r'aggregateRating|reviewCount',json.dumps(graph))
 expected=[] if row['legacy']=='termux.html' else [mapping['privacy.html']] if row['legacy']=='privacy.html' else [mapping['learn.html']]*2 if row['legacy'].startswith('ch-') else [n]*2
 assert [resolve(n,x) for x in p.current]==expected,(n,p.current,expected)
 assert normalized(re.search(r'<header class="site-header">.*?</header>',text,re.S)[0],n)==header0,n
 assert normalized(re.search(r'<footer class="site-footer">.*?</footer>',text,re.S)[0],n)==footer0,n
 for href in p.links:
  u=urlsplit(href)
  if u.scheme:assert u.scheme=='https';external.add(href);continue
  assert not u.netloc and not href.startswith('/'),(n,href)
  target=resolve(n,href);assert target in pages,(n,href,target)
  if u.fragment:assert unquote(u.fragment) in pages[target].ids,(n,href)
  links+=1
  if target!=n:incoming[target]+=1
 for src in p.resources:
  u=urlsplit(src);assert not u.scheme and not u.netloc and not src.startswith('/'),(n,src)
  f=(S/resolve(n,src)).resolve();assert f.is_relative_to(S.resolve()) and f.is_file(),(n,src)
 if row['legacy'].startswith('ch-'):
  num=int(row['legacy'][3:5]);assert f'Chapter {num} of 19' in text
  for x in ['What you will learn','Before you begin','Try it yourself','Common mistakes','Chapter navigation']:assert x in text
  prev=mapping['learn.html' if num==1 else f'ch-{num-1:02}.html'];nxt=mapping['learn.html' if num==19 else f'ch-{num+1:02}.html']
  nav=Page(re.search(r'<nav class="prev-next".*?</nav>',text,re.S)[0]);assert [resolve(n,x) for x in nav.links]==[prev,nxt],n
 # Content regression: every original preformatted example remains byte-for-byte unchanged.
 if row['legacy']!='termux.html':
  old=subprocess.check_output(['git','show','802532d:website/'+row['legacy']],text=True)
  assert re.findall(r'<pre\b.*?</pre>',old,re.S)==re.findall(r'<pre\b.*?</pre>',text,re.S),(n,'code example changed')
assert set(incoming)==set(pages),'orphan page'
learn=pages[mapping['learn.html']]
assert all(mapping[f'ch-{i:02}.html'] in [resolve(mapping['learn.html'],x) for x in learn.links if not urlsplit(x).scheme] for i in range(1,20))
css=(S/'assets/css/style.css').read_text();assert not re.search(r'@import|url\s*\(',css,re.I)
assert not list(S.rglob('*.js'))
assert (S/'assets/images/favicon.svg').read_bytes()==(R/'docs/brand/icon/codec-mark.svg').read_bytes()
roots=re.search(r'CODEC_REPOSITORY_PACKAGES="\n(.*?)\n"',(R/'codec-packages/properties.codec.sh').read_text(),re.S)[1].split()
assert len(roots)==33 and sorted(roots)==sorted(re.findall(r'data-package="([^"]+)"',texts[mapping['packages.html']]))
for tool in ['list_files','search_project','read_file','read_files','find_files','outline_file','read_run_output','request_run']:
 assert tool in texts[mapping['ai.html']] and tool in texts[mapping['ch-19.html']]
assert '<span class="syntax-keyword">' in texts['index.html'] and '&lt;span class=' not in texts['index.html']
alltext='\n'.join(texts.values())
assert not re.search(r'FULL-SITE REVIEW|Review status|review-notice|preview-notice|acceptance.*pending|transcripts.*still required|learn.html#review|COMING IN W',alltext,re.I)
assert 'your code never leaves the device' not in alltext.lower()
assert not re.search(r'mailto:|\+91\s*62967',alltext)
ns={'s':'http://www.sitemaps.org/schemas/sitemap/0.9'}
xml=ET.parse(S/'sitemap.xml');locs=[n.text for n in xml.findall('.//s:loc',ns)];assert len(locs)==30 and set(locs)==canonicals
assert {n.find('s:loc',ns).text:n.find('s:lastmod',ns).text for n in xml.findall('s:url',ns)}=={canonical(p['path']):p['last_modified'] for p in routes}
assert 'Sitemap: '+BASE+'sitemap.xml' in (S/'robots.txt').read_text()
files=[p for p in S.rglob('*') if p.is_file()];assert len(files)==35
report={'pages':30,'files':len(files),'internal_links_and_anchors':links,'missing_links':0,'orphan_pages':0,'unique_titles_descriptions_canonicals':30,'valid_jsonld_documents':30,'executable_author_scripts':0,'sitemap_urls':30,'package_roots':33,'original_preformatted_examples_unchanged':True,'external_hrefs':sorted(external),'uncompressed_bytes':sum(p.stat().st_size for p in files),'status':'PASS'}
(D/'STATIC_REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
