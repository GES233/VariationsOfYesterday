defmodule Mix.Tasks.Voy.Content do
  @shortdoc "Lint the authoring content pack"

  @moduledoc """
  Checks the content pack under `content/` and prints author-facing issues.

      mix voy.content lint

  Errors block a merge. Warnings (for example an unused claim) do not, but
  they usually mean a document is missing.
  """

  use Mix.Task

  @impl true
  def run(argv) do
    Mix.Task.run("compile")

    case argv do
      ["lint"] ->
        lint()

      _other ->
        Mix.raise("用法：mix voy.content lint")
    end
  end

  defp lint do
    %{errors: errors, warnings: warnings} = Voy.World.Content.lint()

    Enum.each(errors, &IO.puts("✗ " <> Voy.World.Content.format_issue(&1)))
    Enum.each(warnings, &IO.puts("· " <> Voy.World.Content.format_issue(&1)))

    case errors do
      [] ->
        IO.puts("内容包检查通过（#{length(warnings)} 条提醒）")

      errors ->
        Mix.raise("#{length(errors)} 个内容错误，修正后再提交")
    end
  end
end
