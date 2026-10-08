package pl.aridlin.kukirin;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import java.util.ArrayList;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent.Post;

@EventBusSubscriber(
   modid = "goplanska_kukirin"
)
public final class ScooterRentalSpawner {
   private static RentalConfig.Settings config;
   private static long next;

   @SubscribeEvent
   public static void commands(RegisterCommandsEvent var0) {
      LiteralArgumentBuilder var1 = (LiteralArgumentBuilder)Commands.literal("scooterrental").requires(var0x -> var0x.hasPermission(2));

      for (int var2 = 0; var2 < 3; var2++) {
         int var3 = var2;
         var1.then(Commands.literal(new String[]{"lime", "bolt", "city"}[var2]).executes(var1x -> {
            ServerPlayer var2x = ((CommandSourceStack)var1x.getSource()).getPlayerOrException();
            return spawn(var2x.serverLevel(), var2x.blockPosition().relative(var2x.getDirection(), 3), var3, false) == null ? 0 : 1;
         }));
      }

      var1.then(Commands.literal("spawning").then(Commands.argument("enabled", BoolArgumentType.bool()).executes(var0x -> {
         boolean var1x = BoolArgumentType.getBool(var0x, "enabled");
         ScooterRental.CONFIG.setSpawning(var1x);
         config = ScooterRental.CONFIG.get();
         ((CommandSourceStack)var0x.getSource()).sendSuccess(() -> Component.literal("Rental spawning: " + var1x), true);
         return 1;
      })));
      var0.getDispatcher().register(var1);
   }

   @SubscribeEvent
   public static void tick(Post var0) {
      MinecraftServer var1 = var0.getServer();
      long var2 = var1.overworld().getGameTime();
      if (var2 % 100L == 0L || config == null) {
         config = ScooterRental.CONFIG.get();
      }

      if (config.spawning() && var2 >= next) {
         next = var2 + (long)config.interval();
         ServerLevel var4 = var1.overworld();
         ArrayList<Scooter> var5 = new ArrayList<>();

         for (Entity var7 : var4.getAllEntities()) {
            if (var7 instanceof Scooter) {
               Scooter var8 = (Scooter)var7;
               if (ScooterRental.isRental(var8)) {
                  var5.add(var8);
               }
            }
         }

         if (var5.size() < config.total()) {
            for (ServerPlayer var19 : var4.players()) {
               if (!var19.isSpectator() && var5.size() < config.total()) {
                  long var20 = var5.stream().filter(var1x -> var1x.distanceToSqr(var19) < 9216.0).count();
                  if (var20 < (long)config.perPlayer()) {
                     BlockPos var10 = null;
                     if (var4.random.nextDouble() < config.doorChance()) {
                        var10 = frontDoor(var4, var19.blockPosition());
                     }

                     for (int var11 = 0; var10 == null && var11 < 8; var11++) {
                        double var12 = var4.random.nextDouble() * Math.PI * 2.0;
                        double var14 = (double)(16 + var4.random.nextInt(33));
                        BlockPos var16 = var19.blockPosition().offset((int)(Math.sin(var12) * var14), 0, (int)(Math.cos(var12) * var14));
                        if (var4.hasChunkAt(var16)) {
                           BlockPos var17 = var4.getHeightmapPos(Types.MOTION_BLOCKING_NO_LEAVES, var16);
                           if (safe(var4, var17)) {
                              var10 = var17;
                           }
                        }
                     }

                     if (var10 != null) {
                        Scooter var21 = spawn(var4, var10, var4.random.nextInt(3), var4.random.nextDouble() < config.zombieChance());
                        if (var21 != null) {
                           var5.add(var21);
                        }
                     }
                  }
               }
            }
         }
      }
   }

   public static boolean safe(ServerLevel var0, BlockPos var1) {
      return var0.hasChunkAt(var1)
         && var0.getWorldBorder().isWithinBounds(var1)
         && var0.getBlockState(var1.below()).isFaceSturdy(var0, var1.below(), Direction.UP)
         && var0.getBlockState(var1).isAir()
         && var0.getBlockState(var1.above()).isAir()
         && var0.getFluidState(var1).isEmpty();
   }

   static BlockPos frontDoor(ServerLevel var0, BlockPos var1) {
      for (int var2 = -8; var2 <= 8; var2++) {
         for (int var3 = -8; var3 <= 8; var3++) {
            for (int var4 = -2; var4 <= 2; var4++) {
               BlockPos var5 = var1.offset(var2, var4, var3);
               if (var0.hasChunkAt(var5)
                  && var0.getBlockState(var5).getBlock() instanceof DoorBlock
                  && var0.getBlockState(var5).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                  Direction var6 = (Direction)var0.getBlockState(var5).getValue(DoorBlock.FACING);
                  BlockPos var7 = var5.relative(var6, 2);
                  if (safe(var0, var7)) {
                     return var7;
                  }
               }
            }
         }
      }

      return null;
   }

   public static Scooter spawn(ServerLevel var0, BlockPos var1, int var2, boolean var3) {
      if (!safe(var0, var1)) {
         return null;
      } else {
         Scooter var4 = (Scooter)((EntityType)Kukirin.SCOOTER.get()).create(var0);
         if (var4 == null) {
            return null;
         } else {
            var4.moveTo((double)var1.getX() + 0.5, (double)var1.getY(), (double)var1.getZ() + 0.5, var0.random.nextFloat() * 360.0F, 0.0F);
            ScooterRental.initialize(var4, var2);
            if (!var0.noCollision(var4)) {
               return null;
            } else if (!var0.addFreshEntity(var4)) {
               return null;
            } else {
               if (var3) {
                  ItemStack var5 = var4.getItemBySlot(EquipmentSlot.FEET).copy();
                  var5.enchant(
                     var0.registryAccess()
                        .lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.parse("minecraft:knockback"))),
                     1
                  );
                  var4.setItemSlot(EquipmentSlot.FEET, var5);
                  Zombie var6 = (Zombie)EntityType.ZOMBIE.create(var0);
                  if (var6 != null) {
                     var6.moveTo(var4.position());
                     var6.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
                     if (var0.addFreshEntity(var6) && !var6.startRiding(var4, true)) {
                        var6.discard();
                     }
                  }
               }

               return var4;
            }
         }
      }
   }
}
