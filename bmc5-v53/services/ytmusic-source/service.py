"""Bounded bearer-protected job API. Existing Minecraft library is never edited."""
import argparse, concurrent.futures, hmac, json, math, os, re, shutil, signal, socket, subprocess, sys, threading, time, uuid, wave
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit
from common import *

MAX_CACHE=2*1024*1024*1024
MAX_TRACKS=256
JOB_TTL=86400
MAX_JOB_OUTPUT=512*1024
HASH=re.compile(r"^[a-f0-9]{64}$")

class Busy(Rejected):pass

class Engine:
    def __init__(self,state,token,public_base,worker=None,legacy_audio=None,max_cache=MAX_CACHE,max_tracks=MAX_TRACKS):
        self.state=Path(state);self.token=token;self.public_base=public_base.rstrip("/")
        self.legacy_audio=Path(legacy_audio) if legacy_audio else None;self.max_cache=max_cache;self.max_tracks=max_tracks
        if not isinstance(token,str) or not 20<=len(token)<=512 or "\r" in token or "\n" in token:raise Rejected("invalid_server_auth")
        self.lock=threading.RLock();self.pool=concurrent.futures.ThreadPoolExecutor(max_workers=2,thread_name_prefix="YTMusic")
        self.slots=threading.BoundedSemaphore(10);self.jobs={};self.worker=worker or self.run_worker
        for name in ("jobs","work","audio","art","records","catalog","transactions"):
            path=self.state/name;path.mkdir(parents=True,exist_ok=True);os.chmod(path,0o755 if name in ("audio","art") else 0o700)
        for p in (self.state/"jobs").glob("*.json"):
            if p.stat().st_size>MAX_JOB_OUTPUT:continue
            try:
                row=json.loads(p.read_text());jid=row["jobId"]
                if str(uuid.UUID(jid))!=jid:continue
                if row["status"] in ("queued","running"):row.update(status="failed",error="service_restarted");atomic_json(p,row)
                if time.time()-row["created"]<=JOB_TTL:self.jobs[jid]=row
            except Exception:continue
        for path in (self.state/"transactions").glob("*.json"):
            transaction=json.loads(path.read_text());jid=transaction["jobId"]
            if str(uuid.UUID(jid))!=jid or path.stem!=jid:raise Rejected("invalid_transaction")
            committed=self.jobs.get(jid,{}).get("status")=="complete"
            receipt=self.state/"jobs"/(jid+".json")
            if not committed and receipt.is_file() and receipt.stat().st_size<=MAX_JOB_OUTPUT:
                recorded=json.loads(receipt.read_text());committed=recorded.get("jobId")==jid and recorded.get("status")=="complete"
            if not committed:self.rollback(transaction)
            path.unlink()
        # Prior interrupted work contains no reusable complete publication; remove only own UUID work directories.
        for p in (self.state/"work").iterdir():
            if p.is_dir() and not p.is_symlink():
                try:uuid.UUID(p.name)
                except ValueError:continue
                shutil.rmtree(p)

    def authorized(self,header):return isinstance(header,str) and hmac.compare_digest(header,"Bearer "+self.token)

    def submit(self,body):
        if not isinstance(body,dict) or set(body)-{"url","mode","maxEntries"}:raise Rejected("invalid_request")
        canonical,kind,_=normalize_url(body.get("url"));mode=body.get("mode","resolve");count=body.get("maxEntries",25)
        if mode not in ("resolve","import") or type(count)!=int or not 1<=count<=MAX_ENTRIES:raise Rejected("invalid_request")
        if not self.slots.acquire(blocking=False):raise Busy("job_queue_full")
        try:
            with self.lock:
                self.expire_jobs()
                if len(self.jobs)>=128:raise Busy("retained_jobs_limit")
                jid=str(uuid.uuid4());row={"jobId":jid,"status":"queued","created":time.time(),"mode":mode,"kind":kind}
                self.jobs[jid]=row;self.save(row)
            self.pool.submit(self.execute,jid,{"url":canonical,"mode":mode,"maxEntries":count})
            return {"jobId":jid,"status":"queued","statusUrl":self.public_base+"/v1/jobs/"+jid}
        except Exception:self.slots.release();raise

    def save(self,row):atomic_json(self.state/"jobs"/(row["jobId"]+".json"),row)
    def expire_jobs(self):
        for jid,row in list(self.jobs.items()):
            if row["status"] not in ("queued","running") and time.time()-row["created"]>JOB_TTL:
                self.jobs.pop(jid);(self.state/"jobs"/(jid+".json")).unlink(missing_ok=True)

    def get(self,jid):
        with self.lock:
            row=self.jobs.get(jid)
            return json.loads(json.dumps(row)) if row else None

    def rollback(self,transaction):
        for name,value in transaction["prior"].items():
            if not re.fullmatch(r"catalog/[A-Za-z0-9_-]{11}\.json",name):raise Rejected("invalid_transaction")
            path=self.state/name
            if value is None:path.unlink(missing_ok=True)
            else:atomic_json(path,value)
        (self.state/"records"/(transaction["jobId"]+".json")).unlink(missing_ok=True)
        for name in transaction["newFiles"]:
            if not re.fullmatch(r"(?:audio/[a-f0-9]{64}\.wav|art/[a-f0-9]{64}\.png)",name):raise Rejected("invalid_transaction")
            (self.state/name).unlink(missing_ok=True)

    def run_worker(self,request,work):
        atomic_json(work/"request.json",request)
        env={"PATH":"/usr/bin:/bin","HOME":str(work/"private-home"),"PYTHONPATH":str(Path(__file__).parent/"vendor"),"LANG":"C.UTF-8"}
        (work/"private-home").mkdir()
        output=work/"worker-result.json"
        with output.open("wb") as stream:
            process=subprocess.Popen([sys.executable,str(Path(__file__).with_name("worker.py")),str(work)],
               stdin=subprocess.DEVNULL,stdout=stream,stderr=subprocess.DEVNULL,env=env,start_new_session=True)
            try:process.wait(timeout=900)
            except subprocess.TimeoutExpired:
                os.killpg(process.pid,signal.SIGTERM)
                try:process.wait(timeout=3)
                except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL);process.wait()
                raise Rejected("job_timeout")
        if process.returncode or not output.is_file() or output.stat().st_size>MAX_JOB_OUTPUT:raise Rejected("worker_failed")
        result=json.loads(output.read_text())
        if "error" in result:raise Rejected(result["error"] if re.fullmatch("[a-z_]{1,64}",str(result["error"])) else "worker_failed")
        return result

    def validate_file(self,work,item,kind):
        folder="audio" if kind=="wav" else "art";maximum=MAX_WAV if kind=="wav" else MAX_ART
        source=work/item["file"]
        if source.is_symlink() or not source.is_file() or not source.resolve().is_relative_to(work.resolve()) or not HASH.fullmatch(item["sha256"]):raise Rejected("invalid_worker_file")
        if not 1<=source.stat().st_size<=maximum or source.stat().st_size!=item["bytes"] or digest(source)!=item["sha256"]:raise Rejected("invalid_worker_file")
        if kind=="wav":
            with wave.open(str(source)) as wav:
                duration=wav.getnframes()/wav.getframerate()
                if wav.getnchannels()!=1 or wav.getsampwidth()!=2 or not 0<duration<=MAX_DURATION+.1 or item["durationTicks"]!=math.ceil(duration*20):raise Rejected("invalid_worker_audio")
        if kind=="png":
            data=source.read_bytes()
            if data[:8]!=b"\x89PNG\r\n\x1a\n" or int.from_bytes(data[16:20],"big")!=64 or int.from_bytes(data[20:24],"big")!=64:raise Rejected("invalid_artwork")
        if kind=="wav" and self.legacy_audio:
            legacy=self.legacy_audio/(item["sha256"]+".wav")
            if legacy.is_file() and not legacy.is_symlink():
                if legacy.stat().st_size!=item["bytes"] or digest(legacy)!=item["sha256"]:raise Rejected("legacy_integrity_error")
                # Read-only de-duplication: never rewrite, chmod, touch or remove original library audio.
                return source,legacy,True
        target=self.state/folder/(item["sha256"]+"."+kind)
        if target.exists():
            if target.is_symlink() or target.stat().st_size!=item["bytes"] or digest(target)!=item["sha256"]:raise Rejected("cache_integrity_error")
            return source,target,True
        return source,target,False

    def publish_file(self,validated):
        source,target,existing=validated
        if not existing:
            temporary=target.with_name(".pending-"+uuid.uuid4().hex)
            try:
                shutil.copyfile(source,temporary);os.chmod(temporary,0o644)
                with temporary.open("rb") as file:os.fsync(file.fileno())
                os.replace(temporary,target)
                directory=os.open(target.parent,os.O_RDONLY|os.O_DIRECTORY)
                try:os.fsync(directory)
                finally:os.close(directory)
            finally:temporary.unlink(missing_ok=True)
        return target

    def capacity(self,entries,mode):
        files=list((self.state/"audio").glob("*.wav"))+list((self.state/"art").glob("*.png"))
        size=sum(p.stat().st_size for p in files);extra={}
        published={p.stem for p in (self.state/"catalog").glob("*.json")}
        if mode=="import":published.update(e["videoId"] for e in entries)
        if len(published)>self.max_tracks:raise Rejected("published_track_capacity")
        for row in entries:
            assets=[]
            if mode=="import":assets.append(("audio",row,"wav"))
            if "artwork" in row:assets.append(("art",row["artwork"],"png"))
            for folder,item,extension in assets:
                if not HASH.fullmatch(str(item.get("sha256",""))) or type(item.get("bytes"))!=int:raise Rejected("invalid_worker_file")
                target=self.state/folder/(item["sha256"]+"."+extension)
                legacy=self.legacy_audio/(item["sha256"]+".wav") if extension=="wav" and self.legacy_audio else None
                if not target.exists() and not (legacy and legacy.is_file()):extra[(folder,item["sha256"])]=item["bytes"]
        if size+sum(extra.values())>self.max_cache:raise Rejected("published_bytes_capacity")

    def cleanup(self):
        # Published audio is permanent until an explicit future administrative deletion request.
        # Only transient, expired resolve artwork may be removed; catalog covers are pinned.
        keep=set()
        for path in (self.state/"records").glob("*.json"):
            if time.time()-path.stat().st_mtime>JOB_TTL:path.unlink();continue
            try:
                for entry in json.loads(path.read_text()):
                    if "artwork" in entry:keep.add(entry["artwork"]["sha256"])
            except Exception:continue
        for path in (self.state/"catalog").glob("*.json"):
            try:
                entry=json.loads(path.read_text())
                if "artwork" in entry:keep.add(entry["artwork"]["sha256"])
            except Exception:continue
        for path in (self.state/"art").glob("*.png"):
            if path.stem not in keep:path.unlink()

    def execute(self,jid,request):
        work=self.state/"work"/jid
        try:
            work.mkdir(mode=0o700)
            with self.lock:self.jobs[jid].update(status="running");self.save(self.jobs[jid])
            result=self.worker(request,work);entries=result.get("entries")
            if not isinstance(entries,list) or not 1<=len(entries)<=request["maxEntries"]:raise Rejected("invalid_worker_result")
            with self.lock:
                self.cleanup();self.capacity(entries,request["mode"])
                public=[];assets=[]
                for row in entries:
                    entry={k:row[k] for k in ("videoId","id","title","artist","album","duration","durationTicks","sourceUrl")}
                    # Validate untrusted worker output again before allowing content into a catalog.
                    expected=metadata({"id":entry["videoId"],"duration":entry["duration"],"title":entry["title"],"artist":entry["artist"],"album":entry["album"]})
                    if entry["id"]!=expected["id"] or entry["durationTicks"]!=expected["durationTicks"]:raise Rejected("invalid_worker_metadata")
                    for key in ("title","artist","album","sourceUrl"):entry[key]=expected[key]
                    if request["mode"]=="import":
                        assets.append(self.validate_file(work,row,"wav"));entry.update(sha256=row["sha256"],bytes=row["bytes"],audioUrl="https://prol.aridlin.pl/scooter-audio/"+row["sha256"]+".wav")
                    if "artwork" in row:
                        art=row["artwork"];assets.append(self.validate_file(work,art,"png"));entry["artwork"]={"sha256":art["sha256"],"bytes":art["bytes"],"url":self.public_base+"/v1/art/"+art["sha256"]+".png"}
                    public.append(entry)
                # Every row/file is verified before the first public move. Roll back only this
                # job's newly created assets and changed metadata while retaining the lock.
                new_files={asset[1] for asset in assets if not asset[2] and not asset[1].exists()};prior={};record=self.state/"records"/(jid+".json")
                if request["mode"]=="import":
                    for entry in public:
                        path=self.state/"catalog"/(entry["videoId"]+".json")
                        if path not in prior:prior[path]=json.loads(path.read_text()) if path.exists() else None
                transaction={"jobId":jid,"newFiles":[p.relative_to(self.state).as_posix() for p in new_files],"prior":{p.relative_to(self.state).as_posix():v for p,v in prior.items()}}
                journal=self.state/"transactions"/(jid+".json");atomic_json(journal,transaction)
                try:
                    for asset in assets:
                        self.publish_file(asset)
                    atomic_json(record,public)
                    if request["mode"]=="import":
                        for entry in public:
                            path=self.state/"catalog"/(entry["videoId"]+".json")
                            atomic_json(path,entry)
                    self.jobs[jid].update(status="complete",entries=public,completed=time.time());self.save(self.jobs[jid])
                    journal.unlink()
                except Exception:
                    self.rollback(transaction);journal.unlink(missing_ok=True)
                    self.jobs[jid].pop("entries",None)
                    raise
        except Exception as error:
            code=str(error) if isinstance(error,Rejected) and re.fullmatch("[a-z_]{1,64}",str(error)) else "job_failed"
            message={"published_track_capacity":"The permanent imported library has reached its track limit. Ask an administrator to increase capacity; existing tracks remain available.","published_bytes_capacity":"The permanent imported library has reached its storage limit. Ask an administrator to increase capacity; existing tracks remain available.","playlist_entries_limit":"Choose a public playlist containing at most 25 tracks.","public_youtube_unavailable":"YouTube did not provide this public track without login. No cookies or private account are used."}.get(code,"This public music job could not be completed within its limits.")
            with self.lock:self.jobs[jid].update(status="failed",error=code,errorMessage=message,completed=time.time());self.save(self.jobs[jid])
        finally:
            if work.is_dir() and not work.is_symlink():shutil.rmtree(work)
            self.slots.release()

class Server(ThreadingHTTPServer):
    daemon_threads=True
    def __init__(self,address,engine):self.engine=engine;self.connections=threading.BoundedSemaphore(16);super().__init__(address,Handler)
    def verify_request(self,request,address):request.settimeout(10);return True
    def process_request(self,request,address):
        if not self.connections.acquire(blocking=False):request.close();return
        try:super().process_request(request,address)
        except Exception:self.connections.release();raise
    def process_request_thread(self,request,address):
        try:super().process_request_thread(request,address)
        finally:self.connections.release()

class Handler(BaseHTTPRequestHandler):
    protocol_version="HTTP/1.0"
    def log_message(self,*args):pass
    def send_json(self,status,obj):
        data=json.dumps(obj,separators=(",",":"),ensure_ascii=False).encode();self.send_response(status);self.send_header("Content-Type","application/json");self.send_header("Cache-Control","no-store");self.send_header("Content-Length",str(len(data)));self.end_headers();self.wfile.write(data)
    def auth(self):
        if self.server.engine.authorized(self.headers.get("Authorization")):return True
        self.send_json(401,{"error":"authorization_required"});return False
    def do_POST(self):
        if not self.auth():return
        if self.path!="/v1/jobs":self.send_json(404,{"error":"not_found"});return
        try:
            if self.headers.get_content_type()!="application/json" or self.headers.get("Transfer-Encoding"):raise Rejected("json_required")
            size=int(self.headers.get("Content-Length","0"))
            if not 0<size<=4096:raise Rejected("request_bytes_limit")
            self.send_json(202,self.server.engine.submit(json.loads(self.rfile.read(size))))
        except Busy as error:self.send_json(429,{"error":str(error)})
        except (Rejected,ValueError,UnicodeError):self.send_json(400,{"error":"invalid_request"})
    def do_GET(self):
        if not self.auth():return
        if self.path=="/v1/health":self.send_json(200,{"ok":True,"maxEntries":MAX_ENTRIES,"maxTrackSeconds":MAX_DURATION,"maxWavBytes":MAX_WAV,"workers":2,"waitingJobs":8,"publishedAudioEvicted":False,"maxPublishedTracks":self.server.engine.max_tracks,"maxCacheBytes":self.server.engine.max_cache});return
        if self.path.startswith("/v1/jobs/"):
            jid=self.path.removeprefix("/v1/jobs/")
            try:
                if str(uuid.UUID(jid))!=jid:raise ValueError()
            except ValueError:self.send_json(404,{"error":"not_found"});return
            row=self.server.engine.get(jid);self.send_json(200 if row else 404,row or {"error":"not_found"});return
        match=re.fullmatch(r"/v1/(art|audio)/([a-f0-9]{64})\.(png|wav)",self.path)
        if not match or (match[1],match[3]) not in (("art","png"),("audio","wav")):self.send_json(404,{"error":"not_found"});return
        path=self.server.engine.state/match[1]/(match[2]+"."+match[3])
        with self.server.engine.lock:
            if not path.exists() and match[1]=="audio" and self.server.engine.legacy_audio:path=self.server.engine.legacy_audio/(match[2]+".wav")
            if path.is_symlink() or not path.is_file():self.send_json(404,{"error":"not_found"});return
            file=path.open("rb");size=path.stat().st_size
        try:
            begin,end=0,size-1;status=200
            value=self.headers.get("Range")
            if value:
                interval=re.fullmatch(r"bytes=([0-9]+)-([0-9]*)",value)
                if not interval or int(interval[1])>=size or interval[2] and int(interval[2])<int(interval[1]):self.send_json(416,{"error":"range_not_satisfiable"});return
                begin=int(interval[1]);end=min(size-1,int(interval[2]) if interval[2] else size-1);status=206
            self.send_response(status);self.send_header("Content-Type","image/png" if match[3]=="png" else "audio/wav");self.send_header("Content-Length",str(end-begin+1));self.send_header("Accept-Ranges","bytes");self.send_header("Cache-Control","private, max-age=3600");self.send_header("X-Content-Type-Options","nosniff")
            if status==206:self.send_header("Content-Range",f"bytes {begin}-{end}/{size}")
            self.end_headers();file.seek(begin);left=end-begin+1
            while left:
                data=file.read(min(left,64*1024))
                if not data:break
                self.wfile.write(data);left-=len(data)
        finally:file.close()

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("--state",required=True);parser.add_argument("--credential",required=True);parser.add_argument("--port",type=int,default=18783);parser.add_argument("--legacy-audio",default="/opt/goplanska-scooter-music/audio");parser.add_argument("--max-cache-bytes",type=int,default=MAX_CACHE);parser.add_argument("--max-published-tracks",type=int,default=MAX_TRACKS);args=parser.parse_args()
    token=json.loads(Path(args.credential).read_text())["bearerToken"]
    engine=Engine(args.state,token,"https://prol.aridlin.pl/scooter-music-api",legacy_audio=args.legacy_audio,max_cache=args.max_cache_bytes,max_tracks=args.max_published_tracks)
    Server(("127.0.0.1",args.port),engine).serve_forever()
