"""Host-only snippet checks. Never substitutes for CodeC/TCC/device transcripts."""
from pathlib import Path
from html.parser import HTMLParser
import tempfile, subprocess, os, json, re, shutil
R=Path(__file__).resolve().parents[3];S=R/'website';results=[]
class Blocks(HTMLParser):
    def __init__(self,text):
        super().__init__(convert_charrefs=True);self.current=None;self.in_code=False;self.blocks=[];self.feed(text)
    def handle_starttag(self,t,a):
        if t=='pre':self.current=[dict(a),'']
        if t=='code' and self.current is not None:self.in_code=True
    def handle_data(self,s):
        if self.current is not None and self.in_code:self.current[1]+=s
    def handle_endtag(self,t):
        if t=='code':self.in_code=False
        if t=='pre' and self.current is not None:self.blocks.append(self.current);self.current=None
fixtures={
('ch-02.html','hello.c'):('', 'Hello from CodeC!\n'),
('ch-03.html','ask.c'):('7\n','Number?\nYou typed 7\n'),
('ch-04.html','probe.c'):('', 'compiler ready\n'),
('ch-08.html','shape.c'):('', 'Start small\n'),
('ch-08.html','types.c'):('', '3 2.50 A\n'),
('ch-08.html','input.c'):('Ada 2000\n','Name and birth year?\nAda will turn 30 in 2030\n'),
('ch-08.html','operators.c'):('', '2 2.5 1\n1\n'),
('ch-08.html','parity.c'):('', 'odd\n'),
('ch-08.html','countdown.c'):('', '3\n2\n1\n'),
('ch-08.html','sum.c'):('', '15\n'),
('ch-08.html','values.c'):('', 'Inside: 2 1\nOutside: 1 2\n'),
('ch-08.html','reverse.c'):('', 'cedoc\n'),
('ch-08.html','pointers.c'):('', 'Before: 1 2; *p=1\nAfter: 2 1; *p=2\n'),
('ch-08.html','converter.c'):('1\n0\n2\n212\n0\n','1 C->F, 2 F->C, 0 quit\nTemperature?\n32.00\n1 C->F, 2 F->C, 0 quit\nTemperature?\n100.00\n1 C->F, 2 F->C, 0 quit\n'),
('ch-15.html','main.c'):('', 'Hello from make\n'),
('ch-15.html','warning.c'):('', 'Read the diagnostic\n'),
('ch-16.html','calc.c'):('+\n2 3\n*\n4 5\n/\n4 0\nq\n','Operation (+ - * /), q quit:\nTwo numbers:\nResult: 5.00\nOperation (+ - * /), q quit:\nTwo numbers:\nResult: 20.00\nOperation (+ - * /), q quit:\nTwo numbers:\nCannot divide by zero\nOperation (+ - * /), q quit:\n'),
('ch-18.html','hello.c'):('', 'Hello, CodeC!\nI review before I run.\n')}
def run(args,root,stdin=None,env=None):return subprocess.run(args,cwd=root,input=stdin,text=True,capture_output=True,timeout=15,env=env)
with tempfile.TemporaryDirectory(prefix='codec-course-host-') as temp:
    for page in sorted(S.glob('ch-*.html')):
        root=Path(temp)/page.stem;root.mkdir();blocks=Blocks(page.read_text()).blocks
        named=[(a,c) for a,c in blocks if a.get('data-filename')]
        for a,c in named:
            filename=a['data-filename'];assert '/' not in filename and filename not in ['..','.'];(root/filename).write_text(c+'\n')
        for a,c in named:
            f=a['data-filename'];lang=a.get('data-language');result={'page':page.name,'file':f,'language':lang}
            if lang=='c':
                args=['cc','-std=c90','-pedantic','-Wall','-Wextra','-Werror',f,'-o','example']
                compilation=run(args,root)
                if f=='warning.c':
                    assert compilation.returncode!=0 and 'unused' in compilation.stderr,compilation.stderr
                    args.remove('-Werror');compilation=run(args,root);result['intentional_unused_warning']='observed, not suppressed'
                assert compilation.returncode==0,(page.name,f,compilation.stderr)
                if f=='card.c':
                    output=run(['./example'],root);assert re.fullmatch(r'Name: Ada\nLessons: 8\nString bytes: 4\nAddress: 0x[0-9a-fA-F]+\n',output.stdout),output.stdout
                else:
                    stdin,expected=fixtures[(page.name,f)];output=run(['./example'],root,stdin);assert output.returncode==0 and output.stdout==expected,(page.name,f,output.stdout,output.stderr)
                result['check']='ANSI C90 host compile + expected output PASS; not TCC/Android'
            elif lang=='bash':
                result_run=run(['bash','-n',f],root);assert result_run.returncode==0,result_run.stderr
                result['check']='bash syntax PASS; device APIs not executed'
            elif lang=='python':
                result_run=run(['python3','-m','py_compile',f],root);assert result_run.returncode==0,result_run.stderr
                if f=='count.py':
                    output=run(['python3',f,'sample.txt'],root);assert output.stdout=='lines: 2\nwords: 3\n',output.stdout
                    assert run(['python3',f],root).returncode==2
                    assert run(['python3',f,'absent.txt'],root).returncode==1
                elif f=='hello.py':assert run(['python3',f],root).stdout=='1 Hello, CodeC\n2 Hello, CodeC\n3 Hello, CodeC\n'
                elif f=='summarize.py':
                    (root/'sample.log').write_text('red blue red\ngreen blue red\n')
                    assert run(['python3',f,'sample.log'],root).stdout=='lines: 2\nwords: 6\nchars: 28\nred 3\nblue 2\ngreen 1\n'
                    assert run(['python3',f],root).returncode==2
                    assert run(['python3',f,'absent.txt'],root).returncode==1
                result['check']='Python syntax + utility/greeting output/error cases PASS'
            elif lang=='json':json.loads(c);result['check']='JSON parse PASS'
            elif lang=='javascript':
                # ES module example is checked as .mjs; no browser/remote execution here.
                tmp=root/'syntax.mjs';tmp.write_text(c);output=run(['node','--check',str(tmp)],root);assert output.returncode==0,output.stderr
                result['check']='JavaScript syntax PASS'
            elif lang=='make':
                output=run(['make','run'],root);assert output.returncode==0 and 'Hello from make' in output.stdout,(output.stdout,output.stderr)
                assert run(['make','clean'],root).returncode==0 and not (root/'hello').exists()
                assert (root/'main.c').exists() and (root/'greeting.h').exists();result['check']='make run/clean PASS; source preserved'
            else:result['check']='fixture / header parsed; used by related checks'
            results.append(result)
    # P5 is exercised with explicit LOCAL TEST STUBS, never real device/package operations.
    root=Path(temp)/'ch-16';stubs=root/'test-stubs';stubs.mkdir();log=root/'stub-actions'
    scripts={
      'codec-battery':'#!/bin/sh\n[ "${BATTERY_FAIL:-0}" = 0 ] || exit 1\nprintf \'%s\\n\' "$BATTERY_JSON"\n',
      'codec-tts':'#!/bin/sh\nprintf \'speech:%s\\n\' "$*" >> "$ACTION_LOG"\n[ "${TTS_FAIL:-0}" = 0 ] || exit 1\nprintf \'OK\\n\'\n',
      'pkg':'#!/bin/sh\nprintf \'pkg:%s\\n\' "$*" >> "$ACTION_LOG"\n[ "${PKG_FAIL:-0}" = 0 ]\n'}
    for f,c in scripts.items():p=stubs/f;p.write_text(c);p.chmod(0o700)
    env=dict(os.environ,PATH=str(stubs)+os.pathsep+os.environ['PATH'],BATTERY_JSON='{"percentage":73}',ACTION_LOG=str(log))
    for name,args,stdin,extra,rc in [('normal',[],None,{},0),('null',[],None,{'BATTERY_JSON':'{"percentage":null}'},1),('range',[],None,{'BATTERY_JSON':'{"percentage":101}'},1),('battery-fail',[],None,{'BATTERY_FAIL':'1'},1),('speech-fail',[],None,{'TTS_FAIL':'1'},1),('decline',['--upgrade'],'NO\n',{},1),('upgrade',['--upgrade'],'YES\n',{},0),('upgrade-fail',['--upgrade'],'YES\n',{'PKG_FAIL':'1'},1)]:
        log.unlink(missing_ok=True);output=run(['bash','morning.sh']+args,root,stdin,dict(env,**extra));assert output.returncode==rc,(name,output.stdout,output.stderr)
        actions=log.read_text() if log.exists() else ''
        if name=='normal':assert 'pkg:' not in actions and 'speech:Good morning, battery at 73 percent' in actions
        if name in ['null','range','battery-fail','decline']:assert not actions,(name,actions)
        if name=='upgrade':assert 'pkg:update' in actions and 'pkg:upgrade -y' in actions
        if name=='upgrade-fail':assert 'speech:' not in actions
        results.append({'page':'ch-16.html','file':'morning.sh','case':name,'check':'LOCAL STUB CONTROL-FLOW PASS; NOT Android/device acceptance'})
report={'status':'PASS','checks':results,'device_acceptance':'PENDING: ch08 input/loops/pointers/converter and P1/P5 owner transcripts'}
(R/'web_docs/chat-web6/SNIPPET_REPORT.json').write_text(json.dumps(report,indent=2)+'\n');print('PASS:',len(results),'host/fixture checks; device gates remain PENDING')
