defmodule Voy.Kanren.CoreTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.Kanren.{ReifiedVar, Result}

  test "occurs-check rejects cyclic terms" do
    assert run(1, fn x -> eq(x, [x]) end) == []
  end

  test "an open list tail unifies with a closed list" do
    results =
      run(1, fn q ->
        fresh [tail] do
          eq(q, [{:a, 1} | tail])
          eq(q, a: 1)
        end
      end)

    assert terms(results) == [[a: 1]]
  end

  test "propso does not impose attribute order on an unbound entity" do
    same =
      run(1, fn entity ->
        all([propso(entity, a: 1, b: 2), eq(entity, a: 1, b: 2)])
      end)

    swapped =
      run(1, fn entity ->
        all([propso(entity, a: 1, b: 2), eq(entity, b: 2, a: 1)])
      end)

    assert terms(same) == [[a: 1, b: 2]]
    assert terms(swapped) == [[b: 2, a: 1]]
  end

  test "propso preserves extra attributes and rejects missing ones" do
    with_extra =
      run(1, fn entity ->
        all([
          propso(entity, a: 1, b: 2),
          eq(entity, c: 3, b: 2, a: 1)
        ])
      end)

    assert terms(with_extra) == [[c: 3, b: 2, a: 1]]

    assert run(1, fn q ->
             all([eq(q, a: 1), propso(q, missing: 2)])
           end) == []
  end

  test "compound disequality keeps its meaning across later bindings" do
    satisfied =
      run(1, fn q ->
        fresh [u, v] do
          neq({u, 1}, {2, v})
          eq(u, 2)
          eq(v, 2)
          eq(q, {u, v})
        end
      end)

    violated =
      run(1, fn q ->
        fresh [u, v] do
          neq({u, 1}, {2, v})
          eq(u, 2)
          eq(v, 1)
          eq(q, {u, v})
        end
      end)

    assert terms(satisfied) == [{2, 2}]
    assert violated == []
  end

  test "reify returns a stable result with no raw variables in residual constraints" do
    [result] =
      run(1, fn q ->
        fresh [x, y] do
          neq({x, 1}, {2, y})
          eq(q, {x, y})
        end
      end)

    assert %Result{} = result
    assert {%ReifiedVar{}, %ReifiedVar{}} = result.term
    assert [_] = result.constraints
    refute inspect(result) =~ "%Voy.Kanren.Var{"
  end

  test "reify omits constraints disconnected from the query" do
    [result] =
      run(1, fn q ->
        fresh [x, unrelated] do
          neq(unrelated, 1)
          eq(q, x)
        end
      end)

    assert %Result{term: %ReifiedVar{}, constraints: []} = result
  end

  test "fresh, conde, and tableo are thin relational helpers" do
    assert terms(
             run(2, fn q ->
               conde do
                 [eq(q, :first)]
                 [eq(q, :second)]
               end
             end)
           ) == [:first, :second]

    rows = [[id: :first, value: 1], [id: :second, value: 2]]
    assert terms(run(2, fn q -> tableo(rows, id: q) end)) == [:first, :second]
  end
end
