defmodule Voy.Kanren do
  @moduledoc "Imports the relational kernel and its optional surface syntax."

  defmacro __using__(_opts) do
    quote do
      import Voy.Kanren.Core
      import Voy.Kanren.DSL
    end
  end
end
