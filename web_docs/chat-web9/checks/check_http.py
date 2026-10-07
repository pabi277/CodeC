"""Check every planned canonical route at a local /CodeC/ preview, never deploy."""
from pathlib import Path
from urllib.request import urlopen
import json,os
R=Path(__file__).resolve().parents[3];S=R/'website';D=R/'web_docs/chat-web9'
config=json.loads((D/'SITE_MAP.json').read_text());base=os.environ.get('BASE_URL','http://127.0.0.1:8000/CodeC/')
assert base.endswith('/')
results=[]
for r in config['routes']:
 path=r['path'];route=path[:-10] if path.endswith('index.html') else path
 with urlopen(base+route,timeout=10) as response:
  assert response.status==200 and response.headers.get_content_type()=='text/html'
  assert response.read()==(S/path).read_bytes(),route
  results.append({'route':route,'status':200,'exact_source_bytes':True})
for path in ['sitemap.xml','robots.txt','assets/images/social-card.png','assets/images/favicon.svg','assets/css/style.css','learn/licenses/CC-BY-4.0.txt','learn/licenses/MIT-EXAMPLES.txt','learn/licenses/SCOPE.txt']:
 with urlopen(base+path,timeout=10) as response:
  assert response.status==200 and response.read()==(S/path).read_bytes()
  results.append({'route':path,'status':200,'exact_source_bytes':True})
(D/'HTTP_REPORT.json').write_text(json.dumps({'base':base,'routes':results,'status':'PASS','scope':'local prefix only; not public deployment/indexing'},indent=2)+'\n')
print('PASS:',len(results),'canonical and asset routes served at local /CodeC/; exact source bytes')
