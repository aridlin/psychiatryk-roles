package pl.aridlin.kukirin;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
@Mod("goplanska_kukirin")
public final class Kukirin {
 static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"goplanska_kukirin");
 static final DeferredRegister<net.minecraft.sounds.SoundEvent> SOUNDS=DeferredRegister.create(Registries.SOUND_EVENT,"goplanska_kukirin");
 public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> MOTOR=SOUNDS.register("motor",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse("goplanska_kukirin:motor")));
 public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> WIND=SOUNDS.register("wind",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse("goplanska_kukirin:wind")));
 public static final DeferredHolder<net.minecraft.sounds.SoundEvent,net.minecraft.sounds.SoundEvent> WAV=SOUNDS.register("wav",()->net.minecraft.sounds.SoundEvent.createVariableRangeEvent(net.minecraft.resources.ResourceLocation.parse("goplanska_kukirin:wav")));
 static final DeferredRegister<net.minecraft.world.item.crafting.RecipeSerializer<?>> RECIPES=DeferredRegister.create(Registries.RECIPE_SERIALIZER,"goplanska_kukirin");
 public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,net.minecraft.world.item.crafting.RecipeSerializer<ScooterDyeRecipe>> DYE=RECIPES.register("scooter_dye",ScooterDyeRecipe.Serializer::new);
 public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,net.minecraft.world.item.crafting.RecipeSerializer<ScooterUpgradeRecipe>> UPGRADE=RECIPES.register("scooter_upgrade",ScooterUpgradeRecipe.Serializer::new);
 public static final DeferredHolder<net.minecraft.world.item.crafting.RecipeSerializer<?>,net.minecraft.world.item.crafting.RecipeSerializer<BoundScooterRecipe>> BINDING=RECIPES.register("bound_scooter",BoundScooterRecipe.Serializer::new);
 static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("goplanska_kukirin");
 public static final DeferredBlock<net.minecraft.world.level.block.Block> DISMANTLER=BLOCKS.register("scooter_dismantler",()->new net.minecraft.world.level.block.Block(net.minecraft.world.level.block.state.BlockBehaviour.Properties.ofFullCopy(net.minecraft.world.level.block.Blocks.SMITHING_TABLE)){
  @Override protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack,net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.world.entity.player.Player player,InteractionHand hand,net.minecraft.world.phys.BlockHitResult hit){if(!stack.is(ITEM.get())||!ScooterEnchants.bound(stack))return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;if(!level.isClientSide&&player instanceof net.minecraft.server.level.ServerPlayer p){if(!p.getUUID().equals(ScooterEnchants.owner(stack)))return net.minecraft.world.ItemInteractionResult.FAIL;net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(p,new ScooterDismantler(pos,hand,false));}return net.minecraft.world.ItemInteractionResult.sidedSuccess(level.isClientSide);}
 });
 static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("goplanska_kukirin");
 public static final DeferredHolder<EntityType<?>,EntityType<Scooter>> SCOOTER=ENTITIES.register("scooter",()->EntityType.Builder.of(Scooter::new,MobCategory.MISC).sized(.8f,1.5f).clientTrackingRange(10).updateInterval(1).build("goplanska_kukirin:scooter"));
 public static final DeferredItem<Item> ITEM=ITEMS.register("kukirin_scooter",()->new Item(new Item.Properties().stacksTo(1)){
  @Override public int getEnchantmentValue(){return 15;}
  @Override public boolean isEnchantable(ItemStack stack){return true;}
  @Override public InteractionResult useOn(UseOnContext c){var level=c.getLevel();var pos=c.getClickedPos().relative(c.getClickedFace());if(level.isClientSide)return InteractionResult.SUCCESS;var scooter=SCOOTER.get().create(level);if(scooter==null)return InteractionResult.FAIL;scooter.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,c.getRotation(),0);if(!level.noCollision(scooter))return InteractionResult.FAIL;if(c.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)ScooterEnchants.bind(c.getItemInHand(),player);if(ScooterEnchants.bound(c.getItemInHand())&&c.getPlayer()!=null&&!c.getPlayer().getUUID().equals(ScooterEnchants.owner(c.getItemInHand())))return InteractionResult.FAIL;scooter.setItemSlot(EquipmentSlot.FEET,c.getItemInHand().copyWithCount(1));level.addFreshEntity(scooter);if(c.getPlayer()==null||!c.getPlayer().isCreative())c.getItemInHand().shrink(1);return InteractionResult.CONSUME;}
 });
 public static final DeferredItem<BlockItem> DISMANTLER_ITEM=ITEMS.registerSimpleBlockItem(DISMANTLER);
 public Kukirin(IEventBus bus){ENTITIES.register(bus);BLOCKS.register(bus);ITEMS.register(bus);RECIPES.register(bus);SOUNDS.register(bus);bus.addListener(ScooterAudioPacket::register);bus.addListener(ScooterBoost::register);bus.addListener(ScooterControl::register);bus.addListener(ScooterLunge::register);bus.addListener(ScooterStorageOpen::register);bus.addListener(ScooterDismantler::register);bus.addListener(this::attributes);bus.addListener(this::tab);}
 void attributes(EntityAttributeCreationEvent e){e.put(SCOOTER.get(),net.minecraft.world.entity.Mob.createMobAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.35).add(Attributes.STEP_HEIGHT,1.3).build());}
 void tab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){e.accept(ITEM);e.accept(DISMANTLER_ITEM);for(var preset:ScooterPresets.create(e.getParameters().holders()))e.accept(preset);}}
}
