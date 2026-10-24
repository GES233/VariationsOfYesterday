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

  test "archive prose agrees with relic and gazetteer context" do
    for topo <- TestFixtures.all_topographies(),
        {:ok, slices} <- [Voy.World.compile_branch(topo)],
        slice <- slices do
      relic = slice["era1"]["guardian_relic"]
      tech_note = slice["archives"]["tech_note"]["note"]
      gazetteer_title = slice["archives"]["gazetteer"]["title"]

      case relic do
        "carved_diversion_stele" ->
          refute tech_note =~ "铜钲"
          refute gazetteer_title =~ "铜钲"

        "ancestral_bronze_zheng" ->
          refute tech_note =~ "不可强堵暗河"
      end

      refute inspect(slice) =~ "断碑垭口木架"
    end
  end

  test "cross-era reconciliation matches semantic clues instead of prose" do
    results =
      run_all(fn q ->
        fresh [topo, slice, archives, tech_note, council_doc, era3, gazetteer, official] do
          membero(topo, TestFixtures.sample_topographies())
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
          eq(q, tech_note: tech_note, council_document: council_doc, era3: era3)
        end
      end)
      |> terms()

    assert [result] = results

    assert result[:tech_note][:true_cause] == :c0_removed_and_vulnerable_window_blasting
    assert result[:council_document][:governance] == :brigade_elder_joint_council
    assert result[:era3][:fate] == :Fate_Assimilated_NewZhai
  end
end
