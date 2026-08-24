import unittest, urllib.request, json, os

class FetchLogTest(unittest.TestCase):
    def test_fetch_log(self):
        url = "https://api.github.com/repos/pabi277/CodeC/actions/runs/32725989643/jobs"
        req = urllib.request.Request(url)
        with urllib.request.urlopen(req) as response:
            jobs = json.loads(response.read().decode())['jobs']
        
        for job in jobs:
            if job['name'] == 'Build CodeC packages (aarch64)':
                log_url = f"https://api.github.com/repos/pabi277/CodeC/actions/jobs/{job['id']}/logs"
                req = urllib.request.Request(log_url)
                try:
                    with urllib.request.urlopen(req) as log_res:
                        log = log_res.read().decode('utf-8', errors='ignore')
                        lines = log.split('\n')
                        print("==== AARCH64 BUILD LOG TAIL ====")
                        for line in lines[-200:]:
                            print(line)
                        print("================================")
                except Exception as e:
                    print("Error fetching log:", e)
