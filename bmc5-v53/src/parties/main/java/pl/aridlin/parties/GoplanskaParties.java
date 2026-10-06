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
    private record Invitation(UUID issuer,int group,long expires) {}
    private static final Map<UUID,Invitation> invitations=new HashMap<>();
    private static net.minecraft.world.scores.Objective objective(MinecraftServer s) {
        var board=s.getScoreboard();var obj=board.getObjective("goplanska_party");
        if(obj==null)obj=board.addObjective("goplanska_party",net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,Component.literal("Party"),net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER,false,null);
        return obj;
    }
    private static void assign(MinecraftServer server,String name,int group) {
        server.getScoreboard().getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(name),objective(server)).set(group);
    }
    private static boolean isLeader(ServerPlayer p) {
        int group=party(p);return group>0&&p.getUUID().equals(PartyData.get(p.getServer()).leaders.get(group));
    }
    private static List<String> memberNames(ServerPlayer p) {
        int group=party(p);if(group==0)return List.of();
        return p.getServer().getScoreboard().listPlayerScores(objective(p.getServer())).stream().filter(s->s.value()==group).map(s->s.owner()).sorted().toList();
    }
    private void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var obj=objective(p.getServer());var data=PartyData.get(p.getServer());
        if(p.getServer().getScoreboard().getPlayerScoreInfo(p,obj)==null) {
            String name=p.getGameProfile().getName().toLowerCase(Locale.ROOT);
            int group=switch(name){case "aridlin","szybkiorzech","errar"->1;case "rozowykocurek","kocurek","kameleon"->2;default->0;};
            assign(p.getServer(),p.getScoreboardName(),group);
            if(group>0&&(name.equals("aridlin")||name.equals("rozowykocurek"))) {data.leaders.put(group,p.getUUID());data.setDirty();}
        }
        sync(p.getServer());
    }
    private static int color(ServerPlayer p) {
        int group=party(p);return 0xff000000|PartyData.get(p.getServer()).colors.getOrDefault(group,group==1?0xffdd33:0x55ffff);
    }
    private static int setColor(ServerPlayer p,String requested) {
        if(!isLeader(p)){p.sendSystemMessage(Component.literal("Only your party leader can change its color."));return 0;}
        Integer rgb=null;var named=net.minecraft.ChatFormatting.getByName(requested.toLowerCase(Locale.ROOT));
        if(named!=null&&named.isColor())rgb=named.getColor();
        else if(requested.matches("(?i)[0-9a-f]{6}"))rgb=Integer.parseInt(requested,16);
        if(rgb==null){p.sendSystemMessage(Component.literal("Use a Minecraft color name or six hex digits, e.g. yellow or ffdd33."));return 0;}
        var data=PartyData.get(p.getServer());data.colors.put(party(p),rgb);data.setDirty();sync(p.getServer());
        p.sendSystemMessage(Component.literal("Party color updated.").withStyle(style->style.withColor(color(p)&0xffffff)));return 1;
    }
    private static int create(ServerPlayer p) {
        if(party(p)!=0){p.sendSystemMessage(Component.literal("Leave your current party first."));return 0;}
        int group=Math.max(2,p.getServer().getScoreboard().listPlayerScores(objective(p.getServer())).stream().mapToInt(s->s.value()).max().orElse(2))+1;
        assign(p.getServer(),p.getScoreboardName(),group);var data=PartyData.get(p.getServer());data.leaders.put(group,p.getUUID());data.setDirty();
        p.sendSystemMessage(Component.literal("Persistent party created. Invite someone with /party invite <player>."));sync(p.getServer());return 1;
    }
    private static int invite(ServerPlayer p,ServerPlayer target) {
        if(!isLeader(p)){p.sendSystemMessage(Component.literal("Only your party leader can invite players. /party create creates your own party."));return 0;}
        if(p==target||party(target)!=0){p.sendSystemMessage(Component.literal("That player already belongs to a party."));return 0;}
        invitations.put(target.getUUID(),new Invitation(p.getUUID(),party(p),p.getServer().overworld().getGameTime()+12000));
        target.sendSystemMessage(Component.literal(p.getGameProfile().getName()+" invited you to a party. Use /party accept within 10 minutes."));
        p.sendSystemMessage(Component.literal("Party invitation sent to "+target.getGameProfile().getName()+"."));return 1;
    }
    private static int accept(ServerPlayer p) {
        var invite=invitations.remove(p.getUUID());var data=PartyData.get(p.getServer());
        if(party(p)!=0||invite==null||invite.expires()<p.getServer().overworld().getGameTime()||!invite.issuer().equals(data.leaders.get(invite.group()))) {
            p.sendSystemMessage(Component.literal("No valid invitation. Leave your party before accepting another."));return 0;
        }
        assign(p.getServer(),p.getScoreboardName(),invite.group());p.sendSystemMessage(Component.literal("You joined the party."));sync(p.getServer());return 1;
    }
    private static int kick(ServerPlayer p,String requested) {
        if(!isLeader(p)){p.sendSystemMessage(Component.literal("Only your party leader can kick members."));return 0;}
        String name=memberNames(p).stream().filter(n->n.equalsIgnoreCase(requested)).findFirst().orElse(null);
        if(name==null||name.equalsIgnoreCase(p.getGameProfile().getName())){p.sendSystemMessage(Component.literal("Choose another member of your own party."));return 0;}
        assign(p.getServer(),name,0);var target=p.getServer().getPlayerList().getPlayerByName(name);
        if(target!=null){PartyData.get(p.getServer()).chatMode.remove(target.getUUID());target.sendSystemMessage(Component.literal("You were removed from the party."));}
        PartyData.get(p.getServer()).setDirty();p.sendSystemMessage(Component.literal("Removed "+name+" from the party."));sync(p.getServer());return 1;
    }
    private static int leave(ServerPlayer p) {
        int group=party(p);if(group==0)return 0;
        if(isLeader(p)&&memberNames(p).size()>1){p.sendSystemMessage(Component.literal("Remove the other members before leaving your party."));return 0;}
        var data=PartyData.get(p.getServer());if(isLeader(p))data.leaders.remove(group);
        data.chatMode.remove(p.getUUID());data.setDirty();assign(p.getServer(),p.getScoreboardName(),0);sync(p.getServer());p.sendSystemMessage(Component.literal("You left the party."));return 1;
    }
    public GoplanskaParties(IEventBus bus) {
        bus.addListener(this::network);
        NeoForge.EVENT_BUS.addListener(this::login);
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
                .map(other->new MemberSync.Member(other.getUUID(),other.getGameProfile().getName(),other.level().dimension().location().toString(),other.getX(),other.getY()+other.getBbHeight()+.5,other.getZ(),color(other),0,other.getId(),"player")).collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));
            if(!p.getPersistentData().getBoolean("GoplanskaScooterChamsOff"))for(var record:pl.aridlin.kukirin.ScooterIndex.get(server).entries.values()) {
                if(!record.owner().equals(p.getUUID())||members.size()>=256)continue;
                var world=server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,net.minecraft.resources.ResourceLocation.parse(record.dimension())));
                var entity=world==null?null:world.getEntity(record.id());
                if(entity!=null&&entity.isRemoved())continue;
                double x=entity==null?record.pos().getX()+.5:entity.getX(),y=entity==null?record.pos().getY()+2:entity.getY()+entity.getBbHeight()+.5,z=entity==null?record.pos().getZ()+.5:entity.getZ();
                members.add(new MemberSync.Member(record.id(),"Your KuKirin",record.dimension(),x,y,z,record.color(),0,entity==null?-1:entity.getId(),"scooter"));
            }

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
        boolean leader=isLeader(p);
        if(group==0||(!leader&&!ctx.getSource().hasPermission(2))){ctx.getSource().sendFailure(Component.literal("Only your party leader or a server operator can change friendly fire."));return 0;}
        var d=PartyData.get(p.getServer());if(enabled)d.friendlyFire.add(group);else d.friendlyFire.remove(group);d.setDirty();
        for(var member:p.getServer().getPlayerList().getPlayers())if(party(member)==group)member.sendSystemMessage(Component.literal("Party friendly fire: "+(enabled?"on":"off")));return 1;
    }
    private void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("chams").then(Commands.literal("scooter").then(Commands.literal("off").executes(c->scooterChams(c.getSource().getPlayerOrException(),false))).then(Commands.literal("on").executes(c->scooterChams(c.getSource().getPlayerOrException(),true)))));
        e.getDispatcher().register(Commands.literal("chamsteam").executes(c->{var p=c.getSource().getPlayerOrException();boolean enabled=!p.getPersistentData().getBoolean("GoplanskaChamsEveryone");p.getPersistentData().putBoolean("GoplanskaChamsEveryone",enabled);sync(p.getServer());p.sendSystemMessage(Component.literal(enabled?"Chams and markers: everyone. /chamsteam returns to party only.":"Chams and markers: party only."));return 1;}));
        var party=Commands.literal("party").executes(c->{c.getSource().sendSuccess(()->Component.literal("/party new | color <name/hex> | invite <player> | accept | kick <player> | leave | members | chat <message> | waypoint add/list/remove | friendlyfire on/off"),false);return 1;});
        party.then(Commands.literal("new").executes(c->create(c.getSource().getPlayerOrException())));
        party.then(Commands.literal("color").executes(c->{var p=c.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(String.format("Party color: %06x",color(p)&0xffffff)));return 1;}).then(Commands.argument("color",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(List.of("yellow","gold","red","green","aqua","blue","light_purple","white","ffdd33"),b)).executes(c->setColor(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"color")))));
        party.then(Commands.literal("create").executes(c->create(c.getSource().getPlayerOrException())));
        party.then(Commands.literal("invite").then(Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(c->invite(c.getSource().getPlayerOrException(),net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player")))));
        party.then(Commands.literal("accept").executes(c->accept(c.getSource().getPlayerOrException())));
        party.then(Commands.literal("kick").then(Commands.argument("player",StringArgumentType.word()).suggests((c,b)->net.minecraft.commands.SharedSuggestionProvider.suggest(memberNames(c.getSource().getPlayerOrException()),b)).executes(c->kick(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"player")))));
        party.then(Commands.literal("leave").executes(c->leave(c.getSource().getPlayerOrException())));
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
    private static int scooterChams(ServerPlayer player,boolean enabled) {
        player.getPersistentData().putBoolean("GoplanskaScooterChamsOff",!enabled);sync(player.getServer());
        player.sendSystemMessage(Component.literal("Your scooter highlights: "+(enabled?"on":"off")));return 1;
    }
}
