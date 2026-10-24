# Voy World

A dependency-free Elixir prototype of the Variations of Yesterday relational world model. It is a design and verification tool, not a runtime dependency of the Minecraft mod.

## Layout

- `Voy.Kanren`: microKanren kernel, disequality constraints, open attributes, and thin DSL.
- `Voy.World.Canon`: linter for a compiled canon (names, dates, attribution split, uniqueness).
- `Voy.World.Causality`: causal stages, observations, ritual synthesis, and narrative lenses.
- `Voy.World.Chronicle`: the shared forward/reverse branch-history relation.
- `Voy.World.Content`: the authoring pack loader, linter, and archive assembly.
- `Voy.World.Export`: versioned JSON bundle (`schema_version`) for one or more nodes.
- `Voy.World.Facts`: ordered, append-only log of observations, actions, predictions and settlements.
- `Voy.World.Node`: stable node identities, rule sets, fate constraints, and name-independent semantic solving.
- `Voy.World.Naming`: late display-name binding and structure/archive materialization.
- `Voy.World.Profile`: numeric stock (lag bands, phase windows, dose ranges, regime cut points).
- `Voy.World.Projection`: structure and archive projections.
- `Voy.World.Texts`: every document, decree and explanation as data, rendered from the timeline.
- `Voy.World.Timeline`: dated anchors and the date forms prose is allowed to use.
- `Voy.World`: JSON-safe branch and node entry points.

## Contracts (M1)

Three boundary objects carry the port. They are additive: none of them changes a
rule, and the existing relations keep working unchanged.

1. **`Voy.World.Profile`** owns the numbers the symbolic layer is allowed to
   name. A measured lag is mapped onto a declared band (`:tau_immediate`,
   `:tau_5`, `:tau_10`) instead of a rule comparing floats; declared gaps stay
   out of band; `describe/1` renders the whole stock as JSON. Swapping the stock
   moves the same measurement into another band without touching a rule, and
   `regime/3` is the reference partition the JVM `MandateReadout` must
   reproduce.
2. **`Voy.World.Export`** is the wire format: `schema_version`, `rule_set`,
   the stock that produced the canon, and normalized node records (structures
   as `site`/`description`, tagged tuples as `tag`/`value`). Deterministic, no
   timestamps, validated against a JSON round-trip so atoms and tuples cannot
   leak into the contract.
3. **`Voy.World.Facts`** is the time axis: entries carry a tick, a site, an
   actor and open attributes; `caused_by`/`evidence` build chains; predictions
   must carry a `claim` and a `due_tick` and stay `pending/1` until a
   settlement with `outcome: :held | :failed | :partial` closes them.

```sh
mix run examples/contracts_demo.exs
```

## Prose and dates as data (M2a)

The rules carry no prose of their own: they render it. `Voy.World.Texts` holds
the code-side templates (`structure_*` for place-attached site descriptions and
`lens_*` for the runtime three-lens causal explanations), and
`Voy.World.Timeline` owns the dated anchors and the three forms prose may use
(Gregorian, two-digit field notation, reign form). Both `Voy.World.Texts` and
`Voy.World.Content` share the `{{name}}` placeholder syntax; rendering a date is
the only way a year can reach a player-facing string, and rendering refuses to
leave a placeholder unbound.

`Voy.World.Canon` then lints the result:

- the timeline, code-side templates, authoring content pack, and fate reachability
  are all coherent (`check_timeline/0`, `check_texts/0`, `check_content/1`,
  `check_fates/0`);
- structures and documents name the same sites as the topography;
- every year written into prose belongs to the timeline, in whichever form;
- each history carries a corroborating document (the gazetteer precedent the
  field notebook cites) and a contradicting one (the official report never
  adopts the truth's cause tag);
- a complete topography compiles to exactly one history, so reverse queries are
  unambiguous.

`Canon.check_corpus/1` runs all of it over a list of topographies and reports
`:ok` / `:unsupported` / issues per shape, which is what the test suite does for
all 48 feature combinations (8 supported, 40 undecided, 0 inconsistent).

## Authoring pack (M2b)

Writers, including non-technical ones, edit `content/` only:

```
content/claims.tsv             one row per causal claim (who may cite it)
content/documents/**/*.md      one file per document (slot, applicability, prose)
content/README.md              the writer's guide (Chinese)
```

`Voy.World.Content` loads and validates the pack; `Voy.World.Projection`
translates the loaded pack into relational goals so both forward history
compilation and reverse clue-reconciliation queries select directly from
`content/` without duplicate code-side archive templates.

`mix voy.content` provides the writer workflow:

```sh
mix voy.content lint
mix voy.content preview [baishui|heishui|xuanguan|nuoliang|all]
mix voy.content template <gazetteer|official_report|tech_note|council_document>
```

`mix voy.content lint` prints author-facing errors (Chinese, `file:line`, legal
values, and closest-value suggestions); `mix test` treats them as assertions.
CI (`.github/workflows/voy_world.yml`) runs formatting, the suite, and the pack
lint on every pull request, so the author's compiler is the PR itself. The
design record lives in `docs/design-note-03-authoring-pipeline.md`.


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

## Diagrams

The current topography, history, fate, and solver relations are illustrated in:

- [Landform to Chronicle](docs/diagrams/01_landform_to_chronicle.png)
- [Fate-to-landform candidate matrix](docs/diagrams/02_fate_to_landform_matrix.png)
- [Bidirectional microKanren solving](docs/diagrams/03_microkanren_bidirectional_solver.png)

Regenerate them with Python, matplotlib, and an installed CJK font:

```sh
python examples/render_landform_diagrams.py
```

## Run

```sh
mix test
mix voy.content lint
mix run examples/sandbox.exs
mix run examples/contracts_demo.exs
```

The ExUnit suite covers the six sandbox deductions, all 48 topography feature combinations, forward/reverse round-trips, language-neutral semantic golden cases in `test/fixtures/world_cases.json`, the three M1 contracts (stock coverage of the frozen rule vocabulary, export schema and JSON round-trip, fact-log ordering and prediction settlement), the M2a canon linter (names, dates, attribution split, uniqueness), and the M2b content pack (parsing, lint failures, and byte-identical parity with the rules).
