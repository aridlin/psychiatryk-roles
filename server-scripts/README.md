# Server-side farming script

Copy `kubejs/server_scripts/psychiatryk_diamond_hoe.js` to the same path on the
NeoForge 1.21.1 server. It needs KubeJS, Right Click Harvest, and Open Parties
and Claims from the current pack. Apply changes with
`kubejs reload server-scripts` from the console; clients do not need to update.

- Diamond hoe on a mature crop: harvest a 32 x 32 horizontal area at that Y,
  replanting through Right Click Harvest. Drops enter the player's inventory;
  any overflow remains on the ground.
- Diamond hoe on grass, dirt, or a dirt path: till 16 x 16 only where water is
  within four blocks horizontally at the same Y or one block above.
- Wheat seeds in the main hand with a diamond hoe in the offhand: plant a 4 x 4
  area on farmland and consume one seed per planted crop.

Only loaded chunks are touched. Each cell checks the world border, spawn
protection, and Open Parties and Claims access. Crouch for an ordinary
single-block interaction. Jobs run over several ticks to limit spikes.

`tests/hoe_smoke_test.js` is for a disposable local NeoForge server only. It
uses a fake player and checks the crop boundary, inventory collection, water
filter, 16-seed consumption, and full-inventory overflow. Never copy that test
script to the public server.
