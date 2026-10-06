package pl.aridlin.kukirin;
import java.util.*;
/** Independent temporal filter per OpenAL source; no environment state leaks between sounds. */
public final class SoundEnvironmentSmoothing {
 private record State(float[] values,long time){}
 public static volatile long applications;
 private static final Map<Integer,State> states=new HashMap<>();
 public static synchronized void reset(int source){states.remove(source);}
 public static synchronized float[] apply(int source,float[] target,long now){
  applications++;var old=states.get(source);float[] next=target.clone();
  if(old!=null&&now>=old.time&&now-old.time<3_000_000_000L){
   double seconds=(now-old.time)/1e9,alpha=1-Math.exp(-seconds/.45);
   for(int i=0;i<next.length;i++)next[i]=(float)(old.values[i]+(target[i]-old.values[i])*alpha);
  }
  states.put(source,new State(next,now));
  if(states.size()>512)states.entrySet().removeIf(e->now-e.getValue().time>3_000_000_000L);
  return next.clone();
 }
 private SoundEnvironmentSmoothing(){}
}
