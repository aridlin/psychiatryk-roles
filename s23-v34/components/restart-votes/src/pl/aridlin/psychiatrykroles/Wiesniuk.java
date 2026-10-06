package pl.aridlin.psychiatrykroles;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
public final class Wiesniuk {
 static CompoundTag data(Player p){var root=p.getPersistentData();if(!root.contains(Player.PERSISTED_NBT_TAG))root.put(Player.PERSISTED_NBT_TAG,new CompoundTag());return root.getCompound(Player.PERSISTED_NBT_TAG);}
 static boolean is(Player p){return data(p).getBoolean("GoplanskaWiesniuk");}
 static void set(ServerPlayer p,boolean active){data(p).putBoolean("GoplanskaWiesniuk",active);data(p).putBoolean("GoplanskaWiesniukAssigned",true);p.refreshDisplayName();p.refreshTabListName();}
 @SubscribeEvent public void login(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent e){if(e.getEntity() instanceof ServerPlayer p&&!data(p).getBoolean("GoplanskaWiesniukAssigned")&&p.getGameProfile().getName().equalsIgnoreCase("SzybkiOrzech"))set(p,true);}
 @SubscribeEvent public void commands(net.neoforged.neoforge.event.RegisterCommandsEvent e){e.getDispatcher().register(net.minecraft.commands.Commands.literal("wiesniuk").executes(c->{var p=c.getSource().getPlayerOrException();p.sendSystemMessage(Component.literal(is(p)?"Wieśniuk: vanilla prices, unlimited trading; poison from zombies; 50% healing; 1 iron / 30 minutes online.":"You are not Wieśniuk."));return 1;}).then(net.minecraft.commands.Commands.literal("add").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(c->{set(net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"),true);return 1;}))).then(net.minecraft.commands.Commands.literal("remove").requires(s->s.hasPermission(2)).then(net.minecraft.commands.Commands.argument("player",net.minecraft.commands.arguments.EntityArgument.player()).executes(c->{set(net.minecraft.commands.arguments.EntityArgument.getPlayer(c,"player"),false);return 1;}))));}
 @SubscribeEvent public void heal(net.neoforged.neoforge.event.entity.living.LivingHealEvent e){if(e.getEntity() instanceof ServerPlayer p&&is(p))e.setAmount(e.getAmount()*.5f);}
 @SubscribeEvent public void damage(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post e){if(e.getNewDamage()>0&&e.getEntity() instanceof ServerPlayer p&&is(p)&&e.getSource().getEntity() instanceof net.minecraft.world.entity.monster.Zombie)p.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON,100,0));}
 @SubscribeEvent public void tick(net.neoforged.neoforge.event.tick.PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p&&p.tickCount%20==0&&is(p)&&p.isAlive()){var d=data(p);int ticks=d.getInt("GoplanskaWiesniukIronTicks")+20;if(ticks>=36000){ticks-=36000;var item=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT);if(!p.getInventory().add(item))p.drop(item,false);p.sendSystemMessage(Component.literal("Wieśniuk: +1 iron ingot."));}d.putInt("GoplanskaWiesniukIronTicks",ticks);}}
 @SubscribeEvent(priority=EventPriority.LOWEST) public void tab(net.neoforged.neoforge.event.entity.player.PlayerEvent.TabListNameFormat e){if(e.getEntity() instanceof ServerPlayer p&&is(p))e.setDisplayName(name(p));}
 @SubscribeEvent(priority=EventPriority.LOWEST) public void name(net.neoforged.neoforge.event.entity.player.PlayerEvent.NameFormat e){if(e.getEntity() instanceof ServerPlayer p&&is(p))e.setDisplayname(name(p));}
 static Component name(ServerPlayer p){return Component.literal("[Wieśniuk] ").withStyle(net.minecraft.ChatFormatting.GOLD).append(Component.literal(p.getGameProfile().getName()).withStyle(net.minecraft.ChatFormatting.WHITE));}
}
