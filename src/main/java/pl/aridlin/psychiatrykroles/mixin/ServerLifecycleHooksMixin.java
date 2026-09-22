package pl.aridlin.psychiatrykroles.mixin;

import net.minecraftforge.server.ServerLifecycleHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import pl.aridlin.psychiatrykroles.LoginRejectionMessages;

@Mixin(value = ServerLifecycleHooks.class, remap = false)
abstract class ServerLifecycleHooksMixin {
    @ModifyArg(
        method = "handleServerLogin",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/server/ServerLifecycleHooks;rejectConnection(Lnet/minecraft/network/Connection;Lnet/minecraftforge/network/ConnectionType;Ljava/lang/String;)V"
        ),
        index = 2
    )
    private static String psychiatrykRoles$pointRejectedClientsToSetup(String original) {
        return LoginRejectionMessages.rewriteForgeLoginMessage(original);
    }
}
