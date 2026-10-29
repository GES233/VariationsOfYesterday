defmodule Voy.World.Ascribe do
  @moduledoc """
  Reverse abduction over a declared hypothesis space.

  The player does not read an answer out of a document; they take the
  surface records of three institutions and ask which explanations of the
  season are still possible. This module answers that question by running
  the *same* observation pipeline forward for every hypothesis in the
  declared space `H` and keeping the ones whose predicted records survive
  the recorded ones. There is no rule that maps a cause tag to an answer,
  no per-case branch, and nothing that reads a fate, a document id or a
  person's name.

  `H` is `Voy.World.Slice.hypotheses/1`: every combination of
  `c0_state x pulse_kind x safe_phase_window` the content can produce. The
  space is small, discrete and *declared*; uniqueness later in this module
  means "unique within H given E", never "the historical truth".

  Four answers, four different shapes -- an empty list must never stand in
  for more than one of them:

    * `{:unique, hypothesis}` -- exactly one survivor;
    * `{:multiple, %{candidates: [...], differing: [...]}}` -- the record is
      complete and the model genuinely cannot separate these;
    * `{:none, %{...}}` -- the record contains a value that every
      hypothesis contradicts: the evidence is contradictory, not thin;
    * `{:incomplete, %{candidates: [...], missing: [...]}}` -- the archives
      lost a field the protocol expects, and that gap is why the answer is
      not unique.

  Identifiability is not a property of the story, it is a property of the
  record's resolution. `identifiability/2` measures it, so a claim that "the
  player can work it out" has a number attached.
  """

  alias Voy.World.{Contract, Observation, Slice}
  use Voy.Kanren

  @type hypothesis :: map()

  @doc """
  The alternatives to the story that the slice's own archives make
  plausible, and what each one would have looked like if it were true.

  These are *etiological competitors*, not members of H: they are not
  parameter combinations of the model but different explanations of the same
  season. Each is refuted (or not) by the evidence, and the refutation has
  to name the field that does it.
  """
  @spec alternatives() :: %{atom() => [map()]}
  def alternatives do
    %{
      # 秋雨自然侵蚀: the official scapegoat. A rain-driven mudflow has no
      # repeated frame slams at a cycle period, no work order, and the
      # mountain answers *after* the rain, not before it.
      autumn_rain: [
        %{field: :impact_count, value: :single},
        %{field: :sign_order, value: :after_rain},
        %{field: :amplitude, value: :none}
      ],
      # 纯自然蚀空: a slow geological failure with nobody's hand on it. It can
      # explain one event but not a cycle, and it leaves no rig signature at
      # all.
      natural_collapse: [
        %{field: :impact_count, value: :single},
        %{field: :amplitude, value: :none}
      ]
    }
  end

  # ==== prediction (the forward model) ==== //

  @doc """
  What each lens would have recorded, if `hypothesis` were true.

  This is the only forward model; the ground truth traces used by the tests
  go through the same function, so an abductive answer can never come from a
  table the predictor does not use.
  """
  @spec predict(hypothesis(), Keyword.t()) :: [map()]
  def predict(hypothesis, opts \\ []) do
    contract = Keyword.get_lazy(opts, :contract, fn -> Contract.load!() end)
    resolution = Keyword.get(opts, :resolution, 0.25)

    hypothesis
    |> Slice.trace(contract)
    |> Observation.observe_all(resolution: resolution)
    |> Observation.evidence()
  end

  # ==== relational Kanren predicates ==== //

  @doc """
  Relational domain of hypotheses `H`: unifies `h_kw` with
  `[c0_state: c0, pulse_kind: kind, safe_phase_window: window]`.
  """
  def hypothesiso(h_kw, %Contract{} = contract) do
    pulses = Contract.world_pulses(contract)

    fresh [c0, kind, window] do
      eq(h_kw, [c0_state: c0, pulse_kind: kind, safe_phase_window: window])
      membero(c0, [:c0_collapsed, :c0_intact])
      membero(kind, pulses)
      membero(window, [:absolute, :relative, :late])
    end
  end

  @doc """
  Bureaucratic taboo relation (`neq` / `=/=` firewall, design note 02 §2.2):
  `:official_hq` is forbidden from unifying `cause_tag` with mechanical or
  ritual causes, forcing unification onto `:seasonal_rain_mudslide`.
  """
  def official_tabooo(cause) do
    all([
      membero(cause, [:c0_collapsed, :RelativeRefractory, :mountain_god, :seasonal_rain_mudslide]),
      neq(cause, :c0_collapsed),
      neq(cause, :RelativeRefractory),
      neq(cause, :mountain_god)
    ])
  end

  @doc """
  Relational abduction goal: `h_kw` is a hypothesis in `H` whose forward
  observation trace unifies with every recorded item in `evidence` within the
  instrument `resolution`.
  """
  def abduceo(h_kw, evidence, %Contract{} = contract, resolution) do
    conj(
      hypothesiso(h_kw, contract),
      fn state ->
        case Voy.Kanren.Core.walk_star(h_kw, state.subst) do
          [c0_state: c0, pulse_kind: kind, safe_phase_window: window]
          when is_atom(c0) and is_atom(kind) and is_atom(window) ->
            hypothesis = %{c0_state: c0, pulse_kind: kind, safe_phase_window: window}
            predicted = predict(hypothesis, contract: contract, resolution: resolution)
            evidence_consistento(evidence, predicted, resolution).(state)

          _ ->
            mzero()
        end
      end
    )
  end

  defp evidence_consistento([], _predicted, _tolerance), do: fn state -> unit(state) end

  defp evidence_consistento([observation | rest], predicted, tolerance) do
    conj(
      record_consistento(observation, predicted, tolerance),
      evidence_consistento(rest, predicted, tolerance)
    )
  end

  defp record_consistento(%{field: field, value: value} = observation, predicted, tolerance) do
    case Enum.find(predicted, &(&1.lens == observation.lens and &1.field == field)) do
      nil ->
        fn state -> unit(state) end

      %{value: :unrecorded} ->
        fn state -> unit(state) end

      %{value: _predicted_value} when value == :unrecorded ->
        fn state -> unit(state) end

      %{value: predicted_value} when is_number(value) and is_number(predicted_value) ->
        if abs(value - predicted_value) <= tolerance do
          fn state -> unit(state) end
        else
          # Force a relational failure via disequality on the mismatched value
          all([eq(value, predicted_value)])
        end

      %{value: predicted_value} ->
        eq(value, predicted_value)
    end
  end

  # ==== abduction ==== //

  @doc """
  Which hypotheses in `H` survive the evidence.

  Evaluates survivorship via the `Voy.Kanren` relational goal `abduceo/4`.
  See the module doc for the four answers. The comparison tolerance is the
  rig clock's read-off resolution, taken from the evidence itself
  (`Observation.resolution/2`) so a caller cannot compare a prediction made
  at one resolution against a record made at another.
  """
  @spec explain([map()], Keyword.t()) :: tuple()
  def explain(evidence, opts \\ []) do
    contract = Keyword.get_lazy(opts, :contract, fn -> Contract.load!() end)
    resolution = Keyword.get_lazy(opts, :resolution, fn -> Observation.resolution(evidence) end)

    candidates =
      Voy.Kanren.Core.run_all(fn q -> abduceo(q, evidence, contract, resolution) end)
      |> Voy.Kanren.Core.terms()
      |> Enum.map(&Map.new/1)

    contradictions =
      if candidates == [] do
        for hypothesis <- Slice.hypotheses(contract),
            {:contradiction, c} <- [evaluate(hypothesis, evidence, contract, resolution)] do
          {hypothesis, c}
        end
      else
        []
      end

    missing = missing_fields(evidence)

    cond do
      candidates == [] ->
        {:none, %{contradictions: Enum.take(contradictions, 3), missing: missing}}

      length(candidates) == 1 ->
        {:unique, hd(candidates)}

      true ->
        # Two candidates left. Is it because the archives lost a field that
        # would have separated them (`:incomplete`), or because the field was
        # recorded and the instrument's resolution cannot separate them
        # (`:multiple`)? The first is a hole in the record, the second is a
        # limit of the model -- they must not be reported as the same answer.
        losing = Enum.filter(missing, &discriminating?(&1, candidates, contract, resolution))

        if losing != [] do
          {:incomplete, %{candidates: candidates, missing: Enum.sort(losing)}}
        else
          {:multiple, %{candidates: candidates, differing: differing(candidates, contract, resolution)}}
        end
    end
  end

  defp discriminating?(field, candidates, contract, resolution) do
    candidates
    |> Enum.map(fn hypothesis ->
      hypothesis
      |> predict(contract: contract, resolution: resolution)
      |> Enum.find_value(fn record ->
        if record.field == field and record.value != :unrecorded, do: record.value
      end)
    end)
    |> Enum.uniq()
    |> length() > 1
  end

  defp evaluate(hypothesis, evidence, contract, resolution) do
    predicted = predict(hypothesis, contract: contract, resolution: resolution)

    Enum.reduce_while(evidence, :consistent, fn observation, _acc ->
      case compare(observation, predicted, resolution) do
        :match -> {:cont, :consistent}
        {:contradiction, detail} -> {:halt, {:contradiction, detail}}
      end
    end)
  end

  defp compare(%{field: field, value: value} = observation, predicted, tolerance) do
    case Enum.find(predicted, &(&1.lens == observation.lens and &1.field == field)) do
      nil ->
        # The model predicts nothing on this channel: an extra record can
        # neither confirm nor exclude. Ignored, and reported as irrelevant
        # by the ablation instead.
        :match

      %{value: :unrecorded} ->
        :match

      %{value: predicted_value} ->
        if value == :unrecorded do
          :match
        else
          agree(value, predicted_value, tolerance, observation, field)
        end
    end
  end

  defp agree(value, predicted, tolerance, observation, field) when is_number(value) and is_number(predicted) do
    if abs(value - predicted) <= tolerance do
      :match
    else
      {:contradiction,
       %{lens: observation.lens, field: field, recorded: value, predicted: predicted, tolerance: tolerance}}
    end
  end

  defp agree(value, value, _tolerance, _observation, _field), do: :match

  defp agree(value, predicted, _tolerance, observation, field) do
    {:contradiction,
     %{lens: observation.lens, field: field, recorded: value, predicted: predicted}}
  end

  defp missing_fields(evidence) do
    recorded = evidence |> Enum.filter(&(&1.value != :unrecorded)) |> Enum.map(& &1.field) |> MapSet.new()
    Enum.reject(Observation.required_fields(), &MapSet.member?(recorded, &1))
  end

  defp differing(candidates, contract, resolution) do
    predicted = Enum.map(candidates, &{&1, predict(&1, contract: contract, resolution: resolution)})

    keys =
      predicted
      |> Enum.flat_map(fn {_hypothesis, records} -> Enum.map(records, &{&1.lens, &1.field}) end)
      |> Enum.uniq()

    for {lens, field} = key <- keys,
        values = field_values(predicted, key),
        values |> Enum.map(&elem(&1, 1)) |> Enum.uniq() |> length() > 1 do
      %{lens: lens, field: field, by_hypothesis: values}
    end
  end

  defp field_values(predicted, {lens, field}) do
    Enum.map(predicted, fn {hypothesis, records} ->
      value =
        case Enum.find(records, &(&1.lens == lens and &1.field == field)) do
          nil -> nil
          record -> record.value
        end

      {hypothesis, value}
    end)
  end

  # ==== ablation ==== //

  @doc """
  Drop each evidence item in turn and report what the answer becomes.

  An item is necessary when dropping it loses uniqueness; it is irrelevant
  when dropping it changes nothing. The result is the measured minimal
  chain -- no authoring decision about which document matters, only what the
  abducer does without it.
  """
  @spec ablate([map()], Keyword.t()) :: %{baseline: tuple(), items: [map()]}
  def ablate(evidence, opts \\ []) do
    baseline = explain(evidence, opts)

    items =
      for index <- 0..(length(evidence) - 1) do
        {item, without} = List.pop_at(evidence, index)
        result = explain(without, opts)

        %{
          lens: item.lens,
          field: item.field,
          value: item.value,
          without: result,
          necessary?: necessary?(baseline, result),
          irrelevant?: result == baseline
        }
      end

    %{baseline: baseline, items: items}
  end

  defp necessary?({:unique, _baseline}, without), do: not unique?(without)
  defp necessary?(_baseline, _without), do: false

  defp unique?({:unique, _}), do: true
  defp unique?(_), do: false

  @doc """
  The leave-one-out essential core (`∩ S`): items whose single removal from
  the full evidence immediately loses uniqueness because no backup witness
  exists in `evidence`.

  Note: when redundant witnesses exist (e.g. both `official_hq/water_level`
  and `tech_inner/impact_count` pin `c0_state`), this core is the
  *intersection* of all minimal sufficient witness sets, so it is a proper
  subset of each sufficient set. Use `minimal_sufficient_sets/2` for sets that
  are themselves sufficient to recover `{:unique, h}`.
  """
  @spec minimal_chain([map()], Keyword.t()) :: [map()]
  def minimal_chain(evidence, opts \\ []) do
    for %{necessary?: true} = item <- ablate(evidence, opts).items do
      %{lens: item.lens, field: item.field}
    end
  end

  @doc """
  All inclusion-minimal subsets `S` of `evidence` (as `[%{lens, field}]`) that
  are *themselves sufficient* (`explain(subset_records(evidence, S)) == {:unique, h}`)
  and *minimal* (dropping any member of `S` loses `:unique`).

  Their intersection across `S` is always `minimal_chain(evidence)`.
  """
  @spec minimal_sufficient_sets([map()], Keyword.t()) :: [[map()]]
  def minimal_sufficient_sets(evidence, opts \\ []) do
    contract = Keyword.get_lazy(opts, :contract, fn -> Contract.load!() end)
    resolution = Keyword.get_lazy(opts, :resolution, fn -> Observation.resolution(evidence) end)
    opts = Keyword.merge(opts, contract: contract, resolution: resolution)

    case explain(evidence, opts) do
      {:unique, _truth} = target ->
        core_keys = minimal_chain(evidence, opts)
        all_Possible = Slice.hypotheses(contract)

        # Only recorded items that vary across H can ever contribute to
        # separating hypotheses; constant/unrecorded items never shrink candidates.
        active_extras =
          for item <- evidence,
              item.value != :unrecorded,
              key = %{lens: item.lens, field: item.field},
              key not in core_keys,
              varies_across_h?(key, all_Possible, contract, resolution) do
            key
          end

        subsets_by_size(active_extras)
        |> Enum.reduce([], fn extra_keys, minimal_acc ->
          candidate_keys = core_keys ++ extra_keys
          candidate_set = MapSet.new(candidate_keys)

          if Enum.any?(minimal_acc, &MapSet.subset?(MapSet.new(&1), candidate_set)) do
            minimal_acc
          else
            sub_ev = subset_records(evidence, candidate_keys)

            if explain(sub_ev, opts) == target do
              minimal_acc ++ [candidate_keys]
            else
              minimal_acc
            end
          end
        end)

      _ ->
        []
    end
  end

  @doc "Filter `evidence` down to the records named by `keys` (`[%{lens, field}]`)."
  @spec subset_records([map()], [map()]) :: [map()]
  def subset_records(evidence, keys) do
    key_set = MapSet.new(keys, &{&1.lens, &1.field})
    Enum.filter(evidence, &MapSet.member?(key_set, {&1.lens, &1.field}))
  end

  defp varies_across_h?(%{lens: lens, field: field}, hypotheses, contract, resolution) do
    hypotheses
    |> Enum.map(fn h ->
      h
      |> predict(contract: contract, resolution: resolution)
      |> Enum.find_value(fn r -> if r.lens == lens and r.field == field, do: r.value end)
    end)
    |> Enum.uniq()
    |> length() > 1
  end

  defp subsets_by_size(list) do
    0..length(list)
    |> Enum.flat_map(&combinations(list, &1))
  end

  defp combinations(_list, 0), do: [[]]
  defp combinations([], _k), do: []

  defp combinations([h | t], k) when k > 0 do
    for(rest <- combinations(t, k - 1), do: [h | rest]) ++ combinations(t, k)
  end

  @doc "The evidence items the abducer can lose without any consequence."
  @spec irrelevant([map()], Keyword.t()) :: [map()]
  def irrelevant(evidence, opts \\ []) do
    for %{irrelevant?: true} = item <- ablate(evidence, opts).items do
      %{lens: item.lens, field: item.field}
    end
  end

  # ==== etiological competitors ==== //

  @doc """
  Try to exclude a competing explanation with the same evidence.

  Returns `{:excluded, [reasons]}` when the record contradicts at least one
  of the alternative's predictions, or `{:not_excluded, [survivors]}`. An
  alternative that cannot be excluded is a real result, not a bug.
  """
  @spec refute([map()], atom()) :: {:excluded, [map()]} | {:not_excluded, [map()]} | :unknown_alternative
  def refute(evidence, alternative) do
    case Map.fetch(alternatives(), alternative) do
      :error ->
        :unknown_alternative

      {:ok, predictions} ->
        reasons =
          for %{field: field, value: expected} <- predictions,
              record = Enum.find(evidence, &(&1.field == field)),
              record != nil,
              record.value != :unrecorded,
              record.value != expected do
            %{lens: record.lens, field: field, expected: expected, recorded: record.value}
          end

        if reasons == [] do
          survivors = for %{field: field, value: expected} <- predictions, do: %{field: field, expected: expected}
          {:not_excluded, survivors}
        else
          {:excluded, reasons}
        end
    end
  end

  @doc """
  Measure identifiability as a function of the rig clock's resolution.

  The observation is re-run at each resolution -- both the record and the
  prediction use the same one -- so the answer says what the *instrument*
  can support. Uniqueness is a property of the record, and this is the
  honest way to state "the player can work it out": at which resolution the
  answer holds together and what it collapses to when it does not.
  """
  @spec identifiability(map(), [number()]) :: [{number(), tuple()}]
  def identifiability(trace, resolutions \\ [0.1, 0.25, 0.5, 1.0]) do
    contract = Map.fetch!(trace, :contract)

    for resolution <- resolutions do
      evidence =
        trace
        |> Observation.observe_all(resolution: resolution)
        |> Observation.evidence()

      {resolution, explain(evidence, contract: contract)}
    end
  end

  @doc """
  A deliberately naive classifier, kept only as a control: it answers from
  the official cause column alone.

  Both worlds in the slice file the same cause tag, so this classifier must
  return the same answer for two different hidden explanations. If it ever
  looks right, it is right by accident -- which is exactly what
  `Voy.World.AscribeSpec` asserts against.
  """
  @spec official_only([map()]) :: {:unique, map()} | tuple()
  def official_only(evidence) do
    case Enum.find(evidence, &(&1.field == :cause_tag and &1.value == :seasonal_rain_mudslide)) do
      nil -> {:incomplete, %{candidates: [], missing: [:cause_tag]}}
      _record -> {:unique, %{c0_state: :c0_collapsed, pulse_kind: :alpha_shock, safe_phase_window: :relative}}
    end
  end
end
