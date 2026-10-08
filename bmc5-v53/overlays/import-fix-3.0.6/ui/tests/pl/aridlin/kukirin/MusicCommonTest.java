package pl.aridlin.kukirin;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.lang.reflect.*;
import sun.misc.Unsafe;
public class MusicCommonTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args) throws Exception {
  List<String> names=new ArrayList<>();for(int i=0;i<5000;i++)names.add("track-"+i+".wav");
  check(ScooterMusic.nextPlaylist(names,"track-24.wav",false,new Random(0)).equals("track-25.wav"),"playlist advances beyond old25 cap");
  check(ScooterMusic.nextPlaylist(names,"track-4998.wav",false,new Random(0)).equals("track-4999.wav"),"playlist final imported track reachable");
  check(ScooterMusic.nextPlaylist(names,"track-4999.wav",false,new Random(0)).equals("track-0.wav"),"playlist wraps at real end");
  check(!ScooterMusic.nextPlaylist(names,"track-25.wav",true,new Random(0)).equals("track-25.wav"),"shuffle excludes current song");
  Field started=ScooterMusicSources.class.getDeclaredField("started");started.setAccessible(true);((AtomicBoolean)started.get(null)).set(true);
  Field songs=ScooterMusicSources.class.getDeclaredField("songs");songs.setAccessible(true);Map<String,ScooterMusicSources.Song> catalog=new LinkedHashMap<>();for(String name:names)catalog.put(name,new ScooterMusicSources.Song(name,name,"https://example.invalid/track.wav","","0".repeat(64),44,20));songs.set(null,Map.copyOf(catalog));
  List<String> library=ScooterMusic.songs();check(library.containsAll(names),"all5000rows included");check(library.size()==5081,"5000imported+79vanilla+2bundled");
  Field u=Unsafe.class.getDeclaredField("theUnsafe");u.setAccessible(true);Unsafe unsafe=(Unsafe)u.get(null);Field id=ScooterMusic.Source.class.getDeclaredField("id");id.setAccessible(true);
  Method modes=ScooterMusic.class.getDeclaredMethod("modes",ScooterMusic.Source.class,boolean.class);modes.setAccessible(true);
  for(int i=0;i<400;i++){ScooterMusic.Source source=(ScooterMusic.Source)unsafe.allocateInstance(ScooterMusic.Source.class);id.set(source,new UUID(1,i));check(modes.invoke(null,source,true)!=null,"settings beyond256sources accepted");}
  System.out.println("{\"success\":true,\"assertions\":"+checks+",\"native_game_launched\":false}");
 }
}
