package pl.aridlin.psychiatrykroles.peeb;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PeebPackets {
   private static Consumer<PeebStatePayload> clientReceiver = var0 -> {
   };
   private static Consumer<PeebConfigPayload> clientConfigReceiver = var0 -> {
   };

   private PeebPackets() {
   }

   public static void clientHandler(Consumer<PeebStatePayload> var0) {
      clientReceiver = Objects.requireNonNull(var0);
   }

   public static void clientConfigHandler(Consumer<PeebConfigPayload> var0) {
      clientConfigReceiver = Objects.requireNonNull(var0);
   }

   static void register(RegisterPayloadHandlersEvent var0) {
      PayloadRegistrar var1 = var0.registrar("4");
      var1.playToServer(PeebActionPayload.TYPE, PeebActionPayload.STREAM_CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2) {
               if (var0x.attach()) {
                  PeebGrapple.attach(var2, var0x.target());
               } else {
                  PeebGrapple.release(var2);
               }
            }
         }));
      var1.playToClient(PeebStatePayload.TYPE, PeebStatePayload.STREAM_CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> clientReceiver.accept(var0x)));
      var1.playToClient(PeebConfigPayload.TYPE, PeebConfigPayload.STREAM_CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var0x.values().valid()) {
               clientConfigReceiver.accept(var0x);
            }
         }));
      var1.playToServer(PeebConfigEditPayload.TYPE, PeebConfigEditPayload.STREAM_CODEC, (var0x, var1x) -> var1x.enqueueWork(() -> {
            if (var1x.player() instanceof ServerPlayer var2 && var2.hasPermissions(2)) {
               try {
                  if (var0x.action() == 0) {
                     PeebConfig.send(var2, true);
                  } else if (var0x.action() == 1 && var0x.values().valid()) {
                     PeebConfig.save(var0x.values());
                     PeebConfig.broadcast(var2.getServer());
                     PeebConfig.send(var2, true);
                     var2.displayClientMessage(Component.literal("Peeb settings saved and applied live."), false);
                  } else if (var0x.action() == 2) {
                     PeebConfig.reload();
                     PeebConfig.broadcast(var2.getServer());
                     PeebConfig.send(var2, true);
                     var2.displayClientMessage(Component.literal("Peeb settings reloaded."), false);
                  } else {
                     var2.displayClientMessage(Component.literal("Invalid Peeb settings; unchanged."), false);
                  }
               } catch (Exception var4) {
                  var2.displayClientMessage(Component.literal("Could not read or save Peeb settings; current values retained."), false);
               }

               return;
            }
         }));
   }

   public static void requestAttach(Vec3 var0) {
      PacketDistributor.sendToServer(new PeebActionPayload(true, var0.x, var0.y, var0.z), new CustomPacketPayload[0]);
   }

   public static void release() {
      PacketDistributor.sendToServer(new PeebActionPayload(false, 0.0, 0.0, 0.0), new CustomPacketPayload[0]);
   }

   public static void requestConfig() {
      PacketDistributor.sendToServer(new PeebConfigEditPayload(0, PeebConfig.DEFAULT), new CustomPacketPayload[0]);
   }

   public static void saveConfig(PeebConfig.Values var0) {
      PacketDistributor.sendToServer(new PeebConfigEditPayload(1, var0), new CustomPacketPayload[0]);
   }

   public static void reloadConfig() {
      PacketDistributor.sendToServer(new PeebConfigEditPayload(2, PeebConfig.DEFAULT), new CustomPacketPayload[0]);
   }
}
