# Voy World

A dependency-free Elixir prototype of the Variations of Yesterday relational world model. It is a design and verification tool, not a runtime dependency of the Minecraft mod.

## Layout

- `Voy.Kanren`: microKanren kernel, disequality constraints, open attributes, and thin DSL.
- `Voy.World.Causality`: causal stages, observations, ritual synthesis, and narrative lenses.
- `Voy.World.Chronicle`: the shared forward/reverse branch-history relation.
- `Voy.World.Node`: stable node identities, rule sets, fate constraints, and name-independent semantic solving.
- `Voy.World.Naming`: late display-name binding and structure/archive materialization.
- `Voy.World.Projection`: structure and archive projections.
- `Voy.World`: JSON-safe branch and node entry points.

## Node API

A node specification separates stable identity, generation constraints, and display names:

```elixir
spec = [
  node_id: "node_rift_07",
  branch_id: "branch_rift",
  rule_set: :default_branch_v1,
  fate_constraint: {:require, :Fate_Ascended_SpatialRift},
  names: [node: "黑水支沟", up: "鬼哭漏斗", pass: "断碑垭口", down: "悬棺寨"]
]

{:ok, candidates} = Voy.World.reverse_node(spec)
{:ok, record} = Voy.World.materialize_node(spec, hd(candidates))
```

`solve_node/2` also accepts partial topography constraints. `compile_node/2` compiles a complete named node directly, while `reverse_node/1` returns unnamed semantic candidates with stable site IDs. Fate constraints support `:any`, `{:require, fate}`, and `{:one_of, fates}`. The original branch API remains available for compatibility and golden-case verification.

## Run

```sh
mix test
mix run examples/sandbox.exs
```

The ExUnit suite covers the six sandbox deductions, all 48 topography feature combinations, forward/reverse round-trips, and language-neutral semantic golden cases in `test/fixtures/world_cases.json`.
