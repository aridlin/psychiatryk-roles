import pl.aridlin.kukirin.*;
public final class BoostDecayTest {
 static void check(boolean yes,String why){if(!yes)throw new AssertionError(why);System.out.println("PASS "+why);}
 public static void main(String[] a){
  for(boolean air:new boolean[]{false,true})for(double input:new double[]{0,1}){
   var decay=new ScooterOverspeed();var m=new ScooterHandling.Motion(0,200/72d);double start=m.speed(),limit=ScooterHandling.TOP_SPEED;
   for(int tick=0;tick<100;tick++){
    var old=m;m=ScooterHandling.step(m.x(),m.z(),0,input,!air,true);
    m=ScooterFlight.accelerate(m,0,input,old.speed(),limit,false,false,!air);
    m=decay.apply(m,limit,false,tick);
    if(tick==19)check(m.speed()>limit+(start-limit)*.75,"boost keeps most excess speed after 1 sec air="+air+" throttle="+input);
    if(tick==79)check(m.speed()>limit+.01,"boost remains above cruise at 4 sec air="+air+" throttle="+input);
   }
   check(Math.abs(m.speed()-limit)<1e-6,"boost tapers to cruise at 5 sec air="+air+" throttle="+input);
  }
  var d=new ScooterOverspeed();var m=new ScooterHandling.Motion(0,100/72d);d.begin(m.speed(),ScooterHandling.TOP_SPEED,0,40);
  for(int tick=0;tick<40;tick++){m=ScooterHandling.step(m.x(),m.z(),0,1,true,true);m=d.apply(m,ScooterHandling.TOP_SPEED,false,tick);if(tick==19)check(m.speed()>ScooterHandling.TOP_SPEED+.1,"spear impulse still above cruise after 1 sec");}
  check(Math.abs(m.speed()-ScooterHandling.TOP_SPEED)<1e-6,"single spear impulse finishes its 2 sec taper");
  var flight=new ScooterHandling.Motion(0,200/72d);var next=ScooterFlight.accelerate(flight,0,1,flight.speed(),ScooterHandling.TOP_SPEED,true,false,true);
  check(Math.abs(next.speed()-flight.speed())<1e-8,"rocket expiry does not snap netherite scooter to 180 km/h");
  var brakes=new ScooterOverspeed();var stop=new ScooterHandling.Motion(0,2);for(int t=0;t<20;t++){stop=ScooterHandling.step(stop.x(),stop.z(),0,-1,true,true);stop=brakes.apply(stop,.72,true,t);}
  check(stop.speed()<1.2,"intentional braking still slows an overspeed scooter immediately");
 }
}
