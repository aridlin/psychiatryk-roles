# Elytra scooter default Loyalty I

This one-resource overlay adds `minecraft:loyalty: 1` to the result components
of `data/goplanska_kukirin/recipe/kukirin_scooter.json`. The existing Elytra and
dragon-egg-shard recipe grid, output count and prebound marker remain identical.
It does not modify ordinary scooter crafting or the separate Nether Star path.

```sh
python build.py --base /path/focused-addon.jar --output-dir /path/output
```

The builder verifies the before/after SHA and confirms the decoded recipe is
identical after removing the new enchantment component. It creates only the
single-resource overlay and invokes no game, network or Git operation.

`tests/EndgameLoyaltyQA.java` executes against the actual loaded recipe manager:
preview and assembled Loyalty I, actual owner binding, preservation through
upgrades and higher Loyalty, and unchanged ordinary/Nether-Star binding behavior.
All 11 checks passed in the isolated dedicated fixture. See the sibling Peeb
observer instructions and the sanitized dedicated receipt. This result proves
the API and crafting cases tested there; it does not assert every interactive
upgrade recipe can be used in survival.
