# AMRAC — Advanced Air Combat

A Minecraft Java Edition 26.2 mod for NeoForge: modern jet fighters, beyond-visual-range missiles, a realistic flight model and AI pilots.

## Requirements

- **JDK 25** (to build)
- Minecraft **26.2**
- NeoForge **26.2.0.88+**

The first build needs an internet connection; Gradle downloads Minecraft and NeoForge. The build asks for a Java 25 toolchain, so Gradle picks up an installed JDK 25 even if your default Java is older.

## Build

Windows:

```powershell
.\gradlew.bat build
```

Linux / macOS:

```bash
./gradlew build
```

The mod jar is written to `build/libs/amrac-neoforge-1.1.0+26.2.jar`. The `-sources.jar` next to it contains source code only; don't install it.

## Install

1. Install NeoForge for Minecraft 26.2.
2. Put `amrac-neoforge-1.1.0+26.2.jar` into the `mods` folder.
3. Do this on **both the client and the server**.

## Run in development

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

The client runs in `run/`, the server in `run-server/`.

## Configuration

Created on first launch, in the game's `config` folder:

- `config/amrac/flightmodel/aircraft/` and `missile/`: one JSON file per aircraft and missile
- `config/amrac/flightmodel/environment.json`: atmosphere and environment
- `config/amrac/flightmodel/ai/pilot.json`: AI pilot settings (server only)
- `config/amrac.properties`: world rules (fuel, crash damage)

Edits are picked up within half a second, no restart needed. In multiplayer the server's files are used and synced to every player.

## Project layout

- `src/main/java`: all game code, common, server and client (rendering, HUD, controls, screens)
  - `amrac.platform`: the layer between the game code and the loader (events, networking, HUD, keys, renderers)
  - `amrac.neoforge`: the NeoForge entry point, which connects NeoForge events to `amrac.platform`
  - `amrac.mixin`: client mixins
- `src/main/resources`: all resources (textures, meshes, sounds, language files, recipes, default JSON files), plus `META-INF/neoforge.mods.toml` and `accesstransformer.cfg`

## Credits

- Authors: nonamenov6638, EmanonTheBeauty, uumatter, AntsHsu

## License

- **Source code** (`src/main/java`): LGPL-3.0-only
- **Everything else**, including all assets under `src/main/resources` (textures, 3D models, sounds, icons, language and data files): All Rights Reserved. These may not be copied, modified or redistributed outside this mod without permission.

See `NOTICE` for the full terms, `COPYING.LESSER` for the LGPL-3.0 text and `COPYING` for the GPL-3.0 text it builds on.
