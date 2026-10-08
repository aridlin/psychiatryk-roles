import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.phys.Vec3;
import pl.aridlin.psychiatrykroles.peeb.*;
import pl.aridlin.psychiatrykroles.peeb.client.PeebClient;

public final class StateCodecTest {
 static int checks;
 static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 public static void main(String[] args){
  UUID uuid=UUID.fromString("a524e9ed-5b51-4779-9d70-bfdca8809fff");
  for(boolean active:new boolean[]{false,true})for(boolean friend:new boolean[]{false,true})for(boolean present:new boolean[]{false,true}){
   var packet=new PeebStatePayload(uuid,73,active,present?Optional.of(new Vec3(1.2,2.3,3.4)):Optional.empty(),present?8:0,-179.5f,friend);
   var buf=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   PeebStatePayload.STREAM_CODEC.encode(buf,packet);
   check(PeebStatePayload.STREAM_CODEC.decode(buf).equals(packet),"actual codec retains teammate mode, anchor, id and yaw");
   check(buf.readableBytes()==0,"actual state codec consumes appended byte");buf.release();
  }
  check(!new PeebStatePayload(uuid,1,true,Optional.empty(),0).pullingTeammate(),"binary legacy five-argument packet constructor");
  check(!new PeebStatePayload(uuid,1,true,Optional.empty(),0,32).pullingTeammate(),"binary legacy six-argument packet constructor");
  check(!new PeebClient.Grapple(Vec3.ZERO,2).pullingTeammate(),"legacy two-argument grapple constructor");
  check(!new PeebClient.Grapple(Vec3.ZERO,2,32).pullingTeammate(),"legacy three-argument grapple constructor");
  check(new PeebClient.Grapple(Vec3.ZERO,2,32,true).pullingTeammate(),"render rope carries pull mode");
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_state_codec\":true,\"legacy_constructors\":true,\"game_launched\":false}");
 }
}
