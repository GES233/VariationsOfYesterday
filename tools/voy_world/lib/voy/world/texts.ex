defmodule Voy.World.Texts do
  @moduledoc """
  Prose as data: every document, decree and explanation the relation can
  emit lives here, keyed by a stable name, with its dates bound to
  `Voy.World.Timeline` instead of typed into the sentence.

  Rules render from this table (`Texts.render!/2`) instead of carrying long
  strings inline. Three consequences follow, all of which the port needs:

    * prose can be revised, translated or replaced without touching a rule;
    * a year can only appear in a document if the timeline owns it, which is
      what `Voy.World.Canon` checks;
    * the Scala side receives text keys and renders them from its own copy of
      the table, so a JVM port does not have to transplant Chinese literals
      into rule code.

  Placeholders are `%{name}`; bindings are declarative (`{:short_year,
  anchor}`, `{:reign_date, reign, anchor}`, `{:month_numeral, month}`) and
  resolve through the timeline. Rendering refuses to leave a placeholder
  unbound rather than emitting `%{...}` into a player-facing document.
  """

  alias Voy.World.Timeline

  @templates %{
    archive_gazetteer_zheng: %{
      bindings: %{reign_date: {:reign_date, :guangxu, :era2a_zheng_case}},
      title: "《旧州府抚夷清丈残卷·铜钲案》",
      excerpt:
        "%{reign_date}清丈局强收寨门铜钲入库铸钱，是秋山鸣石崩掩没局署；" <>
          "流官惊惧，发还铜钲任山官依古法岁祭叩击，山患遂平（外甲内寨）"
    },
    archive_gazetteer_stele: %{
      bindings: %{reign_date: {:reign_date, :guangxu, :era2a_stele_case}},
      title: "《旧州府抚夷清丈残卷·石堰案》",
      excerpt:
        "%{reign_date}委员毁峡口分水石碑改辟官田，越旬落水洞暗河倒灌吞没官仓；" <>
          "遂准山官复立石堰，按候泄洪乃安（外甲内寨）"
    },
    archive_gazetteer_cliff: %{
      bindings: %{},
      title: "《旧州府抚夷清丈残卷·立碑案》",
      excerpt:
        "其寨悬居绝壁，栈道断绝，丈量胥吏望谷生畏，仅于山口立『归化碑』敷衍上峰，" <>
          "实未入寨门半步，听任山官自治"
    },
    archive_cause_c0_blasting_zheng: %{
      bindings: %{year_short: {:short_year, :era2b_reclamation}},
      text:
        "对照清丈残卷所载拆钟旧案，%{year_short}年收缴铜钲再度断绝阻尼（c0=0）；" <>
          "302队点炮又撞上簧片蜂鸣段，致全谷陷入自持振荡"
    },
    archive_cause_c0_blasting_stele: %{
      bindings: %{year_short: {:short_year, :era2b_reclamation}},
      text:
        "对照清丈残卷所载毁堰旧案，%{year_short}年拆毁分水石碑再度断绝阻尼（c0=0）；" <>
          "302队点炮又撞上簧片蜂鸣段，致全谷陷入自持振荡"
    },
    archive_cause_rain_excuse: %{
      bindings: %{},
      text: "归咎于连日秋雨导致常规岩移滑坡，绝口不提收缴镇山古器与302队违规点炮"
    },
    archive_cause_overwhelming_zheng: %{
      bindings: %{},
      text:
        "清丈残卷早戒『不可强拆铜钲、绝山阻尼』，总指挥部偏以超高压水泥封井强压山息，" <>
          "应力冲穿截断阈值，整条沟进入 Burnout 吸引子"
    },
    archive_cause_overwhelming_stele: %{
      bindings: %{},
      text:
        "清丈残卷早戒『不可强堵暗河』，总指挥部偏用超高压反向水泥封井，" <>
          "应力冲穿截断阈值，整条沟进入 Burnout 吸引子"
    },
    archive_cause_relocation: %{
      bindings: %{},
      text: "宣称矿区资源正常枯竭、垦殖分场全员已按计划整建制平稳搬迁"
    },
    archive_cause_rift_exile: %{
      bindings: %{},
      text:
        "依靠半山老寨完好的 c0 缓冲，源头未直接烧毁，" <>
          "但核心组在起峰极值（Excitation）强开谐振腔，全员化为搬石头的末影黑影"
    },
    archive_cause_equipment_retrieval: %{
      bindings: %{},
      text: "深孔观测站科研任务圆满结束，设备回收完毕，人员调离备案"
    },
    archive_cause_c0_intact: %{
      bindings: %{},
      text:
        "镇山古器从未离位（c0 完好），林场小震皆被宽容区吸收；" <>
          "莫在鱼鳞水纹时动土，要在万籁俱寂时叩钟"
    },
    archive_cause_backward_custom: %{
      bindings: %{},
      text: "山高路远暂设挂靠林场，旧式山官鸣钟习俗待后续条件成熟再行改组"
    },
    archive_council_joint_council: %{
      bindings: %{},
      title: "《场部—议事会联席保命规约》",
      decree:
        "老保管员（原山官后裔）看水纹听虫鸣掌令旗，场部司务长按《交接班制度》点名记工分；" <>
          "凡死寂回吸窗至，立敲红绸气缸三声"
    },
    archive_council_sealed_zone: %{
      bindings: %{},
      title: "《封山隔离水泥墙警示铁牌》",
      decree: "灰白死寂区内无声无息，场部与议事会筹备组全体失踪，严禁任何人员越过水泥封锁线"
    },
    archive_council_rift_oath: %{
      bindings: %{},
      title: "《山官火塘封山铁誓》",
      decree: "源头掩体已化为黑影裂隙，见末影搬石切勿直视，镇山古器日夜须有人值守"
    },
    archive_council_ancestral_ledger: %{
      bindings: %{},
      title: "《岁祭账房木刻借阅约》",
      decree: "林场撤牌后岁祭如仪，外客若以干粮铁器相赠，可借阅旧州府清丈卷与水文台账"
    },
    lens_rain: %{
      bindings: %{month_numeral: {:month_numeral, :flood_month}},
      text: "%{month_numeral}月连日秋雨导致上游常规岩层疲劳滑坡（系自然损耗，与排险爆破无关）"
    },
    lens_blasting: %{
      bindings: %{year_short: {:short_year, :era2b_reclamation}},
      text:
        "此前下游聚落恢复变慢（临界慢化 Strained），%{year_short}年收走铜钲后阻尼归零（c0=0）；" <>
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
  @spec render(atom(), atom(), Keyword.t()) :: {:ok, String.t()} | {:error, term()}
  def render(key, field, overrides \\ []) do
    with {:ok, template} <- expect(template(key, field), {:unknown_text, key, field}),
         {:ok, bindings} <- bindings(key) do
      case render_template(template, Keyword.merge(bindings, overrides)) do
        {:ok, rendered} ->
          {:ok, rendered}

        {:error, {:unbound_placeholder, leftover}} ->
          {:error, {:unbound_placeholder, key, field, leftover}}
      end
    end
  end

  @doc false
  @spec render_template(String.t(), Keyword.t()) :: {:ok, String.t()} | {:error, term()}
  def render_template(template, bindings) when is_binary(template) and is_list(bindings) do
    rendered =
      Enum.reduce(bindings, template, fn {name, value}, acc ->
        String.replace(acc, "%{#{name}}", to_string(value))
      end)

    case Regex.run(~r/%\{[a-z_]+\}/, rendered) do
      nil -> {:ok, rendered}
      [leftover] -> {:error, {:unbound_placeholder, leftover}}
    end
  end

  @doc "Render one field, raising on an unknown key or an unbound placeholder."
  @spec render!(atom(), atom(), Keyword.t()) :: String.t()
  def render!(key, field, overrides \\ []) do
    case render(key, field, overrides) do
      {:ok, rendered} ->
        rendered

      {:error, reason} ->
        raise ArgumentError, "cannot render #{inspect(key)}.#{field}: #{inspect(reason)}"
    end
  end

  @doc "All resolved bindings of one key."
  @spec bindings(atom()) :: {:ok, Keyword.t()} | {:error, term()}
  def bindings(key) do
    case Map.fetch(@templates, key) do
      {:ok, entry} ->
        entry
        |> Map.get(:bindings, %{})
        |> Enum.reduce_while({:ok, []}, fn {name, spec}, {:ok, acc} ->
          case resolve(spec) do
            {:ok, value} -> {:cont, {:ok, [{name, value} | acc]}}
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
