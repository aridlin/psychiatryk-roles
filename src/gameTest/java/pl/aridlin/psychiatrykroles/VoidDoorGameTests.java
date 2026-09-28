package pl.aridlin.psychiatrykroles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import java.util.UUID;

@GameTestHolder("psychiatryk_roles")
@PrefixGameTestTemplate(false)
public final class VoidDoorGameTests {
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
