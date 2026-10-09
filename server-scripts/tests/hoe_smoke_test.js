// Isolated fixture only. Never deploy to the public server.
var TestTick = Java.loadClass('net.neoforged.neoforge.event.tick.ServerTickEvent$Post');
var TestNeoForge = Java.loadClass('net.neoforged.neoforge.common.NeoForge');
var TestFakePlayers = Java.loadClass('net.neoforged.neoforge.common.util.FakePlayerFactory');
var TestItems = Java.loadClass('net.minecraft.world.item.Items');
var TestStack = Java.loadClass('net.minecraft.world.item.ItemStack');
var TestInteger = Java.loadClass('java.lang.Integer');
var TestBlocks = Java.loadClass('net.minecraft.world.level.block.Blocks');
var TestCrop = Java.loadClass('net.minecraft.world.level.block.CropBlock');
var TestPos = Java.loadClass('net.minecraft.core.BlockPos');
var TestDir = Java.loadClass('net.minecraft.core.Direction');
var TestHand = Java.loadClass('net.minecraft.world.InteractionHand');
var TestHit = Java.loadClass('net.minecraft.world.phys.BlockHitResult');
var TestVec = Java.loadClass('net.minecraft.world.phys.Vec3');
var TestClick = Java.loadClass('net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$RightClickBlock');
var TestItemEntity = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');
var TestEntityJoin = Java.loadClass('net.neoforged.neoforge.event.entity.EntityJoinLevelEvent');
var TestPriority = Java.loadClass('net.neoforged.bus.api.EventPriority');

var hoeTestTick = 0;
var hoeTestPlayer = null;
var hoeTestLevel = null;
var hoeTestOverflow = false;
var hoeTestDroppedCount = 0;
NativeEvents.onEvent(TestPriority.LOWEST, TestEntityJoin, event => {
  if (hoeTestOverflow && event.getEntity() instanceof TestItemEntity)
    hoeTestDroppedCount += event.getEntity().getItem().getCount();
});
function hoeTestPos(x, z) { return new TestPos(x, 70, z); }
function hoeTestBlockId(state) { return String(state.getBlock().builtInRegistryHolder().key().location()); }
function hoeTestInventoryCount(player, id) {
  var inventory = player.getInventory();
  var count = 0;
  for (var slot = 0; slot < inventory.getContainerSize(); slot++) {
    var stack = inventory.getItem(slot);
    if (!stack.isEmpty() && String(stack.getItem().builtInRegistryHolder().key().location()) === id)
      count += stack.getCount();
  }
  return count;
}
function hoeTestClick(player, pos) {
  var hit = new TestHit(TestVec.atCenterOf(pos), TestDir.UP, pos, false);
  TestNeoForge.EVENT_BUS.post(new TestClick(player, TestHand.MAIN_HAND, pos, hit));
}
NativeEvents.onEvent(TestTick, event => {
  hoeTestTick++;
  if (hoeTestTick === 30) {
    try {
      hoeTestLevel = event.getServer().overworld();
      console.info('HOE_TEST_DIMENSION key=' + String(hoeTestLevel.dimension)
        + ' locationMember=' + String(hoeTestLevel.dimension.location)
        + ' locationType=' + typeof hoeTestLevel.dimension.location);
      hoeTestPlayer = TestFakePlayers.getMinecraft(hoeTestLevel);
      hoeTestPlayer.getInventory().selected = 0;
      hoeTestPlayer.getInventory().setItem(0, new TestStack(TestItems.DIAMOND_HOE));
      hoeTestPlayer.setPos(0.5, 72, 0.5);
      for (var x = 14; x <= 29; x++) {
        for (var z = -7; z <= 8; z++) hoeTestLevel.setBlock(hoeTestPos(x, z), TestBlocks.DIRT.defaultBlockState(), 3);
      }
      hoeTestLevel.setBlock(hoeTestPos(21, 1), TestBlocks.WATER.defaultBlockState(), 3);
      hoeTestLevel.setBlock(hoeTestPos(0, 0), TestBlocks.FARMLAND.defaultBlockState(), 3);
      hoeTestLevel.setBlock(hoeTestPos(15, 15), TestBlocks.FARMLAND.defaultBlockState(), 3);
      hoeTestLevel.setBlock(hoeTestPos(17, 17), TestBlocks.FARMLAND.defaultBlockState(), 3);
      var ripe = TestBlocks.WHEAT.defaultBlockState().setValue(TestCrop.AGE, TestInteger.valueOf(7));
      hoeTestLevel.setBlock(hoeTestPos(0, 0).above(), ripe, 3);
      hoeTestLevel.setBlock(hoeTestPos(15, 15).above(), ripe, 3);
      hoeTestLevel.setBlock(hoeTestPos(17, 17).above(), ripe, 3);
      hoeTestClick(hoeTestPlayer, hoeTestPos(0, 0).above());
      console.info('HOE_TEST_HARVEST_CLICK_POSTED');
    } catch (error) { console.error('HOE_TEST_SETUP_FAILED ' + error); }
  }
  if (hoeTestTick === 110) {
    try {
      var cropCenterState = hoeTestLevel.getBlockState(hoeTestPos(0, 0).above());
      var cropEdgeState = hoeTestLevel.getBlockState(hoeTestPos(15, 15).above());
      var cropOutsideState = hoeTestLevel.getBlockState(hoeTestPos(17, 17).above());
      var wheatCollected = hoeTestInventoryCount(hoeTestPlayer, 'minecraft:wheat');
      var cropOk = hoeTestBlockId(cropCenterState) === 'minecraft:wheat'
        && hoeTestBlockId(cropEdgeState) === 'minecraft:wheat'
        && hoeTestBlockId(cropOutsideState) === 'minecraft:wheat'
        && Number(cropCenterState.getValue(TestCrop.AGE)) === 0
        && Number(cropEdgeState.getValue(TestCrop.AGE)) === 0
        && Number(cropOutsideState.getValue(TestCrop.AGE)) === 7
        && wheatCollected >= 2;
      console.info('HOE_TEST_HARVEST ' + (cropOk ? 'PASS' : 'FAIL')
        + ' center=' + cropCenterState + ' edge=' + cropEdgeState + ' outside=' + cropOutsideState
        + ' wheatInInventory=' + wheatCollected);
      hoeTestClick(hoeTestPlayer, hoeTestPos(21, 0));
      console.info('HOE_TEST_TILL_CLICK_POSTED');
    } catch (error) { console.error('HOE_TEST_HARVEST_FAILED ' + error); }
  }
  if (hoeTestTick === 160) {
    try {
      var tillNear = hoeTestLevel.getBlockState(hoeTestPos(24, 0));
      var tillFar = hoeTestLevel.getBlockState(hoeTestPos(28, 0));
      var tillOk = hoeTestBlockId(tillNear) === 'minecraft:farmland'
        && hoeTestBlockId(tillFar) === 'minecraft:dirt';
      console.info('HOE_TEST_TILL ' + (tillOk ? 'PASS' : 'FAIL') + ' near=' + tillNear + ' far=' + tillFar);
      for (var x = 40; x <= 43; x++) {
        for (var z = 0; z <= 3; z++) {
          hoeTestLevel.setBlock(hoeTestPos(x, z), TestBlocks.FARMLAND.defaultBlockState(), 3);
          hoeTestLevel.setBlock(hoeTestPos(x, z).above(), TestBlocks.AIR.defaultBlockState(), 3);
        }
      }
      hoeTestPlayer.getInventory().setItem(0, new TestStack(TestItems.WHEAT_SEEDS, 64));
      hoeTestPlayer.setItemInHand(TestHand.OFF_HAND, new TestStack(TestItems.DIAMOND_HOE));
      hoeTestClick(hoeTestPlayer, hoeTestPos(41, 1));
      console.info('HOE_TEST_PLANT_CLICK_POSTED');
    } catch (error) { console.error('HOE_TEST_TILL_FAILED ' + error); }
  }
  if (hoeTestTick === 180) {
    try {
      var seedCorner = hoeTestLevel.getBlockState(hoeTestPos(43, 3).above());
      var seedOutside = hoeTestLevel.getBlockState(hoeTestPos(44, 4).above());
      var seedsLeft = hoeTestPlayer.getMainHandItem().getCount();
      var seedOk = hoeTestBlockId(seedCorner) === 'minecraft:wheat'
        && seedOutside.isAir() && seedsLeft === 48;
      console.info('HOE_TEST_PLANT ' + (seedOk ? 'PASS' : 'FAIL')
        + ' corner=' + seedCorner + ' outside=' + seedOutside + ' seedsLeft=' + seedsLeft);
    } catch (error) { console.error('HOE_TEST_PLANT_FAILED ' + error); }
  }
  if (hoeTestTick === 190) {
    try {
      var fullInventory = hoeTestPlayer.getInventory();
      for (var slot = 1; slot < 36; slot++) fullInventory.setItem(slot, new TestStack(TestItems.STONE, 64));
      fullInventory.setItem(0, new TestStack(TestItems.DIAMOND_HOE));
      var ripeAgain = TestBlocks.WHEAT.defaultBlockState().setValue(TestCrop.AGE, TestInteger.valueOf(7));
      hoeTestLevel.setBlock(hoeTestPos(0, 0).above(), ripeAgain, 3);
      hoeTestOverflow = true;
      hoeTestClick(hoeTestPlayer, hoeTestPos(0, 0).above());
      console.info('HOE_TEST_OVERFLOW_CLICK_POSTED');
    } catch (error) { console.error('HOE_TEST_OVERFLOW_SETUP_FAILED ' + error); }
  }
  if (hoeTestTick === 270) {
    try {
      var overflowState = hoeTestLevel.getBlockState(hoeTestPos(0, 0).above());
      var overflowOk = hoeTestDroppedCount > 0
        && hoeTestInventoryCount(hoeTestPlayer, 'minecraft:wheat') === 0
        && Number(overflowState.getValue(TestCrop.AGE)) === 0;
      console.info('HOE_TEST_OVERFLOW ' + (overflowOk ? 'PASS' : 'FAIL')
        + ' spawnedItems=' + hoeTestDroppedCount + ' wheatInInventory='
        + hoeTestInventoryCount(hoeTestPlayer, 'minecraft:wheat'));
      hoeTestOverflow = false;
    } catch (error) { console.error('HOE_TEST_OVERFLOW_FAILED ' + error); }
  }
});
