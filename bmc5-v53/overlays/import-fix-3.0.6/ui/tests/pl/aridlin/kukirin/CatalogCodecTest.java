package pl.aridlin.kukirin;
import java.util.*;
import io.netty.buffer.Unpooled;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.core.RegistryAccess;
public class CatalogCodecTest {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  List<String> names=new ArrayList<>(); for(int i=0;i<5000;i++) names.add("track-"+i+".wav");
  var catalog=new ScooterMusicMenu.Catalog(List.copyOf(names),true,false,true);
  var buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
  ScooterMusicMenu.Catalog.CODEC.encode(buffer,catalog);
  var decoded=ScooterMusicMenu.Catalog.CODEC.decode(buffer);
  check(decoded.equals(catalog),"catalog larger than old1024 limit roundtrips");
  check(buffer.readableBytes()==0,"all catalog bytes consumed");buffer.release();
  UUID transfer=UUID.randomUUID();
  for(int offset=0;offset<names.size();offset+=64){int end=Math.min(offset+64,names.size());
   var part=new ScooterMusicMenu.CatalogPart(transfer,offset,end==names.size(),true,true,false,true,"track-4000.wav",List.copyOf(names.subList(offset,end)));
   buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);
   ScooterMusicMenu.CatalogPart.CODEC.encode(buffer,part);
   check(buffer.readableBytes()<32768,"bounded transport packet");
   check(ScooterMusicMenu.CatalogPart.CODEC.decode(buffer).equals(part),"part roundtrip");
   check(buffer.readableBytes()==0,"all part bytes consumed");buffer.release();
  }
  buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);buffer.writeVarInt(5000);buffer.writeBoolean(true);buffer.writeBoolean(false);buffer.writeBoolean(true);
  boolean rejected=false;try{ScooterMusicMenu.Catalog.CODEC.decode(buffer);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"malformed count rejects without allocation");buffer.release();
  buffer=new RegistryFriendlyByteBuf(Unpooled.buffer(),RegistryAccess.EMPTY);rejected=false;try{ScooterMusicMenu.CatalogPart.CODEC.encode(buffer,new ScooterMusicMenu.CatalogPart(transfer,0,true,true,true,false,true,List.copyOf(names.subList(0,65))));}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"one part cannot exceed packet chunk size");buffer.release();
  System.out.println("{\"success\":true,\"assertions\":"+checks+",\"native_game_launched\":false}");
 }
}
