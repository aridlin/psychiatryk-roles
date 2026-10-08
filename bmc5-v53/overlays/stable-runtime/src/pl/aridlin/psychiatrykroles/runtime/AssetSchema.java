package pl.aridlin.psychiatrykroles.runtime;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;

/** Bounded version-one PNG preload protocol. IDs never become filesystem paths on the client. */
public final class AssetSchema {
    private AssetSchema() {}
    public static final int VERSION=1, MAX_ASSETS=32, MAX_FILE=128*1024, MAX_JSON_FILE=64*1024, MAX_TOTAL=2*1024*1024;
    public static final int MAX_SIDE=512, MAX_PIXELS=4*1024*1024, CHUNK=16*1024;
    public static final int MAX_MANIFEST=12*1024, MAX_REQUEST=4096;
    // Server resends a fresh manifest after 30 s; leave time for it to reach the client.
    public static final long TIMEOUT_NANOS=40_000_000_000L;
    public static final long SERVER_TIMEOUT_NANOS=30_000_000_000L;
    public static final Gson JSON=new Gson();

    public record Entry(String id,String kind,String sha256,int bytes,int width,int height) {}
    public record Manifest(int schema,String revision,List<Entry> assets) {}
    public record Request(int schema,String revision,List<String> missing) {}
    public record Ready(int schema,String revision) {}

    public static void require(boolean ok,String why){if(!ok)throw new IllegalArgumentException(why);}
    public static void id(String value){
        require(value!=null&&value.length()<=80&&value.matches("[a-z0-9_-]+(?:/[a-z0-9_-]+){0,3}"),"Invalid asset ID");
    }
    public static void hash(String value){require(value!=null&&value.matches("[a-f0-9]{64}"),"Invalid SHA-256");}
    public static String sha256(byte[] bytes){
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    public static int[] pngDimensions(byte[] bytes){
        require(bytes!=null&&bytes.length>=33&&bytes.length<=MAX_FILE,"PNG byte limit");
        byte[] signature={(byte)137,80,78,71,13,10,26,10};
        for(int i=0;i<signature.length;i++)require(bytes[i]==signature[i],"PNG signature");
        var b=ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        require(b.getInt(8)==13&&b.getInt(12)==0x49484452,"PNG IHDR");
        int width=b.getInt(16),height=b.getInt(20);
        require(width>0&&width<=MAX_SIDE&&height>0&&height<=MAX_SIDE,"PNG dimensions");
        return new int[]{width,height};
    }
    public static Manifest manifest(List<Entry> entries){
        var sorted=new ArrayList<>(entries);sorted.sort(Comparator.comparing(Entry::id));
        checkEntries(sorted);
        return new Manifest(VERSION,sha256(JSON.toJson(sorted).getBytes(StandardCharsets.UTF_8)),List.copyOf(sorted));
    }
    private static void checkEntries(List<Entry> entries){
        require(entries!=null&&entries.size()<=MAX_ASSETS,"Asset count");
        var names=new HashSet<String>();long bytes=0,pixels=0;
        for(var e:entries){require(e!=null,"Null asset");id(e.id());hash(e.sha256());
            require("png".equals(e.kind())||"json".equals(e.kind()),"Asset kind");
            require(names.add(e.id()),"Duplicate asset");
            require(e.bytes()>0&&e.bytes()<= ("json".equals(e.kind())?MAX_JSON_FILE:MAX_FILE),"Asset byte limit");
            if("png".equals(e.kind()))require(e.width()>0&&e.width()<=MAX_SIDE&&e.height()>0&&e.height()<=MAX_SIDE,"Asset dimensions");
            else require(e.width()==0&&e.height()==0,"JSON has no texture dimensions");
            bytes+=e.bytes();pixels+=(long)e.width()*e.height();
            require(bytes<=MAX_TOTAL&&pixels<=MAX_PIXELS,"Manifest budget");
        }
    }
    public static Manifest parseManifest(String raw){
        var m=JSON.fromJson(object(raw,MAX_MANIFEST,8),Manifest.class);
        require(m!=null&&m.schema()==VERSION,"Manifest schema");checkEntries(m.assets());hash(m.revision());
        require(m.revision().equals(manifest(m.assets()).revision()),"Manifest revision");
        return m;
    }
    public static Request parseRequest(String raw){
        var r=JSON.fromJson(object(raw,MAX_REQUEST,8),Request.class);
        require(r!=null&&r.schema()==VERSION&&r.missing()!=null&&r.missing().size()<=MAX_ASSETS,"Request schema");hash(r.revision());
        var names=new HashSet<String>();for(String name:r.missing()){id(name);require(names.add(name),"Duplicate request");}
        return r;
    }
    public static Ready parseReady(String raw){
        var r=JSON.fromJson(object(raw,256,8),Ready.class);
        require(r!=null&&r.schema()==VERSION,"Ready schema");hash(r.revision());return r;
    }
    /** Verify a generic JSON data asset before any scene or animation engine can use it. */
    public static String jsonText(byte[] bytes){
        require(bytes!=null&&bytes.length>0&&bytes.length<=MAX_JSON_FILE,"JSON asset size");
        try{CharBuffer decoded=StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes));String raw=decoded.toString();object(raw,MAX_JSON_FILE,16);return raw;
        }catch(java.nio.charset.CharacterCodingException invalid){throw new IllegalArgumentException("Invalid UTF-8 JSON",invalid);}
    }
    private static JsonObject object(String raw,int maximum,int maximumDepth){
        require(raw!=null&&raw.length()<=maximum,"Asset document size");
        int depth=0;boolean quoted=false,escaped=false;
        for(char c:raw.toCharArray()){
            if(quoted){if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
            else if(c=='"')quoted=true;
            else if(c=='{'||c=='[')require(++depth<=maximumDepth,"Asset document depth");
            else if(c=='}'||c==']')require(--depth>=0,"Asset document balance");
        }
        require(depth==0&&!quoted,"Incomplete asset document");
        var json=JsonParser.parseString(raw);require(json.isJsonObject(),"Expected object");return json.getAsJsonObject();
    }
}
