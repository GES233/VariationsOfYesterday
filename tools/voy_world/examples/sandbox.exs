defmodule Voy.World.Sandbox do
  alias Voy.World.{Ascribe, Observation, Pathway, Schema, Slice, Witness}

  def run do
    banner("推演一：五跳合一", "由五份局部单据恢复唯一证据链")
    show(pathway())

    banner("推演二：干扰排除", "材质不符与外运单据不能补成故事")
    show(decoy_rejection())

    banner("推演三：种子物证", "同一历史在不同存档使用不同批号、载体与实测勘定格")
    show(seeded_witnesses())

    banner("推演四：四纪历史正向编译", "由三条支沟地形生成历史与结构")
    show(forward_histories())

    banner("推演五：从结局反推地形", "目标：飞升空间畸变点")
    {:ok, slices} = Voy.World.reverse_worldgen(:Fate_Ascended_SpatialRift)
    show(slices)

    banner("推演六：数值证据反演", "三方观测字段在声明候选空间内唯一恢复隐藏解释")
    show(numeric_abduction())
  end

  def topographies do
    [
      Schema.topology(
        branch_id: :branch_01_baishui,
        up_name: "野猪箐裂隙",
        pass_name: "断碑垭口",
        down_name: "白水坝",
        up_terrain: :steep_fissure_headwater,
        pass_terrain: :canyon_pass,
        down_terrain: :alluvial_flat,
        up_geology: :active_fault_vein,
        accessibility: :tractor_road_accessible
      ),
      Schema.topology(
        branch_id: :branch_02_heishui,
        up_name: "鬼哭漏斗",
        pass_name: "锁龙峡",
        down_name: "黑水坪",
        up_terrain: :karst_sinkhole_headwater,
        pass_terrain: :karst_gorge,
        down_terrain: :alluvial_flat,
        up_geology: :deep_stress_cavity,
        accessibility: :tractor_road_accessible
      ),
      Schema.topology(
        branch_id: :branch_03_nuoliang,
        up_name: "老君林场",
        pass_name: "响水涧口",
        down_name: "糯良古寨",
        up_terrain: :steep_fissure_headwater,
        pass_terrain: :canyon_pass,
        down_terrain: :mid_terrace,
        up_geology: :minor_fracture,
        accessibility: :cliff_isolated
      )
    ]
  end

  defp pathway do
    {:ok, %{chain: chain}} =
      Pathway.compile_chain(:ancestral_bronze_zheng, :Fate_Assimilated_NewZhai)

    Pathway.connect_edges(chain)
  end

  defp decoy_rejection do
    {:ok, %{chain: chain, decoys: decoys}} =
      Pathway.compile_chain(:ancestral_bronze_zheng, :Fate_Assimilated_NewZhai)

    %{
      mixed_documents: Pathway.connect_edges(chain ++ decoys),
      decoys_alone: Pathway.connect_edges(decoys),
      ablated: Pathway.connect_edges(tl(chain))
    }
  end

  defp seeded_witnesses do
    for seed <- [101, 202] do
      dossier =
        Witness.render_dossier(
          seed,
          :branch_01_baishui,
          :ancestral_bronze_zheng,
          :Fate_Assimilated_NewZhai,
          %{up: "野猪箐", pass: "断碑垭口", down: "白水坝"}
        )

      Map.take(dossier.binding, [
        :seed,
        :batch_id,
        :vessel_name,
        :safe_tick,
        :safe_phase,
        :safe_delay
      ])
    end
  end

  defp forward_histories do
    Enum.flat_map(topographies(), fn topo ->
      {:ok, slices} = Voy.World.compile_branch(topo)
      slices
    end)
  end

  defp numeric_abduction do
    contract = Voy.World.Contract.load!()
    hidden = %{c0_state: :c0_collapsed, pulse_kind: :alpha_shock, safe_phase_window: :relative}

    evidence =
      hidden
      |> Slice.trace(contract)
      |> Observation.observe_all()
      |> Observation.evidence()

    Ascribe.explain(evidence, contract: contract)
  end

  defp banner(title, subtitle) do
    IO.puts("\n==================================================================")
    IO.puts("【#{title}】")
    IO.puts(subtitle)
    IO.puts("==================================================================")
  end

  defp show([]), do: raise("sandbox query returned no solutions")
  defp show(results), do: IO.inspect(results, pretty: true, width: 100, limit: :infinity)
end

Voy.World.Sandbox.run()
