defmodule Voy.Kanren.DSL do
  @moduledoc "Thin syntax over the ordinary goal constructors in `Voy.Kanren.Core`."

  defmacro fresh(vars, do: block) when is_list(vars) do
    goals = block_goals(block)

    quote do
      Voy.Kanren.Core.fresh_n(unquote(length(vars)), fn unquote(vars) ->
        Voy.Kanren.Core.all(unquote(goals))
      end)
    end
  end

  defmacro conde(do: block) do
    branches =
      block
      |> block_goals()
      |> Enum.map(fn goals ->
        quote do
          Voy.Kanren.Core.delay(fn ->
            Voy.Kanren.Core.all(unquote(goals))
          end)
        end
      end)

    quote do
      Voy.Kanren.Core.any(unquote(branches))
    end
  end

  defp block_goals({:__block__, _, goals}), do: goals
  defp block_goals(goal), do: [goal]
end
