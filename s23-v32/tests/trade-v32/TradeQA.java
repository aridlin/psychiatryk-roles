import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.core.registries.*;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.*;
import net.minecraft.world.item.trading.*;

public class TradeQA {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    static MerchantOffer adapt(MerchantOffer base,MerchantOffer state,boolean special)throws Exception{
        var method=Class.forName("pl.aridlin.psychiatrykroles.VillagerTradeRebalance").getDeclaredMethod("forPlayer",MerchantOffer.class,MerchantOffer.class,boolean.class);method.setAccessible(true);return(MerchantOffer)method.invoke(null,base,state,special);
    }
    public static void main(String[] args)throws Exception{
        net.neoforged.fml.loading.LoadingModList.of(List.of(),List.of(),List.of(),List.of(),Map.of());net.minecraft.SharedConstants.tryDetectVersion();net.minecraft.server.Bootstrap.bootStrap();
        var result=new ItemStack(Items.DIAMOND_SWORD);result.set(DataComponents.CUSTOM_NAME,Component.literal("Existing enchanted weapon"));result.set(DataComponents.DAMAGE,22);
        var enchant=new Enchantment(Component.literal("Fixture enchant"),Enchantment.definition(HolderSet.direct(Items.DIAMOND_SWORD.builtInRegistryHolder()),1,3,Enchantment.constantCost(1),Enchantment.constantCost(10),1,EquipmentSlotGroup.MAINHAND),HolderSet.direct(),DataComponentMap.EMPTY);
        result.enchant(Holder.direct(enchant),3);
        var baseline=new MerchantOffer(new ItemCost(Items.EMERALD,21),Optional.of(new ItemCost(Items.BOOK,1)),result.copy(),0,3,30,.2f,0);
        check(TradeAgent.applied,"real MerchantOffer has exact compiled candidate handlers applied");
        var inflated=new MerchantOffer(new ItemCost(Items.EMERALD_BLOCK,14),Optional.of(new ItemCost(Items.BOOK,1)),result.copy(),2,6,30,0f,4);inflated.setSpecialPriceDiff(-2);
        var normal=adapt(baseline,inflated,false);
        check(normal.getBaseCostA().is(Items.EMERALD)&&normal.getBaseCostA().getCount()==21,"old emerald-block inflation replaced by stored vanilla price");
        check(normal.getCostB().is(Items.BOOK)&&normal.getCostB().getCount()==1,"second ingredient preserved");
        check(normal.getUses()==2&&normal.getMaxUses()==3,"normal stock returns to vanilla without resetting uses");
        check(normal.getDemand()==4&&normal.getSpecialPriceDiff()==-2&&normal.getPriceMultiplier()==.2f,"demand, gossip discount and vanilla multiplier preserved");
        check(ItemStack.isSameItemSameComponents(normal.getResult(),result)&&normal.getResult().isEnchanted()&&normal.getResult().getDamageValue()==22,"existing enchantment/components/count preserved");
        normal.increaseUses();check(normal.isOutOfStock(),"normal third use exhausts vanilla stock");normal.updateDemand();check(normal.getDemand()==7,"normal restock demand still follows vanilla calculation");normal.resetUses();check(!normal.isOutOfStock(),"normal resetUses restocks normally");
        var special=adapt(baseline,inflated,true);check(special.getMaxUses()==Integer.MAX_VALUE&&!special.isOutOfStock(),"Wiesniuk unlimited marker and available stock");
        for(int i=0;i<1000;i++)special.increaseUses();check(!special.isOutOfStock()&&special.getUses()==2,"1000 consecutive trades remain available without consuming ordinary stock");special.updateDemand();check(special.getDemand()==4,"unlimited restock avoids demand overflow and price distortion");
        var forced=special.copy();forced.setToOutOfStock();check(!forced.isOutOfStock(),"forced vanilla sold-out marker cannot limit unlimited offer");forced.increaseUses();check(forced.getUses()==Integer.MAX_VALUE,"unlimited purchases never change a preexisting uses counter");
        var back=adapt(baseline,special,false);check(back.getMaxUses()==3&&back.getUses()==2&&!back.isOutOfStock()&&back.getBaseCostA().getCount()==21,"switching back restores exact remaining normal stock and vanilla price");
        var coal=new MerchantOffer(new ItemCost(Items.COAL,15),new ItemStack(Items.EMERALD),16,2,.05f);var coalNormal=adapt(coal,coal,false);check(coalNormal.getBaseCostA().is(Items.COAL)&&coalNormal.getBaseCostA().getCount()==15&&coalNormal.getMaxUses()==16,"buying non-emerald commodities keeps vanilla price and amount");
        var offerList=new MerchantOffers();offerList.add(normal);offerList.add(special);int size=offerList.size();for(int i=0;i<size;i++)offerList.set(i,adapt(i==0?baseline:coal,offerList.get(i),i==1));check(offerList.size()==size,"adapting replaces entries without adding/removing offers");
        // Codec roundtrip without the fixture's unregistered direct enchant holder.
        var serializable=adapt(coal,coal,true);var ops=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY).createSerializationContext(NbtOps.INSTANCE);
        var encoded=MerchantOffer.CODEC.encodeStart(ops,serializable).getOrThrow();var restored=MerchantOffer.CODEC.parse(ops,encoded).getOrThrow();restored.updateDemand();check(restored.getMaxUses()==Integer.MAX_VALUE&&!restored.isOutOfStock()&&restored.getDemand()==0,"unlimited saved offer remains protected after actual codec reload");
        System.out.println("TRADE QA SUCCESS "+checks+" real MerchantOffer checks; no world/game/server launched.");
    }
}
