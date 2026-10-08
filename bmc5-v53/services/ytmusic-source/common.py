"""Small strict contract shared by API, isolated yt-dlp worker and tests."""
import hashlib, ipaddress, json, math, os, re, socket, tempfile
from pathlib import Path
from urllib.parse import urlsplit, parse_qs

VIDEO = re.compile(r"^[A-Za-z0-9_-]{11}$")
PLAYLIST = re.compile(r"^[A-Za-z0-9_-]{10,100}$")
MAX_ENTRIES = 25
MAX_WAV = 16 * 1024 * 1024
MAX_INPUT = 64 * 1024 * 1024
MAX_DURATION = 600
MAX_ART = 16 * 1024
HOSTS = {"youtube.com", "www.youtube.com", "music.youtube.com", "youtu.be"}

class Rejected(ValueError): pass

def normalize_url(value):
    if not isinstance(value, str) or len(value) > 2048 or any(ord(c) <= 32 or ord(c) >= 127 for c in value):
        raise Rejected("invalid_url")
    try:
        u = urlsplit(value)
        if u.scheme != "https" or u.hostname not in HOSTS or u.username or u.password or u.port not in (None,443) or u.fragment:
            raise Rejected("youtube_https_only")
    except ValueError: raise Rejected("invalid_url") from None
    q = parse_qs(u.query, keep_blank_values=True)
    if any(len(q.get(k,[])) > 1 for k in ("v","list")): raise Rejected("ambiguous_url")
    video = None
    if u.hostname == "youtu.be":
        video = u.path.removeprefix("/")
        if "/" in video: raise Rejected("invalid_video")
    elif u.path == "/watch": video = q.get("v",[None])[0]
    elif u.path != "/playlist": raise Rejected("watch_or_public_playlist_only")
    if video is not None:
        if not VIDEO.fullmatch(video): raise Rejected("invalid_video")
        # Tracking, redirects, playlist context and arbitrary query parameters do not reach yt-dlp.
        return "https://music.youtube.com/watch?v="+video, "track", video
    playlist = q.get("list",[""])[0]
    if not PLAYLIST.fullmatch(playlist) or not playlist.startswith(("PL","OLAK5uy")):
        raise Rejected("public_playlist_only")
    return "https://www.youtube.com/playlist?list="+playlist, "playlist", playlist

def clean_text(value, limit):
    if not isinstance(value,str): return ""
    return " ".join("".join(c for c in value if c >= " " and c != "\x7f").split())[:limit]

def metadata(info):
    video = info.get("id", "")
    if not isinstance(video,str) or not VIDEO.fullmatch(video): raise Rejected("invalid_provider_id")
    duration = info.get("duration")
    if not isinstance(duration,(int,float)) or isinstance(duration,bool) or not math.isfinite(duration) or duration <= 0 or duration > MAX_DURATION:
        raise Rejected("duration_limit")
    if info.get("is_live") or info.get("live_status") in ("is_live","is_upcoming","post_live") or info.get("has_drm"):
        raise Rejected("live_or_drm_unavailable")
    if info.get("availability") in ("private","premium_only","subscriber_only","needs_auth"):
        raise Rejected("public_tracks_only")
    title = clean_text(info.get("track") or info.get("title"),160) or video
    artist = clean_text(info.get("artist") or info.get("creator") or info.get("uploader"),120)
    return {"videoId":video,"id":"youtube_"+video+".wav","title":title,"artist":artist,
            "album":clean_text(info.get("album"),120),"duration":round(float(duration),3),
            "durationTicks":math.ceil(float(duration)*20),"sourceUrl":"https://music.youtube.com/watch?v="+video}

def provider_url(value):
    """Only provider/CDN URLs emitted by YouTube extraction, never caller-supplied media URLs."""
    u=urlsplit(value)
    host=u.hostname or ""
    allowed=host in HOSTS|{"youtubei.googleapis.com","www.googleapis.com","jnn-pa.googleapis.com","consent.youtube.com"} or host.endswith((".googlevideo.com",".ytimg.com"))
    if u.scheme!="https" or not allowed or u.username or u.password or u.port not in (None,443): raise Rejected("provider_host_rejected")
    addresses=socket.getaddrinfo(host,443,type=socket.SOCK_STREAM)
    if not addresses or any(not ipaddress.ip_address(row[4][0]).is_global for row in addresses): raise Rejected("nonpublic_provider_address")
    return value

def atomic_json(path, value, mode=0o600):
    path=Path(path);path.parent.mkdir(parents=True,exist_ok=True)
    fd,tmp=tempfile.mkstemp(prefix=".pending-",dir=path.parent)
    try:
        with os.fdopen(fd,"w") as f: json.dump(value,f,ensure_ascii=False,separators=(",",":"));f.write("\n");f.flush();os.fsync(f.fileno())
        os.chmod(tmp,mode);os.replace(tmp,path)
        directory=os.open(path.parent,os.O_RDONLY|os.O_DIRECTORY)
        try:os.fsync(directory)
        finally:os.close(directory)
    finally:
        if os.path.exists(tmp):os.unlink(tmp)

def digest(path):
    with open(path,"rb") as f:return hashlib.file_digest(f,"sha256").hexdigest()
