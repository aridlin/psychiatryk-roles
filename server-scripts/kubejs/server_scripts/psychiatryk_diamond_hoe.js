// Server-only KubeJS 2101.7.2 / NeoForge 21.1.252. Reload with:
// /kubejs reload server-scripts
// A normal right-click on a mature crop harvests a 32x32 horizontal square;
// right-clicking tillable soil hoes a 16x16 square. Wheat seeds with a diamond
// hoe in the offhand plant a 4x4 square. None loads new chunks.

var HoeRightClick = Java.loadClass('net.neoforged.neoforge.event.entity.player.PlayerInteractEvent$RightClickBlock');
var HoePriority = Java.loadClass('net.neoforged.bus.api.EventPriority');
var HoeItems = Java.loadClass('net.minecraft.world.item.Items');
var HoeHand = Java.loadClass('net.minecraft.world.InteractionHand');
var HoeResult = Java.loadClass('net.minecraft.world.InteractionResult');
var HoePos = Java.loadClass('net.minecraft.core.BlockPos');
var HoeDirection = Java.loadClass('net.minecraft.core.Direction');
var HoeHit = Java.loadClass('net.minecraft.world.phys.BlockHitResult');
var HoeVec = Java.loadClass('net.minecraft.world.phys.Vec3');
var HoeContext = Java.loadClass('net.minecraft.world.item.context.UseOnContext');
var HoeResourceLocation = Java.loadClass('net.minecraft.resources.ResourceLocation');
var HoeFluidTags = Java.loadClass('net.minecraft.tags.FluidTags');
var HoeItemEntity = Java.loadClass('net.minecraft.world.entity.item.ItemEntity');
var HoeEntityJoin = Java.loadClass('net.neoforged.neoforge.event.entity.EntityJoinLevelEvent');
var HoeCrop = Java.loadClass('net.minecraft.world.level.block.CropBlock');
var HoeWart = Java.loadClass('net.minecraft.world.level.block.NetherWartBlock');
var HoeCocoa = Java.loadClass('net.minecraft.world.level.block.CocoaBlock');
var HoeHarvest = Java.loadClass('io.github.jamalam360.rightclickharvest.RightClickHarvest');
var HoeClaims = Java.loadClass('xaero.pac.common.server.api.OpenPACServerAPI');

var hoeTillable = new Set([
  'minecraft:grass_block', 'minecraft:dirt', 'minecraft:dirt_path'
]);
var hoeJobs = [];
var hoeBusyPlayers = new Set();
var hoeCollect = null;
var HOE_BLOCKS_PER_TICK = 16;

NativeEvents.onEvent(HoePriority.HIGHEST, HoeEntityJoin, event => {
  if (hoeCollect === null || !(event.getEntity() instanceof HoeItemEntity)
      || String(event.getLevel().dimension) !== String(hoeCollect.level.dimension)) return;
  var entity = event.getEntity();
  var stack = entity.getItem().copy();
  if (stack.isEmpty()) return;
  hoeCollect.player.getInventory().add(stack);
  if (stack.isEmpty()) event.setCanceled(true);
  else entity.setItem(stack); // only overflow remains in the world
});

function hoeItemId(stack) {
  return String(stack.getItem().builtInRegistryHolder().key().location());
}

function hoeBlockId(state) {
  return String(state.getBlock().builtInRegistryHolder().key().location());
}

function hoeMature(state) {
  var block = state.getBlock();
  if (block instanceof HoeCrop) return block.isMaxAge(state);
  if (block instanceof HoeWart) return Number(state.getValue(HoeWart.AGE)) === 3;
  if (block instanceof HoeCocoa) return Number(state.getValue(HoeCocoa.AGE)) === 2;
  return false;
}

function hoeClaimAccess(job, pos) {
  var border = job.level.getWorldBorder();
  if (pos.getX() + 0.5 < border.getMinX() || pos.getX() + 0.5 > border.getMaxX()
      || pos.getZ() + 0.5 < border.getMinZ() || pos.getZ() + 0.5 > border.getMaxZ()) return false;
  if (job.level.getServer().isUnderSpawnProtection(job.level, pos, job.player)) return false;
  var key = (pos.getX() >> 4) + ',' + (pos.getZ() >> 4);
  if (job.claims.has(key)) return job.claims.get(key);
  // Fail closed if the claims API is unavailable. The server has OPAC installed.
  var allowed = false;
  try {
    var api = HoeClaims.get(job.level.getServer());
    allowed = api != null && api.getChunkProtection().hasChunkAccess(
      job.player.getGameProfile().getId(), HoeResourceLocation.parse(String(job.level.dimension)), pos.getX() >> 4, pos.getZ() >> 4);
  } catch (error) {
    console.error('Diamond hoe claim check failed: ' + error);
  }
  job.claims.set(key, allowed);
  return allowed;
}

function hoeHit(pos) {
  return new HoeHit(HoeVec.atCenterOf(pos), HoeDirection.UP, pos, false);
}

function hoeScanWater(job) {
  // A 24x24 snapshot covers every hydration search for the 16x16 square.
  // Scan it over several ticks, then use a prefix sum for constant-time cell
  // checks. This avoids 256 repeated 9x9x2 world queries.
  var width = 24;
  var stop = Math.min(job.waterIndex + 64, width * width);
  for (; job.waterIndex < stop; job.waterIndex++) {
    var x = job.waterX + Math.floor(job.waterIndex / width);
    var z = job.waterZ + job.waterIndex % width;
    var found = 0;
    for (var dy = 0; dy <= 1; dy++) {
      var probe = new HoePos(x, job.waterY + dy, z);
      if (job.level.hasChunkAt(probe)
          && job.level.getFluidState(probe).getType().is(HoeFluidTags.WATER)) {
        found = 1;
        break;
      }
    }
    job.waterGrid[job.waterIndex] = found;
  }
  if (job.waterIndex < width * width) return;
  var stride = width + 1;
  var sum = new Array(stride * stride).fill(0);
  for (var ix = 0; ix < width; ix++) {
    for (var iz = 0; iz < width; iz++) {
      sum[(ix + 1) * stride + iz + 1] = job.waterGrid[ix * width + iz]
        + sum[ix * stride + iz + 1] + sum[(ix + 1) * stride + iz]
        - sum[ix * stride + iz];
    }
  }
  for (var sx = 0; sx < 16; sx++) {
    for (var sz = 0; sz < 16; sz++) {
      var nearby = sum[(sx + 9) * stride + sz + 9]
        - sum[sx * stride + sz + 9] - sum[(sx + 9) * stride + sz]
        + sum[sx * stride + sz];
      if (nearby > 0) job.hydrated.add((job.waterX + 4 + sx) + ',' + (job.waterZ + 4 + sz));
    }
  }
  job.waterGrid = null;
  job.waterPhase = false;
}

function hoeProcess(job, pos) {
  if (!job.level.hasChunkAt(pos) || !hoeClaimAccess(job, pos)) return;
  var state = job.level.getBlockState(pos);
  if (job.kind === 'plant') {
    if (hoeBlockId(state) !== 'minecraft:farmland' || !job.level.getBlockState(pos.above()).isAir()) return;
    if (job.player.getMainHandItem().isEmpty()) return;
    job.player.getMainHandItem().getItem().useOn(new HoeContext(job.player, HoeHand.MAIN_HAND, hoeHit(pos)));
    return;
  }
  if (job.kind === 'harvest') {
    if (!hoeMature(state)) return;
    // The installed harvest mod handles loot, replanting, durability and the
    // ordinary break/place protection events. false disables its small radius.
    hoeCollect = { player: job.player, level: job.level };
    try {
      HoeHarvest.onBlockUse(job.player, job.level, HoeHand.MAIN_HAND, hoeHit(pos), false);
    } finally {
      hoeCollect = null;
    }
    return;
  }
  if (!hoeTillable.has(hoeBlockId(state))) return;
  if (!job.level.getBlockState(pos.above()).isAir()) return;
  if (!job.hydrated.has(pos.getX() + ',' + pos.getZ())) return;
  // Vanilla HoeItem.useOn preserves tool wear, game events and ordinary tilling behavior.
  job.player.getMainHandItem().getItem().useOn(new HoeContext(job.player, HoeHand.MAIN_HAND, hoeHit(pos)));
}

NativeEvents.onEvent(HoePriority.HIGHEST, HoeRightClick, event => {
  var level = event.getLevel();
  if (level.isClientSide() || event.getHand() !== HoeHand.MAIN_HAND || event.getEntity().isSpectator()) return;
  var player = event.getEntity();
  if (player.isCrouching()) return; // crouch for ordinary single-block behavior
  var isDiamondHoe = hoeItemId(event.getItemStack()) === 'minecraft:diamond_hoe';
  var isSeedPlanter = hoeItemId(event.getItemStack()) === 'minecraft:wheat_seeds'
    && hoeItemId(player.getOffhandItem()) === 'minecraft:diamond_hoe';
  if (!isDiamondHoe && !isSeedPlanter) return;
  var pos = event.getPos();
  if (!level.hasChunkAt(pos)) return;
  var state = level.getBlockState(pos);
  var kind = null;
  if (isSeedPlanter && hoeBlockId(state) === 'minecraft:farmland' && level.getBlockState(pos.above()).isAir())
    kind = 'plant';
  else if (isDiamondHoe && hoeMature(state)) kind = 'harvest';
  else if (isDiamondHoe && hoeTillable.has(hoeBlockId(state))
      && level.getBlockState(pos.above()).isAir()) kind = 'till';
  if (kind === null) return;

  // A plain UUID string is stable across Rhino wrappers and safe as a Set key.
  var id = String(player.getGameProfile().getId());
  if (!hoeBusyPlayers.has(id)) {
    if (hoeJobs.length >= 6) return;
    var size = kind === 'harvest' ? 32 : kind === 'till' ? 16 : 4;
    var low = -Math.floor((size - 1) / 2);
    var job = { kind: kind, level: level, player: player, id: id,
      claims: new Map(), positions: [], index: 0, hydrated: new Set(),
      waterPhase: kind === 'till', waterGrid: kind === 'till' ? new Array(576) : null,
      waterIndex: 0, waterX: pos.getX() + low - 4,
      waterY: pos.getY(), waterZ: pos.getZ() + low - 4 };
    if (!hoeClaimAccess(job, pos)) return;
    for (var dx = low; dx < low + size; dx++) {
      for (var dz = low; dz < low + size; dz++) {
        job.positions.push(new HoePos(pos.getX() + dx, pos.getY(), pos.getZ() + dz));
      }
    }
    hoeBusyPlayers.add(id);
    hoeJobs.push(job);
  }
  event.setCancellationResult(HoeResult.SUCCESS);
  event.setCanceled(true);
});

ServerEvents.tick(event => {
  // Tilling can notify many neighboring blocks, so spread that work more
  // gently than read-mostly crop scanning.
  var budget = hoeJobs.length > 0 && hoeJobs[0].kind === 'till'
    ? 8 : HOE_BLOCKS_PER_TICK;
  while (budget > 0 && hoeJobs.length > 0) {
    var job = hoeJobs[0];
    try {
      if (!job.player.isAlive()
          || (job.kind === 'plant'
            ? hoeItemId(job.player.getMainHandItem()) !== 'minecraft:wheat_seeds'
              || hoeItemId(job.player.getOffhandItem()) !== 'minecraft:diamond_hoe'
            : hoeItemId(job.player.getMainHandItem()) !== 'minecraft:diamond_hoe')
          || String(job.player.level.dimension)
            !== String(job.level.dimension)) {
        hoeBusyPlayers.delete(job.id);
        hoeJobs.shift();
        continue;
      }
      if (job.waterPhase) {
        hoeScanWater(job);
        return;
      }
      hoeProcess(job, job.positions[job.index]);
    } catch (error) {
      console.error('Diamond hoe area action stopped at cell ' + job.index + ': ' + error);
      hoeBusyPlayers.delete(job.id);
      hoeJobs.shift();
      continue;
    }
    job.index++;
    budget--;
    if (job.index >= job.positions.length) {
      hoeBusyPlayers.delete(job.id);
      hoeJobs.shift();
    }
  }
});
