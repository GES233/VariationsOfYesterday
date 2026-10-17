defmodule Voy.Kanren.Core do
  @moduledoc """
  A small relational kernel with occurs-check, fair search, disequality,
  and open association-list attributes.

  Queries over fully open attributes can describe infinitely many lists.
  Use `run/2` with a finite limit unless the entity has a closed skeleton.
  """

  alias Voy.Kanren.{ReifiedVar, Result, State, Var}

  @type stream :: [] | {:mature, State.t(), stream()} | {:immature, (-> stream())}
  @type goal :: (State.t() -> stream())

  def walk(%Var{} = u, subst) do
    case Map.fetch(subst, u) do
      {:ok, value} -> walk(value, subst)
      :error -> u
    end
  end

  def walk(u, _subst), do: u

  @spec unify(term(), term(), State.t()) :: State.t() | nil
  def unify(u, v, %State{subst: subst, neq: neq} = state) do
    case unify_terms(u, v, subst) do
      nil ->
        nil

      new_subst ->
        case verify_neq(neq, new_subst, []) do
          nil -> nil
          valid_neq -> %{state | subst: new_subst, neq: valid_neq}
        end
    end
  end

  defp verify_neq([], _subst, acc), do: Enum.reverse(acc)

  defp verify_neq([{a, b} | rest], subst, acc) do
    case unify_terms(a, b, subst) do
      nil -> verify_neq(rest, subst, acc)
      ^subst -> nil
      _extended -> verify_neq(rest, subst, [{a, b} | acc])
    end
  end

  defp unify_terms(u, v, subst) do
    u = walk(u, subst)
    v = walk(v, subst)

    cond do
      u == v ->
        subst

      var?(u) ->
        ext_s(u, v, subst)

      var?(v) ->
        ext_s(v, u, subst)

      is_list(u) and is_list(v) ->
        unify_lists(u, v, subst)

      is_tuple(u) and is_tuple(v) and tuple_size(u) == tuple_size(v) ->
        unify_terms(Tuple.to_list(u), Tuple.to_list(v), subst)

      true ->
        nil
    end
  end

  defp unify_lists([], [], subst), do: subst

  defp unify_lists([u | us], [v | vs], subst) do
    case unify_terms(u, v, subst) do
      nil -> nil
      new_subst -> unify_terms(us, vs, new_subst)
    end
  end

  defp unify_lists(_, _, _), do: nil

  defp ext_s(u, v, subst) do
    if occurs?(u, v, subst), do: nil, else: Map.put(subst, u, v)
  end

  defp occurs?(x, v, subst) do
    v = walk(v, subst)

    cond do
      x == v -> true
      var?(v) -> false
      is_list(v) -> occurs_list?(x, v, subst)
      is_tuple(v) -> v |> Tuple.to_list() |> Enum.any?(&occurs?(x, &1, subst))
      true -> false
    end
  end

  defp occurs_list?(_x, [], _subst), do: false

  defp occurs_list?(x, [h | t], subst) do
    occurs?(x, h, subst) or occurs?(x, t, subst)
  end

  def mzero, do: []
  def unit(%State{} = state), do: {:mature, state, []}

  def mplus([], stream), do: stream
  def mplus({:immature, f}, stream), do: {:immature, fn -> mplus(stream, f.()) end}
  def mplus({:mature, h, t}, stream), do: {:mature, h, mplus(t, stream)}

  def bind([], _goal), do: []
  def bind({:immature, f}, goal), do: {:immature, fn -> bind(f.(), goal) end}
  def bind({:mature, h, t}, goal), do: mplus(goal.(h), bind(t, goal))

  def eq(u, v) do
    fn %State{} = state ->
      case unify(u, v, state) do
        nil -> mzero()
        new_state -> unit(new_state)
      end
    end
  end

  def neq(u, v) do
    fn %State{subst: subst, neq: neq} = state ->
      case unify_terms(u, v, subst) do
        nil -> unit(state)
        ^subst -> mzero()
        _extended -> unit(%{state | neq: [{u, v} | neq]})
      end
    end
  end

  def conj(g1, g2), do: fn state -> bind(g1.(state), g2) end
  def disj(g1, g2), do: fn state -> mplus(g1.(state), g2.(state)) end

  def all([]), do: fn state -> unit(state) end
  def all([goal]), do: goal
  def all([goal | goals]), do: conj(goal, all(goals))

  def any([]), do: fn _state -> mzero() end
  def any([goal]), do: goal
  def any([goal | goals]), do: disj(goal, any(goals))

  def delay(goal_fun), do: fn state -> {:immature, fn -> goal_fun.().(state) end} end

  defp pull([]), do: []
  defp pull({:immature, f}), do: pull(f.())
  defp pull({:mature, _, _} = stream), do: stream

  def take(stream, n), do: stream |> do_take(n, []) |> Enum.reverse()

  defp do_take(_stream, 0, acc), do: acc

  defp do_take(stream, n, acc) do
    case pull(stream) do
      [] -> acc
      {:mature, h, t} -> do_take(t, n - 1, [h | acc])
    end
  end

  def take_all(stream), do: stream |> do_take_all([]) |> Enum.reverse()

  defp do_take_all(stream, acc) do
    case pull(stream) do
      [] -> acc
      {:mature, h, t} -> do_take_all(t, [h | acc])
    end
  end

  def call_fresh(f) do
    fn %State{counter: counter} = state ->
      var = %Var{id: counter}
      goal = f.(var)
      goal.(%{state | counter: counter + 1})
    end
  end

  def fresh_n(0, f), do: f.([])

  def fresh_n(n, f) when n > 0 do
    call_fresh(fn var ->
      fresh_n(n - 1, fn vars -> f.([var | vars]) end)
    end)
  end

  def membero(x, list) do
    fresh_n(2, fn [h, t] ->
      conj(
        eq(list, [h | t]),
        disj(eq(x, h), delay(fn -> membero(x, t) end))
      )
    end)
  end

  def lookupo(k, v, entity), do: membero({k, v}, entity)

  def propso(_entity, []), do: fn state -> unit(state) end

  def propso(entity, [{k, v} | rest]) do
    conj(lookupo(k, v, entity), propso(entity, rest))
  end

  def tableo(rows, pattern) do
    fresh_n(1, fn [row] ->
      all([membero(row, rows), propso(row, pattern)])
    end)
  end

  def walk_star(v, subst) do
    v = walk(v, subst)

    cond do
      var?(v) -> v
      is_list(v) -> walk_star_list(v, subst)
      is_tuple(v) -> v |> Tuple.to_list() |> Enum.map(&walk_star(&1, subst)) |> List.to_tuple()
      true -> v
    end
  end

  defp walk_star_list([], _subst), do: []

  defp walk_star_list([h | t], subst) do
    [walk_star(h, subst) | walk_star(t, subst)]
  end

  defp reify_s(v, subst) do
    v = walk(v, subst)

    cond do
      var?(v) -> Map.put(subst, v, %ReifiedVar{id: map_size(subst)})
      is_list(v) -> reify_s_list(v, subst)
      is_tuple(v) -> v |> Tuple.to_list() |> Enum.reduce(subst, &reify_s/2)
      true -> subst
    end
  end

  defp reify_s_list([], subst), do: subst
  defp reify_s_list([h | t], subst), do: reify_s(t, reify_s(h, subst))

  def reify(var, %State{subst: subst, neq: neq}) do
    walked = walk_star(var, subst)

    walked_neq =
      Enum.map(neq, fn {a, b} ->
        {walk_star(a, subst), walk_star(b, subst)}
      end)

    active_neq = relevant_neq(walked_neq, free_vars(walked))

    reify_map =
      Enum.reduce(active_neq, reify_s(walked, %{}), fn {a, b}, acc ->
        reify_s(b, reify_s(a, acc))
      end)

    constraints =
      active_neq
      |> Enum.map(fn {a, b} ->
        {walk_star(a, reify_map), walk_star(b, reify_map)}
      end)
      |> Enum.uniq()

    %Result{term: walk_star(walked, reify_map), constraints: constraints}
  end

  defp relevant_neq(constraints, query_vars) do
    {active, relevant_vars} =
      Enum.reduce(constraints, {[], query_vars}, fn {a, b} = pair, {acc, vars} ->
        pair_vars = free_vars({a, b})

        if MapSet.disjoint?(pair_vars, vars) do
          {acc, vars}
        else
          {[pair | acc], MapSet.union(vars, pair_vars)}
        end
      end)

    if MapSet.equal?(relevant_vars, query_vars) do
      Enum.reverse(active)
    else
      relevant_neq(constraints, relevant_vars)
    end
  end

  defp free_vars(term), do: do_free_vars(term, MapSet.new())
  defp do_free_vars(%Var{} = var, acc), do: MapSet.put(acc, var)

  defp do_free_vars([h | t], acc) do
    do_free_vars(t, do_free_vars(h, acc))
  end

  defp do_free_vars(tuple, acc) when is_tuple(tuple) do
    tuple |> Tuple.to_list() |> Enum.reduce(acc, &do_free_vars/2)
  end

  defp do_free_vars(_term, acc), do: acc
  defp var?(%Var{}), do: true
  defp var?(_term), do: false

  def run(n, f) do
    call_fresh(f).(%State{})
    |> take(n)
    |> Enum.map(&reify(%Var{id: 0}, &1))
  end

  def run_all(f) do
    call_fresh(f).(%State{})
    |> take_all()
    |> Enum.map(&reify(%Var{id: 0}, &1))
  end

  def terms(results), do: Enum.map(results, & &1.term)
end
