package pl.aridlin.psychiatrykroles;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.level.BlockEvent;
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
        private float exitYaw;

        private TrackingPlayer(ServerLevel level) {
            super(level, new GameProfile(UUID.randomUUID(), "door-test"));
        }

        @Override public void teleportTo(ServerLevel level, double x, double y, double z, float yaw, float pitch) {
            teleportedLevel = level;
            exitYaw = yaw;
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
            level.setBlock(destination, level.getBlockState(destination).setValue(DoorBlock.OPEN, false), 2);
            level.setBlock(destination.above(), level.getBlockState(destination.above()).setValue(DoorBlock.OPEN, false), 2);
            helper.assertTrue(level.getBlockState(source).getValue(DoorBlock.OPEN)
                && !level.getBlockState(destination).getValue(DoorBlock.OPEN), "Only the entry door needs to be open");
            helper.assertTrue(level.getBlockState(source).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && level.getBlockState(source.above()).is(Blocks.DARK_OAK_DOOR)
                && level.getBlockState(source.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER
                && level.getBlockState(destination).getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER
                && level.getBlockState(destination.above()).is(Blocks.DARK_OAK_DOOR)
                && level.getBlockState(destination.above()).getValue(DoorBlock.HALF) == DoubleBlockHalf.UPPER,
                "Both test doors must retain their upper and lower halves");
            player.setPos(source.getX() - .25, source.getY() + .01, source.getZ() + .5);
            player.setYRot(-90); // Walk east, facing east.
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
            helper.assertTrue(player.exitYaw == Direction.WEST.toYRot(),
                "Forward crossing must face away from the west exit");

            var backwards = new TrackingPlayer(level);
            backwards.setYRot(90); // Walk east while looking west.
            backwards.setPos(source.getX() - .25, source.getY() + .01, source.getZ() + .5);
            var secondPass = new VoidDoors();
            secondPass.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, backwards));
            backwards.setPos(source.getX() + .35, source.getY() + .01, source.getZ() + .5);
            secondPass.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, backwards));
            helper.assertTrue(backwards.teleportedLevel == level
                && net.minecraft.util.Mth.wrapDegrees(backwards.exitYaw - Direction.EAST.toYRot()) == 0,
                "Backward crossing must face the door from the west exit");
            Vec3 plane = VoidDoorGeometry.center(source, Direction.EAST);
            helper.assertTrue(VoidDoorGeometry.touches(source, Direction.EAST,
                new AABB(plane.x - .01, plane.y + .5, plane.z - .01,
                    plane.x + .01, plane.y + .6, plane.z + .01)),
                "Any part of an entity hitbox touching the door plane must count");
            var item = new net.minecraft.world.entity.item.ItemEntity(level,
                plane.x, plane.y + .5, plane.z, new ItemStack(Items.DIAMOND));
            level.addFreshEntity(item);
            doors.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END,
                level, () -> true));
            helper.assertTrue(item.getX() > destination.getX() - 1,
                "Dropped items touching the door plane must teleport");
        } finally {
            data.removeDoor(dimension, source);
            data.removeDoor(dimension, destination);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void eitherDoorSynchronizesOpenAndClosed(GameTestHelper helper) {
        var level = helper.getLevel();
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos second = helper.absolutePos(new BlockPos(2, 1, 1));
        for (BlockPos pos : java.util.List.of(first, second)) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
            var lower = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(pos, lower, 2);
            level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        }
        var data = VoidDoorData.get(level.getServer());
        String dimension = level.dimension().location().toString();
        UUID pair = UUID.randomUUID();
        data.addDoor(pair, new VoidDoorData.DoorPosition(dimension, first));
        data.addDoor(pair, new VoidDoorData.DoorPosition(dimension, second));
        var doors = new VoidDoors();
        try {
            var tick = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, level.getServer());
            doors.onServerTick(tick);
            long closedVisuals = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class,
                new AABB(first).inflate(1, 2, 1), entity -> entity.getTags().contains("psychiatrykVoidPlane"))
                .size();
            helper.assertTrue(closedVisuals == 9,
                "Closed door must contain four black plane sections and five frame segments");
            level.setBlock(first.east(), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            doors.onServerTick(tick);
            long besideSlab = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class,
                new AABB(first).inflate(1, 2, 1), entity -> entity.getTags().contains("psychiatrykVoidPlane"))
                .size();
            helper.assertTrue(besideSlab == 9,
                "A bottom slab beside a tall door edge must leave its visible frame in place");
            level.setBlock(first.east(), Blocks.STONE.defaultBlockState(), 2);
            doors.onServerTick(tick);
            long framedBesideBlock = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class,
                new AABB(first).inflate(1, 2, 1), entity -> entity.getTags().contains("psychiatrykVoidPlane"))
                .size();
            helper.assertTrue(framedBesideBlock == 8,
                "A solid neighbor must hide only its adjacent lower frame segment");
            level.setBlock(first.east(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(first.above(2), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.BOTTOM), 2);
            doors.onServerTick(tick);
            long belowSlab = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class,
                new AABB(first).inflate(1, 2, 1), entity -> entity.getTags().contains("psychiatrykVoidPlane"))
                .size();
            helper.assertTrue(belowSlab == 8,
                "A bottom slab directly above the door must cover its top frame");
            level.setBlock(first.above(2), Blocks.AIR.defaultBlockState(), 2);
            for (BlockPos pos : java.util.List.of(second, second.above()))
                level.setBlock(pos, level.getBlockState(pos).setValue(DoorBlock.OPEN, true), 2);
            doors.onServerTick(tick);
            helper.assertTrue(level.getBlockState(first).getValue(DoorBlock.OPEN)
                && level.getBlockState(first.above()).getValue(DoorBlock.OPEN),
                "Opening either endpoint must open both halves of its partner");
            for (BlockPos pos : java.util.List.of(first, first.above()))
                level.setBlock(pos, level.getBlockState(pos).setValue(DoorBlock.OPEN, false), 2);
            doors.onServerTick(tick);
            helper.assertTrue(!level.getBlockState(second).getValue(DoorBlock.OPEN)
                && !level.getBlockState(second.above()).getValue(DoorBlock.OPEN),
                "Closing either endpoint must close both halves of its partner");
        } finally {
            data.removeDoor(dimension, first);
            data.removeDoor(dimension, second);
            doors.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, level.getServer()));
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
        helper.assertTrue(helper.getLevel().getRecipeManager()
            .byKey(new ResourceLocation("psychiatryk_roles", "void_trapdoor")).isPresent(),
            "The linked trapdoor recipe must load from the data pack");
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
        ItemStack trapdoorOutput = new ItemStack(Items.DARK_OAK_TRAPDOOR, 2);
        trapdoorOutput.getOrCreateTag().putBoolean("psychiatrykVoidTrapdoor", true);
        ShapedRecipe trapdoorRecipe = new ShapedRecipe(new ResourceLocation("psychiatryk_roles", "void_trapdoor"), "",
            CraftingBookCategory.MISC, 1, 1, NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.ENDER_PEARL)),
            trapdoorOutput);
        ItemStack trapdoorPair = trapdoorRecipe.assemble((net.minecraft.world.inventory.CraftingContainer) null,
            helper.getLevel().registryAccess());
        helper.assertTrue(VoidTrapdoors.pair(trapdoorPair) != null && trapdoorPair.getMaxStackSize() == 2,
            "Trapdoor recipe must assign a stackable linked pair during assembly");
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

    @GameTest(template = "empty")
    public static void codeFromAnvilUnlocksOnlyWithCorrectText(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var player = new TrackingPlayer(level);
        ItemStack pair = new ItemStack(Items.DARK_OAK_DOOR, 2);
        pair.getOrCreateTag().putBoolean("psychiatrykVoidDoor", true);
        VoidDoors.assignCraftedPair(pair);
        var anvil = new AnvilUpdateEvent(pair, ItemStack.EMPTY, "secret 123", 0, player);
        var doors = new VoidDoors();
        doors.onAnvilUpdate(anvil);
        ItemStack coded = anvil.getOutput();
        helper.assertTrue(!coded.isEmpty() && coded.getCount() == 2,
            "Anvil must return the complete linked pair");
        helper.assertTrue(!coded.getTag().toString().contains("secret 123"),
            "The plain code must not be kept on the item");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
        var lower = Blocks.DARK_OAK_DOOR.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
        level.setBlock(pos, lower, 2);
        level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
        var data = VoidDoorData.get(level.getServer());
        UUID id = VoidDoors.pair(coded);
        String dimension = level.dimension().location().toString();
        data.addDoor(id, new VoidDoorData.DoorPosition(dimension, pos));
        data.setCode(id, coded.getTag().getString("psychiatrykVoidDoorCode"));
        helper.assertTrue(data.code(id).equals(VoidDoorData.load(data.save(new CompoundTag())).code(id)),
            "The code digest must survive saving and loading the world");
        try {
            var wrong = new VoidDoorCodeMenu(1, player.getInventory(), doors, level, pos, id);
            wrong.setItemName("wrong");
            wrong.clicked(wrong.getResultSlot(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            helper.assertTrue(!level.getBlockState(pos).getValue(DoorBlock.OPEN),
                "Wrong code must keep the door closed");
            var correct = new VoidDoorCodeMenu(2, player.getInventory(), doors, level, pos, id);
            correct.setItemName("secret 123");
            correct.clicked(correct.getResultSlot(), 0, net.minecraft.world.inventory.ClickType.PICKUP, player);
            helper.assertTrue(level.getBlockState(pos).getValue(DoorBlock.OPEN),
                "Correct code must open the door");
            helper.assertTrue(player.getInventory().countItem(Items.PAPER) == 0,
                "The anvil prompt must never grant its paper placeholder");
            level.setBlock(pos, level.getBlockState(pos).setValue(DoorBlock.OPEN, false), 2);
            level.setBlock(pos.above(), level.getBlockState(pos.above()).setValue(DoorBlock.OPEN, false), 2);
            helper.assertTrue(doors.hasCodeSession(player, id),
                "Successful code entry must keep a 15-second session for that player");
            helper.assertTrue(!doors.hasCodeSession(new TrackingPlayer(level), id),
                "The code session must not unlock the pair for another player");
            var shifted = new VoidDoorCodeMenu(3, player.getInventory(), doors, level, pos, id);
            shifted.setItemName("secret 123");
            shifted.quickMoveStack(player, shifted.getResultSlot());
            helper.assertTrue(player.getInventory().countItem(Items.PAPER) == 0,
                "Shift-clicking code paper must not grant even a ghost server item");
        } finally {
            data.removeDoor(dimension, pos);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void trapdoorPairTeleportsAndKeepsItsLink(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos source = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos destination = helper.absolutePos(new BlockPos(2, 1, 1));
        var opened = Blocks.DARK_OAK_TRAPDOOR.defaultBlockState()
            .setValue(TrapDoorBlock.HALF, Half.BOTTOM).setValue(TrapDoorBlock.OPEN, true);
        level.setBlock(source, opened, 2);
        level.setBlock(destination, opened.setValue(TrapDoorBlock.OPEN, false)
            .setValue(TrapDoorBlock.FACING, Direction.SOUTH), 2);
        level.setBlock(destination.north(), Blocks.STONE.defaultBlockState(), 2);
        var data = VoidTrapdoorData.get(level.getServer());
        UUID id = UUID.randomUUID();
        String dimension = level.dimension().location().toString();
        data.addDoor(id, new VoidTrapdoorData.DoorPosition(dimension, source));
        data.addDoor(id, new VoidTrapdoorData.DoorPosition(dimension, destination));
        try {
            var drops = Block.getDrops(opened, level, source, null);
            helper.assertTrue(drops.size() == 1 && id.equals(VoidTrapdoors.pair(drops.get(0))),
                "Mined trapdoor must retain the linked pair ID");
            var traveler = new TrackingPlayer(level);
            traveler.setPos(source.getX() + .5, source.getY() + .1, source.getZ() + .5);
            var trapdoors = new VoidTrapdoors();
            var tick = new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, level.getServer());
            trapdoors.onServerTick(tick);
            long fullFrame = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class,
                new AABB(source).inflate(.5), entity -> entity.getTags().contains("psychiatrykVoidTrapdoorPlane"))
                .size();
            helper.assertTrue(fullFrame == 5, "Trapdoor must keep its plane and four exposed frame edges");
            level.setBlock(source.east(), Blocks.STONE_SLAB.defaultBlockState()
                .setValue(SlabBlock.TYPE, SlabType.TOP), 2);
            trapdoors.onServerTick(tick);
            long besideTopSlab = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class,
                new AABB(source).inflate(.5), entity -> entity.getTags().contains("psychiatrykVoidTrapdoorPlane"))
                .size();
            helper.assertTrue(besideTopSlab == 5,
                "A top slab cannot hide a bottom-half trapdoor frame");
            level.setBlock(source.east(), Blocks.STONE.defaultBlockState(), 2);
            trapdoors.onServerTick(tick);
            long partlyHiddenFrame = level.getEntitiesOfClass(net.minecraft.world.entity.Display.class,
                new AABB(source).inflate(.5), entity -> entity.getTags().contains("psychiatrykVoidTrapdoorPlane"))
                .size();
            helper.assertTrue(partlyHiddenFrame == 4,
                "A solid neighbor must suppress only the adjoining trapdoor frame edge");
            level.setBlock(source.east(), Blocks.AIR.defaultBlockState(), 2);
            level.setBlock(destination, level.getBlockState(destination).setValue(TrapDoorBlock.OPEN, false), 2);
            trapdoors.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, traveler));
            trapdoors.onPlayerTick(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, traveler));
            helper.assertTrue(traveler.teleportedLevel == level, "Open trapdoor must teleport to its linked partner");
            helper.assertTrue(traveler.getY() > destination.getY() + 1
                && traveler.getDeltaMovement().y > 0 && traveler.getDeltaMovement().z > 0,
                "Trapdoor exit must pop above its destination and away from the north support block");
            helper.assertTrue(level.getBlockState(destination).getValue(TrapDoorBlock.OPEN) == false,
                "Destination need not be open at the instant of contact");
            helper.assertTrue(VoidTrapdoors.touches(new Vec3(source.getX() + .5, source.getY() + .1,
                source.getZ() + .5), new AABB(source.getX() + .45, source.getY() + .08,
                source.getZ() + .45, source.getX() + .55, source.getY() + .12, source.getZ() + .55)),
                "Any part of the hitbox touching the plane must count");
            var item = new net.minecraft.world.entity.item.ItemEntity(level,
                source.getX() + .5, source.getY() + .1, source.getZ() + .5, new ItemStack(Items.DIAMOND));
            level.addFreshEntity(item);
            trapdoors.onLevelTick(new TickEvent.LevelTickEvent(LogicalSide.SERVER, TickEvent.Phase.END,
                level, () -> true));
            helper.assertTrue(item.getX() > destination.getX() - 1,
                "A dropped item touching the plane must also teleport");
        } finally {
            data.removeDoor(dimension, source);
            data.removeDoor(dimension, destination);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void shiftBreakingEitherPortalReturnsTheLinkedPair(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        String dimension = level.dimension().location().toString();
        var player = FakePlayerFactory.getMinecraft(level);
        player.setShiftKeyDown(true);
        var doors = new VoidDoors();
        var doorData = VoidDoorData.get(level.getServer());
        BlockPos first = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos second = helper.absolutePos(new BlockPos(2, 1, 1));
        UUID doorPair = UUID.randomUUID();
        for (BlockPos pos : java.util.List.of(first, second)) {
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), 2);
            var lower = Blocks.DARK_OAK_DOOR.defaultBlockState()
                .setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(pos, lower, 2);
            level.setBlock(pos.above(), lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), 2);
            doorData.addDoor(doorPair, new VoidDoorData.DoorPosition(dimension, pos));
        }
        String digest = VoidDoors.codeHash(doorPair, "the code");
        doorData.setCode(doorPair, digest);
        var doorBreak = new BlockEvent.BreakEvent(level, first, level.getBlockState(first), player);
        doors.onBreak(doorBreak);
        helper.assertTrue(doorBreak.isCanceled() && level.getBlockState(first).isAir()
            && level.getBlockState(first.above()).isAir() && level.getBlockState(second).isAir()
            && level.getBlockState(second.above()).isAir(),
            "Shift-breaking a door must remove both complete endpoints exactly once");
        boolean returnedDoors = player.getInventory().items.stream().anyMatch(stack ->
            stack.getCount() == 2 && doorPair.equals(VoidDoors.pair(stack))
                && digest.equals(stack.getTag().getString("psychiatrykVoidDoorCode")));
        helper.assertTrue(returnedDoors, "Both doors and their code must return as one linked pair");

        var trapdoors = new VoidTrapdoors();
        var trapdoorData = VoidTrapdoorData.get(level.getServer());
        BlockPos trapFirst = helper.absolutePos(new BlockPos(0, 1, 3));
        BlockPos trapSecond = helper.absolutePos(new BlockPos(2, 1, 3));
        UUID trapPair = UUID.randomUUID();
        for (BlockPos pos : java.util.List.of(trapFirst, trapSecond)) {
            level.setBlock(pos, Blocks.DARK_OAK_TRAPDOOR.defaultBlockState(), 2);
            trapdoorData.addDoor(trapPair, new VoidTrapdoorData.DoorPosition(dimension, pos));
        }
        var trapBreak = new BlockEvent.BreakEvent(level, trapSecond,
            level.getBlockState(trapSecond), player);
        trapdoors.onBreak(trapBreak);
        helper.assertTrue(trapBreak.isCanceled() && level.getBlockState(trapFirst).isAir()
            && level.getBlockState(trapSecond).isAir(),
            "Shift-breaking either trapdoor must remove its partner");
        helper.assertTrue(player.getInventory().items.stream().anyMatch(stack ->
            stack.getCount() == 2 && trapPair.equals(VoidTrapdoors.pair(stack))),
            "Both trapdoors must return as one linked pair");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void clearAirIsAValidFallbackForBothPortals(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos destination = helper.absolutePos(new BlockPos(1, 10, 1));
        for (BlockPos pos : BlockPos.betweenClosed(destination.offset(-3, -4, -3),
            destination.offset(3, 4, 3))) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2);
        var traveler = new TrackingPlayer(level);
        traveler.setPos(destination.getX() + .5, destination.getY() + .01, destination.getZ() + .5);
        Vec3 doorExit = VoidDoors.safeExit(level, traveler, destination, Direction.NORTH);
        helper.assertTrue(doorExit != null, "Door must use clear air when neither side has floor support");
        BlockPos doorFloor = BlockPos.containing(doorExit.x, doorExit.y - .1, doorExit.z);
        helper.assertTrue(level.getBlockState(doorFloor).isAir(), "Door fallback must genuinely lack support");
        Vec3 trapdoorExit = VoidTrapdoors.safeExit(level, traveler, destination, Direction.SOUTH);
        helper.assertTrue(trapdoorExit != null,
            "Trapdoor must use clear air when no supported candidate is available");
        helper.assertTrue(trapdoorExit.y > destination.getY() + 1
            && trapdoorExit.z > destination.getZ() + .5,
            "Trapdoor air fallback must remain above the hatch and away from its support side");
        BlockPos trapdoorFloor = BlockPos.containing(trapdoorExit.x, trapdoorExit.y - .1, trapdoorExit.z);
        helper.assertTrue(level.getBlockState(trapdoorFloor).isAir(),
            "Trapdoor fallback must genuinely lack support");
        helper.succeed();
    }
}
