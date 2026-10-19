# Prints the three M1 contracts: numeric stock, export bundle, fact log.
#
#   mix run examples/contracts_demo.exs

defmodule Voy.World.ContractsDemo do
  alias Voy.World.{Export, Facts, Profile}

  @topography [
    branch_id: :branch_01_baishui,
    up_name: "野猪箐裂隙",
    pass_name: "断碑垭口",
    down_name: "白水坝",
    up_terrain: :steep_fissure_headwater,
    pass_terrain: :canyon_pass,
    down_terrain: :alluvial_flat,
    up_geology: :active_fault_vein,
    accessibility: :tractor_road_accessible
  ]

  def run do
    profile = Profile.default()

    banner("stock", "the bands the rule layer may name")
    IO.inspect(Profile.describe(profile), limit: :infinity)
    IO.puts("lag 5.0 -> #{inspect(Profile.lag_band(profile, 5.0))}")
    IO.puts("lag 7.5 -> #{inspect(Profile.lag_band(profile, 7.5))} (declared gap)")

    banner("export", "one branch as a versioned bundle")
    {:ok, bundle} = Export.export_branch(@topography)
    :ok = Export.validate(bundle)
    {:ok, json} = Export.to_json(bundle)

    IO.puts(
      "schema_version=#{bundle["schema_version"]} " <>
        "rule_set=#{bundle["rule_set"]} bytes=#{byte_size(json)}"
    )

    IO.puts(String.slice(json, 0, 280) <> " ...")
    node = bundle["nodes"] |> hd()
    IO.inspect(Map.take(node, ["node_id", "sites", "structures"]), limit: :infinity)

    banner("facts", "a prediction, then its settlement")
    log = Facts.new()

    {:ok, seen, log} =
      Facts.record(log,
        tick: 120,
        kind: :saw,
        actor: {:crowd, "brigade_04"},
        site: "branch_01_baishui/up",
        props: [regime: :Strained, phase: :RelativeRefractory]
      )

    {:ok, shot, log} =
      Facts.record(log,
        tick: 124,
        kind: :did,
        actor: {:player, "u1"},
        site: "branch_01_baishui/up",
        props: [item: "2号岩石硝铵炸药点炮"],
        caused_by: seen.id,
        evidence: [seen.id]
      )

    {:ok, forecast, log} =
      Facts.record(log,
        tick: 125,
        kind: :predicted,
        actor: {:engine, "mandate"},
        site: "branch_01_baishui/down",
        props: [claim: "下游 5 刻内提前起峰", due_tick: 130],
        caused_by: shot.id,
        evidence: [seen.id]
      )

    IO.inspect(Facts.pending(log), label: "pending before settlement")

    {:ok, verdict, log} = Facts.settle(log, forecast.id, :held, tick: 129)

    IO.inspect(
      Enum.map(Facts.entries(log), &Map.take(&1, [:id, :tick, :kind, :site])),
      limit: :infinity
    )

    {:ok, chain} = Facts.ancestors(log, verdict.id)
    IO.puts("chain: " <> Enum.map_join(chain, " -> ", &"#{&1.kind}##{&1.id}"))
    IO.puts("pending after settlement: #{inspect(Facts.pending(log))}")
  end

  defp banner(title, subtitle) do
    IO.puts("\n==================================================================")
    IO.puts("【#{title}】#{subtitle}")
    IO.puts("==================================================================")
  end
end

Voy.World.ContractsDemo.run()
