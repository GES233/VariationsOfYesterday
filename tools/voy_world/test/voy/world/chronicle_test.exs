defmodule Voy.World.ChronicleTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.{Chronicle, TestFixtures}

  test "all supported slices survive a forward/reverse round-trip" do
    for topo <- TestFixtures.all_topographies() do
      case Voy.World.compile_branch(topo) do
        {:ok, [slice]} ->
          {:ok, reverse_slices} = Voy.World.reverse_worldgen(slice["era3"]["fate"])
          signature = TestFixtures.public_feature_signature(slice["topography"])

          assert signature in Enum.map(
                   reverse_slices,
                   &TestFixtures.public_feature_signature(&1["topography"])
                 )

        {:error, _reason} ->
          :ok
      end
    end
  end

  test "coverage distinguishes an absent geology pair from a decided history" do
    classified = Enum.map(TestFixtures.all_topographies(), &{&1, Voy.World.compile_branch(&1)})

    frequencies =
      Enum.frequencies_by(classified, fn
        {_topo, {:ok, _}} -> :ok
        {_topo, {:error, reason}} -> reason
      end)

    assert frequencies == %{ok: 24, unsupported_topography: 24}

    paired = [
      {:steep_fissure_headwater, :active_fault_vein},
      {:karst_sinkhole_headwater, :deep_stress_cavity},
      {:steep_fissure_headwater, :minor_fracture}
    ]

    unsupported =
      for {topo, {:error, reason}} <- classified,
          reason == :unsupported_topography,
          do: TestFixtures.feature_signature(topo)

    assert length(unsupported) == 24

    assert Enum.all?(unsupported, fn signature ->
             {signature[:up_terrain], signature[:up_geology]} not in paired
           end)
  end

  test "five-hop prose agrees with relic and local historical context" do
    for topo <- TestFixtures.all_topographies(),
        {:ok, slices} <- [Voy.World.compile_branch(topo)],
        slice <- slices do
      refute Map.has_key?(slice, "archives")
      relic = slice["era1"]["guardian_relic"] |> String.to_existing_atom()
      fate = slice["era3"]["fate"] |> String.to_existing_atom()

      dossier =
        Voy.World.Witness.render_dossier(
          101,
          slice["branch_id"],
          relic,
          fate,
          %{up: topo[:up_name], pass: topo[:pass_name], down: topo[:down_name]}
        )

      hop1 = hd(dossier.documents)
      hop4 = Enum.at(dossier.documents, 3)

      case relic do
        :carved_diversion_stele ->
          refute hop1.body =~ "铜钲"
          refute hop4.body =~ "铜钲"

        :ancestral_bronze_zheng ->
          refute hop4.body =~ "不可强堵暗河"
      end
    end
  end

  test "cross-era reconciliation matches semantic clues instead of prose" do
    results =
      run_all(fn q ->
        fresh [topo, slice, era1, era2a, era2b, era3] do
          membero(topo, TestFixtures.sample_topographies())
          Chronicle.compile_branch_sliceo(topo, slice)
          propso(topo, down_name: "白水坝")
          propso(slice, era1: era1, era2a: era2a, era2b: era2b, era3: era3)
          propso(era1, guardian_relic: :ancestral_bronze_zheng)
          propso(era2a, gazetteer_precedent: :case_1894_zheng_return_calmed_slide)
          propso(era2b, disturbance_mode: :periodic_shallow_blasting)

          propso(era3,
            fate: :Fate_Assimilated_NewZhai,
            post_withdrawal_polity: :brigade_elder_joint_council
          )

          eq(q, era1: era1, era2a: era2a, era2b: era2b, era3: era3)
        end
      end)
      |> terms()

    assert [result] = results
    assert result[:era2a][:gazetteer_precedent] == :case_1894_zheng_return_calmed_slide
    assert result[:era3][:post_withdrawal_polity] == :brigade_elder_joint_council
    assert result[:era3][:fate] == :Fate_Assimilated_NewZhai
  end

  test "history_through_era2bo stops at the threshold before Era 3" do
    topo = TestFixtures.baishui_topo()

    partials =
      run_all(fn q -> Chronicle.history_through_era2bo(topo, q) end)
      |> terms()

    assert [partial] = partials
    assert Keyword.keys(partial) == [:era1, :era2a, :era2b]
    assert partial[:era1][:guardian_relic] == :ancestral_bronze_zheng
    assert partial[:era2b][:c0_status] == :c0_collapsed
    assert partial[:era2b][:pulse] == :alpha_shock
  end

  test "forward and backward intervention queries resolve all reachable fates and Calm rescues" do
    # Forward query on Baishui (baseline: Fate_Assimilated_NewZhai, c0_collapsed + alpha_shock)
    baishui = TestFixtures.baishui_topo()
    assert {:ok, reachable} = Chronicle.solve_interventions(baishui)

    by_intervention =
      Map.new(reachable, fn row -> {row.intervention, {row.target_fate, row.target_regime}} end)

    assert by_intervention == %{
             no_intervention: {:Fate_Assimilated_NewZhai, :Oscillating},
             reanchor_vessel: {:Fate_Tolerated_FalseAlarm, :Calm},
             dampen_headwater: {:Fate_Deferred_ChronicSeep, :Strained},
             reanchor_and_dampen: {:Fate_Ancestral_ControlGroup, :Calm}
           }

    # Backward query: how to save Baishui (Fate_Assimilated_NewZhai) to reach :Calm?
    assert {:ok, calm_solutions} = Chronicle.solve_interventions(baishui, target_regime: :Calm)

    assert Enum.map(calm_solutions, &{&1.intervention, &1.target_fate}) == [
             {:reanchor_vessel, :Fate_Tolerated_FalseAlarm},
             {:reanchor_and_dampen, :Fate_Ancestral_ControlGroup}
           ]

    # Backward query: how to reach Fate_Ancestral_ControlGroup specifically from Baishui?
    assert {:ok, [ancestral_solution]} =
             Chronicle.solve_interventions(baishui, target_fate: :Fate_Ancestral_ControlGroup)

    assert ancestral_solution.intervention == :reanchor_and_dampen
    assert ancestral_solution.effective_c0 == :c0_intact
    assert ancestral_solution.effective_pulse == :heavy_kick
    assert ancestral_solution.target_regime == :Calm

    # Backward query: how to save a ChronicSeep valley (c0_collapsed + heavy_kick) to reach :Calm?
    mahao_topo = [
      branch_id: :branch_05_mahao,
      up_name: "蚂蟥窠裂隙",
      pass_name: "老熊垭口",
      down_name: "水冬瓜坝",
      up_terrain: :steep_fissure_headwater,
      pass_terrain: :canyon_pass,
      down_terrain: :alluvial_flat,
      up_geology: :minor_fracture,
      accessibility: :tractor_road_accessible
    ]

    assert {:ok, seep_reachable} = Chronicle.solve_interventions(mahao_topo)

    assert Enum.map(seep_reachable, &{&1.intervention, &1.target_fate, &1.target_regime}) == [
             {:no_intervention, :Fate_Deferred_ChronicSeep, :Strained},
             {:reanchor_vessel, :Fate_Ancestral_ControlGroup, :Calm}
           ]

    assert {:ok, [seep_rescue]} =
             Voy.World.solve_interventions(mahao_topo, target_regime: "Calm")

    assert seep_rescue["intervention"] == "reanchor_vessel"
    assert seep_rescue["target_fate"] == "Fate_Ancestral_ControlGroup"
    assert seep_rescue["target_regime"] == "Calm"

    # Burnout valley (Heishui: c0_collapsed + OverwhelmingNegativeSuppression) can also be queried
    heishui = TestFixtures.heishui_topo()

    assert {:ok, [reanchored_burnout]} =
             Chronicle.solve_interventions(heishui, intervention: :reanchor_vessel)

    assert reanchored_burnout.target_fate == :Fate_Ascended_SpatialRift
    assert reanchored_burnout.target_regime == :Strained
  end
end
