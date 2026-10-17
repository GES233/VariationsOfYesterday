# Voy World

A dependency-free Elixir prototype of the Variations of Yesterday relational world model. It is a design and verification tool, not a runtime dependency of the Minecraft mod.

## Layout

- `Voy.Kanren`: microKanren kernel, disequality constraints, open attributes, and thin DSL.
- `Voy.World.Causality`: causal stages, observations, ritual synthesis, and narrative lenses.
- `Voy.World.Chronicle`: the shared forward/reverse branch-history relation.
- `Voy.World.Projection`: structure and archive projections.
- `Voy.World`: JSON-safe compilation and reverse-worldgen entry points.

## Run

```sh
mix test
mix run examples/sandbox.exs
```

The ExUnit suite covers the six sandbox deductions, all 48 topography feature combinations, forward/reverse round-trips, and language-neutral semantic golden cases in `test/fixtures/world_cases.json`.
