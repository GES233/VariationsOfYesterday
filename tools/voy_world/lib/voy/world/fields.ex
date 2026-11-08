defmodule Voy.World.Fields do
  @moduledoc """
  Uniform field access for the keyword lists and atom/string-keyed maps the
  world model passes around.

  Every reader used to carry its own copy of these clauses (audit E5). Keeping
  them here defines the string-key fallback and the "missing vs nil" semantics
  once, instead of letting five copies drift apart.
  """

  @type fields :: keyword() | map()

  @doc "Fetch a field; maps fall back to the string form of the atom key."
  @spec fetch(fields(), atom()) :: {:ok, term()} | :error
  def fetch(fields, key) when is_list(fields), do: Keyword.fetch(fields, key)

  def fetch(fields, key) when is_map(fields) do
    case Map.fetch(fields, key) do
      {:ok, value} -> {:ok, value}
      :error -> Map.fetch(fields, Atom.to_string(key))
    end
  end

  def fetch(_fields, _key), do: :error

  @doc "Fetch a field or raise `KeyError` (same contract as `Keyword.fetch!/2`)."
  @spec fetch!(fields(), atom()) :: term()
  def fetch!(fields, key) do
    case fetch(fields, key) do
      {:ok, value} -> value
      :error -> raise KeyError, key: key, term: fields
    end
  end

  @doc "Fetch a field, or `nil` when absent."
  @spec get(fields(), atom()) :: term() | nil
  def get(fields, key) do
    case fetch(fields, key) do
      {:ok, value} -> value
      :error -> nil
    end
  end

  @doc "Whether the field is present (a `nil` value still counts as present)."
  @spec has?(fields(), atom()) :: boolean()
  def has?(fields, key), do: match?({:ok, _}, fetch(fields, key))

  @doc """
  First present key wins (a `nil` value counts as absent); otherwise `default`.

  Accepts either a single key or a list of candidate keys.
  """
  @spec fetch_opt(fields(), atom() | [atom()], term()) :: term()
  def fetch_opt(fields, keys, default) when is_list(keys) do
    Enum.find_value(keys, default, fn key ->
      case fetch(fields, key) do
        {:ok, value} -> value
        :error -> nil
      end
    end)
  end

  def fetch_opt(fields, key, default) do
    case fetch(fields, key) do
      {:ok, value} -> value
      :error -> default
    end
  end
end
