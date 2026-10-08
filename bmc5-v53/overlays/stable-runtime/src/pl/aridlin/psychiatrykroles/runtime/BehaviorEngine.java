package pl.aridlin.psychiatrykroles.runtime;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;

/** Server-authored behavior graph. Client packets only select already authorized controls. */
public final class BehaviorEngine {
    public static boolean matches(Schema.Behavior behavior,ServerPlayer p){
        if(behavior==null||!p.hasPermissions(behavior.permission()))return false;
        for(String condition:behavior.conditions()){
            boolean yes=switch(condition){
                case "sneaking"->p.isShiftKeyDown();
                case "not_sneaking"->!p.isShiftKeyDown();
                case "creative"->p.isCreative();
                case "survival"->!p.isCreative()&&!p.isSpectator();
                case "on_ground"->p.onGround();
                case "in_water"->p.isInWater();
                default->condition.startsWith("dimension:")&&p.level().dimension().location().toString().equals(condition.substring(10));
            };
            if(!yes)return false;
        }
        return true;
    }
    public static boolean run(Schema.Behavior behavior,ServerPlayer p){
        if(!matches(behavior,p))return false;
        for(var step:behavior.steps()){
            String target=step.target();
            switch(step.op()){
                case "message"->p.sendSystemMessage(Component.literal(target));
                case "command"->p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(),target);
                case "function"->p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"function "+target);
                case "effect"->p.addEffect(new MobEffectInstance(BuiltInRegistries.MOB_EFFECT.getHolderOrThrow(ResourceKey.create(Registries.MOB_EFFECT,ResourceLocation.parse(target))),(int)(step.amount()*20),0));
                case "sound"->p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"playsound "+target+" master @s ~ ~ ~ 1 1");
                case "particle"->p.getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack().withPermission(2),"particle "+target+" ~ ~1 ~ 0 0 0 0 1 force @s");
                case "velocity"->{p.setDeltaMovement(p.getLookAngle().scale(step.amount()));p.hurtMarked=true;}
                case "open"->RuntimeServer.open(p,target);
                case "give"->{var stack=new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(target)),Math.max(1,Math.min(64,(int)step.amount())));if(!p.addItem(stack))p.drop(stack,false);}
                default->throw new IllegalStateException("Unchecked behavior step");
            }
        }
        return true;
    }
}
