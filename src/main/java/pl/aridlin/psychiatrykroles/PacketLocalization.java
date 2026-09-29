package pl.aridlin.psychiatrykroles;

import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelPromise;
import java.util.function.UnaryOperator;

final class PacketLocalization {
    static void install(ChannelPipeline pipeline, String name, UnaryOperator<Object> localize) {
        // Outbound runs tail-to-head. Before "encoder" would receive encoded bytes, too late.
        pipeline.addAfter("encoder", name, new ChannelDuplexHandler() {
            @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                super.write(context, localize.apply(message), promise);
            }
        });
    }
}
