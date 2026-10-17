defmodule Voy.Kanren.Var do
  @moduledoc "A logic variable identified within a substitution state."

  @enforce_keys [:id]
  defstruct [:id]

  @type t :: %__MODULE__{id: non_neg_integer()}

  defimpl String.Chars do
    def to_string(%{id: id}), do: "?#{id}"
  end
end

defmodule Voy.Kanren.ReifiedVar do
  @moduledoc "An unnamed logic variable in a reified result."

  @enforce_keys [:id]
  defstruct [:id]

  @type t :: %__MODULE__{id: non_neg_integer()}

  defimpl String.Chars do
    def to_string(%{id: id}), do: "?_#{id}"
  end
end

defmodule Voy.Kanren.Result do
  @moduledoc "A reified term and the disequality constraints still attached to it."

  @enforce_keys [:term, :constraints]
  defstruct [:term, :constraints]

  @type t :: %__MODULE__{term: term(), constraints: [{term(), term()}]}
end
