package pl.aridlin.psychiatrykroles.migration;

import java.util.Set;
import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** New-world survival policy. Server-side gates complement recipe/JEI removal. */
public final class MigrationPolicy {
    private static final Set<String> OUR_NAMESPACES=Set.of("psychiatryk_roles","goplanska_kukirin","goplanska_starter");
    private static final Set<String> ALLOWED=Set.of("psychiatryk_roles:return_mirror","psychiatryk_roles:loot_lens","psychiatryk_roles:dragon_egg_shards","goplanska_kukirin:kukirin_scooter","psychiatryk_roles:void_door","psychiatryk_roles:void_trapdoor");
    private static final Set<String> STATIONS=Set.of("oceanography_table","woodworking_table","decayed_workbench","purpur_altar","blueprint_table","gardening_table","hunting_post","mining_bench","gilded_station","chiller");
    public static boolean station(ResourceLocation id){return id!=null&&id.getNamespace().equals("morevillagers")&&STATIONS.contains(id.getPath());}
    public static boolean station(ItemStack stack){return !stack.isEmpty()&&station(BuiltInRegistries.ITEM.getKey(stack.getItem()));}
    public static boolean allowRecipe(ResourceLocation id,JsonElement json) {
        if(OUR_NAMESPACES.contains(id.getNamespace())&&!ALLOWED.contains(id.toString()))return false;
        if(json.isJsonObject()) {
            var result=json.getAsJsonObject().get("result");
            if(result!=null) {
                String output=null;
                if(result.isJsonPrimitive()&&result.getAsJsonPrimitive().isString())output=result.getAsString();
                else if(result.isJsonObject())for(String key:new String[]{"id","item"})if(result.getAsJsonObject().has(key)){output=result.getAsJsonObject().get(key).getAsString();break;}
                if(output!=null&&station(ResourceLocation.tryParse(output)))return false;
            }
        }
        return true;
    }
    @SubscribeEvent public void blockUse(PlayerInteractEvent.RightClickBlock event) {
        if(!(event.getEntity() instanceof ServerPlayer p)||p.isCreative())return;
        var blockId=BuiltInRegistries.BLOCK.getKey(event.getLevel().getBlockState(event.getPos()).getBlock());
        if(station(event.getItemStack())||station(blockId)) {
            event.setCanceled(true);event.setCancellationResult(InteractionResult.FAIL);
            p.displayClientMessage(Component.literal("These villager workstations are disabled for this season."),true);
        }
    }
    @SubscribeEvent public void blockStationPickup(net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Pre event) {
        if(event.getPlayer() instanceof ServerPlayer p&&!p.isCreative()&&station(event.getItemEntity().getItem()))
            event.setCanPickup(net.neoforged.neoforge.common.util.TriState.FALSE);
    }
    @SubscribeEvent public void crafted(PlayerEvent.ItemCraftedEvent event) {
        if(!(event.getEntity() instanceof ServerPlayer p))return;
        var stack=event.getCrafting();
        if(stack.is(pl.aridlin.kukirin.Kukirin.ITEM.get())&&pl.aridlin.kukirin.ScooterEnchants.bound(stack))
            pl.aridlin.kukirin.ScooterEnchants.bind(stack,p);
        var custom=stack.get(DataComponents.CUSTOM_DATA);
        if(custom!=null&&custom.copyTag().getBoolean("psychiatrykReturnMirror")) {
            stack.set(DataComponents.CUSTOM_NAME,Component.literal("Mirror of Returning").withStyle(net.minecraft.ChatFormatting.AQUA));
        }
    }
}
