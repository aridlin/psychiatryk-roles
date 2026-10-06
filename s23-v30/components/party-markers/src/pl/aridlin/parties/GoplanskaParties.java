package pl.aridlin.parties;
import java.util.*;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

@Mod("goplanska_parties")
public final class GoplanskaParties {
    public GoplanskaParties(IEventBus bus) {
        bus.addListener(this::network);
        NeoForge.EVENT_BUS.addListener(TestZombie::commands);
        NeoForge.EVENT_BUS.addListener(this::commands);
        NeoForge.EVENT_BUS.addListener(this::chat);
        NeoForge.EVENT_BUS.addListener(this::damage);
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void network(RegisterPayloadHandlersEvent e) {
        e.registrar("2").optional().playToClient(WaypointSync.TYPE,WaypointSync.CODEC,
            (data,ctx) -> ctx.enqueueWork(() -> pl.aridlin.partymarkers.ClientWaypoints.receive(data)));
        e.registrar("3").optional().playToClient(MemberSync.TYPE,MemberSync.CODEC,
            (data,ctx) -> ctx.enqueueWork(() -> pl.aridlin.partymarkers.ClientWaypoints.receiveMembers(data)));
    }
    public static int party(ServerPlayer player) {
        var board=player.getServer().getScoreboard();var objective=board.getObjective("goplanska_party");
        if(objective==null)return 0;var score=board.getPlayerScoreInfo(player,objective);return score==null?0:Math.max(0,score.value());
    }
    public static boolean protectedFrom(int victim,int attacker,boolean friendlyFire) { return victim>0 && victim==attacker && !friendlyFire; }
    private void damage(LivingIncomingDamageEvent e) {
        if(e.getEntity() instanceof ServerPlayer victim && e.getSource().getEntity() instanceof ServerPlayer attacker && victim!=attacker) {
            int group=party(victim);
            if(protectedFrom(group,party(attacker),PartyData.get(victim.getServer()).friendlyFire.contains(group)))e.setCanceled(true);
        }
    }
    private void chat(ServerChatEvent e) {
        if(PartyData.get(e.getPlayer().getServer()).chatMode.contains(e.getPlayer().getUUID())) {
            e.setCanceled(true);send(e.getPlayer(),e.getRawText());
        }
    }
    private static int send(ServerPlayer player,String message) {
        int group=party(player);
        if(group==0){player.sendSystemMessage(Component.literal("You are not in a party. Use /party chat toggle to return to global chat."));return 0;}
        Component text=Component.literal("[Party] <"+player.getGameProfile().getName()+"> "+message).withStyle(net.minecraft.ChatFormatting.AQUA);
        for(var member:player.getServer().getPlayerList().getPlayers())if(party(member)==group)member.sendSystemMessage(text);
        return 1;
    }
    private void tick(ServerTickEvent.Post e) { if(e.getServer().getTickCount()%5==0)sync(e.getServer()); }
    private static void sync(MinecraftServer server) {
        PartyData data=PartyData.get(server);
        for(var p:server.getPlayerList().getPlayers()) if(p.connection.hasChannel(WaypointSync.TYPE))
            PacketDistributor.sendToPlayer(p,new WaypointSync(data.points(party(p))));
        for(var p:server.getPlayerList().getPlayers())if(p.connection.hasChannel(MemberSync.TYPE)) {
            int group=party(p);var members=server.getPlayerList().getPlayers().stream().filter(other->other!=p&&(p.getPersistentData().getBoolean("GoplanskaChamsEveryone")||group>0&&party(other)==group))
                .map(other->new MemberSync.Member(other.getUUID(),other.getGameProfile().getName(),other.level().dimension().location().toString(),other.getX(),other.getY()+other.getBbHeight()+.5,other.getZ(),0xff000000|(((net.deadlydiamond98.way.util.mixin.IWayPlayer)other).way$getColor()&0xffffff),other.level()==p.level()?CoverThickness.between((net.minecraft.server.level.ServerLevel)p.level(),p.getEyePosition(),other.position().add(0,other.getBbHeight()*.5,0)):0,other.getId(),"player")).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            TestZombie.append(p,members);
            PacketDistributor.sendToPlayer(p,new MemberSync(members));
        }
    }
    private static int toggle(CommandContext<CommandSourceStack> ctx)throws CommandSyntaxException {
        var p=ctx.getSource().getPlayerOrException();var d=PartyData.get(p.getServer());
        if(!d.chatMode.remove(p.getUUID())) { if(party(p)==0){ctx.getSource().sendFailure(Component.literal("You are not in a party."));return 0;}d.chatMode.add(p.getUUID()); }
        d.setDirty();p.sendSystemMessage(Component.literal(d.chatMode.contains(p.getUUID())?"Chat now goes to your party. /party chat toggle returns to global chat.":"Chat now goes to everyone."));return 1;
    }
    private static int waypoint(CommandContext<CommandSourceStack> ctx,boolean add)throws CommandSyntaxException {
        var p=ctx.getSource().getPlayerOrException();int group=party(p);var d=PartyData.get(p.getServer());String name=StringArgumentType.getString(ctx,"name");
        if(group==0||!name.matches("[\\p{L}\\p{N}_-]{1,32}")){ctx.getSource().sendFailure(Component.literal("Join a party and use a waypoint name of 1–32 letters, digits, _ or -."));return 0;}
        if(add&&d.points(group).size()>=32&&!d.points(group).stream().anyMatch(w->w.name().equalsIgnoreCase(name))){ctx.getSource().sendFailure(Component.literal("Your party already has 32 waypoints."));return 0;}
        boolean removed=d.waypoints.removeIf(w->w.party()==group&&w.name().equalsIgnoreCase(name));
        if(add)d.waypoints.add(new PartyData.Waypoint(group,name,p.level().dimension().location().toString(),p.getX(),p.getY(),p.getZ(),p.getUUID()));
        if(!add&&!removed){ctx.getSource().sendFailure(Component.literal("Waypoint not found."));return 0;}
        d.setDirty();sync(p.getServer());p.sendSystemMessage(Component.literal((add?"Saved party waypoint: ":"Removed party waypoint: ")+name));return 1;
    }
    private static int friendlyFire(CommandContext<CommandSourceStack> ctx,boolean enabled)throws CommandSyntaxException {
        var p=ctx.getSource().getPlayerOrException();int group=party(p);String name=p.getGameProfile().getName();
        boolean leader=group==1&&name.equalsIgnoreCase("aridlin")||group==2&&name.equalsIgnoreCase("rozowykocurek");
        if(group==0||(!leader&&!ctx.getSource().hasPermission(2))){ctx.getSource().sendFailure(Component.literal("Only your party leader or a server operator can change friendly fire."));return 0;}
        var d=PartyData.get(p.getServer());if(enabled)d.friendlyFire.add(group);else d.friendlyFire.remove(group);d.setDirty();
        for(var member:p.getServer().getPlayerList().getPlayers())if(party(member)==group)member.sendSystemMessage(Component.literal("Party friendly fire: "+(enabled?"on":"off")));return 1;
    }
    private void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("chamsteam").executes(c->{var p=c.getSource().getPlayerOrException();boolean enabled=!p.getPersistentData().getBoolean("GoplanskaChamsEveryone");p.getPersistentData().putBoolean("GoplanskaChamsEveryone",enabled);sync(p.getServer());p.sendSystemMessage(Component.literal(enabled?"Chams and markers: everyone. /chamsteam returns to party only.":"Chams and markers: party only."));return 1;}));
        var party=Commands.literal("party").executes(c->{c.getSource().sendSuccess(()->Component.literal("/party members | chat <message> | chat toggle | waypoint add/list/remove | friendlyfire on/off"),false);return 1;});
        party.then(Commands.literal("members").executes(c->{var p=c.getSource().getPlayerOrException();int id=party(p);var objective=p.getServer().getScoreboard().getObjective("goplanska_party");
            var names=objective==null||id==0?List.<String>of():p.getServer().getScoreboard().listPlayerScores(objective).stream().filter(s->s.value()==id).map(s->s.owner()).sorted().toList();
            p.sendSystemMessage(Component.literal("Party "+id+": "+String.join(", ",names)));return 1;}));
        party.then(Commands.literal("chat").then(Commands.literal("toggle").executes(GoplanskaParties::toggle)).then(Commands.argument("message",StringArgumentType.greedyString()).executes(c->send(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"message")))));
        party.then(Commands.literal("waypoint")
            .then(Commands.literal("add").then(Commands.argument("name",StringArgumentType.word()).executes(c->waypoint(c,true))))
            .then(Commands.literal("remove").then(Commands.argument("name",StringArgumentType.word()).executes(c->waypoint(c,false))))
            .then(Commands.literal("list").executes(c->{var p=c.getSource().getPlayerOrException();var points=PartyData.get(p.getServer()).points(party(p));
                p.sendSystemMessage(Component.literal("Party waypoints: "+points.size()));for(var w:points)p.sendSystemMessage(Component.literal(w.name()+" — "+w.dimension()+" "+(int)w.x()+", "+(int)w.y()+", "+(int)w.z()));return 1;})));
        party.then(Commands.literal("friendlyfire").then(Commands.literal("on").executes(c->friendlyFire(c,true))).then(Commands.literal("off").executes(c->friendlyFire(c,false))));
        e.getDispatcher().register(party);
        e.getDispatcher().register(Commands.literal("pc").then(Commands.argument("message",StringArgumentType.greedyString()).executes(c->send(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"message")))));
    }
}
