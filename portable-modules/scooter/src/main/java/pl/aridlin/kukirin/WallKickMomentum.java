package pl.aridlin.kukirin;
/** Retain only the speed lost on a recent wall impact; consume it once. */
public final class WallKickMomentum {
 public static final int GRACE_TICKS=7;
 private ScooterHandling.Motion impact;private long at;
 public void record(ScooterHandling.Motion requested,ScooterHandling.Motion actual,boolean collision,long tick){
  if(!collision||requested.speed()<=actual.speed()+.025)return;
  if(impact==null||tick-at>GRACE_TICKS||requested.speed()>impact.speed()){impact=requested;at=tick;}
 }
 public ScooterHandling.Motion take(ScooterHandling.Motion current,double nx,double nz,long tick){
  var saved=impact;impact=null;
  return saved!=null&&tick>=at&&tick-at<=GRACE_TICKS&&saved.speed()>current.speed()&&saved.x()*nx+saved.z()*nz<-.01?saved:current;
 }
}
