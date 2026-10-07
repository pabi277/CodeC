"""Local preview under the planned /CodeC/ deployment prefix; never a publisher."""
from http.server import ThreadingHTTPServer,SimpleHTTPRequestHandler
from pathlib import Path
from urllib.parse import urlsplit
ROOT=Path(__file__).resolve().parents[3]/'website'
class Handler(SimpleHTTPRequestHandler):
    def __init__(self,*args,**kwargs):super().__init__(*args,directory=str(ROOT),**kwargs)
    def serve(self, head=False):
        route=urlsplit(self.path).path
        if route in ['/', '/CodeC']:
            self.send_response(302);self.send_header('Location','/CodeC/');self.end_headers();return
        if not route.startswith('/CodeC/'):
            self.send_error(404);return
        if head: super().do_HEAD()
        else: super().do_GET()
    def translate_path(self, path):
        return super().translate_path(path[len('/CodeC'):] if path.startswith('/CodeC/') else path)
    def do_GET(self): self.serve()
    def do_HEAD(self): self.serve(head=True)
    def end_headers(self):
        self.send_header('X-Robots-Tag','noindex') # Preview only; not site metadata.
        super().end_headers()
ThreadingHTTPServer(('0.0.0.0',8000),Handler).serve_forever()
