package pl.aridlin.clientqa.mixin;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pl.aridlin.clientqa.CaptureProbe;
/** Count reaching the original slice allocation, after the candidate HEAD gate. */
@Pseudo
@Mixin(targets="dev.flashbackfix.compat.InboundPayloadCapture",remap=false,priority=500)
public abstract class CaptureProbeMixin {
 @Inject(method="capture",at=@At(value="INVOKE",target="Lio/netty/buffer/ByteBuf;slice(II)Lio/netty/buffer/ByteBuf;"),require=1)
 private static void qa$allocationPath(ConnectionProtocol protocol,CustomPacketPayload payload,ByteBuf buffer,int start,int end,CallbackInfo callback){CaptureProbe.slices++;}
}
