# BMC5 v53 + Psychiatryk Roles 3.0.8 source

[Corrected 3.0.8 launch](PATCH-3.0.8-LOADERFIX.md) · [Original 3.0.8 patch](PATCH-3.0.8.md) · [Install guide](INSTALL-3.0.8.md) · [Player guide](PLAY-3.0.8.md) · [Production receipt](patch-evidence/production-3.0.8.json)

The existing Ordynator, Pacjent, Konsultant and Contractor role framework is restored; Better MC survival item/recipe restrictions remain. Peeb supports ordinary dyes; optional 1.3-block grapple stepping is admin controlled and defaults off. Initiation near maximum range is synchronized. The reported eye separation, undyed tusk and terrain-corner snag have a separate follow-up candidate and are not included in this loader repair. Music cards stay sharp, are half size and sit beside the actual Xaero minimap. Shared streaming retries transient failures. Conditional client fixes bridge legacy JEI unit transfers and repair the exact legacy Flywheel range clear defect while skipping fixed or unknown variants.

The original 3.0.8 authored sources are in `overlays/ui-polish-3.0.8/`; the historical baseline portable builder is `build-ui-polish-3.0.8.py`. Apply the separately verified loader repair using `overlays/loader-compat-3.0.8/build.py` for the current corrected JAR. The actual portable build reproduced the complete candidate SHA `de57e55ed0171644758b2c82689c1fcfa680ad6a8bc7d4ba72b86d3b4ae16c0f`. Native signature and focused fixtures do not establish live listening or player-visible gameplay. [Production verification](patch-evidence/production-3.0.8.json) confirms fresh startup, paired server/client payloads, pinned AutoModpack delivery and matching public downloads. The [previous 3.0.7 receipt](patch-evidence/production-3.0.7.json) remains historical. A separate authored ProLiant capacity service source is in `services/proliant-capacity-3.0.8/`; no private roster plan, audio catalog or deployment logs are included.

## Corrected 3.0.8 launch

[Client-loader repair and normal crop growth](PATCH-3.0.8-LOADERFIX.md) · [Actual corrected launch/join evidence](patch-evidence/launch-loaderfix-3.0.8.json). The current corrected addon SHA is `56cc13966c2ed202ff54ac9e0b401fce32325842d93fff2be22b1ee14de9b614`. The original de57e55e source build and production receipt above remain historical. The repair changes only two client compatibility plugins and was published to the running server without a restart.

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

The historical 3.0.4 addon was SHA-256 9d5e4271882a6ca112b7cdc859afb962cf3e42921f83a5fec05fba88ad11919e. Its placed-source acoustic regression is documented in patch-evidence/jukebox-acoustics-3.0.4.json. That receipt describes the earlier activation state; the recorded 3.0.6 activation is preserved as the previous deployment checkpoint.

The qualified 3.0.3 baseline was SHA-256 daa891b2f6e7c50fa9663efd13fc66e43bb9c1b6682c66833f0a30d4d1ca51a4. Wearable equipment, menu/networking, moving Sound Physics and lifecycle have separate native runtime evidence in patch-evidence/wearable-jukebox-3.0.3.json.

The original 3.0.0 baseline was SHA-256 43868feae389494896b258787cd5305fce43d9d22e595a59b587147f7ff13b53. Its historical client-runtime-report.json records27
actual full-client checks, including server join, synchronized recipe set and
real escrow GUI actions. Server counterpart passes55 checks; no production/
remote updater/performance result is inferred from isolated fixtures.
Historical preparation receipts are kept unchanged. The previous 3.0.6 activation and publication remain in patch-evidence/production-3.0.6.json; the new 3.0.7 source checks are in PORTABLE-REBUILD-3.0.7.json; live 3.0.7 startup, AutoModpack delivery and public downloads are recorded in patch-evidence/production-3.0.7.json.

## Historical 3.0.5 source preparation

[Peeb and music 3.0.5](PATCH-3.0.5.md) contains recovered implementation source, an exact-checkpoint Peeb rope/dither overlay and focused tests. That document records an earlier source preparation. The subsequent 3.0.5 and 3.0.6 activations have separate historical receipts. See [source and asset provenance](overlays/peeb-music-3.0.5/README.md).

The [spinning jukebox-disc follow-up](overlays/jukebox-disc-3.0.5/README.md)
preserves the approved Peeb checkpoint and renders missing-cover tracks too.
Its exact e0f6a9df candidate passed actual playback/framebuffer rotation and
cleanup checks. Historical Peeb source and provenance remain unchanged.
