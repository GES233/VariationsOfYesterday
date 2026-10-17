defmodule Voy.World.Sandbox do
  import Voy.Kanren.Core
  import Voy.Kanren.DSL

  alias Voy.World.{Causality, Chronicle, Schema}

  def run do
    banner("推演一：溯因断案", "由下游提前起峰反推作业者、相位与传导路径")
    show(abduction())

    banner("推演二：一案三表", "同一事故事实经不同世界观透镜投影")
    show(lenses())

    banner("推演三：邪典仪式合成", "只指定推迟尖峰，不预设行动者")
    show(ritual_synthesis())

    banner("推演四：四纪历史正向编译", "由三条支沟地形生成历史、结构与档案")
    show(forward_histories())

    banner("推演五：从结局反推地形", "目标：飞升空间畸变点")
    {:ok, slices} = Voy.World.reverse_worldgen(:Fate_Ascended_SpatialRift)
    show(slices)

    banner("推演六：跨时代考据", "旧方志先例 + 官样通报共同约束真相")
    show(reconciliation())
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

  defp abduction do
    run_all(fn q ->
      fresh [initial, propagation, outcome] do
        Causality.causal_stageso(initial, propagation, outcome)

        propso(outcome,
          settlement: :baishui_flat,
          outcome: :premature_excitation_spike
        )

        eq(q, initial: initial, propagation: propagation, outcome: outcome)
      end
    end)
    |> terms()
  end

  defp lenses do
    run_all(fn q ->
      fresh [initial, propagation, outcome, lens] do
        Causality.causal_stageso(initial, propagation, outcome)

        propso(outcome,
          settlement: :baishui_flat,
          outcome: :premature_excitation_spike
        )

        Causality.lens_projectiono(lens, initial, propagation, outcome, q)
      end
    end)
    |> terms()
  end

  defp ritual_synthesis do
    run_all(fn q ->
      fresh [initial, propagation, outcome, actor, site, phase, nat_sign, inst_sign, item] do
        Causality.causal_stageso(initial, propagation, outcome)

        propso(initial,
          actor: actor,
          action_site: site,
          phase: phase,
          natural_sign: nat_sign,
          instrument_sign: inst_sign,
          action_item: item
        )

        propso(outcome,
          settlement: :baishui_flat,
          outcome: :spike_delayed_by_8_6
        )

        eq(q,
          actor: actor,
          site: site,
          phase: phase,
          natural_sign: nat_sign,
          instrument_sign: inst_sign,
          item: item
        )
      end
    end)
    |> terms()
  end

  defp forward_histories do
    Enum.flat_map(topographies(), fn topo ->
      {:ok, slices} = Voy.World.compile_branch(topo)
      slices
    end)
  end

  defp reconciliation do
    run_all(fn q ->
      fresh [topo, slice, archives, tech_note, council_doc, era3, gazetteer, official] do
        membero(topo, topographies())
        Chronicle.compile_branch_sliceo(topo, slice)
        propso(slice, era3: era3, archives: archives)

        propso(archives,
          gazetteer: gazetteer,
          official_report: official,
          tech_note: tech_note,
          council_document: council_doc
        )

        propso(gazetteer,
          precedent: :case_1894_zheng_return_calmed_slide,
          recorded_settlement: "白水坝"
        )

        propso(official, claimed_cause: :seasonal_rain_mudslide)

        eq(q,
          recovered_truth: tech_note,
          current_council_rules: council_doc,
          current_regime: era3
        )
      end
    end)
    |> terms()
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
