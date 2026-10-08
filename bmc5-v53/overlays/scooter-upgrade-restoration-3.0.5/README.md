# Scooter upgrade restoration and held Peeb armor

This final overlay starts from the exact 106f checkpoint and restores 23
existing, unchanged JSON recipes: 16 dyes, Nether Star binding, chest, jukebox,
note block, saddle, netherite and infinite-battery upgrades. These are smithing
recipes with an empty template slot, the scooter in the base slot and the
specified addition. Infinite battery retains its original command-block
ingredient; this restoration does not make that ingredient survival-obtainable.

The migration policy admits only those exact IDs with their original recipe
types. The dismantler and unlisted custom recipes remain blocked. The existing
endgame crafting recipe and its default Loyalty I are byte-identical to 106f.

Held Peeb now works with armor in either hand. Wearing Peeb still requires
empty helmet, leggings and boots slots. Only the corresponding armor tooltip
value changes in English and Polish. The overlay compiles MigrationPolicy and
the PeebMode family; audio, grapple, renderer, packet shapes and other base
entries remain identical.

```sh
python build.py --base /path/106f.jar \
  --classpath-file /path/neoforge-classpath.txt --output-dir /path/output
```

Java 21 and an external Minecraft 1.21.1 / NeoForge 21.1.250 classpath are
required. `restoration-manifest.json` pins every source/resource SHA and the
typed allowlist. The builder verifies an exact five-changed/23-added entry diff
and writes the overlay, candidate and provenance. It invokes no game, network,
production or Git operation. The frozen result is
`53c622c5b69e1d264eacee2e9b9d686e663d3e08d28a905523dd85fa07f5d509`.

`tests/ScooterUpgradeQA.java` checks the actual loaded recipe manager, native
smithing lookup, assembly, all 16 accent colors, binding, every original upgrade,
duplicate rejection, rental restrictions, component preservation, typed policy
and held/worn Peeb armor semantics. Its 243 assertions passed on the exact final
artifact. The included observer source also passed 63 common checks and the
sibling Loyalty test's 11 assertions. The isolated server status responded,
all dimensions saved, normal stop completed and the JVM exited 0.

The tests use disposable server players and representative item components;
no user inventory, world or credentials are copied. The 40 focused native client
checks ran on 106f; audio/client/render entries are byte-identical in the final
artifact, and their receipt records this inheritance. Human input/feel,
shaderpack-on rendering and production activation are not claimed.
