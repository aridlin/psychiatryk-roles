package pl.aridlin.psychiatrykroles;
import java.util.*;
import net.minecraft.world.item.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
public final class CustomPresets {
 public static final List<String> EXTRA=List.of("chat-book","recipe-book","poker-menu","welcome-book","void-door","void-trapdoor","immersive-void-door","immersive-void-trapdoor","kinker-starter","kukirin-scooter","marking-torch","loot-lens","phase-charm","spyglass");
 public static List<String> extend(List<String> old){var all=new ArrayList<>(old);for(var s:EXTRA)if(!all.contains(s))all.add(s);return List.copyOf(all);}
 public static List<String> extendSpyglass(List<String> old){if(old.contains("spyglass"))return old;var all=new ArrayList<>(old);all.add("spyglass");return List.copyOf(all);}
 public static ItemStack spyglassExtra(String name,ServerPlayer player,long expires){return "spyglass".equalsIgnoreCase(name)?extra(name,player,expires):null;}
 public static ItemStack extra(String name,ServerPlayer player,long expires){name=name.toLowerCase(Locale.ROOT);if(!EXTRA.contains(name))return null;ItemStack stack;
 switch(name){
 case "void-door"->stack=VoidGive.pair("door",PsychiatrykRoles.isEnglish(player));
 case "void-trapdoor"->stack=VoidGive.pair("trapdoor",PsychiatrykRoles.isEnglish(player));
 case "immersive-void-door"->stack=VoidGive.pair("immersive_door",PsychiatrykRoles.isEnglish(player));
 case "immersive-void-trapdoor"->stack=VoidGive.pair("immersive_trapdoor",PsychiatrykRoles.isEnglish(player));
 case "kinker-starter"->stack=new ItemStack(pl.aridlin.starter.KinkerStarter.STARTER.get());
 case "kukirin-scooter"->stack=new ItemStack(pl.aridlin.kukirin.Kukirin.ITEM.get());
 case "marking-torch"->stack=new ItemStack(MarkingTorches.ITEM,2);
 case "phase-charm"->stack=new ItemStack(PhaseCharm.ITEM);
 case "loot-lens"->stack=new ItemStack(LootLens.ITEM);
 case "spyglass"->stack=new ItemStack(Items.SPYGLASS);
 case "welcome-book"->{try{var m=PsychiatrykRoles.class.getDeclaredMethod("makeWelcomeBook",net.minecraft.world.entity.player.Player.class);m.setAccessible(true);stack=(ItemStack)m.invoke(null,player);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
 default->{var recipe=player.getServer().getRecipeManager().byKey(ResourceLocation.fromNamespaceAndPath("psychiatryk_roles",name.replace('-','_')));stack=recipe.map(r->r.value().getResultItem(player.registryAccess()).copy()).orElse(ItemStack.EMPTY);}
 }
 if(!stack.isEmpty()){ItemTagCompat.putBoolean(stack,"psychiatrykConsultantItem",true);if(name.equals("spyglass"))ItemTagCompat.putString(stack,"psychiatrykConsultantAction","spyglass");if(expires>0)ItemTagCompat.putLong(stack,"psychiatrykConsultantExpiresAt",expires);if(name.equals("chat-book"))ChatBook.localize(stack,PsychiatrykRoles.isEnglish(player));}
 return stack;
 }
 public static boolean isConsultantSpyglass(ItemStack stack){return stack.is(Items.SPYGLASS)&&ItemTagCompat.has(stack)&&ItemTagCompat.read(stack).getBoolean("psychiatrykConsultantItem")&&"spyglass".equals(ItemTagCompat.read(stack).getString("psychiatrykConsultantAction"));}
}
