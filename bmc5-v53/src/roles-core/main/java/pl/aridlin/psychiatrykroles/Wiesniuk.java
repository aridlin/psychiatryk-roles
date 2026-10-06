package pl.aridlin.psychiatrykroles;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;

/** Only finite trading stock and vanilla golden-apple curing benefits. */
public final class Wiesniuk {
    static CompoundTag data(Player p) {
        var root=p.getPersistentData();
        if(!root.contains(Player.PERSISTED_NBT_TAG))root.put(Player.PERSISTED_NBT_TAG,new CompoundTag());
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
    public static boolean is(Player p) {
        return p.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getBoolean("GoplanskaWiesniuk");
    }
    static void set(ServerPlayer p,boolean active) {
        data(p).putBoolean("GoplanskaWiesniuk",active);
        data(p).putBoolean("GoplanskaWiesniukAssigned",true);
        p.refreshDisplayName();p.refreshTabListName();
    }
    @SubscribeEvent public void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e) {
        if(e.getEntity() instanceof ServerPlayer p && p.getGameProfile().getName().equalsIgnoreCase("SzybkiOrzech")
            && !data(p).getBoolean("GoplanskaWiesniukAssigned"))set(p,true);
    }
    @SubscribeEvent public void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e) {
        e.getDispatcher().register(net.minecraft.commands.Commands.literal("wiesniuk")
            .executes(c->{var p=c.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(is(p)
                ?"Wieśniuk: 25% more uses per vanilla offer; golden apples cure zombie villagers without Weakness."
                :"You are not Wieśniuk."));return 1;})
            .then(net.minecraft.commands.Commands.literal("add").requires(s->s.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player())
                    .executes(c->{set(net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"),true);return 1;})))
            .then(net.minecraft.commands.Commands.literal("remove").requires(s->s.hasPermission(2))
                .then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player())
                    .executes(c->{set(net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"),false);return 1;}))));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void tab(net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat e) {
        if(e.getEntity() instanceof ServerPlayer p&&is(p))e.setDisplayName(name(p));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public void name(net.neoforged.neoforge.event.entity.player.PlayerEvent.NameFormat e) {
        if(e.getEntity() instanceof ServerPlayer p&&is(p))e.setDisplayname(name(p));
    }
    static Component name(ServerPlayer p) {
        return Component.literal("[Wieśniuk] ").withStyle(net.minecraft.ChatFormatting.GOLD)
            .append(Component.literal(p.getGameProfile().getName()).withStyle(net.minecraft.ChatFormatting.WHITE));
    }
}
