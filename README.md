# Variations of Yesterday

[中文说明](README-zh.md)

**Variations of Yesterday** is a Minecraft 1.21.2 (Fabric / Scala 3) mod about a living mountain landscape driven by an excitable dynamical system (**Mandate Engine**) and fragmented 1980s hydrological-engineering / folk-ritual records.

## Core Architecture

- **Mandate Engine (`src/main/scala/.../inner/mandate/`)**: Coupled FitzHugh–Nagumo fast subsystem and slow land-stress dynamics with hysteresis, spatial advection-diffusion on a lattice (`MandateGrid`), Pekko typed actor tick-event scheduling (`MandateAgent`), and semantic phase/regime readout (`MandateReadout`).
- **Overworld Bridge (`MandateRuntime`, `MandateOverworld`)**: Translates player actions (mining, blasting, ringing village bells, diegetic clock/compass inspection) into channel events and applies Overworld stagnation (`沉滞`) when baseline calming (`c0`) collapses.
- **Relational World Model (`tools/voy_world/`)**: Zero-dependency Elixir + microKanren engine (`Voy.Kanren`) that compiles topography into 4-era histories and 6 fates (`Chronicle`), synthesizes 5-hop local-evidence dossiers with seeded credentials (`Pathway`, `Witness`, `Content`), and performs multi-lens abductive reasoning (`Observation`, `Ascribe`, `Facts`).

## Quick Start

```sh
# JVM / Mod tests & client
./gradlew test
./gradlew runClient

# Relational world model & authoring linter
cd tools/voy_world
mix test
mix voy.content lint
```

## License

All Rights Reserved (ARR).
