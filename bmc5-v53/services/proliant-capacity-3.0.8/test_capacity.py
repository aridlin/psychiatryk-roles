"""Exercise actual HTTP admission/ranges without downloads or game processes."""
from pathlib import Path
import concurrent.futures, hashlib, http.client, importlib.util, json, sys, tempfile, threading, time

ROOT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'service'))
import service

checks=0
def check(value):
    global checks
    assert value
    checks+=1

TOKEN='local-fixture-token-not-a-real-credential'
KEY='a'*64
BODY=b'RIFF'+bytes(range(256))*4
class Engine:
    max_tracks=0
    max_cache=0
    legacy_audio=None
    def __init__(self,path):self.state=path;self.lock=threading.RLock()
    def authorized(self,header):return header=='Bearer '+TOKEN
    def get(self,jid):return None

class Held(service.Handler):
    def get_authorized(self):
        if self.headers.get('X-Fixture-Hold')=='yes':
            with self.server.condition:
                self.server.entered+=1;self.server.condition.notify_all()
            self.server.release.wait(10)
        super().get_authorized()

def request(server,path,extra=None,auth=True):
    connection=http.client.HTTPConnection('127.0.0.1',server.server_port,timeout=15)
    headers={'Authorization':'Bearer '+TOKEN} if auth else {}
    headers.update(extra or {})
    try:
        connection.request('GET',path,headers=headers)
        response=connection.getresponse()
        return response.status,dict(response.getheaders()),response.read()
    finally:connection.close()

with tempfile.TemporaryDirectory() as temporary:
    path=Path(temporary);(path/'audio').mkdir();(path/'audio'/(KEY+'.wav')).write_bytes(BODY)
    server=service.Server(('127.0.0.1',0),Engine(path),max_connections=40,max_audio_streams=32,max_api_requests=2)
    server.RequestHandlerClass=Held;server.condition=threading.Condition();server.entered=0;server.release=threading.Event()
    thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
    with concurrent.futures.ThreadPoolExecutor(max_workers=34) as pool:
        futures=[pool.submit(request,server,'/v1/audio/'+KEY+'.wav',{'X-Fixture-Hold':'yes','Range':'bytes=16-47'}) for _ in range(32)]
        with server.condition:check(server.condition.wait_for(lambda:server.entered==32,timeout=10))
        status,headers,body=request(server,'/v1/health')
        check(status==200);check(json.loads(body)['httpCapacity']==server.capacities)
        status,headers,body=request(server,'/v1/audio/'+KEY+'.wav')
        check(status==503);check(headers['Retry-After']=='2');check(json.loads(body)['error']=='request_capacity')
        check(request(server,'/v1/audio/'+KEY+'.wav',auth=False)[0]==401)
        server.release.set()
        for future in futures:
            status,headers,body=future.result()
            check(status==206);check(body==BODY[16:48]);check(headers['Content-Range']=='bytes 16-47/1028')
    check(request(server,'/v1/audio/'+KEY+'.wav',{'Range':'bytes=1028-'})[0]==416)
    check(request(server,'/v1/audio/'+KEY+'.wav')[2]==BODY)
    # Exhaust API slots: audio stays available, API gets an explicit retry response.
    for _ in range(2):check(server.api_requests.acquire(blocking=False))
    check(request(server,'/v1/health')[0]==503)
    check(request(server,'/v1/audio/'+KEY+'.wav',{'Range':'bytes=0-3'})[2]==b'RIFF')
    for _ in range(2):server.api_requests.release()
    # Exhaust global admission: clients get valid HTTP rather than a dropped socket.
    for _ in range(40):check(server.connections.acquire(blocking=False))
    status,headers,body=request(server,'/v1/health')
    check(status==503);check(headers['Retry-After']=='2');check(json.loads(body)['error']=='connection_capacity')
    for _ in range(40):server.connections.release()
    check(request(server,'/v1/health')[0]==200)
    server.shutdown();server.server_close();thread.join(timeout=3)

defaults=service.Server(('127.0.0.1',0),Engine(Path('/unused')))
check(defaults.capacities=={'connections':160,'audioStreams':128,'apiRequests':16});defaults.server_close()
report={'success':True,'checks':checks,'simultaneousAuthenticatedListeners':32,'apiResponsiveDuringAudioSaturation':True,'explicit503RetryAfter':True,'authorizationPreserved':True,'audioRangeBytesIdentical':True,'productionMutation':False,'gameLaunched':False}
(ROOT/'capacity-proof.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report))
