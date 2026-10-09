# Server-only release boundary

Audited against queued 3.0.9 jar SHA-256 `a513a1e338f0651648dfa32ecf9f3049daab312c2304183083337ecbc43572de` (525 classes). The guard is for releases **after** the shared stable runtime has been installed on clients once. It compares a future server jar against that installed client jar and rejects any changed entry outside a small allowlist.

| Area | Permitted implementation families | Frozen shared boundary |
|---|---|---|
| Void doors | Pair SavedData (`VoidDoorData`, `VoidTrapdoorData`), portal link/exit/sweep/occlusion math | `VoidDoors`, `VoidTrapdoors`, anvil menu, item/recipe/drop mixins, renderer and resource assets |
| Poker | `PokerGame`, `PokerData`, hand/deposit/item-value rules, activity and commands | `PokerCard`, menu/menu registration, screen, payloads and displayed item registries |
| Villagers and progression | `VillagerTradeRebalance`, `SeasonProgression` | Villager mixins and shared registration |
| Scooter | Rental spawner and payment policy | Scooter entity, movement/prediction/tuning, audio client, music menus/sources/cache and packets |
| Music | No legacy music implementation is pre-approved; put new server-owned behavior behind the runtime server feature interface | Music screens, audio/cache classes and every music packet/codec |
| Roles/admin | `RoleData`, roster/startup, help, restart and old-world server commands/data | Main `PsychiatrykRoles` mod registration, role packets, client UI and mixins |
| Grapple | `PeebGrapple` implementation and new `runtime/grapple/` adapter, rules and server events | `PeebSharedPhysics`, `PeebGrappleWire`, `PeebClient`, `PeebMovement`, packet shape and client-predicted physics; the one-time 3.0.10 client redirects every direct `PeebGrapple` call from its two client-root classes to the frozen physics helper, while the server's existing packet sends pass through the frozen wire helper |

The gate also rejects an allowed class if its bytecode references `net.minecraft.client`, registry registration, or custom-payload/codec APIs; if an installed client class directly references it; if its binary class/member shape removes or changes existing members; or if a baseline entry is deleted. It validates the server-feature provider list and default-menu JSON. New `runtime/server/` classes are permitted under the same checks. This is deliberately a release gate, not a proof that arbitrary code is safe: reflection, optional mod integration, world-save compatibility, script side effects and gameplay behavior still require native regression tests, a staged server restart, and an actual unchanged-client join. Data packs, assets, mixin configs, registries and payloads remain frozen by default.
