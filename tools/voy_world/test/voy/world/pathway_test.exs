defmodule Voy.World.PathwayTest do
  @moduledoc """
  The five acceptance theorems of design note 05, plus the API shape they rest
  on.

  Every theorem is checked over all twelve `(relic, fate)` pairs, so a single
  hard-coded story cannot pass.
  """

  use ExUnit.Case, async: true

  alias Voy.Kanren.Core
  alias Voy.World.{Canon, Content, Contract, Pathway, Schema, Witness}

  @relic_names %{
    ancestral_bronze_zheng: "铜钲",
    carved_diversion_stele: "分水石堰"
  }

  @names %{up: "野猪箐", pass: "断碑垭口", down: "白水坝"}
  @branch :branch_01_baishui

  @ascended_spec [
    node_id: "node_rift_07",
    branch_id: "branch_rift",
    rule_set: :default_branch_v1,
    fate_constraint: {:require, :Fate_Ascended_SpatialRift},
    names: [node: "黑水支沟", up: "鬼哭漏斗", pass: "断碑垭口", down: "悬棺寨"]
  ]

  @ascended_features [
    up_terrain: :karst_sinkhole_headwater,
    pass_terrain: :canyon_pass,
    down_terrain: :mid_terrace,
    up_geology: :deep_stress_cavity,
    accessibility: :cliff_isolated
  ]

  defp pairs do
    for relic <- Pathway.relics(), fate <- Schema.fates(), do: {relic, fate}
  end

  defp main_documents(dossier) do
    Enum.filter(dossier.documents, &(&1.role == :main))
  end

  defp decoy_documents(dossier) do
    Enum.filter(dossier.documents, &(&1.role == :decoy))
  end

  defp by_hop(documents) do
    Map.new(documents, fn %{hop: hop} = document -> {hop, document} end)
  end

  defp occurrences(body, needle) do
    body |> String.split(needle) |> length() |> Kernel.-(1)
  end

  defp safe_tick_in(body) do
    case Regex.run(~r/第(\d+)格/, body) do
      [_, tick] -> String.to_integer(tick)
      nil -> flunk("no safe tick in: #{body}")
    end
  end

  defp scramble(edges), do: Enum.sort_by(edges, &:erlang.phash2/1)

  # ==== theorem 1: multi-hop synthesis ==== //

  test "every (relic, fate) compiles exactly one five-hop chain" do
    for {relic, fate} <- pairs() do
      assert {:ok, %{chain: chain, decoys: decoys}} = Pathway.compile_chain(relic, fate)
      assert length(chain) == 5
      assert Enum.map(chain, & &1.hop) == [1, 2, 3, 4, 5]
      assert length(decoys) == 2

      assert hd(chain).from == {:relic, relic, expected_damping(relic)}

      assert List.last(chain).to ==
               {:emergent_ritual, fate, {:vessel, :main}, :spike_delayed_or_stabilized}

      for [left, right] <- Enum.chunk_every(chain, 2, 1, :discard) do
        assert left.to == right.from
      end
    end
  end

  test "the declared edges carry the five registers and lenses" do
    for {relic, fate} <- pairs() do
      {:ok, %{chain: chain}} = Pathway.compile_chain(relic, fate)

      assert Enum.map(chain, & &1.doc_kind) == [
               :gazetteer_excerpt,
               :requisition_slip,
               :workshop_card,
               :field_notebook,
               :council_ordinance
             ]

      assert Enum.map(chain, & &1.lens) == [
               :gazetteer,
               :official_logistics,
               workshop_lens(relic),
               :hydro_tech,
               :council_folk
             ]

      for edge <- chain do
        assert Map.keys(edge) |> Enum.sort() ==
                 [:attrs, :doc_kind, :from, :hop, :lens, :relation, :to]
      end
    end
  end

  test "connect_edges recovers the chain from node unification alone" do
    for {relic, fate} <- pairs() do
      {:ok, %{chain: chain}} = Pathway.compile_chain(relic, fate)

      assert [connection] = Pathway.connect_edges(chain)
      assert connection.start == {:relic, relic, expected_damping(relic)}

      assert connection.finish ==
               {:emergent_ritual, fate, {:vessel, :main}, :spike_delayed_or_stabilized}

      assert connection.vessel == {:vessel, :main}
      assert connection.path == chain
    end
  end

  test "edgeo solves every single hop of every pair" do
    for {relic, fate} <- pairs() do
      edges =
        Core.run_all(fn edge -> Pathway.edgeo(relic, fate, edge) end)
        |> Core.terms()

      assert Enum.map(edges, & &1.hop) == [1, 2, 3, 4, 5]
      assert edges == Pathway.edges(relic, fate)
    end
  end

  test "compile_chain reports unknown inputs instead of guessing" do
    assert {:error, {:unknown_relic, :not_a_relic}} =
             Pathway.compile_chain(:not_a_relic, :Fate_Assimilated_NewZhai)

    assert {:error, :unknown_fate} = Pathway.compile_chain(:ancestral_bronze_zheng, :not_a_fate)
  end

  # ==== theorem 2: no shortcut / path ablation ==== //

  test "deleting any single hop disconnects start from finish" do
    for {relic, fate} <- pairs() do
      {:ok, %{chain: chain}} = Pathway.compile_chain(relic, fate)

      for dropped <- 1..5 do
        remaining = Enum.reject(chain, &(&1.hop == dropped))
        assert length(remaining) == 4
        assert Pathway.connect_edges(remaining) == []
      end
    end
  end

  test "chaining obeys from/to terms, not a fate label" do
    for {relic, fate} <- pairs() do
      {:ok, %{chain: chain}} = Pathway.compile_chain(relic, fate)
      other_fate = Enum.find(Schema.fates(), &(&1 != fate))

      labelled = Enum.map(chain, &Map.put(&1, :fate, fate))
      [hop1, hop2, hop3, hop4, hop5] = labelled

      broken = %{
        hop5
        | from: {:modulation_protocol, {:vessel, :main}, :measured_delay_window, other_fate}
      }

      assert Pathway.connect_edges([hop1, hop2, hop3, hop4, broken]) == []
    end
  end

  # ==== theorem 3: decoy rejection ==== //

  test "the two decoy classes declare their breakpoints" do
    for relic <- Pathway.relics() do
      assert [material, destination] = Pathway.decoys(relic)
      assert material.attrs[:decoy_class] == :material_mismatch
      assert material.attrs[:breaks_at] == {3, 4}
      assert material.attrs[:batch] == {:batch, :decoy_a}
      assert material.attrs[:material] in [:plain_grey_iron, :plain_limestone_block]
      assert material.attrs[:anchor] in [{:tau_breaths, 0}, {:siphon_head_m, 0}]
      assert destination.attrs[:decoy_class] == :destination_dead_end
      assert destination.attrs[:breaks_at] == {2, 3}
      assert destination.attrs[:batch] == {:batch, :decoy_b}
      assert destination.attrs[:disposition] == :shipped_out_to_provincial_smelter
    end
  end

  test "five true documents plus two decoys still chain only the true vessel" do
    for {relic, fate} <- pairs() do
      {:ok, %{chain: chain, decoys: decoys}} = Pathway.compile_chain(relic, fate)

      for _reload <- 1..3 do
        shuffled = scramble(chain ++ decoys)
        assert length(shuffled) == 7
        assert Enum.sort(shuffled) == Enum.sort(chain ++ decoys)

        assert [connection] = Pathway.connect_edges(shuffled)
        assert connection.path == chain
        assert connection.vessel == {:vessel, :main}

        assert connection.finish ==
                 {:emergent_ritual, fate, {:vessel, :main}, :spike_delayed_or_stabilized}
      end
    end
  end

  test "the decoy breakpoints are unification failures" do
    for {relic, fate} <- pairs() do
      {:ok, %{chain: chain, decoys: [material, destination]}} = Pathway.compile_chain(relic, fate)
      hop3 = Enum.at(chain, 2)
      hop4 = Enum.at(chain, 3)

      assert Pathway.unifies?(hop4.from, hop4.from)
      refute Pathway.unifies?(material.to, hop4.from)
      refute Pathway.unifies?(destination.to, hop3.from)
    end
  end

  test "decoys cannot repair an ablated chain" do
    for {relic, fate} <- pairs() do
      {:ok, %{chain: chain, decoys: decoys}} = Pathway.compile_chain(relic, fate)
      without_workshop = Enum.reject(chain, &(&1.hop == 3))

      assert Pathway.connect_edges(without_workshop ++ decoys) == []

      without_notebook = Enum.reject(chain, &(&1.hop == 4))
      assert Pathway.connect_edges(without_notebook ++ decoys) == []
    end
  end

  # ==== theorem 4: seed isolation and internal coherence ==== //

  test "different saves draw different, internally isolated credentials" do
    for {relic, fate} <- pairs() do
      first = Witness.bind(101, @branch, relic, fate)
      second = Witness.bind(202, @branch, relic, fate)

      assert {first.batch_id, first.vessel_name, first.safe_tick} !=
               {second.batch_id, second.vessel_name, second.safe_tick}

      batches = [
        first.batch_id,
        first.decoys.material_mismatch.batch_id,
        first.decoys.destination_dead_end.batch_id
      ]

      assert length(Enum.uniq(batches)) == 3

      vessels = [
        first.vessel_name,
        first.decoys.material_mismatch.vessel_name
      ]

      assert length(Enum.uniq(vessels)) == 2
      assert first.vessel_code != first.decoys.material_mismatch.vessel_code
    end
  end

  test "each dossier repeats its identifiers and tick verbatim" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names)
      binding = dossier.binding
      documents = dossier.documents
      assert length(documents) == 7
      main = main_documents(dossier)
      assert length(main) == 5
      hops = by_hop(main)

      assert String.contains?(hops[2].body, binding.batch_id)
      assert String.contains?(hops[3].body, binding.batch_id)

      assert String.contains?(hops[3].body, binding.vessel_name)
      assert String.contains?(hops[3].body, binding.vessel_code)
      assert String.contains?(hops[5].body, binding.vessel_name)
      assert String.contains?(hops[5].body, binding.vessel_code)

      assert safe_tick_in(hops[4].body) == binding.safe_tick
      assert safe_tick_in(hops[5].body) == binding.safe_tick

      assert binding.safe_tick > 0
    end
  end

  test "every safe tap is an exact delayed measurement for metal resonance" do
    contract = Contract.load!()
    {:ok, measured} = Contract.safe_ticks(contract, :alpha_metal_resonance)
    assert measured != []
    assert Enum.map(measured, & &1.tick) == Witness.safe_ticks()

    for {relic, fate} <- pairs(), seed <- [101, 202, 303, 404] do
      binding = Witness.bind(seed, @branch, relic, fate)
      row = Enum.find(measured, &(&1.tick == binding.safe_tick))
      assert row.outcome == :delayed
      assert binding.safe_delay == row.delta
      assert binding.safe_window == row.window
      assert_in_delta binding.safe_phase, row.phase, 1.0e-12
    end
  end

  test "witnesses refuse inputs that have no pathway" do
    assert_raise ArgumentError, fn ->
      Witness.bind(101, @branch, :not_a_relic, :Fate_Assimilated_NewZhai)
    end

    assert_raise ArgumentError, fn ->
      Witness.bind(101, @branch, :ancestral_bronze_zheng, :not_a_fate)
    end
  end

  test "place names default to neutral wording when the caller supplies none" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate)
      assert length(dossier.documents) == 7
      assert hd(main_documents(dossier)).body =~ "隘口"
      refute Enum.any?(dossier.documents, &String.contains?(&1.body, "{{"))
    end
  end

  # ==== theorem 5: epistemic locality linter ==== //

  test "the batch number appears exactly twice, only in hops 2 and 3" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names)
      binding = dossier.binding
      main = main_documents(dossier)
      hops = by_hop(main)

      positions =
        for document <- main, occurrences(document.body, binding.batch_id) > 0, do: document.hop

      assert positions == [2, 3]
      assert Enum.sum(Enum.map(main, &occurrences(&1.body, binding.batch_id))) == 2
      refute String.contains?(hops[1].body, binding.batch_id)
      refute String.contains?(hops[4].body, binding.batch_id)
      refute String.contains?(hops[5].body, binding.batch_id)
    end
  end

  test "the carrier drawing code appears exactly twice, only in hops 3 and 5" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names)
      binding = dossier.binding
      main = main_documents(dossier)
      hops = by_hop(main)

      positions =
        for document <- main,
            occurrences(document.body, binding.vessel_code) > 0,
            do: document.hop

      assert positions == [3, 5]
      assert Enum.sum(Enum.map(main, &occurrences(&1.body, binding.vessel_code))) == 2
      refute String.contains?(hops[1].body, binding.vessel_code)
      refute String.contains?(hops[2].body, binding.vessel_code)
      refute String.contains?(hops[4].body, binding.vessel_code)
    end
  end

  test "the measured duty-cycle inversion appears only in hop 4" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names)
      main = main_documents(dossier)
      hops = by_hop(main)

      for marker <- ["28.1%", "60.6%"] do
        positions =
          for document <- main, String.contains?(document.body, marker), do: document.hop

        assert positions == [4]
      end

      assert hops[4].body =~ "+15.62"
      assert occurrences(hops[4].body, "28.1%") == 1
      assert occurrences(hops[4].body, "60.6%") == 1
    end
  end

  test "no single card joins the relic name, the batch and the carrier code" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names)
      binding = dossier.binding
      relic_name = Map.fetch!(@relic_names, relic)

      for document <- main_documents(dossier) do
        joined =
          String.contains?(document.body, relic_name) and
            String.contains?(document.body, binding.batch_id) and
            String.contains?(document.body, binding.vessel_code)

        refute joined, "hop #{document.hop} knows relic, batch and carrier at once"
      end
    end
  end

  test "each register refuses the vocabulary of the other four" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names)
      binding = dossier.binding
      hops = by_hop(main_documents(dossier))
      relic_name = Map.fetch!(@relic_names, relic)

      # hop 1: gazetteer fragment, no batch, carrier, team or modern year
      refute String.contains?(hops[1].body, binding.batch_id)
      refute String.contains?(hops[1].body, binding.vessel_name)
      refute String.contains?(hops[1].body, binding.vessel_code)
      refute String.contains?(hops[1].body, "302队")
      refute Regex.match?(~r/(?:19|20)\d{2}/, hops[1].body)

      # hop 2: requisition and batch, no finished carrier, phase or council
      refute String.contains?(hops[2].body, binding.vessel_name)
      refute String.contains?(hops[2].body, binding.vessel_code)
      refute String.contains?(hops[2].body, "28.1%")
      refute String.contains?(hops[2].body, "60.6%")
      refute String.contains?(hops[2].body, "议事会")

      # hop 3: batch, carrier and its own echo, no relic, blast or rite
      refute String.contains?(hops[3].body, relic_name)
      refute String.contains?(hops[3].body, "点炮")
      refute String.contains?(hops[3].body, "爆破")
      refute String.contains?(hops[3].body, "祭祀")
      refute String.contains?(hops[3].body, "红绸")

      # hop 4: measurement only, no batch, carrier code or council
      refute String.contains?(hops[4].body, binding.batch_id)
      refute String.contains?(hops[4].body, binding.vessel_code)
      refute String.contains?(hops[4].body, "议事会")

      # hop 5: the standing rule, no batch, Qing survey or explanation
      refute String.contains?(hops[5].body, binding.batch_id)
      refute String.contains?(hops[5].body, "清丈")
      refute String.contains?(hops[5].body, "28.1%")
      refute String.contains?(hops[5].body, "60.6%")
    end
  end

  test "decoy documents never carry the true identifiers" do
    for {relic, fate} <- pairs() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names)
      binding = dossier.binding
      decoys = decoy_documents(dossier)
      assert length(decoys) == 2

      for document <- decoys do
        refute String.contains?(document.body, binding.batch_id)
        refute String.contains?(document.body, binding.vessel_code)
        refute String.contains?(document.body, "28.1%")
        refute String.contains?(document.body, "60.6%")
      end
    end
  end

  # ==== theorem 6: c0_status unification firewall and 6-fate regime differentiation ==== //

  test "c0_collapsed workshop edges cannot unify with c0_intact field notebooks" do
    for relic <- Pathway.relics() do
      {:ok, %{chain: collapsed}} = Pathway.compile_chain(relic, :Fate_Assimilated_NewZhai)
      {:ok, %{chain: intact}} = Pathway.compile_chain(relic, :Fate_Tolerated_FalseAlarm)

      [c1, c2, c3, _c4, _c5] = collapsed
      [_i1, _i2, _i3, i4, i5] = intact

      refute Pathway.unifies?(c3.to, i4.from)
      assert Pathway.connect_edges([c1, c2, c3, i4, i5]) == []
    end
  end

  test "the six fates render distinct physical regimes and respect c0_intact preservation" do
    for relic <- Pathway.relics() do
      dossiers =
        Map.new(Schema.fates(), fn fate ->
          {fate, Witness.render_dossier(101, @branch, relic, fate, @names)}
        end)

      hop4_bodies =
        Enum.map(Schema.fates(), fn fate ->
          by_hop(main_documents(dossiers[fate]))[4].body
        end)

      hop5_bodies =
        Enum.map(Schema.fates(), fn fate ->
          by_hop(main_documents(dossiers[fate]))[5].body
        end)

      assert length(Enum.uniq(hop4_bodies)) == 6
      assert length(Enum.uniq(hop5_bodies)) == 6

      for intact_fate <- [
            :Fate_Ancestral_ControlGroup,
            :Fate_Tolerated_FalseAlarm,
            :Fate_Ascended_SpatialRift
          ] do
        hop2 = by_hop(main_documents(dossiers[intact_fate]))[2].body
        assert hop2 =~ "留存原地未拆"
        refute hop2 =~ "全数拆"
      end

      burnout_hop5 = by_hop(main_documents(dossiers[:Fate_Annihilated_BurnoutZone]))[5].body
      assert burnout_hop5 =~ "灰白"
      assert burnout_hop5 =~ "封禁"
      refute burnout_hop5 =~ "推迟当旬落石"

      false_alarm_hop4 = by_hop(main_documents(dossiers[:Fate_Tolerated_FalseAlarm]))[4].body
      assert false_alarm_hop4 =~ "四十七次"

      rift_hop5 = by_hop(main_documents(dossiers[:Fate_Ascended_SpatialRift]))[5].body
      assert rift_hop5 =~ "黑影裂隙"

      seep_hop5 = by_hop(main_documents(dossiers[:Fate_Deferred_ChronicSeep]))[5].body
      assert seep_hop5 =~ "慢渗"
    end
  end

  # ==== canon static checker (design note 05 §3) ==== //

  test "Canon.check_pathways accepts every declared pair and seed" do
    assert Canon.check_pathways() == :ok
    assert Canon.check_pathways([101, 202, 303, 404]) == :ok
    assert Canon.check_pathways([]) == :ok
  end

  # ==== seeded node dossier (design note 05 §1) ==== //

  test "normalize_spec treats the seed as optional and rejects non-integers" do
    assert {:ok, spec} = Voy.World.Node.normalize_spec(@ascended_spec)
    assert spec.seed == nil

    assert {:ok, %{seed: 42}} =
             Voy.World.Node.normalize_spec(Keyword.put(@ascended_spec, :seed, 42))

    assert {:error, :invalid_seed} =
             Voy.World.Node.normalize_spec(Keyword.put(@ascended_spec, :seed, "42"))
  end

  test "compile_node requires a seed and mounts the sole five-hop narrative" do
    assert {:error, :missing_seed} = Voy.World.compile_node(@ascended_spec, @ascended_features)

    assert {:ok, [seeded]} =
             Voy.World.compile_node(Keyword.put(@ascended_spec, :seed, 303), @ascended_features)

    refute Map.has_key?(seeded, "archives")

    dossier = seeded["dossier"]
    assert dossier["seed"] == 303
    assert dossier["relic"] == seeded["era1"]["guardian_relic"]
    assert dossier["fate"] == seeded["era3"]["fate"]
    assert length(dossier["documents"]) == 7
    assert length(Enum.filter(dossier["documents"], &(&1["role"] == "main"))) == 5
    assert length(Enum.filter(dossier["documents"], &(&1["role"] == "decoy"))) == 2
    assert dossier["binding"]["safe_tick"] in Witness.safe_ticks()
  end

  # ==== content preview micro-chain section ==== //

  test "preview renders history and the five-hop dossier without global summaries" do
    assert {:ok, text} = Content.preview("baishui", seed: 202)
    assert text =~ "【节点档案预览：baishui（白水坝）】"
    assert text =~ "结局：Fate_Assimilated_NewZhai"
    assert text =~ "【五跳微链卷宗（seed=202，含2份同车间干扰单据）】"
    refute text =~ "① 旧州府残卷"
    refute text =~ "② 垦殖总场官样通报"

    dossier =
      Witness.render_dossier(
        202,
        :branch_01_baishui,
        :ancestral_bronze_zheng,
        :Fate_Assimilated_NewZhai,
        %{up: "野猪箐裂隙", pass: "断碑垭口", down: "白水坝"}
      )

    for document <- dossier.documents do
      assert text =~ document.body
      assert text =~ document.title
    end

    assert {:ok, default_text} = Content.preview("baishui")
    assert default_text =~ "seed=101"

    assert {:ok, other} = Content.preview("baishui", seed: 303)
    assert other != default_text
    assert other =~ "seed=303"

    assert {:ok, all} = Content.preview("all", seed: 404)
    assert length(String.split(all, "【五跳微链卷宗（seed=404，含2份同车间干扰单据）】")) == 7

    assert {:error, message} = Content.preview("baishui", seed: "202")
    assert message =~ "seed 必须是整数"
  end

  # ==== determinism ==== //

  test "repeated calls return the same dossier and the same connections" do
    for {relic, fate} <- pairs() do
      first = Witness.render_dossier(101, @branch, relic, fate, @names)
      second = Witness.render_dossier(101, @branch, relic, fate, @names)
      assert first == second

      {:ok, %{chain: chain, decoys: decoys}} = Pathway.compile_chain(relic, fate)
      shuffled = scramble(chain ++ decoys)
      assert Pathway.connect_edges(shuffled) == Pathway.connect_edges(shuffled)
    end
  end

  defp expected_damping(:ancestral_bronze_zheng), do: :acoustic_damping
  defp expected_damping(:carved_diversion_stele), do: :karst_siphon_relief

  defp workshop_lens(:ancestral_bronze_zheng), do: :workshop_tech
  defp workshop_lens(:carved_diversion_stele), do: :hydro_tech
end
