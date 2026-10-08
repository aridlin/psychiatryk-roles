package pl.aridlin.kukirin;

import com.google.gson.*;
import java.nio.file.*;
import java.net.URI;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Server-only private catalog. Readers see an immutable snapshot; disk work stays off the tick thread. */
public final class ScooterMusicSources {
 public record Song(String id,String title,String url,String bearer,String sha256,int bytes,int durationTicks) { @Override public String toString(){return "Song[id="+id+",bytes="+bytes+"]";} }
 private static volatile Map<String,Song> songs=Map.of();
 private static final AtomicBoolean started=new AtomicBoolean();
 private static long stamp=-1,size=-1;
 private static final ScheduledExecutorService io=Executors.newSingleThreadScheduledExecutor(r->{Thread t=new Thread(r,"Scooter music catalog");t.setDaemon(true);return t;});
 private static Path file(){return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("goplanska-scooter/music-sources.json");}
 public static Map<String,Song> catalog(){if(started.compareAndSet(false,true))io.scheduleWithFixedDelay(ScooterMusicSources::reload,0,5,TimeUnit.SECONDS);return songs;}
 static synchronized void reload(){
  try {
   Path p=file();if(!Files.isRegularFile(p)||Files.isSymbolicLink(p)){songs=Map.of();stamp=-1;size=-1;return;}
   long modified=Files.getLastModifiedTime(p).toMillis(),length=Files.size(p);if(modified==stamp&&length==size)return;
   if(length>1024*1024)throw new IllegalArgumentException("Catalog exceeds 1 MiB");
   JsonObject root;try(var reader=Files.newBufferedReader(p)){root=JsonParser.parseReader(reader).getAsJsonObject();}
   String base=root.get("baseUrl").getAsString(),bearer=root.has("bearerToken")?root.get("bearerToken").getAsString():"";
   if(bearer.length()>512||bearer.indexOf('\r')>=0||bearer.indexOf('\n')>=0)throw new IllegalArgumentException("Invalid authorization");
   URI uri=URI.create(base);ScooterMusicHttpCache.validateUri(uri);
   if(!base.endsWith("/"))throw new IllegalArgumentException("Music base URL must end with slash");
   LinkedHashMap<String,Song> loaded=new LinkedHashMap<>();
   for(JsonElement e:root.getAsJsonArray("songs")){
    JsonObject o=e.getAsJsonObject();String id=o.get("id").getAsString(),title=o.has("title")?o.get("title").getAsString():id,sha=o.get("sha256").getAsString();
    int bytes=o.get("bytes").getAsInt(),ticks=o.get("durationTicks").getAsInt();
    if(!validName(id)||!sha.matches("[a-f0-9]{64}")||bytes<44||ticks<1||title.length()>256||loaded.size()>=1024)throw new IllegalArgumentException("Invalid song entry");
    String url=base+sha+".wav";ScooterMusicHttpCache.validateUri(URI.create(url));
    if(loaded.put(id,new Song(id,title,url,bearer,sha,bytes,ticks))!=null)throw new IllegalArgumentException("Duplicate song ID");
   }
   songs=Collections.unmodifiableMap(loaded);stamp=modified;size=length;
  }catch(Exception ex){org.slf4j.LoggerFactory.getLogger("ScooterMusic").warn("Music catalog update rejected; retaining the last valid catalog ({}).",ex.getClass().getSimpleName());}
 }
 public static boolean validName(String n){return n!=null&&n.length()<=256&&n.matches("[A-Za-z0-9_. -]+\\.wav")&&!n.contains("..");}
 private ScooterMusicSources(){}
}
