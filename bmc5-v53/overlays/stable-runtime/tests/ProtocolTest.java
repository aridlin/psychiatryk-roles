import pl.aridlin.psychiatrykroles.runtime.*;
import com.google.gson.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
import io.netty.buffer.Unpooled;
import java.nio.file.*;
import java.util.*;
public final class ProtocolTest {
 static int checks;static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 static void rejects(Runnable r,String why){boolean rejected=false;try{r.run();}catch(RuntimeException ex){rejected=true;}check(rejected,why);}
 public static void main(String[]args)throws Exception{
  String raw=Files.readString(Path.of(args[0]));var config=Schema.config(raw);check(config.menus().size()==4,"default menus");check(config.items().size()==1,"only information item enabled");
  var object=JsonParser.parseString(raw).getAsJsonObject();object.addProperty("futureOptionalField","ignored");check(Schema.revision(Schema.config(object.toString())).equals(Schema.revision(config)),"unknown optional fields remain compatible");
  object.addProperty("schema",2);rejects(()->Schema.config(object.toString()),"unknown major schema rejected");
  rejects(()->Schema.config("[".repeat(40)+"]".repeat(40)),"depth bounded");rejects(()->Schema.config(" ".repeat(65537)),"size bounded");
  for(String bad:List.of("{}","null","[]","{\"schema\":1}","{\"schema\":1,\"menus\":{},\"items\":{}}"))rejects(()->Schema.config(bad),"malformed config");
  var def=JsonParser.parseString(raw).getAsJsonObject();var entry=def.getAsJsonObject("menus").getAsJsonObject("scooter").getAsJsonArray("entries").get(0).getAsJsonObject();entry.addProperty("permission",0);rejects(()->Schema.config(def.toString()),"settings cannot remove authorization");entry.addProperty("permission",2);entry.addProperty("target","java.lang.Runtime.exec");rejects(()->Schema.config(def.toString()),"unknown binding cannot execute code");
  var behaviorDoc=JsonParser.parseString(raw).getAsJsonObject();var behaviors=new JsonObject();var behavior=new JsonObject();behavior.addProperty("permission",0);behavior.add("conditions",new JsonArray());var steps=new JsonArray();steps.add(JsonParser.parseString("{\"op\":\"message\",\"target\":\"Live feature\",\"amount\":0}"));behavior.add("steps",steps);behaviors.add("new_feature",behavior);behaviorDoc.add("behaviors",behaviors);
  behaviorDoc.getAsJsonObject("items").getAsJsonObject("guide").addProperty("action","behavior");behaviorDoc.getAsJsonObject("items").getAsJsonObject("guide").addProperty("target","new_feature");check(Schema.config(behaviorDoc.toString()).behaviors().size()==1,"new server-defined behavior loaded without client schema change");
  var hooks=new JsonObject();hooks.addProperty("join","new_feature");hooks.addProperty("sneak","new_feature");behaviorDoc.add("hooks",hooks);check(Schema.config(behaviorDoc.toString()).hooks().size()==2,"hot-reloadable event hooks validate");
  hooks.addProperty("arbitrary_java_event","new_feature");rejects(()->Schema.config(behaviorDoc.toString()),"unregistered dynamic hook rejected");hooks.remove("arbitrary_java_event");
  steps.set(0,JsonParser.parseString("{\"op\":\"function\",\"target\":\"../outside\",\"amount\":0}"));rejects(()->Schema.config(behaviorDoc.toString()),"invalid function identifier rejected");
  steps.set(0,JsonParser.parseString("{\"op\":\"velocity\",\"target\":\"\",\"amount\":2}"));rejects(()->Schema.config(behaviorDoc.toString()),"unsafe behavior velocity rejected");
  var v=new Schema.View(1,"a".repeat(64),"token","main","Menu",List.of(new Schema.Control("new","future widget","future-widget","Ignored safely",0,0,0,0)));
  check(Schema.view(Schema.JSON.toJson(v)).controls().get(0).kind().equals("future-widget"),"old client tolerates unknown widget kind");
  var styled=new Schema.View(1,"a".repeat(64),"token","main","Scene",List.of(new Schema.Control("tile","Arbitrary layout","rect","",0,0,1,0,Map.of("x","0.1","y","0.2","w","0.5","h","0.25","color","#FF003344","futureProperty","ignored"))));
  check(Schema.view(Schema.JSON.toJson(styled)).controls().get(0).props().containsKey("futureProperty"),"optional presentation properties roundtrip");
  rejects(()->Schema.view(Schema.JSON.toJson(new Schema.View(1,"a".repeat(64),"token","main","Bad",List.of(new Schema.Control("x","X","rect","",0,0,1,0,Map.of("x","NaN")))))),"nonfinite layout rejected");
  rejects(()->Schema.view(Schema.JSON.toJson(new Schema.View(1,"a".repeat(64),"token","main","Bad",List.of(new Schema.Control("x","X","rect","",0,0,1,0,Map.of("item","../escape")))))),"invalid item icon rejected");
  for(int i=0;i<2000;i++){
   var action=new Schema.Action(1,"a".repeat(64),"session-"+i,"main","setting",String.valueOf(i*.01));String text=Schema.JSON.toJson(action);
   var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{RuntimeNetwork.Action.CODEC.encode(b,new RuntimeNetwork.Action(text));check(RuntimeNetwork.Action.CODEC.decode(b).json().equals(text)&&b.readableBytes()==0,"action codec exact roundtrip");check(Schema.action(text).equals(action),"action JSON roundtrip");}finally{b.release();}
  }
  for(int n:new int[]{0,1,100,2048,65536}){String text="x".repeat(n);var b=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{RuntimeNetwork.Snapshot.CODEC.encode(b,new RuntimeNetwork.Snapshot(text));check(RuntimeNetwork.Snapshot.CODEC.decode(b).json().equals(text)&&b.readableBytes()==0,"snapshot boundary "+n);}finally{b.release();}}
  var overflow=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);try{rejects(()->RuntimeNetwork.Action.CODEC.encode(overflow,new RuntimeNetwork.Action("x".repeat(2049))),"oversize wire action");}finally{overflow.release();}
  for(String id:List.of("../escape","UPPER","minecraft:stone","x\nstop","","x".repeat(65)))rejects(()->Schema.id(id),"ID rejects injection");
  for(double bad:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,5})rejects(()->{try{Settings.write("scooter.steering",bad);}catch(ReflectiveOperationException ex){throw new RuntimeException(ex);}},"setting hard boundary");
  System.out.println("PASS "+checks+" protocol/schema/boundary checks; v1 wire layout unchanged for optional fields");
 }
}
