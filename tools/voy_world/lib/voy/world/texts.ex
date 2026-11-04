defmodule Voy.World.Texts do
  @moduledoc """
  Place-attached structure descriptions (`structure_*`), rendered by
  `Voy.World.Projection.compile_structureso/5`.

  Player-facing narrative documents live in the five-hop authoring pack under
  `content/`.
  """

  @templates %{
    structure_assimilated: %{
      upstream: "302队废弃爆破钻探台（常驻脉冲源）",
      downstream: "场部—议事会驻地：贴有火塘符纸的农场砖混宿舍（variations-of-yesterday:farm_dorm_unit_*）",
      pass_zheng: "峡谷隘口·原铜钲基座改挂红绸东方红气缸体的木架 + 验山垂架",
      pass_stele: "溶洞峡口·原分水碑残座改挂红绸东方红气缸体的铁架 + 验山垂架"
    },
    structure_annihilated: %{
      upstream: "水泥封死的超高压注浆井群（灰白死寂核心）",
      downstream: "空无一人的钙化垦殖分场废墟（Burnout 绝对静默区）",
      pass_zheng: "被水泥封堵的古铜钲亭废墟与焊死在极值的验山垂架",
      pass_stele: "坍塌钙化的石雕分水堰与焊死在极值的验山垂架"
    },
    structure_ascended: %{
      upstream: "深渊谐振掩体与黑曜石畸变裂隙（末影人游荡点）",
      downstream: "严守封山戒律的半山绝壁孤寨（偶受空间裂隙微扰）",
      pass_zheng: "尚存岁祭镇山铜钲与流官归化断碑的险峻峡口",
      pass_stele: "尚存石雕分水古碑与流官归化断碑的溶洞峡口"
    },
    structure_ancestral: %{
      upstream: "撤牌废弃的林场水文观测哨",
      downstream: "恢复山官岁祭的原生老寨（以物易物交换旧档案）",
      pass_zheng: "完好如初的古寨镇山铜钲亭（c0 维持源完好）",
      pass_stele: "完好如初的古寨石雕分水堰（c0 维持源完好）"
    },
    structure_chronic_seep: %{
      upstream: "302队季节点炮的浅层爆破钻探台（常年低于点火阈值）",
      downstream: "半撤空的垦殖分场：两套账本并行的待撤观测站（variations-of-yesterday:farm_dorm_unit_*）",
      pass_zheng: "峡谷隘口·空置的铜钲亭基座（铜钲已入库封存）与一副未挂红绸的空木架",
      pass_stele: "溶洞峡口·断水的石雕分水堰残槽（碑身已拆运）与验山垂架"
    },
    structure_false_alarm: %{
      upstream: "仍在点炮的302队浅层爆破钻探台（周期脉冲，屡被吸收）",
      downstream: "绝壁听山孤寨：悬空木寮与逐次记录的簧片监听匣",
      pass_zheng: "完好如初的镇山铜钲亭与刻满逐次记痕的验山垂架",
      pass_stele: "完好如初的石雕分水堰与刻满逐次记痕的验山垂架"
    }
  }

  @doc "All structure text keys."
  @spec keys() :: [atom()]
  def keys, do: Map.keys(@templates)

  @doc "Payload fields of one key."
  @spec fields(atom()) :: {:ok, [atom()]} | :error
  def fields(key) do
    case Map.fetch(@templates, key) do
      {:ok, entry} -> {:ok, entry |> Map.keys() |> Enum.sort()}
      :error -> :error
    end
  end

  @doc "Look up one structure description."
  @spec render(atom(), atom()) :: {:ok, String.t()} | {:error, term()}
  def render(key, field) do
    with {:ok, entry} <- Map.fetch(@templates, key),
         {:ok, text} when is_binary(text) <- Map.fetch(entry, field) do
      {:ok, text}
    else
      _ -> {:error, {:unknown_text, key, field}}
    end
  end

  @doc "Look up one structure description, raising on an unknown key or field."
  @spec render!(atom(), atom()) :: String.t()
  def render!(key, field) do
    case render(key, field) do
      {:ok, text} ->
        text

      {:error, reason} ->
        raise ArgumentError, "cannot render #{inspect(key)}.#{field}: #{inspect(reason)}"
    end
  end

  @doc "Check that every structure description is non-empty and free of unresolved placeholders."
  @spec validate() :: :ok | {:error, [term()]}
  def validate do
    issues =
      Enum.flat_map(@templates, fn {key, entry} ->
        Enum.flat_map(entry, fn {field, text} ->
          cond do
            text == "" -> [{:empty_text, key, field}]
            String.contains?(text, "{{") -> [{:unbound_placeholder, key, field}]
            true -> []
          end
        end)
      end)

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end
end
