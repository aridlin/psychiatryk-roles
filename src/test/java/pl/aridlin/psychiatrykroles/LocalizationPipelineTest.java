package pl.aridlin.psychiatrykroles;

import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LocalizationPipelineTest {
    private record ItemPacket(String name) {}
    @Test void translationHappensBeforePacketEncoderConsumesTheObject() {
        EmbeddedChannel channel = new EmbeddedChannel();
        channel.pipeline().addLast("encoder", new MessageToMessageEncoder<ItemPacket>() {
            @Override protected void encode(ChannelHandlerContext context, ItemPacket packet, List<Object> output) {
                output.add(packet.name());
            }
        });
        PacketLocalization.install(channel.pipeline(), "localize", message -> message instanceof ItemPacket
            ? new ItemPacket("Drzwi Pustki") : message);
        ItemPacket original = new ItemPacket("Void Doors");
        assertTrue(channel.writeOutbound(original));
        assertEquals("Drzwi Pustki", channel.readOutbound());
        assertEquals("Void Doors", original.name());
        channel.finishAndReleaseAll();
    }
}
