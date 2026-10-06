package pl.aridlin.tradeqa;
import pl.aridlin.psychiatrykroles.VillagerTradeRebalance;

import java.nio.file.*;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.trading.*;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod("goplanska_trade_v32_qa")
public class TradeServerQA {
    private int ticks,checks;
    public TradeServerQA(){NeoForge.EVENT_BUS.addListener(this::tick);NeoForge.EVENT_BUS.addListener(this::stopped);}
    private void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("TRADE_SERVER_PASS "+label);}
    private void stopped(ServerStoppedEvent event){try{Files.writeString(Path.of("trade-qa-stopped.txt"),"ServerStopped after normal save/close\n");}catch(Exception e){throw new RuntimeException(e);}}
    private void tick(ServerTickEvent.Post event){
        if(++ticks!=20)return;
        Throwable failure=null;
        try{
            var world=event.getServer().overworld();var registries=world.registryAccess();var ops=registries.createSerializationContext(NbtOps.INSTANCE);
            var item=new ItemStack(Items.DIAMOND_SWORD);item.enchant(registries.lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.UNBREAKING),2);item.set(DataComponents.DAMAGE,37);
            var base=new MerchantOffer(new ItemCost(Items.EMERALD,21),Optional.empty(),item.copy(),0,3,30,.2f,0);
            var inflated=new MerchantOffer(new ItemCost(Items.EMERALD_BLOCK,14),Optional.empty(),item.copy(),2,6,30,0f,3);inflated.setSpecialPriceDiff(-1);
            var coal=new MerchantOffer(new ItemCost(Items.COAL,15),new ItemStack(Items.EMERALD),16,2,.05f);
            var villager=EntityType.VILLAGER.create(world);villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH).setLevel(5));var offers=new MerchantOffers();offers.add(inflated);offers.add(coal);villager.setOffers(offers);
            var baselines=new ListTag();baselines.add(MerchantOffer.CODEC.encodeStart(ops,base).getOrThrow());baselines.add(MerchantOffer.CODEC.encodeStart(ops,coal).getOrThrow());villager.getPersistentData().put("GoplanskaVanillaOffers",baselines);
            var player=FakePlayerFactory.get(world,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"TradeQA"));var role=new CompoundTag();player.getPersistentData().put(Player.PERSISTED_NBT_TAG,role);
            var constructor=VillagerTradeRebalance.class.getDeclaredConstructor();constructor.setAccessible(true);var adapter=constructor.newInstance();var open=new PlayerInteractEvent.EntityInteract(player,InteractionHand.MAIN_HAND,villager);
            adapter.onVillagerOpen(open);var normal=villager.getOffers().getFirst();
            check(normal.getBaseCostA().is(Items.EMERALD)&&normal.getBaseCostA().getCount()==21,"actual interaction restores vanilla base price");
            check(normal.getMaxUses()==3&&normal.getUses()==2&&normal.getDemand()==3&&normal.getSpecialPriceDiff()==-1,"normal stock/demand/discount preserved");
            check(normal.getResult().isEnchanted()&&ItemStack.isSameItemSameComponents(normal.getResult(),item),"registered enchantment and damaged item components preserved");
            check(villager.getOffers().size()==2&&villager.getVillagerData().getLevel()==5,"offer count and villager level retained");
            role.putBoolean("GoplanskaWiesniuk",true);adapter.onVillagerOpen(open);var unlimited=villager.getOffers().getFirst();check(unlimited.getMaxUses()==Integer.MAX_VALUE&&!unlimited.isOutOfStock(),"actual class interaction grants unlimited stock");
            for(int i=0;i<1000;i++)unlimited.increaseUses();check(unlimited.getUses()==2&&!unlimited.isOutOfStock(),"actual Mixin 1000 purchases keep ordinary stock intact");
            unlimited.updateDemand();check(unlimited.getDemand()==3,"actual Mixin prevents unlimited demand overflow");
            var restoredOffer=MerchantOffer.CODEC.parse(ops,MerchantOffer.CODEC.encodeStart(ops,unlimited).getOrThrow()).getOrThrow();restoredOffer.increaseUses();restoredOffer.updateDemand();check(restoredOffer.getUses()==2&&restoredOffer.getDemand()==3&&!restoredOffer.isOutOfStock(),"real registered-enchantment CODEC reload retains unlimited protections");
            role.putBoolean("GoplanskaWiesniuk",false);adapter.onVillagerOpen(open);normal=villager.getOffers().getFirst();check(normal.getMaxUses()==3&&normal.getUses()==2&&!normal.isOutOfStock(),"next normal customer retains one remaining trade");
            normal.increaseUses();check(normal.isOutOfStock(),"normal vanilla stock exhaustion untouched");normal.updateDemand();check(normal.getDemand()==6,"normal vanilla restock demand untouched");normal.resetUses();check(!normal.isOutOfStock(),"normal vanilla restock resets uses");
            var serialized=villager.saveWithoutId(new CompoundTag());check(serialized.getCompound("NeoForgeData").getList("GoplanskaOfferSnapshot",10).size()==2,"existing snapshot save hook still records both offers");var loaded=EntityType.VILLAGER.create(world);loaded.load(serialized);check(loaded.getOffers().size()==2&&loaded.getOffers().getFirst().getResult().isEnchanted(),"villager entity reload preserves offers and enchantments");loaded.getOffers().removeFirst();VillagerTradeRebalance.restoreSnapshot(loaded);check(loaded.getOffers().size()==2&&loaded.getOffers().stream().anyMatch(o->o.getResult().isEnchanted()),"missing-offer repair still restores existing enchanted trade");
            loaded.setVillagerData(loaded.getVillagerData().setProfession(VillagerProfession.FARMER));loaded.getOffers().clear();VillagerTradeRebalance.restoreSnapshot(loaded);check(loaded.getOffers().isEmpty(),"profession mismatch protects against obsolete offer restoration");
        }catch(Throwable error){failure=error;error.printStackTrace();}
        try{Files.writeString(Path.of("trade-qa-report.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of("success",failure==null,"checks",checks,"error",failure==null?"":failure.toString())));}catch(Exception error){error.printStackTrace();}
        System.out.println("TRADE_SERVER_QA "+(failure==null?"SUCCESS":"FAILURE")+" "+checks+" checks");event.getServer().halt(false);
    }
}
