package pl.aridlin.psychiatrykroles;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.properties.Half;
/** Disabled adapter for the BMC migration. Ordinary Void Door logic has no external portal dependency. */
final class ImmersiveVoidPortals {
 static final String TAG="psychiatrykImmersiveVoidPortal";
 record Endpoint(ServerLevel level,BlockPos position){}
 ImmersiveVoidPortals(String kind){}
 static Endpoint door(ServerLevel level,BlockPos pos,Direction facing){return new Endpoint(level,pos);}
 static Endpoint trapdoor(ServerLevel level,BlockPos pos,Half half){return new Endpoint(level,pos);}
 void begin(){}
 void link(UUID pair,Endpoint a,Endpoint b){}
 void end(){}
 void clear(){}
}
