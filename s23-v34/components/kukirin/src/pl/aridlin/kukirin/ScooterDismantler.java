package pl.aridlin.kukirin;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
public record ScooterDismantler(BlockPos pos,InteractionHand hand,String action) implements CustomPacketPayload {
 public ScooterDismantler(BlockPos pos,InteractionHand hand,boolean confirm){this(pos,hand,confirm?"destroy":"open");}
 public static final Type<ScooterDismantler> TYPE=new Type<>(ResourceLocation.parse("goplanska_kukirin:dismantle"));
 public static final StreamCodec<RegistryFriendlyByteBuf,ScooterDismantler> CODEC=new StreamCodec<>(){public ScooterDismantler decode(RegistryFriendlyByteBuf b){return new ScooterDismantler(b.readBlockPos(),b.readBoolean()?InteractionHand.OFF_HAND:InteractionHand.MAIN_HAND,b.readUtf(32));}public void encode(RegistryFriendlyByteBuf b,ScooterDismantler p){b.writeBlockPos(p.pos());b.writeBoolean(p.hand()==InteractionHand.OFF_HAND);b.writeUtf(p.action(),32);}};
 public Type<ScooterDismantler> type(){return TYPE;}
 public record Result(ItemStack scooter,java.util.List<ItemStack> returned){}
 public static Result remove(ItemStack input,String action){
  var item=input.copy();var returned=new java.util.ArrayList<ItemStack>();
  if(action.equals("trim")){var trim=item.remove(DataComponents.TRIM);if(trim==null)return null;returned.add(new ItemStack(trim.pattern().value().templateItem().value()));returned.add(new ItemStack(trim.material().value().ingredient().value()));}
  else if(action.equals("dye")){var tag=item.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe();if(!tag.contains(ScooterDyeRecipe.COLOR))return null;returned.add(new ItemStack(net.minecraft.world.item.DyeItem.byColor(net.minecraft.world.item.DyeColor.byId(tag.getInt(ScooterDyeRecipe.COLOR)))));CustomData.update(DataComponents.CUSTOM_DATA,item,t->t.remove(ScooterDyeRecipe.COLOR));}
  else if(action.equals("enchants")){if(!item.isEnchanted())return null;item.remove(DataComponents.ENCHANTMENTS);}
  else if(action.equals("name")){if(!item.has(DataComponents.CUSTOM_NAME))return null;item.remove(DataComponents.CUSTOM_NAME);}
  else if(action.equals("star")){if(!ScooterEnchants.bound(item))return null;CustomData.update(DataComponents.CUSTOM_DATA,item,t->{t.remove(ScooterEnchants.BOUND);t.remove(ScooterEnchants.OWNER);});returned.add(new ItemStack(net.minecraft.world.item.Items.NETHER_STAR));}
  else if(java.util.Set.of("chest","jukebox","noteblock","netherite","saddle","infinite").contains(action)){
   if(!ScooterUpgradeRecipe.has(item,action))return null;
   if(action.equals("chest")){item.getOrDefault(DataComponents.CONTAINER,net.minecraft.world.item.component.ItemContainerContents.EMPTY).stream().filter(v->!v.isEmpty()).forEach(v->returned.add(v.copy()));item.remove(DataComponents.CONTAINER);}
   CustomData.update(DataComponents.CUSTOM_DATA,item,t->{t.remove(ScooterUpgradeRecipe.key(action));if(action.equals("jukebox"))t.remove("ScooterDisc");if(action.equals("saddle"))t.remove("GoplanskaScooterPassengersDisabled");});returned.add(new ItemStack(ScooterUpgradeRecipe.addition(action)));
  }else return null;
  return new Result(item,returned);
 }
 public static void register(net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent e){var r=e.registrar("2");r.playBidirectional(TYPE,CODEC,(data,ctx)->ctx.enqueueWork(()->{if(ctx.flow()==net.minecraft.network.protocol.PacketFlow.CLIENTBOUND){if(data.action().equals("open"))ScooterDismantlerClient.open(data);return;}var p=ctx.player();if(p.distanceToSqr(data.pos().getCenter())>36||!p.level().getBlockState(data.pos()).is(Kukirin.DISMANTLER.get()))return;var stack=p.getItemInHand(data.hand());if(!stack.is(Kukirin.ITEM.get())||stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe().getBoolean("GoplanskaRental")||(ScooterEnchants.bound(stack)&&ScooterEnchants.owner(stack)!=null&&!p.getUUID().equals(ScooterEnchants.owner(stack))))return;
   if(data.action().equals("destroy")){stack.shrink(1);p.displayClientMessage(net.minecraft.network.chat.Component.literal("Scooter dismantled."),false);return;}
   var result=remove(stack,data.action());if(result==null)return;
   if(data.action().equals("jukebox")){var disc=ItemStack.parseOptional(p.registryAccess(),stack.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).getUnsafe().getCompound("ScooterDisc"));if(!disc.isEmpty())p.getInventory().placeItemBackInInventory(disc);}
   p.setItemInHand(data.hand(),result.scooter());for(var returned:result.returned())p.getInventory().placeItemBackInInventory(returned);p.getInventory().setChanged();p.displayClientMessage(net.minecraft.network.chat.Component.literal("Removed "+data.action()+"; other scooter data preserved."),false);
  }));}
}
