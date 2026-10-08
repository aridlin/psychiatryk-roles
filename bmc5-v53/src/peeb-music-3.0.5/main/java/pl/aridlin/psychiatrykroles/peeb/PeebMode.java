package pl.aridlin.psychiatrykroles.peeb;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Equipable;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult.Type;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredRegister.Items;
import pl.aridlin.kukirin.MusicCategoryConfig;

@Mod("psychiatryk_peeb")
public final class PeebMode {
   public static final String MOD_ID = "psychiatryk_peeb";
   public static final double MAX_ANCHOR_DISTANCE = 8.0;
   private static final Items ITEMS = DeferredRegister.createItems("psychiatryk_peeb");
   private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, "psychiatryk_peeb");
   public static final DeferredItem<Item> PEEB = ITEMS.register("peeb", () -> new PeebMode.PeebItem(new Properties().stacksTo(1)));
   public static final DeferredHolder<SoundEvent, SoundEvent> STEP = sound("step");
   public static final DeferredHolder<SoundEvent, SoundEvent> JUMP = sound("jump");
   public static final DeferredHolder<SoundEvent, SoundEvent> LAND = sound("land");
   public static final DeferredHolder<SoundEvent, SoundEvent> GRAPPLE_BEGIN = sound("grapple_begin");
   public static final DeferredHolder<SoundEvent, SoundEvent> GRAPPLE_HOLD = sound("grapple_hold");
   public static final DeferredHolder<SoundEvent, SoundEvent> GRAPPLE_END = sound("grapple_end");

   public PeebMode(IEventBus var1) {
      ITEMS.register(var1);
      SOUNDS.register(var1);
      var1.addListener(PeebPackets::register);
      var1.addListener(MusicCategoryConfig::register);
      var1.addListener(PeebMode::creative);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::tick);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::login);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::logout);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::dimension);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::respawn);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::tracking);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::death);
      NeoForge.EVENT_BUS.addListener(PeebGrapple::stopped);
      NeoForge.EVENT_BUS.addListener(PeebConfig::started);
      NeoForge.EVENT_BUS.addListener(PeebConfig::tick);
      NeoForge.EVENT_BUS.addListener(PeebConfig::fall);
      NeoForge.EVENT_BUS.addListener(PeebEquipment::tick);
      NeoForge.EVENT_BUS.addListener(MusicCategoryConfig::started);
      NeoForge.EVENT_BUS.addListener(MusicCategoryConfig::login);
      NeoForge.EVENT_BUS.addListener(MusicCategoryConfig::tick);
   }

   private static DeferredHolder<SoundEvent, SoundEvent> sound(String var0) {
      return SOUNDS.register(var0, () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("psychiatryk_peeb", var0)));
   }

   public static boolean holding(LivingEntity var0) {
      return var0 != null
         && otherArmorEmpty(var0)
         && (var0.getItemBySlot(EquipmentSlot.CHEST).isEmpty() || worn(var0))
         && (worn(var0) || var0.getMainHandItem().is((Item)PEEB.get()) || var0.getOffhandItem().is((Item)PEEB.get()));
   }

   public static boolean worn(LivingEntity var0) {
      return var0 != null && var0.getItemBySlot(EquipmentSlot.CHEST).is((Item)PEEB.get());
   }

   public static boolean otherArmorEmpty(LivingEntity var0) {
      return var0 != null
         && var0.getItemBySlot(EquipmentSlot.HEAD).isEmpty()
         && var0.getItemBySlot(EquipmentSlot.LEGS).isEmpty()
         && var0.getItemBySlot(EquipmentSlot.FEET).isEmpty();
   }

   private static void creative(BuildCreativeModeTabContentsEvent var0) {
      if (var0.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
         var0.accept(PEEB);
      }
   }

   private static final class PeebItem extends Item implements Equipable {
      private PeebItem(Properties var1) {
         super(var1);
      }

      public EquipmentSlot getEquipmentSlot() {
         return EquipmentSlot.CHEST;
      }

      public boolean canEquip(ItemStack var1, EquipmentSlot var2, LivingEntity var3) {
         return var2 == EquipmentSlot.CHEST && PeebMode.otherArmorEmpty(var3);
      }

      public void appendHoverText(ItemStack var1, TooltipContext var2, List<Component> var3, TooltipFlag var4) {
         var3.add(Component.translatable("tooltip.psychiatryk_peeb.wear").withStyle(ChatFormatting.GRAY));
         var3.add(Component.translatable("tooltip.psychiatryk_peeb.armor").withStyle(ChatFormatting.GRAY));
         var3.add(Component.translatable("tooltip.psychiatryk_peeb.controls").withStyle(ChatFormatting.GRAY));
      }

      private InteractionResultHolder<ItemStack> equip(Level var1, Player var2, InteractionHand var3) {
         if (!PeebMode.otherArmorEmpty(var2)) {
            if (!var1.isClientSide) {
               var2.displayClientMessage(Component.translatable("message.psychiatryk_peeb.remove_armor"), true);
            }

            return InteractionResultHolder.fail(var2.getItemInHand(var3));
         } else {
            return this.swapWithEquipmentSlot(this, var1, var2, var3);
         }
      }

      public InteractionResultHolder<ItemStack> use(Level var1, Player var2, InteractionHand var3) {
         if (var2.isShiftKeyDown()) {
            return this.equip(var1, var2, var3);
         } else {
            if (var2 instanceof ServerPlayer var4) {
               if (PeebGrapple.snapshot(var4).anchor().isPresent()) {
                  PeebGrapple.release(var4);
               } else {
                  BlockHitResult var5 = PeebGrapple.raycast(var4);
                  if (var5.getType() == Type.BLOCK) {
                     PeebGrapple.attach(var4, var5.getLocation());
                  }
               }
            }

            return InteractionResultHolder.sidedSuccess(var2.getItemInHand(var3), var1.isClientSide);
         }
      }

      public InteractionResult useOn(UseOnContext var1) {
         if (var1.getPlayer() != null && var1.getPlayer().isShiftKeyDown()) {
            return this.equip(var1.getLevel(), var1.getPlayer(), var1.getHand()).getResult();
         } else {
            if (var1.getPlayer() instanceof ServerPlayer var2) {
               if (PeebGrapple.snapshot(var2).anchor().isPresent()) {
                  PeebGrapple.release(var2);
               } else {
                  PeebGrapple.attach(var2, var1.getClickLocation());
               }
            }

            return InteractionResult.sidedSuccess(var1.getLevel().isClientSide);
         }
      }
   }
}
