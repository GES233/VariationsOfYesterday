defmodule Voy.World.Timeline do
  @moduledoc """
  Dated anchors of the fictional history, as data.

  Prose must not invent years. Every document that mentions a date renders
  it from this module, so moving an anchor rewrites the archives coherently
  and `Voy.World.Canon` can verify the text against the data instead of
  trusting it.

  Only anchors that existing text actually refers to are declared here;
  later eras stay undated until a document needs them.
  """

  @anchors %{
    era2a_zheng_case: 1894,
    era2a_stele_case: 1902,
    era2b_reclamation: 1979
  }

  @reign_start %{guangxu: 1875}
  @reign_end %{guangxu: 1908}
  @reign_name %{guangxu: "光绪"}
  @months %{flood_month: 10}

  @digits ~w(零 一 二 三 四 五 六 七 八 九)

  @doc "All dated anchors."
  @spec anchors() :: %{atom() => pos_integer()}
  def anchors, do: @anchors

  @doc "Gregorian year of an anchor."
  @spec year(atom()) :: pos_integer()
  def year(anchor), do: Map.fetch!(@anchors, anchor)

  @doc "All anchor years, for date linters."
  @spec years() :: [pos_integer()]
  def years, do: @anchors |> Map.values() |> Enum.uniq() |> Enum.sort()

  @doc "Named calendar quantities that are not years."
  @spec month(atom()) :: pos_integer()
  def month(name), do: Map.fetch!(@months, name)

  @doc "Start year of a reign name."
  @spec reign_start(atom()) :: pos_integer()
  def reign_start(reign), do: Map.fetch!(@reign_start, reign)

  @doc "Ordinal year of a reign, 1-based (光绪二十年 is 20)."
  @spec reign_ordinal(atom(), pos_integer()) :: pos_integer()
  def reign_ordinal(reign, year), do: year - reign_start(reign) + 1

  @doc "Reign covering a Gregorian year, or nil for a year outside every reign."
  @spec reign_of(pos_integer()) :: atom() | nil
  def reign_of(year) do
    Enum.find_value(@reign_start, fn {reign, start} ->
      if year >= start and year <= Map.fetch!(@reign_end, reign), do: reign
    end)
  end

  @doc "Reign-form date as written in prose, e.g. `光绪二十年`."
  @spec reign_date(atom(), pos_integer()) :: String.t()
  def reign_date(reign, year) do
    if reign_of(year) != reign do
      raise ArgumentError, "#{year} is not inside the #{reign} reign"
    end

    reign_name = Map.fetch!(@reign_name, reign)
    reign_name <> chinese_numeral(reign_ordinal(reign, year)) <> "年"
  end

  @doc "Two-digit year as written in the field notebooks, e.g. `79`."
  @spec short_year(pos_integer()) :: String.t()
  def short_year(year) do
    year
    |> rem(100)
    |> Integer.to_string()
    |> String.pad_leading(2, "0")
  end

  @doc "Chinese numeral for 0..99 (the range prose dates need)."
  @spec chinese_numeral(0..99) :: String.t()
  def chinese_numeral(0), do: "零"
  def chinese_numeral(number) when number in 1..9, do: Enum.at(@digits, number)
  def chinese_numeral(number) when number in 10..19, do: "十" <> ones(number - 10)

  def chinese_numeral(number) when number in 20..99,
    do: Enum.at(@digits, div(number, 10)) <> "十" <> ones(rem(number, 10))

  @doc "Whether the timeline is internally coherent."
  @spec validate() :: :ok | {:error, term()}
  def validate do
    era2a = [year(:era2a_zheng_case), year(:era2a_stele_case)]

    cond do
      length(Enum.uniq(era2a)) != 2 ->
        {:error, {:era2a_years_not_distinct, era2a}}

      Enum.max(era2a) >= year(:era2b_reclamation) ->
        {:error, {:era2a_not_before_era2b, era2a, year(:era2b_reclamation)}}

      true ->
        validate_months()
    end
  end

  defp validate_months do
    case Enum.reject(@months, fn {_name, month} -> month in 1..12 end) do
      [] -> :ok
      bad -> {:error, {:bad_months, bad}}
    end
  end

  defp ones(0), do: ""
  defp ones(number), do: Enum.at(@digits, number)
end
