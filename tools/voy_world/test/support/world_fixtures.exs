defmodule Voy.World.TestFixtures do
  alias Voy.World.Schema

  def baishui_topo do
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
    )
  end

  def heishui_topo do
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
    )
  end

  def nuoliang_topo do
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
  end

  def sample_topographies, do: [baishui_topo(), heishui_topo(), nuoliang_topo()]

  def all_topographies do
    base = [branch_id: :t, up_name: "U", pass_name: "P", down_name: "D"]

    for up <- Schema.feature_domain(:up_terrain),
        pass <- Schema.feature_domain(:pass_terrain),
        down <- Schema.feature_domain(:down_terrain),
        geo <- Schema.feature_domain(:up_geology),
        access <- Schema.feature_domain(:accessibility) do
      Schema.topology(
        base ++
          [
            up_terrain: up,
            pass_terrain: pass,
            down_terrain: down,
            up_geology: geo,
            accessibility: access
          ]
      )
    end
  end

  def feature_signature(topo) when is_list(topo), do: Keyword.take(topo, Schema.feature_keys())

  def public_feature_signature(topo) do
    Map.take(topo, Enum.map(Schema.feature_keys(), &Atom.to_string/1))
  end
end
