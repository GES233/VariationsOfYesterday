# Export every topography combination the rule layer can be asked about,
# together with the classification the compiler actually returns.
#
#     mix run examples/export_topography_cases.exs
#
# writes `docs/diagrams/topography_cases.json`.

alias Voy.World.{Schema, Witness}

out =
  case System.get_env("VOY_TOPO_OUT") do
    nil -> Path.expand("../docs/diagrams/topography_cases.json", __DIR__)
    custom -> Path.expand(custom)
  end

File.mkdir_p!(Path.dirname(out))

base = [branch_id: :atlas, up_name: "上游", pass_name: "隘口", down_name: "聚落"]

combinations =
  for up <- Schema.feature_domain(:up_terrain),
      pass <- Schema.feature_domain(:pass_terrain),
      down <- Schema.feature_domain(:down_terrain),
      geology <- Schema.feature_domain(:up_geology),
      access <- Schema.feature_domain(:accessibility) do
    base ++
      [
        up_terrain: up,
        pass_terrain: pass,
        down_terrain: down,
        up_geology: geology,
        accessibility: access
      ]
  end

features = Schema.feature_keys()

row = fn topo ->
  {:ok, [slice]} = Voy.World.compile_branch(topo)
  relic = String.to_existing_atom(slice["era1"]["guardian_relic"])
  fate = String.to_existing_atom(slice["era3"]["fate"])
  dossier = Witness.render_dossier(101, :atlas, relic, fate, %{up: "上游", pass: "隘口", down: "聚落"})

  %{
    "features" =>
      Map.new(features, fn key -> {Atom.to_string(key), to_string(Keyword.fetch!(topo, key))} end),
    "status" => "compiled",
    "fate" => slice["era3"]["fate"],
    "regime" => slice["era3"]["final_regime"],
    "phase_rule" => slice["era3"]["phase_rule"],
    "polity" => slice["era3"]["post_withdrawal_polity"],
    "manifestation" => slice["era3"]["manifestation"],
    "relic" => slice["era1"]["guardian_relic"],
    "damping_mode" => slice["era1"]["damping_mode"],
    "precedent" => slice["era2a"]["gazetteer_precedent"],
    "attempt" => slice["era2a"]["attempt"],
    "c0" => slice["era2b"]["c0_status"],
    "disturbance" => slice["era2b"]["disturbance_mode"],
    "stationed_team" => slice["era2b"]["stationed_team"],
    "structures" => slice["structures"],
    "dossier" => %{
      "batch_id" => dossier.binding.batch_id,
      "vessel_name" => dossier.binding.vessel_name,
      "vessel_code" => dossier.binding.vessel_code,
      "safe_tick" => dossier.binding.safe_tick,
      "safe_delay" => dossier.binding.safe_delay
    }
  }
end

rows =
  Enum.map(combinations, fn topo ->
    case Voy.World.compile_branch(topo) do
      {:ok, [_]} ->
        row.(topo)

      {:error, reason} ->
        %{
          "features" =>
            Map.new(features, fn key ->
              {Atom.to_string(key), to_string(Keyword.fetch!(topo, key))}
            end),
          "status" => to_string(reason)
        }
    end
  end)

payload = %{
  "generated_by" => "examples/export_topography_cases.exs",
  "fates" => Enum.map(Schema.fates(), &to_string/1),
  "feature_domains" =>
    Map.new(features, fn key ->
      {Atom.to_string(key), Enum.map(Schema.feature_domain(key), &to_string/1)}
    end),
  "counts" =>
    rows
    |> Enum.frequencies_by(& &1["status"])
    |> Map.new(fn {status, count} -> {status, count} end),
  "cases" => rows
}

File.write!(out, JSON.encode!(payload))
IO.puts("wrote #{out} (#{length(rows)} combinations)")
IO.inspect(payload["counts"], label: "status counts")
