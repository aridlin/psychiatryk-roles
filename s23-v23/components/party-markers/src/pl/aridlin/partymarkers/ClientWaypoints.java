package pl.aridlin.partymarkers;
import java.util.List;
import net.minecraft.client.Minecraft;
import pl.aridlin.parties.PartyData;
import pl.aridlin.parties.WaypointSync;

public final class ClientWaypoints {
    private static List<PartyData.Waypoint> points=List.of();
    private static Object connection;
    private static List<pl.aridlin.parties.MemberSync.Member> members=List.of();
    private static Object memberConnection;
    private static long memberTime;
    private static java.util.Map<java.util.UUID,net.minecraft.world.phys.Vec3> previous=java.util.Map.of();
    public static void receiveMembers(pl.aridlin.parties.MemberSync data){var old=new java.util.HashMap<java.util.UUID,net.minecraft.world.phys.Vec3>();for(var m:members())old.put(m.uuid(),position(m));previous=old;memberTime=System.nanoTime();memberConnection=Minecraft.getInstance().getConnection();members=List.copyOf(data.members());}
    public static net.minecraft.world.phys.Vec3 position(pl.aridlin.parties.MemberSync.Member m){var to=new net.minecraft.world.phys.Vec3(m.x(),m.y(),m.z());var from=previous.getOrDefault(m.uuid(),to);double t=Math.max(0,Math.min(1,(System.nanoTime()-memberTime)/250_000_000.0));return from.lerp(to,t);}
    static List<pl.aridlin.parties.MemberSync.Member> members(){if(memberConnection!=Minecraft.getInstance().getConnection()){members=List.of();memberConnection=null;}return members;}
    public static void receive(WaypointSync data){connection=Minecraft.getInstance().getConnection();points=List.copyOf(data.points());}
    static List<PartyData.Waypoint> points(){if(connection!=Minecraft.getInstance().getConnection()){points=List.of();connection=null;}return points;}
}
