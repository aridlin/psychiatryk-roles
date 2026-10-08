import java.util.Optional;
import pl.aridlin.psychiatrykroles.peeb.PeebAttachment;
import pl.aridlin.psychiatrykroles.peeb.client.PeebClient;
import pl.aridlin.psychiatrykroles.peeb.client.PeebRenderer;
public class MountedRenderTest {
 static int checks;
 static void check(boolean x){checks++;if(!x)throw new AssertionError("check "+checks);}
 public static void main(String[] a){
  for(float p:new float[]{0,.25f,.5f,.75f,1}){
   check(PeebRenderer.mountedBodyYaw(p,70,120,10,50,true)==10+p*40);
   check(PeebRenderer.mountedBodyYaw(p,70,120,10,50,false)==70+p*50);
  }
  check(Math.abs(PeebRenderer.mountedBodyYaw(.5f,0,0,179,-179,true)-180)<.001);
  var moving=new PeebClient.Motion(120,7,1,true,-.5f,3,Optional.empty());
  var riding=PeebRenderer.mountedMotion(moving,true);
  check(riding.speed()==0);check(!riding.airborne());check(riding.verticalSpeed()==0);
  check(riding.walkPhase()==0);check(riding.ageTicks()==120);check(riding.landingAgeTicks()==3);
  check(riding.grapple()==moving.grapple());check(PeebRenderer.mountedMotion(moving,false)==moving);
  var tilted=new PeebAttachment.Tilt(35,20);
  check(PeebRenderer.mountedTilt(tilted,true)==PeebAttachment.Tilt.ZERO);
  check(PeebRenderer.mountedTilt(tilted,false)==tilted);
  System.out.println("{\"success\":true,\"checks\":"+checks+"}");
 }
}
