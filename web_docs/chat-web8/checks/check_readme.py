"""Read-only README link/heading/content and host C example preflight."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlsplit,unquote
import re,json,subprocess,tempfile,hashlib,struct
R=Path(__file__).resolve().parents[3];D=R/'web_docs/chat-web8';text=(R/'README.md').read_text()
# Use a GitHub Markdown API rendering saved by the caller, not an approximation
# of raw HTML links. The API does not add GitHub's page-level heading anchors.
import os
render=Path(os.environ.get('README_RENDER','/home/user/.cache/codec-readme-tools/github-body.html')).read_text()
class P(HTMLParser):
 def __init__(self):super().__init__();self.links=[];self.images=[];self.heading=None;self.headings=[]
 def handle_starttag(self,t,a):
  a=dict(a)
  if t=='a':self.links.append(a['href'])
  if t=='img':assert a.get('alt');self.images.append(a['src'])
  if t in ['h1','h2','h3']:self.heading=''
 def handle_data(self,s):
  if self.heading is not None:self.heading+=s
 def handle_endtag(self,t):
  if t in ['h1','h2','h3'] and self.heading is not None:self.headings.append(self.heading);self.heading=None
p=P();p.feed(render)
def slug(s):return re.sub(r'[^\w\- ]','',s.lower()).replace(' ','-')
anchors={slug(s) for s in p.headings};local=[];external=[]
for href in p.links+p.images:
 u=urlsplit(href)
 if u.scheme:
  assert u.scheme=='https',href;external.append(href);continue
 assert not u.netloc and not href.startswith('/'),href
 if u.path:
  f=(R/unquote(u.path)).resolve();assert f.is_relative_to(R) and f.exists(),href;local.append(href)
 elif u.fragment:assert unquote(u.fragment) in anchors,href
assert p.images==['assets/readme/hero.jpg','assets/readme/workflow.jpg']
assert text.count('<details>')==text.count('</details>')==5
assert text.count('```')%2==0
for required in ['arm64-v8a','x86_64','versionCode **22**','7,217,532','CodeC-IDE-release','CodeC-IDE-debug','same signing','19-chapter','not a public deployment','not screenshots','no manual compiler-engine picker','read-only follow-ups','session-only','task-memory/undo','NVIDIA','dev/test','provider’s terms','Gradle 9.3.1','rule.md','explicit','owner']:
 assert required.lower() in text.lower(),required
for forbidden in ['unsigned-key debug','Settings → Compiler Engine →','uninstall and reinstall the app once','RUN has no keyboard','Switch the engine to Termux','60fps']:
 assert forbidden not in text,forbidden
for tool in ['list_files','search_project','read_file','read_files','find_files','outline_file','read_run_output','request_run']:assert '`'+tool+'`' in text
assert not re.search(r'mailto:|\+91\s*62967|license-MIT|img\.shields\.io',text)
code=re.search(r'```c\n(.*?)\n```',text,re.S)[1]
with tempfile.TemporaryDirectory(prefix='codec-readme-') as tmp:
 f=Path(tmp)/'main.c';f.write_text(code+'\n');exe=Path(tmp)/'hello'
 subprocess.run(['cc','-std=c90','-pedantic','-Wall','-Wextra','-Werror',str(f),'-o',str(exe)],check=True,capture_output=True)
 result=subprocess.run([str(exe)],capture_output=True,text=True,check=True)
 assert result.stdout=='Hello from CodeC!\nMade on my phone.\n'
# No APK/website change can hide inside a documentation refresh.
for path in ['website','app','.github/workflows','codec-packages','docs','scripts','gradle']:
 assert subprocess.check_output(['git','diff','0715ab8','--',path],text=True)=='',path
images=[{'path':n,'bytes':(R/n).stat().st_size,'sha256':hashlib.sha256((R/n).read_bytes()).hexdigest()} for n in p.images]
assert sum(i['bytes'] for i in images)<200_000
report={'status':'PASS','readme_bytes':len(text.encode()),'heading_count':len(p.headings),'internal_link_and_image_refs':len(local),'missing_local_targets':0,'image_count':len(images),'images':images,'external_links':sorted(set(external)),'c_example':'Host C90 compile/output PASS; not Android acceptance','protected_trees':'unchanged from0715ab8','github_markdown_api_render':'parsed; local target/heading assertions PASS'}
(D/'STATIC_REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print(json.dumps(report,indent=2))
