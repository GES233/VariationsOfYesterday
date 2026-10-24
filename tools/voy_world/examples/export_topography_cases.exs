# Export every topography combination the rule layer can be asked about,
# together with the classification the compiler actually returns.
#
# The output is the single source of truth for the diagram renderer: nothing in
# `render_topography_atlas.py` is allowed to hardcode a fate, a precedent or a
# description, so a picture cannot drift away from the rules.
#
#     mix run examples/export_topography_cases.exs
#
# writes `docs/diagrams/topography_cases.json`.

alias Voy.World.Schema

out = Path.expand("../docs/diagrams/topography_cases.json", __DIR__)
File.mkdir_p!(Path.dirname(out))

pack = Voy.World.Content.default_pack()

claim_label = fn tag ->
  case Map.fetch(pack.claims, tag) do
    {:ok, claim} -> claim.label
    :error -> tag
  end
end

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
  {:ok, public} = Voy.World.compile_branch(topo)
  [slice] = public

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
    "archives" => %{
      "gazetteer_title" => slice["archives"]["gazetteer"]["title"],
      "official_claim" => slice["archives"]["official_report"]["claimed_cause"],
      "official_label" =>
        claim_label.("claim." <> slice["archives"]["official_report"]["claimed_cause"]),
      "official_summary" => slice["archives"]["official_report"]["summary"],
      "tech_claim" => slice["archives"]["tech_note"]["true_cause"],
      "tech_label" => claim_label.("claim." <> slice["archives"]["tech_note"]["true_cause"]),
      "tech_corroborated" => slice["archives"]["tech_note"]["corroborated_gazetteer"],
      "tech_note" => slice["archives"]["tech_note"]["note"],
      "council_title" => slice["archives"]["council_document"]["title"],
      "governance" => slice["archives"]["council_document"]["governance"]
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
