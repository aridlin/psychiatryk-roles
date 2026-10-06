package pl.aridlin.kukirin;
import java.nio.file.*;
import java.util.*;
/** Server-owned live handling settings. Invalid edits retain the previous values. */
public final class ScooterTuning {
 public record Values(double steering,double normalGrip,double driftGrip,double recovery,double tyreVolume){
  public boolean valid(){return finite(steering,.25,2)&&finite(normalGrip,.5,1)&&finite(driftGrip,.03,.4)&&driftGrip<normalGrip&&finite(recovery,.05,1)&&finite(tyreVolume,0,1);}
  private static boolean finite(double v,double low,double high){return Double.isFinite(v)&&v>=low&&v<=high;}
 }
 public static final Values DEFAULT=new Values(1,.85,.045,.42,.35);
 private static Values current=DEFAULT,client=DEFAULT;private static long nextPoll,lastStamp=Long.MIN_VALUE;
 public static final Path FILE=Path.of("config/goplanska-scooter/handling.properties");
 public static Values get(boolean clientSide){return clientSide?client:server();}
 public static void receive(Values v){if(v.valid())client=v;}
 public static synchronized Values server(){
  long now=System.currentTimeMillis();if(now<nextPoll)return current;nextPoll=now+1000;
  try{if(!Files.exists(FILE)){save(DEFAULT);return current;}long stamp=Files.getLastModifiedTime(FILE).toMillis();if(stamp==lastStamp)return current;lastStamp=stamp;
   Properties p=new Properties();try(var in=Files.newInputStream(FILE)){p.load(in);}Values v=new Values(number(p,"steeringMultiplier",1),number(p,"normalGrip",.85),number(p,"driftGrip",.045),number(p,"driftRecovery",.42),number(p,"tyreVolume",.35));if(v.valid())current=v;else System.err.println("[Scooter tuning] Invalid values; keeping previous configuration.");
  }catch(Exception ex){System.err.println("[Scooter tuning] Cannot read handling configuration: "+ex.getMessage());}return current;
 }
 private static double number(Properties p,String key,double fallback){return Double.parseDouble(p.getProperty(key,Double.toString(fallback)));}
 public static synchronized void save(Values v)throws java.io.IOException{
  if(!v.valid())throw new IllegalArgumentException("Invalid scooter tuning");Files.createDirectories(FILE.getParent());Path temp=Files.createTempFile(FILE.getParent(),"handling-",".tmp");Files.writeString(temp,"# Live scooter handling; /scooteradmin. Invalid values retain previous configuration.\nsteeringMultiplier="+v.steering()+"\nnormalGrip="+v.normalGrip()+"\ndriftGrip="+v.driftGrip()+"\ndriftRecovery="+v.recovery()+"\ntyreVolume="+v.tyreVolume()+"\n");Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);current=v;lastStamp=Files.getLastModifiedTime(FILE).toMillis();
 }
}
