# v32 villager trade update

## Behavior

- Newly generated offers are stored as their original baseline and no longer inflated.
- On interaction, ordinary players receive stored vanilla baseline costs, vanilla max uses and vanilla price multiplier. Current result item/components/count, XP, used stock, demand and gossip discount stay intact.
- Wieśniuk receives the same vanilla baseline costs with unlimited stock (Integer.MAX_VALUE sentinel). A required common MerchantOffer mixin makes sentinel offers always available, preserves the existing uses counter (class purchases do not consume ordinary stock) and preserves demand during restock. Suppressing sentinel demand recalculation prevents vanilla uses*2-maxUses arithmetic from overflowing/corrupting prices. Ordinary offers retain all vanilla restock logic.
- Existing baseline cache, profession checks and missing-offer snapshot repair are retained. No listing factory reruns or bulk offer resets.
- /wiesniuk help text now says unlimited trading.

Known limit: if an old offer has no original baseline stored, existing behavior preserves the saved offer as its baseline. There is no safe exact reconstruction of an unknown random vanilla price without rerolling its trade. The normal generated/preexisting wrapped offers have stored baselines.

## Exact integration whitelist

Package only the four classes in classes/ and replace goplanska-villager.mixins.json with the source resource. class-hashes.json and build-proof.json contain exact output hashes and comparisons against v31. Do not package qa-classes or trade-agent.jar.

## Verification

18 real MerchantOffer checks pass in qa-results.txt. The fixture bootstraps actual Minecraft registries (no world) and uses a fixture-only Java agent to merge the exact compiled candidate mixin handlers into the actual MerchantOffer class. It verifies normal costs/second ingredient/uses/demand/gossip/multiplier, existing real enchantment/component preservation, exhaustion/restock, 1000 unlimited purchases, sold-out override/unchanged uses counters, normal-player restoration, preserved list size and actual MerchantOffer CODEC save/load behavior.

The fixture is not a NeoForge Mixin bootstrap test; parent dedicated-server startup should verify target injection registration. No game, world or production server was launched or changed by this QA.
