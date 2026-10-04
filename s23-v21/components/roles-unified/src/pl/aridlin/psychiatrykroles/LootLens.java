package pl.aridlin.psychiatrykroles;
import net.minecraft.world.item.*;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
@EventBusSubscriber(modid="psychiatryk_roles",bus=EventBusSubscriber.Bus.MOD)
public final class LootLens {
 public static Item ITEM;
 @SubscribeEvent public static void register(net.neoforged.neoforge.registries.RegisterEvent e){e.register(Registries.ITEM,h->h.register(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles","loot_lens"),ITEM=new Item(new Item.Properties().stacksTo(1).rarity(Rarity.RARE))));}
 public static boolean active(LivingEntity player){if(player==null||!player.isShiftKeyDown()||ITEM==null)return false;var cap=io.wispforest.accessories.api.AccessoriesCapability.get(player);return cap!=null&&cap.isEquipped(ITEM);}
 public static int color(ItemStack stack){return switch(stack.getRarity()){case COMMON->0xffffffff;case UNCOMMON->0xffffff55;case RARE->0xff55ffff;case EPIC->0xffff55ff;default->{var rgb=stack.getRarity().color().getColor();yield rgb==null?0xffffffff:0xff000000|rgb;}};}
}
