defmodule Voy.World.CanonTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.{Canon, Chronicle}

  import Voy.World.TestFixtures

  test "the timeline, structures, five-hop content and fate table are coherent" do
    assert :ok = Canon.check_timeline()
    assert :ok = Canon.check_texts()
    assert :ok = Canon.check_content()
    assert :ok = Canon.check_fates()
    assert :ok = Canon.check_pathways([101, 202, 303, 404])
  end

  test "every supported topography compiles to one consistent canon" do
    summary = Canon.check_corpus(all_topographies())
    assert summary.checked == 48
    assert summary.ok == 24
    assert summary.unsupported == 24
    assert summary.issues == []
  end

  test "a decided topography lints clean" do
    assert :ok = Canon.check_topography(baishui_topo())
    assert :ok = Canon.check_topography(heishui_topo())
    assert :ok = Canon.check_topography(nuoliang_topo())
  end

  test "an unpaired geology is unsupported, not a canon error" do
    assert {:unsupported, :unsupported_topography} =
             Canon.check_topography(
               branch_id: :b,
               up_name: "U",
               pass_name: "P",
               down_name: "D",
               up_terrain: :steep_fissure_headwater,
               pass_terrain: :canyon_pass,
               down_terrain: :mid_terrace,
               up_geology: :deep_stress_cavity,
               accessibility: :cliff_isolated
             )
  end

  test "a structure may not invent a date" do
    topo = baishui_topo()
    slice = compile_raw(topo)
    structures = Keyword.fetch!(slice, :structures)

    corrupted =
      Keyword.put(
        slice,
        :structures,
        Keyword.put(structures, :upstream_site, {"野猪箐裂隙", "光绪三十年封井"})
      )

    assert {:error, issues} = Canon.check_slice(topo, corrupted)
    assert {:undeclared_reign_year, "三十", _text} = List.keyfind(issues, :undeclared_reign_year, 0)
  end

  test "a structure may not name a site the topography does not have" do
    topo = baishui_topo()
    slice = compile_raw(topo)
    structures = Keyword.fetch!(slice, :structures)

    corrupted =
      Keyword.put(slice, :structures, Keyword.put(structures, :downstream_site, {"别处的寨", "描述"}))

    assert {:error, issues} = Canon.check_slice(topo, corrupted)

    assert {:unknown_structure_site, :downstream, "别处的寨"} =
             List.keyfind(issues, :unknown_structure_site, 0)
  end

  defp compile_raw(topo) do
    [slice] = run_all(fn query -> Chronicle.compile_branch_sliceo(topo, query) end) |> terms()
    slice
  end
end
