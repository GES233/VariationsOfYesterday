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
end
