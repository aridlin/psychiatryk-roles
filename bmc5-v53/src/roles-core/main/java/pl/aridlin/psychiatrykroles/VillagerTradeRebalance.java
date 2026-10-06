package pl.aridlin.psychiatrykroles;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.monster.ZombieVillager;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import pl.aridlin.psychiatrykroles.migration.mixin.MerchantStockAccessor;
import pl.aridlin.psychiatrykroles.migration.mixin.ZombieCureInvoker;

/** Never invokes listing factories, regenerates offers, reprices or resets stock. */
public final class VillagerTradeRebalance {
    static final String LIMITS="BmcWiesniukFiniteLimitsV1";
    public VillagerTradeRebalance(){net.neoforged.neoforge.common.NeoForge.EVENT_BUS.register(new Wiesniuk());}
    public static int bonusLimit(int vanilla) {
        if(vanilla<=0)return vanilla;
        return (int)Math.min(Integer.MAX_VALUE,((long)vanilla*5+3)/4);
    }
    static CompoundTag identity(Villager v,MerchantOffer offer) {
        var tag=(CompoundTag)MerchantOffer.CODEC.encodeStart(v.registryAccess().createSerializationContext(NbtOps.INSTANCE),offer).getOrThrow();
        tag.remove("uses");tag.remove("maxUses");tag.remove("specialPrice");tag.remove("demand");
        return tag;
    }
    public static void apply(Villager v,boolean special) {
        var offers=v.getOffers();var limits=v.getPersistentData().getList(LIMITS,Tag.TAG_COMPOUND);
        for(int i=0;i<offers.size();i++){
            MerchantOffer offer=offers.get(i);CompoundTag signature=identity(v,offer), entry=null;
            for(var tag:limits){var candidate=(CompoundTag)tag;if(candidate.getInt("index")==i&&candidate.getCompound("signature").equals(signature)){entry=candidate;break;}}
            if(entry==null){entry=new CompoundTag();entry.putInt("index",i);entry.put("signature",signature);entry.putInt("vanilla",offer.getMaxUses());limits.add(entry);}
            int max=special?bonusLimit(entry.getInt("vanilla")):entry.getInt("vanilla");
            ((MerchantStockAccessor)(Object)offer).bmc$setMaxUses(max);
        }
        v.getPersistentData().put(LIMITS,limits);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public void onVillagerOpen(PlayerInteractEvent.EntityInteract event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        if(event.getTarget() instanceof Villager villager){
            if(!villager.isTrading()||villager.getTradingPlayer()==p)apply(villager,Wiesniuk.is(p));
        }else if(event.getTarget() instanceof ZombieVillager zombie&&Wiesniuk.is(p)
            && event.getItemStack().is(Items.GOLDEN_APPLE)&&!zombie.isConverting()){
            ((ZombieCureInvoker)zombie).bmc$startConverting(p.getUUID(),zombie.getRandom().nextInt(2401)+3600);
            event.getItemStack().consume(1,p);
            event.setCanceled(true);event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
    /** Compatibility names for old helpers; migration keeps vanilla persistence authoritative. */
    public static void restoreSnapshot(Villager v) {}
    public static void saveSnapshot(Villager v) {}
}
