package pl.aridlin.psychiatrykroles;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraft.commands.Commands;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("psychiatryk_roles")
@PrefixGameTestTemplate(false)
public final class VoidDoorGameTests {
    private static final class TrackingPlayer extends FakePlayer {
        private ServerLevel teleportedLevel;

        private TrackingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "door-test"));
        }

        @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            teleportedLevel = level;
            setPos(x, y, z);
        }
    }

    private static final class ChatProbe {
        private final net.minecraft.server.level.ServerPlayer player;
        private boolean sawChat;

        private ChatProbe(net.minecraft.server.level.ServerPlayer player) { this.player = player; }

        @SubscribeEvent public void onChat(ServerChatEvent event) {
            if (event.getPlayer() == player && event.getRawText().equals("hello players")) sawChat = true;
        }
    }

    @GameTest(template = "empty")
    public static void chatBookSendsChatAndRunsCommandsAsPlayer(GameTestHelper helper) {
        var player = FakePlayerFactory.getMinecraft(helper.getLevel());
        var commandRan = new java.util.concurrent.atomic.AtomicBoolean();
        helper.getLevel().getServer().getCommands().getDispatcher().register(
            Commands.literal("chatbookprobe").executes(context -> {
                commandRan.set(context.getSource().getEntity() == player);
                return 1;
            }));
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.getOrCreateTag().putBoolean("psychiatrykChatBook", true);
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf("hello players\n/chatbookprobe"));
        book.getOrCreateTag().put("pages", pages);
        var probe = new ChatProbe(player);
        MinecraftForge.EVENT_BUS.register(probe);
        try { ChatBook.send(player, book); }
        finally { MinecraftForge.EVENT_BUS.unregister(probe); }
        helper.assertTrue(probe.sawChat, "Chat Book must publish a player chat event");
        helper.assertTrue(commandRan.get(), "Book commands must run with the player's command source");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void crossingUsesClearSideOfLinkedDoor(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos destination = helper.absolutePos(new BlockPos(2, 1, 1));
        for (BlockPos door : java.util.List.of(source, destination)) {
            level.setBlock(door.below(), Blocks.STONE.defaultBlockState(), 2);
            var lower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER)
                .setValue(DoorBlock.FACING, Direction.EAST)
                .setValue(DoorBlock.OPEN, true);
            level.setBlock(door, lower, 2);
            level.setBlock(door.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        }
        // The preferred landing side is blocked, but the opposite side is clear.
        BlockPos blocked = destination.east();
        level.setBlock(blocked.below(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(blocked, Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(blocked.above(), Blocks.STONE.defaultBlockState(), 2);
        level.setBlock(destination.west().below(), Blocks.STONE.defaultBlockState(), 2);
        UUID pair = UUID.randomUUID();
        var data = VoidDoorData.get(level.getServer());
        String dimension = level.dimension().location().toString();
        data.removeDoor(dimension, source);
        data.removeDoor(dimension, destination);
        helper.assertTrue(data.addDoor(pair, new VoidDoorData.DoorPosition(dimension, source)),
            "Source door must be registered");
        helper.assertTrue(data.addDoor(pair, new VoidDoorData.DoorPosition(dimension, destination)),
            "Destination door must be registered");

        try {
            var player = new TrackingPlayer(level);
            var doors = new VoidDoors();
            helper.assertTrue(player.serverLevel() == level, "Test player must be in the door dimension");
            helper.assertTrue(!player.isSpectator() && !player.isPassenger() && !player.isSleeping() && player.isAlive(),
                "Test player must be eligible to teleport");
            helper.assertTrue(level.getBlockState(source).getValue(DoorBlock.OPEN)
                && level.getBlockState(destination).getValue(DoorBlock.OPEN), "Both test doors must be open");
            helper.assertTrue(level.getBlockState(source).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && level.getBlockState(source.above()).is(Blocks.DARK_OAK_DOOR)
                && level.getBlockState(source.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && level.getBlockState(destination).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && level.getBlockState(destination.above()).is(Blocks.DARK_OAK_DOOR)
                && level.getBlockState(destination.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER,
                "Both test doors must retain their upper and lower halves");
            player.setPos(source.getX() - .25, source.getY() + .01, source.getZ() + .5);
            Vec3 from = player.position();
            doors.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
            player.setPos(source.getX() + .35, source.getY() + .01, source.getZ() + .5);
            helper.assertTrue(VoidDoorGeometry.crossed(source, Direction.EAST, from, player.position(), player.getBbHeight()),
                "Test player must cross the source plane");
            helper.assertTrue(new VoidDoorData.DoorPosition(dimension, destination).equals(data.partner(dimension, source)),
                "Test doors must remain linked to this destination");
            Vec3 candidate = VoidDoors.safeExit(level, player, destination, Direction.EAST);
            helper.assertTrue(candidate != null, "Destination must have a safe landing");
            doors.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));

            helper.assertTrue(player.teleportedLevel == level, "Crossing must call Minecraft teleportation");
            helper.assertTrue(Math.abs(player.getX() - (destination.getX() - .5)) < .05,
                "Crossing must teleport to the clear side: actual x=" + player.getX()
                    + " expected=" + (destination.getX() - .5) + " candidate=" + candidate);
        } finally {
            data.removeDoor(dimension, source);
            data.removeDoor(dimension, destination);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void chatBookKeepsWrittenLinesAndCommands(GameTestHelper helper) {
        ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
        book.getOrCreateTag().putBoolean("psychiatrykChatBook", true);
        ListTag pages = new ListTag();
        pages.add(StringTag.valueOf("hello players\n/time query daytime"));
        book.getOrCreateTag().put("pages", pages);
        helper.assertTrue(ChatBook.lines(book).equals(java.util.List.of("hello players", "/time query daytime")),
            "Chat Book must read chat and command lines in order");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recipesAndStackLimits(GameTestHelper helper) {
        ItemStack output = new ItemStack(Items.DARK_OAK_DOOR, 2);
        output.getOrCreateTag().putBoolean("psychiatrykVoidDoor", true);
        ShapedRecipe recipe = new ShapedRecipe(new ResourceLocation("psychiatryk_roles", "void_door"), "",
            CraftingBookCategory.MISC, 1, 1, NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.ENDER_PEARL)), output);
        ItemStack first = recipe.assemble((net.minecraft.world.inventory.CraftingContainer) null, helper.getLevel().registryAccess());
        ItemStack second = recipe.assemble((net.minecraft.world.inventory.CraftingContainer) null, helper.getLevel().registryAccess());
        helper.assertTrue(VoidDoors.pair(first) != null, "Crafting must assign the pair before result extraction");
        helper.assertTrue(!VoidDoors.pair(first).equals(VoidDoors.pair(second)), "Each craft needs a fresh link");
        helper.assertTrue(first.getMaxStackSize() == 2, "Void Door stacks must be limited to two");
        helper.assertTrue(new ItemStack(Items.DARK_OAK_DOOR).getMaxStackSize() == 64, "Ordinary doors must remain unchanged");
        helper.assertTrue(!ItemStack.isSameItemSameTags(first, second), "Different pairs must not merge");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void miningPreservesPair(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 3);
        var lower = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(pos, lower, 2);
        level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        UUID pair = UUID.randomUUID();
        var data = VoidDoorData.get(level.getServer());
        String dimension = level.dimension().location().toString();
        data.addDoor(pair, new VoidDoorData.DoorPosition(dimension, pos));
        var drops = Block.getDrops(lower, level, pos, null);
        helper.assertTrue(drops.size() == 1, "Door must produce exactly one drop");
        helper.assertTrue(pair.equals(VoidDoors.pair(drops.get(0))), "Mined door must keep its saved pair NBT");
        helper.assertTrue(Block.getDrops(lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), level, pos.above(), null).isEmpty(),
            "Upper half must not duplicate the item");
        data.removeDoor(dimension, pos);
        helper.succeed();
    }
}
