defmodule Voy.World.Texts do
  @moduledoc """
  Code-side prose as data: structure descriptions and runtime causal lens
  narratives live here, keyed by a stable name, with their dates bound to
  `Voy.World.Timeline` instead of typed into the sentence.

  Archive documents have migrated to the authoring pack under `content/` and
  are loaded by `Voy.World.Content`. This module retains only the templates
  owned by the rule layer:

    * place-attached structure descriptions (`structure_*`), rendered by
      `Voy.World.Projection.compile_structureso/5`;
    * runtime three-lens causal explanations (`lens_*`), rendered by
      `Voy.World.Causality`.

  Placeholders use the same `{{name}}` syntax as `Voy.World.Content`; bindings
  are declarative (`{:short_year, anchor}`, `{:reign_date, reign, anchor}`,
  `{:month_numeral, month}`) and resolve through the timeline. Rendering
  refuses to leave a placeholder unbound rather than emitting `{{...}}` into a
  player-facing string.
  """

  alias Voy.World.Timeline

  @templates %{
    structure_assimilated: %{
      bindings: %{},
      upstream: "302队废弃爆破钻探台（常驻脉冲源）",
      downstream: "场部—议事会驻地：贴有火塘符纸的农场砖混宿舍（variations-of-yesterday:farm_dorm_unit_*）",
      pass_zheng: "峡谷隘口·原铜钲基座改挂红绸东方红气缸体的木架 + 验山垂架",
      pass_stele: "溶洞峡口·原分水碑残座改挂红绸东方红气缸体的铁架 + 验山垂架"
    },
    structure_annihilated: %{
      bindings: %{},
      upstream: "水泥封死的超高压注浆井群（灰白死寂核心）",
      downstream: "空无一人的钙化垦殖分场废墟（Burnout 绝对静默区）",
      pass_zheng: "被水泥封堵的古铜钲亭废墟与焊死在极值的验山垂架",
      pass_stele: "坍塌钙化的石雕分水堰与焊死在极值的验山垂架"
    },
    structure_ascended: %{
      bindings: %{},
      upstream: "深渊谐振掩体与黑曜石畸变裂隙（末影人游荡点）",
      downstream: "严守封山戒律的半山绝壁孤寨（偶受空间裂隙微扰）",
      pass_zheng: "尚存岁祭镇山铜钲与流官归化断碑的险峻峡口",
      pass_stele: "尚存石雕分水古碑与流官归化断碑的溶洞峡口"
    },
    structure_ancestral: %{
      bindings: %{},
      upstream: "撤牌废弃的林场水文观测哨",
      downstream: "恢复山官岁祭的原生老寨（以物易物交换旧档案）",
      pass_zheng: "完好如初的古寨镇山铜钲亭（c0 维持源完好）",
      pass_stele: "完好如初的古寨石雕分水堰（c0 维持源完好）"
    },
    lens_rain: %{
      bindings: %{"date.flood_month" => {:month_numeral, :flood_month}},
      text: "{{date.flood_month}}月连日秋雨导致上游常规岩层疲劳滑坡（系自然损耗，与排险爆破无关）"
    },
    lens_blasting: %{
      bindings: %{"date.era2b_short" => {:short_year, :era2b_reclamation}},
      text:
        "此前下游聚落恢复变慢（临界慢化 Strained），{{date.era2b_short}}年收走铜钲后阻尼归零（c0=0）；" <>
          "302队点炮正撞在簧片蜂鸣段（易损窗），5刻后推过失稳点"
    },
    lens_mountain_god: %{
      bindings: %{},
      text:
        "涧水起鱼鳞碎纹、雀鸟惊飞乃山脉倒换气！四队拆了寨门铜钲断香火，" <>
          "勘探队又在换气口动火药致山神翻身"
    },
    lens_secondary_ignition: %{
      bindings: %{},
      text:
        "谷地明明已陷入周期性自激振荡（Oscillating），302队在回潮易损窗追加爆破，" <>
          "把原本40刻后的尖峰直接提前引爆"
    }
  }

  @reserved [:bindings]

  @doc "All text keys."
  @spec keys() :: [atom()]
  def keys, do: Map.keys(@templates)

  @doc "Payload fields of one key (everything except the binding table)."
  @spec fields(atom()) :: {:ok, [atom()]} | :error
  def fields(key) do
    case Map.fetch(@templates, key) do
      {:ok, entry} -> {:ok, entry |> Map.keys() |> Kernel.--(@reserved) |> Enum.sort()}
      :error -> :error
    end
  end

  @doc "Raw template, placeholders unrendered."
  @spec template(atom(), atom()) :: {:ok, String.t()} | :error
  def template(key, field) do
    with {:ok, entry} <- Map.fetch(@templates, key),
         {:ok, template} when is_binary(template) <- Map.fetch(entry, field) do
      {:ok, template}
    else
      _ -> :error
    end
  end

  @doc """
  Render one field, resolving its dates from `Voy.World.Timeline`.

  `overrides` replaces individual bindings (used by tests and by the linter).
  """
  @spec render(atom(), atom(), Enumerable.t()) :: {:ok, String.t()} | {:error, term()}
  def render(key, field, overrides \\ []) do
    with {:ok, template} <- expect(template(key, field), {:unknown_text, key, field}),
         {:ok, bindings} <- bindings(key) do
      merged =
        bindings
        |> Map.new(fn {k, v} -> {to_string(k), v} end)
        |> Map.merge(Map.new(overrides, fn {k, v} -> {to_string(k), v} end))
        |> Map.to_list()

      case render_template(template, merged) do
        {:ok, rendered} ->
          {:ok, rendered}

        {:error, {:unbound_placeholder, leftover}} ->
          {:error, {:unbound_placeholder, key, field, leftover}}
      end
    end
  end

  @doc false
  @spec render_template(String.t(), Enumerable.t()) :: {:ok, String.t()} | {:error, term()}
  def render_template(template, bindings) when is_binary(template) do
    rendered =
      Enum.reduce(bindings, template, fn {name, value}, acc ->
        String.replace(acc, "{{#{name}}}", to_string(value))
      end)

    case Regex.run(~r/\{\{[a-z0-9_.]+\}\}/, rendered) do
      nil -> {:ok, rendered}
      [leftover] -> {:error, {:unbound_placeholder, leftover}}
    end
  end

  @doc "Render one field, raising on an unknown key or an unbound placeholder."
  @spec render!(atom(), atom(), Enumerable.t()) :: String.t()
  def render!(key, field, overrides \\ []) do
    case render(key, field, overrides) do
      {:ok, rendered} ->
        rendered

      {:error, reason} ->
        raise ArgumentError, "cannot render #{inspect(key)}.#{field}: #{inspect(reason)}"
    end
  end

  @doc "All resolved bindings of one key."
  @spec bindings(atom()) :: {:ok, [{String.t(), String.t()}]} | {:error, term()}
  def bindings(key) do
    case Map.fetch(@templates, key) do
      {:ok, entry} ->
        entry
        |> Map.get(:bindings, %{})
        |> Enum.reduce_while({:ok, []}, fn {name, spec}, {:ok, acc} ->
          case resolve(spec) do
            {:ok, value} -> {:cont, {:ok, [{to_string(name), value} | acc]}}
            {:error, _reason} = error -> {:halt, error}
          end
        end)
        |> case do
          {:ok, bindings} -> {:ok, Enum.reverse(bindings)}
          {:error, _reason} = error -> error
        end

      :error ->
        {:error, {:unknown_text, key}}
    end
  end

  @doc "Whether every template renders and every placeholder has a binding."
  @spec validate() :: :ok | {:error, [term()]}
  def validate do
    issues =
      Enum.flat_map(@templates, fn {key, entry} ->
        fields = entry |> Map.keys() |> Kernel.--(@reserved)

        Enum.flat_map(fields, fn field ->
          case render(key, field) do
            {:ok, rendered} ->
              if rendered == "", do: [{:empty_text, key, field}], else: []

            {:error, reason} ->
              [{:render_failed, key, field, reason}]
          end
        end)
      end)

    case issues do
      [] -> :ok
      issues -> {:error, issues}
    end
  end

  defp resolve({:short_year, anchor}), do: {:ok, Timeline.short_year(Timeline.year(anchor))}

  defp resolve({:reign_date, reign, anchor}),
    do: {:ok, Timeline.reign_date(reign, Timeline.year(anchor))}

  defp resolve({:month_numeral, name}),
    do: {:ok, Timeline.chinese_numeral(Timeline.month(name))}

  defp resolve(spec), do: {:error, {:unknown_binding, spec}}

  defp expect({:ok, value}, _reason), do: {:ok, value}
  defp expect(:error, reason), do: {:error, reason}
end
