defmodule Voy.World.Facts do
  @moduledoc """
  Ordered, append-only log of what the world did, saw, said, predicted and
  settled.

  This is the time axis the relational layer was missing. Every entry
  carries the tick it belongs to, the place it belongs to, an actor, and
  an open attribute list, so a query can ask `as of` a moment instead of
  treating a relation as eternally true. Chains are explicit: `caused_by`
  points at the entry that produced this one, and `evidence` points at the
  entries a claim rests on.

  Predictions are first-class: a `:predicted` entry must carry a `claim`
  and a `due_tick`, and stays `pending/1` until a `:settled` entry with
  `outcome: :held | :failed | :partial` closes it. That is the whole point
  of the layer: the symbolic side states something falsifiable, and the
  numeric side grades it later. Nothing here integrates dynamics; it only
  records.

  Determinism: no wall clock, no auto-increment timestamps. Ticks are
  supplied by the caller and must not go backwards, so the same event
  stream replays to the same log.
  """

  @kinds [:saw, :did, :said, :predicted, :settled]
  @outcomes [:held, :failed, :partial]

  defmodule Fact do
    @moduledoc "One entry of a `Voy.World.Facts` log."

    @type t :: %__MODULE__{
            id: pos_integer(),
            tick: non_neg_integer(),
            kind: atom(),
            actor: term(),
            site: String.t(),
            props: Keyword.t(),
            caused_by: pos_integer() | nil,
            evidence: [pos_integer()]
          }

    defstruct [:id, :tick, :kind, :actor, :site, props: [], caused_by: nil, evidence: []]
  end

  @type t :: %__MODULE__{entries: [Fact.t()], next_id: pos_integer()}

  defstruct entries: [], next_id: 1

  defguardp is_ref(value) when is_integer(value) and value > 0

  @doc "An empty log; the first entry gets id 1."
  @spec new() :: t()
  def new, do: %__MODULE__{}

  @doc "Kinds the log accepts."
  @spec kinds() :: [atom()]
  def kinds, do: @kinds

  @doc "Prediction outcomes the log accepts."
  @spec outcomes() :: [atom()]
  def outcomes, do: @outcomes

  @doc "Number of entries."
  @spec count(t()) :: non_neg_integer()
  def count(%__MODULE__{entries: entries}), do: length(entries)

  @doc "Entries in log order (oldest first)."
  @spec entries(t()) :: [Fact.t()]
  def entries(%__MODULE__{entries: entries}), do: entries

  @doc "Most recent entry, or nil for an empty log."
  @spec last(t()) :: Fact.t() | nil
  def last(%__MODULE__{entries: []}), do: nil
  def last(%__MODULE__{entries: entries}), do: List.last(entries)

  @doc "Current tick, or 0 for an empty log."
  @spec now(t()) :: non_neg_integer()
  def now(%__MODULE__{entries: []}), do: 0
  def now(%__MODULE__{entries: entries}), do: entries |> List.last() |> Map.fetch!(:tick)

  @doc """
  Append one entry.

  Required: `:tick`, `:kind`, `:site`. Optional: `:actor` (default
  `:world`), `:props`, `:caused_by`, `:evidence`. Returns the stored entry
  with its assigned id.
  """
  @spec record(t(), Keyword.t() | map()) :: {:ok, Fact.t(), t()} | {:error, term()}
  def record(%__MODULE__{} = log, attrs) do
    with {:ok, attrs} <- normalize_attrs(attrs),
         :ok <- check_tick(log, attrs[:tick]),
         {:ok, attrs} <- check_required(attrs),
         :ok <- check_actor(attrs[:actor]),
         :ok <- check_props(attrs[:props]),
         :ok <- check_refs(log, attrs),
         :ok <- check_kind_rules(log, attrs) do
      fact = struct!(Fact, Keyword.put(attrs, :id, log.next_id))

      {:ok, fact, %{log | entries: log.entries ++ [fact], next_id: log.next_id + 1}}
    end
  end

  @doc """
  Close a pending prediction with an outcome.

  The settled entry inherits the prediction's site and points back at it
  with `caused_by`. `:tick` defaults to "now" but may be supplied, as may
  `:props` and `:actor`.
  """
  @spec settle(t(), pos_integer(), atom(), Keyword.t()) ::
          {:ok, Fact.t(), t()} | {:error, term()}
  def settle(%__MODULE__{} = log, prediction_id, outcome, opts \\ []) do
    with {:ok, prediction} <- fetch(log, prediction_id),
         :ok <- expect_kind(prediction, :predicted),
         :ok <- check_outcome(outcome),
         :ok <- ensure_pending(log, prediction.id) do
      record(log,
        tick: Keyword.get_lazy(opts, :tick, fn -> max(now(log), prediction.tick) end),
        kind: :settled,
        actor: Keyword.get(opts, :actor, {:engine, "mandate"}),
        site: prediction.site,
        props: [outcome: outcome] ++ Keyword.get(opts, :props, []),
        caused_by: prediction.id
      )
    end
  end

  @doc "Look up one entry."
  @spec fetch(t(), pos_integer()) :: {:ok, Fact.t()} | :error
  def fetch(%__MODULE__{entries: entries}, id) do
    case Enum.find(entries, &(&1.id == id)) do
      nil -> :error
      fact -> {:ok, fact}
    end
  end

  @doc "Entries at an exact tick."
  @spec at(t(), non_neg_integer()) :: [Fact.t()]
  def at(%__MODULE__{entries: entries}, tick), do: Enum.filter(entries, &(&1.tick == tick))

  @doc "Entries at or after a tick."
  @spec since(t(), non_neg_integer()) :: [Fact.t()]
  def since(%__MODULE__{entries: entries}, tick),
    do: Enum.filter(entries, &(&1.tick >= tick))

  @doc "Entries at or before a tick: the snapshot as of that moment."
  @spec up_to(t(), non_neg_integer()) :: [Fact.t()]
  def up_to(%__MODULE__{entries: entries}, tick),
    do: Enum.filter(entries, &(&1.tick <= tick))

  @doc "Entries about one site."
  @spec by_site(t(), String.t()) :: [Fact.t()]
  def by_site(%__MODULE__{entries: entries}, site),
    do: Enum.filter(entries, &(&1.site == site))

  @doc "Entries of one kind."
  @spec by_kind(t(), atom()) :: [Fact.t()]
  def by_kind(%__MODULE__{entries: entries}, kind),
    do: Enum.filter(entries, &(&1.kind == kind))

  @doc "Predictions that no `:settled` entry has closed yet."
  @spec pending(t()) :: [Fact.t()]
  def pending(%__MODULE__{} = log) do
    settled =
      log
      |> by_kind(:settled)
      |> Enum.map(& &1.caused_by)
      |> MapSet.new()

    log
    |> by_kind(:predicted)
    |> Enum.reject(&MapSet.member?(settled, &1.id))
  end

  @doc """
  Chain from the root entry to the given one, following `caused_by`.

  Returns `{:ok, [root, ..., entry]}` or `:error` for an unknown id.
  """
  @spec ancestors(t(), pos_integer()) :: {:ok, [Fact.t()]} | :error
  def ancestors(%__MODULE__{} = log, id) do
    with {:ok, fact} <- fetch(log, id) do
      {:ok, walk(log, fact, [], count(log) + 1)}
    end
  end

  defp walk(_log, nil, acc, _budget), do: acc

  defp walk(_log, _fact, acc, 0), do: acc

  defp walk(_log, %Fact{caused_by: nil} = fact, acc, _budget), do: [fact | acc]

  defp walk(log, %Fact{caused_by: parent_id} = fact, acc, budget) do
    case fetch(log, parent_id) do
      {:ok, parent} -> walk(log, parent, [fact | acc], budget - 1)
      :error -> [fact | acc]
    end
  end

  @doc "One entry as an open attribute list a Kanren goal can match."
  @spec to_term(Fact.t()) :: Keyword.t()
  def to_term(%Fact{} = fact) do
    [
      id: fact.id,
      tick: fact.tick,
      kind: fact.kind,
      actor: fact.actor,
      site: fact.site,
      props: fact.props,
      caused_by: fact.caused_by,
      evidence: fact.evidence
    ]
  end

  @doc "All entries as open attribute lists."
  @spec terms(t()) :: [Keyword.t()]
  def terms(%__MODULE__{entries: entries}), do: Enum.map(entries, &to_term/1)

  # ==== validation ==== //

  defp normalize_attrs(attrs) when is_map(attrs), do: normalize_attrs(Map.to_list(attrs))
  defp normalize_attrs(attrs) when is_list(attrs), do: {:ok, attrs}
  defp normalize_attrs(attrs), do: {:error, {:invalid_attributes, attrs}}

  defp check_tick(%__MODULE__{} = log, tick)
       when is_integer(tick) and tick >= 0 do
    case last(log) do
      nil ->
        :ok

      %Fact{tick: previous} when tick >= previous ->
        :ok

      %Fact{tick: previous} ->
        {:error, {:tick_going_backwards, tick, previous}}
    end
  end

  defp check_tick(_log, tick), do: {:error, {:invalid_tick, tick}}

  defp check_required(attrs) do
    attrs = Keyword.put_new(attrs, :actor, :world)
    attrs = Keyword.put_new(attrs, :props, [])
    attrs = Keyword.put_new(attrs, :evidence, [])

    missing =
      Enum.reject([:tick, :kind, :site], fn key ->
        case Keyword.fetch(attrs, key) do
          {:ok, value} -> not is_nil(value)
          :error -> false
        end
      end)

    cond do
      missing != [] ->
        {:error, {:missing_fields, missing}}

      attrs[:kind] not in @kinds ->
        {:error, {:invalid_kind, attrs[:kind]}}

      not (is_binary(attrs[:site]) and attrs[:site] != "") ->
        {:error, {:invalid_site, attrs[:site]}}

      true ->
        {:ok, attrs}
    end
  end

  defp check_actor(:world), do: :ok
  defp check_actor(actor) when is_atom(actor) and actor in [:world, :engine], do: :ok

  defp check_actor({role, name}) when is_atom(role) and is_binary(name) and name != "",
    do: :ok

  defp check_actor(actor), do: {:error, {:invalid_actor, actor}}

  defp check_props(props) when is_list(props) do
    if Enum.all?(props, &match?({key, _value} when is_atom(key), &1)),
      do: :ok,
      else: {:error, {:invalid_props, props}}
  end

  defp check_props(props) when is_map(props) do
    if Enum.all?(Map.keys(props), &is_atom/1),
      do: :ok,
      else: {:error, {:invalid_props, props}}
  end

  defp check_props(props), do: {:error, {:invalid_props, props}}

  defp check_refs(%__MODULE__{} = log, attrs) do
    with :ok <- check_caused_by(log, attrs[:caused_by]) do
      check_evidence(log, attrs[:evidence])
    end
  end

  defp check_caused_by(_log, nil), do: :ok

  defp check_caused_by(%__MODULE__{} = log, id) when is_ref(id) do
    case fetch(log, id) do
      {:ok, _fact} -> :ok
      :error -> {:error, {:unknown_caused_by, id}}
    end
  end

  defp check_caused_by(_log, id), do: {:error, {:invalid_caused_by, id}}

  defp check_evidence(_log, []), do: :ok

  defp check_evidence(%__MODULE__{} = log, ids) when is_list(ids) do
    unknown = Enum.reject(ids, &match?({:ok, _fact}, fetch(log, &1)))

    case unknown do
      [] -> :ok
      ids -> {:error, {:unknown_evidence, ids}}
    end
  end

  defp check_evidence(_log, evidence), do: {:error, {:invalid_evidence, evidence}}

  defp check_kind_rules(%__MODULE__{} = log, attrs) do
    case attrs[:kind] do
      :predicted -> check_prediction(attrs)
      :settled -> check_settlement(log, attrs)
      _other -> :ok
    end
  end

  defp check_prediction(attrs) do
    props = attrs[:props]

    cond do
      not is_binary(props[:claim]) or props[:claim] == "" ->
        {:error, {:prediction_needs_claim, props[:claim]}}

      not is_integer(props[:due_tick]) or props[:due_tick] < 0 ->
        {:error, {:prediction_needs_due_tick, props[:due_tick]}}

      props[:due_tick] <= attrs[:tick] ->
        {:error, {:prediction_due_in_the_past, props[:due_tick], attrs[:tick]}}

      true ->
        :ok
    end
  end

  defp check_settlement(%__MODULE__{} = log, attrs) do
    with {:ok, prediction} <- require_parent(log, attrs),
         :ok <- expect_kind(prediction, :predicted),
         :ok <- ensure_pending(log, prediction.id) do
      check_outcome(attrs[:props][:outcome])
    end
  end

  defp require_parent(%__MODULE__{} = log, attrs) do
    case attrs[:caused_by] do
      nil -> {:error, {:settlement_needs_caused_by}}
      id -> fetch(log, id)
    end
  end

  defp expect_kind(%Fact{kind: kind}, expected) when kind == expected, do: :ok

  defp expect_kind(%Fact{kind: kind, id: id}, expected),
    do: {:error, {:wrong_fact_kind, id, kind, expected}}

  defp check_outcome(outcome) when outcome in @outcomes, do: :ok
  defp check_outcome(outcome), do: {:error, {:invalid_outcome, outcome, @outcomes}}

  defp ensure_pending(%__MODULE__{} = log, prediction_id) do
    if Enum.any?(by_kind(log, :settled), &(&1.caused_by == prediction_id)) do
      {:error, {:already_settled, prediction_id}}
    else
      :ok
    end
  end
end
