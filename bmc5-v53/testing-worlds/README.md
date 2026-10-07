# Testing worlds migration component

This server-side Roles overlay is included in the staged combined Roles 3.0.1 candidate. Production activation is handled separately.

## Commands

- `/testworld normal`, `/testworld flat`: enter either testing dimension.
- `/testworld back`: return to the previous ordinary dimension and position.
- `/testworld mode survival|creative`: patients can toggle; consultants need an aridlin grant for Creative.
- `/testworld grant <online-player> creative|survival`: requires the actual `aridlin` profile and permission level 2. Other operators cannot issue grants.

Patients default to Creative in testing worlds. Consultants use fully interactive Survival. Existing game mode in ordinary dimensions is restored on return. Normal-world class restrictions and grants are not changed.

## Inventory banks

The two testing dimensions share one test bank. Ordinary dimensions share a separate survival bank. A switch closes open containers and dismounts first. Inventory, armor, offhand, ender items, XP, health, hunger, effects, attributes, shoulder pets and bed respawn state enter the bank.

Serialized NeoForge attachments are deeply copied and outgoing instances removed before restoration. Accessories and Curios are covered through their actual serializers. Framework's three Backpacked inventory fields are banked separately from permanent backpack unlock/challenge fields. UUID, ordinary persistent role data, scoreboard parties, language, recipes and unrelated vanilla player data remain global.

Unknown serialized attachments are banked conservatively; an unavailable attachment type causes restoration to abort before clearing the outgoing bank. Additional third-party attachment semantics should be reviewed before extending this to another pack.

The `psychiatryk_testing_banks` SavedData ledger writes a compressed immutable pre-swap snapshot, forces it, atomically replaces the ledger and forces its containing directory before changing inventory. Player saves are flushed at transaction boundaries when the existing asynchronous player-save service is installed. A refused/failed transition restores the durable source snapshot. Ordinary SavedData autosaves also use atomic writes. Between transactions, the active vanilla player file is authoritative; login captures its fresh state instead of resurrecting an older ledger snapshot.

Cross-bank dimension travel events are blocked, including item/container entities. Test-origin entities cannot join an ordinary level. Logout records only the active bank. Respawn callbacks retain post-death inventory and return to a testing dimension without reloading the pre-death test snapshot. A bank/dimension mismatch fails closed on login and restores the original dimension during a tick.

## Genuine client verification

Earlier isolated checks cover 26 pure snapshot/atomic-write cases and 38 synthetic full-BMC server cases. The genuine no-Immersive-Portals BMC client then passed 55 checks across ordinary, flat and normal dimensions: actual inventory, Accessories, Curios and Backpacked GUIs, network synchronization, grant policies, ordinary Void Door teleport barrier, death/respawn and disconnect/rejoin. The corresponding sixth server run passed 53/54 checks; its failed marker assertion is retained in the original report.

That marker was outside NeoForge `PlayerPersisted`, so vanilla death cloning did not promise to preserve it. The final focused run corrects the fixture, checks the real patient role after death, and passed 33 genuine-client plus 58 server checks on the exact combined candidate `f6b413222daf31ed667bae71e90891407456bc21ebccbc0be1f98594d02b4a5a`. It also covers actual Sophisticated Backpacks: ordinary main-inventory and Curios-equipped items with UUID-backed SavedData contents, empty test-bank pointers, newly created independent test UUIDs, separate writes, exact ordinary item components and storage restoration, real backpack menus, test-bank reentry and genuine test death. Accessories and Curios synchronization are checked alongside it. A preliminary helper used the generic backpack hotkey, which correctly preferred the equipped bag rather than the expected inventory bag; that failed fixture receipt is preserved, and the final helper targets inventory slot 1 explicitly. The exact compatible Backpacks/Core versions and baseline JEI hash are recorded in `qa/genuine-client-report.json`.

Normal testing terrain uses independent seed `202610060400`, verified against 17 generated density samples; survival seed 42 was unchanged in the fixture. Per-dimension loot sequence state is separate. Immersive Portals is absent from the final pack and has not been claimed compatible with this barrier.

All runs used disposable fixtures. Final runs used cached official launch files and synthetic offline identities with token 0, without copying real account credentials. Both final servers stopped normally, and the recorded main-profile hashes stayed unchanged. These results validate the tested frozen candidate; production activation is a separate operation.

## Build and assembly

Run `python build.py` with the local frozen Minecraft/NeoForge compile cache. Output is `build/testing-worlds-overlay.jar`, six classes and three resources. Root assembly must register `psychiatryk-testing-worlds.mixins.json` and copy both dimension resources. The reduced Roles core must provide `testingPatient(ServerPlayer)`.

`qa/setup.py` and `qa/private-*` contain private local fixture infrastructure and must not be published or shipped. Authored QA Java, `qa/run_pure.py` and sanitized JSON reports may be shared. All module source was authored for this project; there is no copied third-party implementation.
