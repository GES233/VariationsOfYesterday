defmodule Voy.World.Observation do
  @moduledoc """
  Institutional observation pipelines (`:official_hq`, `:tech_inner`, `:old_zhai`)
  that transform a numeric `Voy.World.Slice` trace into surface records with
  explicit provenance (`:measured`, `:quantised`, `:clipped`, `:categorical`,
  `:administrative`, `:unrecorded`).
  """

  alias Voy.World.Slice

  @lenses [:official_hq, :tech_inner, :old_zhai]

  @doc "The three institutional lenses of the slice."
  @spec lenses() :: [atom()]
  def lenses, do: @lenses

  @doc """
  Run every lens over one trace.

  Returns `%{lens => %{records: [...], retains: [...], loses: [...]}}`, plus
  the concatenated record list under `:all` for the abducer.
  """
  @spec observe_all(map(), Keyword.t()) :: %{atom() => map()}
  def observe_all(trace, opts \\ []) do
    resolution = Keyword.get(opts, :resolution, 0.25)

    %{
      official_hq: official_hq(trace),
      tech_inner: tech_inner(trace, resolution),
      old_zhai: old_zhai(trace)
    }
  end

  @doc "The flat evidence list an abducer consumes, in deterministic lens order."
  @spec evidence(map()) :: [map()]
  def evidence(observed) do
    for lens <- @lenses,
        report = Map.get(observed, lens),
        report != nil,
        record <- report.records do
      record
    end
  end

  @doc """
  The read-off resolution the record was actually produced at.

  Stamped onto quantised records so a consumer cannot silently compare a
  prediction made at one resolution against an observation made at another;
  that mismatch is how an abducer "proves" a contradiction that is really an
  artefact of the question.
  """
  @spec resolution([map()], number()) :: number()
  def resolution(evidence, default \\ 0.25) do
    Enum.find_value(evidence, default, &Map.get(&1, :resolution))
  end

  @doc """
  Declared fields the slice's protocol expects to exist somewhere in the
  archives. Used by the abducer to tell "the model cannot separate these"
  from "the record lost the field".
  """
  @spec required_fields() :: [atom()]
  def required_fields, do: [:impact_count, :surge_lag, :sign_order, :count_of_blows]

  # ==== the administrative lens ==== //

  @doc """
  总指挥部 (OfficialHQ): monthly administration.

  Senses the slow variable only -- the hydrology gauge, the requisition
  ledger, the seasonal rainfall total -- at monthly aggregation. It cannot
  resolve a single surge: the gauge is read to the bucket and filed to the
  ten-day report, the pendulum rig is not its instrument at all, and the
  cause column comes from a fixed administrative list that the filing rule
  fills whenever a rainfall total was recorded and no third party witnessed
  a production accident.
  """
  def official_hq(trace) do
    %{
      lens: :official_hq,
      records: [
        record(:official_hq, :hydrology, :water_level, bucket(trace.stress), :categorical),
        record(:official_hq, :hydrology, :surge_count, :unrecorded, :unrecorded),
        record(:official_hq, :meteorology, :rain_total, recordable(trace.rain_lag), :quantised),
        record(:official_hq, :ledger, :material, :nitrate_rock_charge, :measured),
        record(:official_hq, :ledger, :quantity, 1.5, :quantised),
        record(
          :official_hq,
          :filing,
          :cause_tag,
          :seasonal_rain_mudslide,
          :administrative
        )
      ],
      retains: [
        "the season the event falls in",
        "that industrial explosives were issued, and how much",
        "the water level, to a bucket",
        "a cause category that the filing rule supplies by itself"
      ],
      loses: [
        "how many surges occurred",
        "the interval between surges",
        "whether the surge came before or after the rain",
        "whether the pulse advanced or delayed the cycle"
      ]
    }
  end

  # ==== the technical lens ==== //

  @doc """
  技术内层 (TechInner): the survey team's instruments.

  Senses the fast channel: the pendulum rig slams its frame on any excursion
  (`:clipped` -- the amplitude is exactly what the instrument cannot
  report), the blast work order timestamps the event, and the rain gauge is
  read every two hours. It counts the season's frame slams and timestamps
  the surge that follows the work order, to the rig clock's resolution.

  `resolution` is the rig clock's read-off resolution in simulation time
  units. It is the single parameter that decides whether the pulse identity
  and landing window can be separated at all, so it is declared rather than
  assumed: `Voy.World.Ascribe.identifiability/2` measures the consequence.
  """
  def tech_inner(trace, resolution) do
    lag = Slice.surge_lag(trace)

    %{
      lens: :tech_inner,
      resolution: resolution,
      records: [
        record(:tech_inner, :rig, :impact_count, count_class(trace.impacts), :categorical),
        record(:tech_inner, :rig, :amplitude, :frame_slam, :clipped),
        quantised(:tech_inner, :rig, :surge_lag, lag, resolution),
        record(:tech_inner, :rig, :surge_direction, direction_of(trace.shift), :measured),
        record(:tech_inner, :gauge, :rain_total, recordable(trace.rain_lag), :quantised),
        record(:tech_inner, :order, :material, :nitrate_rock_charge, :measured)
      ],
      retains: [
        "the number of excursions in the season",
        "the time from the work order to the next surge",
        "the direction of that shift"
      ],
      loses: [
        "the shape of the event -- the frame clips the amplitude, so a sharp blow and a slow push look identical",
        "the slow variable -- stress is invisible on this channel",
        "anything before the survey team arrived"
      ]
    }
  end

  # ==== the communal lens ==== //

  @doc """
  老寨 (OldZhai): the keeper's phenology.

  No numbers at all. Diurnal granularity, the four-phase sign vocabulary of
  §1.3, and a transmission rule: a sign that does not repeat within the
  season is not written down, but the *order* of what did repeat is. The
  keeper cannot date anything, and cannot tell a premature surge from a
  delayed one -- but he can tell whether the mountain answered before or
  after the rain.
  """
  def old_zhai(trace) do
    %{
      lens: :old_zhai,
      records: [
        record(:old_zhai, :phenology, :sign_order, order_of(trace), :measured),
        record(:old_zhai, :phenology, :sign_sequence, signs_of(trace), :categorical),
        record(:old_zhai, :phenology, :magnitude, :unrecorded, :unrecorded),
        record(:old_zhai, :phenology, :surge_lag, :unrecorded, :unrecorded),
        record(:old_zhai, :ritual, :count_of_blows, trace.frame_slams, :measured)
      ],
      retains: [
        "the order of the signs against the rain",
        "the sequence of the four phase categories",
        "how many times the instrument was struck"
      ],
      loses: [
        "any magnitude",
        "any date",
        "whether the cycle advanced or fell behind"
      ]
    }
  end

  # ==== transforms ==== //

  defp record(lens, channel, field, value, provenance) do
    %{lens: lens, channel: channel, field: field, value: value, provenance: provenance}
  end

  defp quantised(lens, channel, field, value, resolution) do
    record(lens, channel, field, quantise(value, resolution), :quantised)
    |> Map.put(:resolution, resolution)
  end

  defp recordable(value) when is_number(value), do: value
  defp recordable(_value), do: :unrecorded

  defp quantise(value, resolution) when is_number(value) and is_number(resolution) do
    round(value / resolution) * resolution
  end

  defp count_class(count) when is_integer(count) and count > 1, do: :repeated
  defp count_class(count) when is_integer(count) and count == 1, do: :single
  defp count_class(_count), do: :unknown

  defp direction_of(shift) when is_number(shift) and shift > 0.05, do: :late
  defp direction_of(shift) when is_number(shift) and shift < -0.05, do: :early
  defp direction_of(_shift), do: :unchanged

  defp order_of(trace) do
    if Slice.signs_before_rain?(trace), do: :before_rain, else: :after_rain
  end

  defp signs_of(trace) do
    case {direction_of(trace.shift), count_class(trace.impacts)} do
      {:unchanged, :single} -> [:resting]
      {:unchanged, _} -> [:excitation, :resting]
      {:late, _} -> [:excitation, :absolute_refractory, :resting]
      {:early, _} -> [:excitation, :resting]
    end
  end

  defp bucket(stress) when is_number(stress) and stress >= 0.9, do: :over_warning
  defp bucket(stress) when is_number(stress) and stress >= 0.4, do: :high
  defp bucket(stress) when is_number(stress) and stress >= 0.18, do: :elevated
  defp bucket(_stress), do: :normal
end
