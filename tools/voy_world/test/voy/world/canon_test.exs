defmodule Voy.World.CanonTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.{Canon, Chronicle}

  import Voy.World.TestFixtures

  test "the timeline, the text table, the content pack and the fate table are coherent" do
    assert :ok = Canon.check_timeline()
    assert :ok = Canon.check_texts()
    assert :ok = Canon.check_content()
    assert :ok = Canon.check_fates()
  end

  test "every supported topography compiles to one consistent canon" do
    summary = Canon.check_corpus(all_topographies())

    assert summary.checked == 48
    assert summary.ok == 8
    assert summary.unsupported == 40
    assert summary.issues == []
  end

  test "a decided topography lints clean" do
    assert :ok = Canon.check_topography(baishui_topo())
    assert :ok = Canon.check_topography(heishui_topo())
    assert :ok = Canon.check_topography(nuoliang_topo())
  end

  test "an undecided topography is reported as unsupported, not as a canon error" do
    assert {:unsupported, :unresolved_history} =
             Canon.check_topography(
               branch_id: :b,
               up_name: "U",
               pass_name: "P",
               down_name: "D",
               up_terrain: :steep_fissure_headwater,
               pass_terrain: :canyon_pass,
               down_terrain: :mid_terrace,
               up_geology: :active_fault_vein,
               accessibility: :cliff_isolated
             )
  end

  test "the official report may not adopt the truth's cause" do
    topo = baishui_topo()
    slice = compile_raw(topo)
    archives = Keyword.fetch!(slice, :archives)
    truth = archives[:tech_note][:true_cause]

    corrupted =
      Keyword.put(
        slice,
        :archives,
        Keyword.put(
          archives,
          :official_report,
          Keyword.put(archives[:official_report], :claimed_cause, truth)
        )
      )

    assert {:error, issues} = Canon.check_slice(topo, corrupted)
    assert {:official_adopts_truth, ^truth} = List.keyfind(issues, :official_adopts_truth, 0)
  end

  test "a document may not invent a reign year" do
    topo = baishui_topo()
    slice = compile_raw(topo)
    archives = Keyword.fetch!(slice, :archives)
    gazetteer = archives[:gazetteer]

    corrupted =
      Keyword.put(
        slice,
        :archives,
        Keyword.put(
          archives,
          :gazetteer,
          Keyword.put(gazetteer, :excerpt, "光绪三十年清丈局强收寨门铜钲入库铸钱")
        )
      )

    assert {:error, issues} = Canon.check_slice(topo, corrupted)
    assert {:undeclared_reign_year, "三十", _text} = List.keyfind(issues, :undeclared_reign_year, 0)
  end

  test "a document may not invent a two-digit field year" do
    topo = baishui_topo()
    slice = compile_raw(topo)
    archives = Keyword.fetch!(slice, :archives)
    tech = archives[:tech_note]

    corrupted =
      Keyword.put(
        slice,
        :archives,
        Keyword.put(
          archives,
          :tech_note,
          Keyword.put(tech, :note, "对照残卷，85年拆钟后阻尼归零")
        )
      )

    assert {:error, issues} = Canon.check_slice(topo, corrupted)
    assert {:undeclared_short_year, "85", _text} = List.keyfind(issues, :undeclared_short_year, 0)
  end

  test "a structure may not name a site the topography does not have" do
    topo = baishui_topo()
    slice = compile_raw(topo)
    structures = Keyword.fetch!(slice, :structures)

    corrupted =
      Keyword.put(
        slice,
        :structures,
        Keyword.put(structures, :downstream_site, {"别处的寨", "描述"})
      )

    assert {:error, issues} = Canon.check_slice(topo, corrupted)

    assert {:unknown_structure_site, :downstream, "别处的寨"} =
             List.keyfind(issues, :unknown_structure_site, 0)
  end

  test "a document naming another settlement is caught" do
    topo = baishui_topo()
    slice = compile_raw(topo)
    archives = Keyword.fetch!(slice, :archives)
    tech = archives[:tech_note]

    corrupted =
      Keyword.put(
        slice,
        :archives,
        Keyword.put(
          archives,
          :tech_note,
          Keyword.put(tech, :settlement, "别的坝子")
        )
      )

    assert {:error, issues} = Canon.check_slice(topo, corrupted)

    assert {:name_mismatch, :tech_settlement, "别的坝子", "白水坝"} =
             List.keyfind(issues, :name_mismatch, 0)
  end

  defp compile_raw(topo) do
    [slice] = run_all(fn query -> Chronicle.compile_branch_sliceo(topo, query) end) |> terms()
    slice
  end
end
