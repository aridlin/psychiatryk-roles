# BMC5 v53 + reduced Psychiatryk Roles 3.0.6

[Install](INSTALL-3.0.6.md) · [Player guide](PLAY-3.0.6.md) · [Patch and verification](PATCH-3.0.6.md)

Previous/Skip controls, a shared playback clock, wearable source continuity and progressive imports without the one-minute cooldown are included. Peeb's elastic grapple retains launch/swing momentum and its head look is corrected. Source-only overlays and their reproducible builder are in `overlays/import-fix-3.0.6/` and `build-import-fix-3.0.6.py`. No game was launched for these checks; live listening and player-visible grapple feel need normal gameplay confirmation.

## Retained features and earlier releases

[Previous 3.0.5 Peeb, streaming and scooter upgrade fixes](PATCH-3.0.5-FOLLOWUP.md) · [Player guide](PLAY-3.0.5-FOLLOWUP.md) · [Activation receipt](patch-evidence/production-3.0.5.json) · [3.0.4 placed jukebox acoustics](PATCH-3.0.4.md) · [3.0.3 wearable jukebox](PATCH-3.0.3.md) · [3.0.2 model restoration](PATCH-3.0.2.md) · [Retained3.0.1 patch](PATCH-3.0.1.md) · [First-run installers](installer/INSTALL.md).

This is our addon, not a redistributed Better MC modpack. Use the [official v53
base](https://www.curseforge.com/minecraft/modpacks/better-mc-neoforge-bmc5/files/8835241)
and [install steps](INSTALL.md). MC1.21.1 / NeoForge21.1.250 / Java21.

[Original six recipes](recipes.json): mirror, loot lens, dragon egg shards,
owner-bound scooter and classic Void Door/trapdoor. Peeb and the restored scooter
smithing recipes are described in the current player guide. Ten MoreVillagers station
recipes and survival use/place/pickup are restricted. Immersive Portals is removed.
The standalone scooter uses its original recipe; the BMC addon overrides it with
iron, dragon-egg shard, elytra and redstone block. Shared poker escrow uses pinned
official ProjectE base EMC values, without installing ProjectE.

Current authored overlays are in src; independent portable modules are in
../portable-modules. Retained authored legacy helper sources are in ../s23-v34
and earlier history. No foreign BMC binaries/models, player worlds, accountcache,
private admin-confirm popup, private audio catalog/tracks or conversation logs
are included. Only authored primitive scooter mesh/texture resources are copied.
Optional portable adapter source folders are not tested installable adapter mods.

The historical 3.0.4 addon was SHA-256 9d5e4271882a6ca112b7cdc859afb962cf3e42921f83a5fec05fba88ad11919e. Its placed-source acoustic regression is documented in patch-evidence/jukebox-acoustics-3.0.4.json. That receipt describes the earlier activation state; the server and native host now distribute 3.0.6.

The qualified 3.0.3 baseline was SHA-256 daa891b2f6e7c50fa9663efd13fc66e43bb9c1b6682c66833f0a30d4d1ca51a4. Wearable equipment, menu/networking, moving Sound Physics and lifecycle have separate native runtime evidence in patch-evidence/wearable-jukebox-3.0.3.json.

The original 3.0.0 baseline was SHA-256 43868feae389494896b258787cd5305fce43d9d22e595a59b587147f7ff13b53. Its historical client-runtime-report.json records27
actual full-client checks, including server join, synchronized recipe set and
real escrow GUI actions. Server counterpart passes55 checks; no production/
remote updater/performance result is inferred from isolated fixtures.
Historical preparation receipts are kept unchanged. Current activation and publication are recorded separately in patch-evidence/production-3.0.6.json.

## Historical 3.0.5 source preparation

[Peeb and music 3.0.5](PATCH-3.0.5.md) contains recovered implementation source, an exact-checkpoint Peeb rope/dither overlay and focused tests. That document records an earlier source preparation. The subsequent 3.0.5 activation and current 3.0.6 activation have separate receipts. See [source and asset provenance](overlays/peeb-music-3.0.5/README.md).

The [spinning jukebox-disc follow-up](overlays/jukebox-disc-3.0.5/README.md)
preserves the approved Peeb checkpoint and renders missing-cover tracks too.
Its exact e0f6a9df candidate passed actual playback/framebuffer rotation and
cleanup checks. Historical Peeb source and provenance remain unchanged.
