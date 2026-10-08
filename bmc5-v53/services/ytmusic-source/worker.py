"""One short-lived process per public resolve/import job; stdout is sanitized JSON only."""
import json, math, os, shutil, subprocess, sys, wave
from pathlib import Path
from urllib.request import Request, build_opener, HTTPRedirectHandler, BaseHandler
from common import *

class Quiet:
    def debug(self,*args):pass
    def info(self,*args):pass
    def warning(self,*args):pass
    def error(self,*args):pass

class ProviderGuard(BaseHandler):
    handler_order=0
    def http_request(self,request):provider_url(request.full_url);return request
    https_request=http_request

def ydl_options(work):
    return {"quiet":True,"no_warnings":True,"logger":Quiet(),"socket_timeout":10,"retries":1,
            "fragment_retries":1,"extractor_retries":1,"file_access_retries":0,"concurrent_fragment_downloads":1,
            "cachedir":False,"cookiefile":None,"cookiesfrombrowser":None,"usenetrc":False,
            "enable_file_urls":False,"allowed_extractors":["youtube.*"],"plugin_dirs":[],
            "noplaylist":True,"js_runtimes":{"node":{"path":"/usr/bin/node"}},
            "remote_components":[],"format":"bestaudio[ext=m4a]/bestaudio",
            "max_filesize":MAX_INPUT,"outtmpl":str(work/"media.%(ext)s"),"overwrites":False,
            "nopart":False,"writethumbnail":False,"writeinfojson":False,"skip_unavailable_fragments":False,
            "http_headers":{"User-Agent":"Mozilla/5.0"}}

def download_guard(progress):
    if max(progress.get("downloaded_bytes") or 0,progress.get("total_bytes") or 0)>MAX_INPUT:raise Rejected("input_bytes_limit")

def audio_convert(source,dest,duration):
    if source.is_symlink() or not source.is_file() or not 0<source.stat().st_size<=MAX_INPUT:raise Rejected("input_bytes_limit")
    sample_rate=22050 if duration<=360 else 11025
    result=subprocess.run(["ffmpeg","-nostdin","-hide_banner","-loglevel","error","-threads","1",
       "-protocol_whitelist","file,pipe","-i",str(source),"-vn","-sn","-dn","-t",str(MAX_DURATION),
       "-ac","1","-ar",str(sample_rate),"-c:a","pcm_s16le","-fs",str(MAX_WAV),"-y",str(dest)],
       stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=90)
    if result.returncode or not dest.is_file() or not 44<=dest.stat().st_size<=MAX_WAV:raise Rejected("conversion_failed")
    with wave.open(str(dest)) as wav:
        length=wav.getnframes()/wav.getframerate()
        if wav.getnchannels()!=1 or wav.getsampwidth()!=2 or not 0<length<=MAX_DURATION+0.1 or abs(length-duration)>max(3,duration*.02):
            raise Rejected("incomplete_audio")
    return length

class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self,*args):raise Rejected("art_redirect_rejected")

def artwork(info,work):
    # Optional cover is requested only from YouTube's fixed thumbnail CDN, never an arbitrary URL.
    url=info.get("thumbnail","")
    try:
        u=urlsplit(url)
        if u.scheme!="https" or not (u.hostname or "").endswith(".ytimg.com") or u.port not in (None,443) or u.username: return None
        provider_url(url)
        raw=work/"cover.input"
        with build_opener(NoRedirect()).open(Request(url,headers={"User-Agent":"Mozilla/5.0"}),timeout=10) as response:
            if int(response.headers.get("Content-Length","0"))>2*1024*1024:return None
            data=response.read(2*1024*1024+1)
        if len(data)>2*1024*1024:return None
        raw.write_bytes(data);png=work/"art.png"
        result=subprocess.run(["ffmpeg","-nostdin","-hide_banner","-loglevel","error","-threads","1",
            "-protocol_whitelist","file,pipe","-i",str(raw),"-frames:v","1","-vf",
            "scale=64:64:force_original_aspect_ratio=increase,crop=64:64,format=rgb24","-y",str(png)],
            stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,timeout=15)
        if result.returncode or not png.is_file() or png.stat().st_size>MAX_ART:return None
        data=png.read_bytes()
        if data[:8]!=b"\x89PNG\r\n\x1a\n" or int.from_bytes(data[16:20],"big")!=64 or int.from_bytes(data[20:24],"big")!=64:return None
        return {"file":"art.png","sha256":digest(png),"bytes":len(data)}
    except Exception:return None

def run(request,work):
    import yt_dlp
    class Restricted(yt_dlp.YoutubeDL):
        def build_request_director(self,*args,**kwargs):
            director=super().build_request_director(*args,**kwargs)
            # The pinned urllib handler runs request processors for every redirect BEFORE transport.
            # Other optional handlers are removed so none can bypass this pre-request guard.
            handler=director.handlers.get("Urllib")
            if handler is None:raise Rejected("guarded_transport_unavailable")
            for other in director.handlers.values():
                if other is not handler:other.close()
            original=handler._create_instance
            def guarded_opener(*a,**kw):
                opener=original(*a,**kw);opener.add_handler(ProviderGuard());return opener
            handler._create_instance=guarded_opener
            director.handlers={"Urllib":handler}
            return director
        def urlopen(self,request):
            url=request if isinstance(request,str) else request.url
            provider_url(url)
            response=super().urlopen(request)
            if getattr(response,"url",None):provider_url(response.url)
            return response
    canonical,kind,_=normalize_url(request["url"])
    maximum=request["maxEntries"]
    options=ydl_options(work)
    if kind=="playlist":
        options.update(extract_flat="in_playlist",noplaylist=False,playlist_items=f"1:{maximum+1}",lazy_playlist=True)
        with Restricted(options) as ydl:listing=ydl.extract_info(canonical,download=False)
        if not listing or listing.get("_type")!="playlist":raise Rejected("public_playlist_unavailable")
        raw=list(listing.get("entries") or [])
        if not raw or len(raw)>maximum:raise Rejected("playlist_entries_limit")
        videos=[]
        for row in raw:
            if not row or not VIDEO.fullmatch(str(row.get("id",""))):raise Rejected("playlist_unavailable_entry")
            if row["id"] not in videos:videos.append(row["id"])
    else:videos=[normalize_url(canonical)[2]]
    entries=[]
    for index,video in enumerate(videos):
        folder=work/str(index);folder.mkdir()
        options=ydl_options(folder);options["progress_hooks"]=[download_guard]
        with Restricted(options) as ydl:
            info=ydl.extract_info("https://music.youtube.com/watch?v="+video,download=False)
            if not info:raise Rejected("public_track_unavailable")
            entry=metadata(info)
            if request["mode"]=="import":
                # Reject DRM/private/live and duration before fetching a single media byte.
                formats=info.get("formats") or []
                for row in formats:
                    if row.get("url"):provider_url(row["url"])
                ydl.process_ie_result(info,download=True)
                media=[p for p in folder.glob("media.*") if p.is_file() and p.suffix not in (".part",".ytdl")]
                if len(media)!=1:raise Rejected("download_incomplete")
                length=audio_convert(media[0],folder/"audio.wav",entry["duration"])
                entry.update(sha256=digest(folder/"audio.wav"),bytes=(folder/"audio.wav").stat().st_size,
                             duration=length,durationTicks=math.ceil(length*20),file=f"{index}/audio.wav")
            cover=artwork(info,folder)
            if cover:cover["file"]=f"{index}/art.png";entry["artwork"]=cover
            entries.append(entry)
        # Raw media never accumulate across a playlist; published PCM is the only retained content.
        for p in folder.glob("media.*"):p.unlink()
    return {"entries":entries,"kind":kind}

if __name__=="__main__":
    import resource
    resource.setrlimit(resource.RLIMIT_FSIZE,(MAX_INPUT,MAX_INPUT))
    work=Path(sys.argv[1]).resolve()
    try:result=run(json.loads((work/"request.json").read_text()),work)
    except Rejected as error:result={"error":str(error)}
    except Exception:result={"error":"public_youtube_unavailable"}
    print(json.dumps(result,ensure_ascii=False,separators=(",",":")),flush=True)
