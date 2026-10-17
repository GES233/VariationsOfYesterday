defmodule Voy.Kanren.State do
  @moduledoc "The immutable substitution, fresh-variable counter, and disequality store."

  alias Voy.Kanren.Var

  defstruct subst: %{}, counter: 0, neq: []

  @type substitution :: %{Var.t() => term()}
  @type t :: %__MODULE__{
          subst: substitution(),
          counter: non_neg_integer(),
          neq: [{term(), term()}]
        }
end
