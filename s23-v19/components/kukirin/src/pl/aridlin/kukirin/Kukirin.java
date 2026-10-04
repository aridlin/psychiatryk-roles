package pl.aridlin.kukirin;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.InteractionResult;
import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
@Mod("goplanska_kukirin")
public final class Kukirin {
 static final DeferredRegister<EntityType<?>> ENTITIES=DeferredRegister.create(Registries.ENTITY_TYPE,"goplanska_kukirin");
 static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("goplanska_kukirin");
 public static final DeferredHolder<EntityType<?>,EntityType<Scooter>> SCOOTER=ENTITIES.register("scooter",()->EntityType.Builder.of(Scooter::new,MobCategory.MISC).sized(.65f,1.1f).clientTrackingRange(10).updateInterval(1).build("goplanska_kukirin:scooter"));
 public static final DeferredItem<Item> ITEM=ITEMS.register("kukirin_scooter",()->new Item(new Item.Properties().stacksTo(1)){
  @Override public InteractionResult useOn(UseOnContext c){var level=c.getLevel();var pos=c.getClickedPos().relative(c.getClickedFace());if(level.isClientSide)return InteractionResult.SUCCESS;var scooter=SCOOTER.get().create(level);if(scooter==null)return InteractionResult.FAIL;scooter.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5,c.getRotation(),0);if(!level.noCollision(scooter))return InteractionResult.FAIL;level.addFreshEntity(scooter);if(c.getPlayer()==null||!c.getPlayer().isCreative())c.getItemInHand().shrink(1);return InteractionResult.CONSUME;}
 });
 public Kukirin(IEventBus bus){ENTITIES.register(bus);ITEMS.register(bus);bus.addListener(this::attributes);bus.addListener(this::tab);}
 void attributes(EntityAttributeCreationEvent e){e.put(SCOOTER.get(),Horse.createBaseHorseAttributes().add(Attributes.MAX_HEALTH,20).add(Attributes.MOVEMENT_SPEED,.35).add(Attributes.JUMP_STRENGTH,0).add(Attributes.STEP_HEIGHT,.6).build());}
 void tab(net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)e.accept(ITEM);}
}
