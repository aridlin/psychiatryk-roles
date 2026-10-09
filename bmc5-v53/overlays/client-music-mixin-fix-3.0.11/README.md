# 3.0.11 client startup hotfix

The exact live 3.0.10 jar fails during client initialization because
`psychiatryk-music-pause.mixins.json` owns `pl.aridlin.psychiatrykroles.music.*`,
including three helper classes that Minecraft's Mixin loader forbids loading
as ordinary classes. This overlay moves those helpers into the disjoint
`pl.aridlin.psychiatrykroles.audio` package and recompiles the music mixin to
refer to them. It changes no other code. The six NeoForge component versions
become `3.0.11-bmc5` so the candidate can be distinguished from 3.0.10.

Build with Java 21, a prepared Minecraft SDK classpath file, and the exact
3.0.10 jar whose SHA-256 is pinned in `build.py`:

```sh
python3 build.py --base /path/to/psychiatryk_roles-3.0.10-bmc5.jar \
  --sdk /path/to/sdk-clean.txt
/usr/lib/jvm/java-21-openjdk/bin/javac -J-Xmx384m --release 21 -d build/testclasses \
  src/pl/aridlin/psychiatrykroles/audio/BackgroundMusicPauseState.java \
  src/pl/aridlin/psychiatrykroles/audio/MusicAudibility.java \
  tests/pl/aridlin/psychiatrykroles/audio/BackgroundPauseTest.java
/usr/lib/jvm/java-21-openjdk/bin/java -cp build/testclasses \
  pl.aridlin.psychiatrykroles.audio.BackgroundPauseTest
```

`build.py` verifies the input hash and exact archive allowlist. The candidate
artifact is in ignored `build/`; `build-proof.json` records its digest. The
pure helper test covers scheduler pause/resume, lifecycle, and audibility.
Client launch and authenticated server join remain separate release gates.
