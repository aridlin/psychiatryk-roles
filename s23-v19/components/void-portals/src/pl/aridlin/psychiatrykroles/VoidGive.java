package pl.aridlin.psychiatrykroles;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.world.item.*;
import net.minecraft.network.chat.Component;
@EventBusSubscriber(modid="psychiatryk_roles")
public final class VoidGive {
 @SubscribeEvent public static void register(RegisterCommandsEvent e){var root=Commands.literal("voidgive").requires(s->s.hasPermission(2));for(String kind:new String[]{"door","immersive_door","trapdoor","immersive_trapdoor"})root.then(Commands.literal(kind).executes(c->give(c.getSource(),c.getSource().getPlayerOrException(),kind)).then(Commands.argument("player",EntityArgument.player()).executes(c->give(c.getSource(),EntityArgument.getPlayer(c,"player"),kind))));e.getDispatcher().register(root);}
 static ItemStack pair(String kind,boolean english){boolean trap=kind.endsWith("trapdoor"),immersive=kind.startsWith("immersive");ItemStack stack=new ItemStack(trap?(immersive?Items.WARPED_TRAPDOOR:Items.DARK_OAK_TRAPDOOR):(immersive?Items.WARPED_DOOR:Items.DARK_OAK_DOOR),2);ItemTagCompat.putBoolean(stack,trap?"psychiatrykVoidTrapdoor":"psychiatrykVoidDoor",true);if(trap){VoidTrapdoors.assignCraftedPair(stack);VoidTrapdoors.localize(stack,english);}else{VoidDoors.assignCraftedPair(stack);VoidDoors.localize(stack,english);}return stack;}
 static int give(CommandSourceStack source,net.minecraft.server.level.ServerPlayer player,String kind){var stack=pair(kind,PsychiatrykRoles.isEnglish(player));if(!player.getInventory().add(stack))player.drop(stack,false);source.sendSuccess(()->Component.literal("Gave a linked "+kind+" pair to "+player.getGameProfile().getName()),true);return 2;}
}
