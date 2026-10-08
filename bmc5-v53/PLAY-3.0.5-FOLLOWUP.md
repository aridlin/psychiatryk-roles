# Playing the 3.0.5 follow-up checkpoint

Roles 3.0.5 (53c622) is active on production and available through AutoModpack
and the website. Fully relaunch after AutoModpack updates, then reconnect.

## Peeb

Hold left-click to grapple while Peeb mode is active. Left Alt looks around;
E opens inventory. Hold Peeb in either hand with any armor, or wear it in the
chest slot with the other armor slots empty. A rope
must initially reach a visible valid surface or hostile living target. Once
attached, turning away or an obstacle entering the view does not drop it.
Destroyed support, leaving reach, leaving the mode, or an invalid/dead target
does release it.

A hostile target moves the anchor with it. Contact can deal 4 damage once per
hook, subject to a one-second player cooldown. Allied targets and disabled PvP
are not valid targets. When driving a Kukirin, grapple pull moves the scooter.

On the ground, Peeb's feet and walk/idle animation use the grounded pose. An
anchor above you can still lift you; the follow-up does not remove that movement.

## Music

The library includes Minecraft's soundtrack and records, including Aria Math.
These tracks play from your installed Minecraft assets. Imported remote tracks
stream while downloading and loop when the actual audio ends. Stop closes the
stream. Multiple remote/native sources can play without the former four-source
rejection. Music remains positional and subject to the game's sound controls.

The source checkpoint removes the import service's earlier duration, file-size,
library-size and track-count limits. Host/authentication rules and inactivity
timeouts still apply. Service activation is a separate operator operation.

## Endgame scooter

The existing Elytra/dragon-egg-shard crafting result has Loyalty I by default
and keeps its existing prebound marker. The recipe grid and output count are
unchanged. Ordinary scooter crafting and the separate Nether Star binding path
are not modified by this one-resource overlay.

## Repaint and upgrade a scooter

At a smithing table, leave the template slot empty, put the scooter in the base
slot and the dye or upgrade ingredient in the addition slot. All 16 accent dyes
are restored. Nether Star binds an ordinary scooter; chest, jukebox, note block,
saddle and netherite block apply their existing upgrades. The infinite-battery
recipe still requires its original command block. Existing ownership, Loyalty
and other item state survive these modifications.

## Verification limits

Automated checks exercised real dedicated-server attachment, enemy damage,
scooter motion and recipe assembly, plus incremental audio and the native
catalog. The focused client fixture checked OpenAL state with a null backend,
including six concurrent sources; it did not record audible speaker output. Physical Left Alt input, human grapple feel and shaderpack-on visuals
need player confirmation; this guide does not claim those checks were completed.
