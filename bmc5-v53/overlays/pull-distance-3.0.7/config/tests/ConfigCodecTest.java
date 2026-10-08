import java.nio.file.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import net.neoforged.fml.loading.FMLPaths;
import pl.aridlin.psychiatrykroles.peeb.*;
public final class ConfigCodecTest {
 private static int checks;
 private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 private static PeebConfig.Values values(double distance){return new PeebConfig.Values(8,.4,.8,.3,true,distance);}
 private static void rejected(double distance)throws Exception{
  PeebConfig.Values v=values(distance);check(!v.valid(),"invalid distance accepted: "+distance);
  try{PeebConfig.save(v);throw new AssertionError("invalid persisted");}catch(IllegalArgumentException expected){checks++;}
 }
 public static void main(String[] args)throws Exception{
  Path game=Path.of(args[0]).toAbsolutePath();Files.createDirectories(game);FMLPaths.loadAbsolutePaths(game);
  check(PeebConfig.DEFAULT.stopDistance()==0,"full pull default");
  PeebConfig.Values legacy=new PeebConfig.Values(8,.4,.8,.3,true);check(legacy.valid()&&legacy.stopDistance()==0,"legacy constructor");
  for(double d:new double[]{0,.0001,.35,1.5,8,32}){
   PeebConfig.Values v=values(d);check(v.valid(),"valid distance");check(v.clamped().equals(v),"valid unchanged");
   var sync=PeebConfigPayload.of(true,v);var sb=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   PeebConfigPayload.STREAM_CODEC.encode(sb,sync);var sd=PeebConfigPayload.STREAM_CODEC.decode(sb);
   check(sd.equals(sync)&&sd.values().equals(v),"sync distance lost");check(sb.readableBytes()==0,"sync bytes not consumed");sb.release();
   for(int action:new int[]{0,1,2}){
    var edit=new PeebConfigEditPayload(action,v);var eb=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
    PeebConfigEditPayload.STREAM_CODEC.encode(eb,edit);check(PeebConfigEditPayload.STREAM_CODEC.decode(eb).equals(edit),"edit distance lost");check(eb.readableBytes()==0,"edit bytes not consumed");eb.release();
   }
  }
  for(double d:new double[]{-1,33,Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY})rejected(d);
  check(values(-1).clamped().stopDistance()==0,"negative clamp");check(values(33).clamped().stopDistance()==32,"high clamp");
  try{values(Double.NaN).clamped();throw new AssertionError("nan clamp");}catch(IllegalArgumentException expected){checks++;}
  String old="range=11\nmaxHorizontalSpeed=0.55\nmaxSpeed=0.95\nstrength=0.7\nfallImmunity=false\n";
  Files.createDirectories(PeebConfig.file().getParent());Files.writeString(PeebConfig.file(),old);
  var loaded=PeebConfig.reload();check(loaded.stopDistance()==0&&loaded.range()==11&&!loaded.fallImmunity(),"old file defaults preserve prior fields");
  var saved=new PeebConfig.Values(11,.55,.95,.7,false,1.23456789);PeebConfig.save(saved);check(Files.readString(PeebConfig.file()).contains("stopDistance=1.23456789"),"serialized exact distance");check(PeebConfig.reload().equals(saved),"save reload lost field");
  Files.writeString(PeebConfig.file(),old+"stopDistance=2.25\n");var live=PeebConfig.server();check(live.stopDistance()==2.25&&live.range()==11,"hot poll failed");
  Files.writeString(PeebConfig.file(),old+"stopDistance=33\n");check(PeebConfig.reload().stopDistance()==32,"file clamp");
  Files.writeString(PeebConfig.file(),old+"stopDistance=NaN\n");try{PeebConfig.reload();throw new AssertionError("nonfinite file");}catch(IllegalArgumentException expected){checks++;}
  System.out.println("{\"success\":true,\"checks\":"+checks+",\"native_packet_codecs\":true,\"actual_config_file_reload\":true,\"game_launched\":false}");
 }
}
