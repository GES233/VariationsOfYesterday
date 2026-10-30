defmodule Mix.Tasks.Voy.Content do
  @shortdoc "Lint, preview, or scaffold the authoring content pack"

  @moduledoc """
  Authoring tools for the content pack under `content/`:

      mix voy.content lint
      mix voy.content preview [baishui|heishui|xuanguan|nuoliang|mahao|tingshan|all] [--seed N]
      mix voy.content template <gazetteer|official_report|tech_note|council_document>

    * `lint` checks the pack and prints author-facing issues (`file:line` plus
      closest-value hints). Errors block a merge; warnings do not.
    * `preview` compiles the preset node(s) and prints the player-facing
      structures and archives in reading order so writers can review voice and
      continuity without running the game. `--seed N` selects the world seed
      the appended five-hop micro-chain dossier is bound to (default 101).
    * `template` prints a blank Markdown document card for the chosen slot.
  """

  use Mix.Task

  @impl true
  def run(argv) do
    Mix.Task.run("compile")

    {opts, args, invalid} = OptionParser.parse(argv, strict: [seed: :integer])

    case invalid do
      [] -> dispatch(args, opts)
      [{flag, _value} | _] -> Mix.raise("无法识别的参数 #{flag}；用法见 mix help voy.content")
    end
  end

  defp dispatch(["lint"], _opts) do
    lint()
  end

  defp dispatch(["preview"], opts) do
    preview("all", opts)
  end

  defp dispatch(["preview", target], opts) do
    preview(target, opts)
  end

  defp dispatch(["template", slot], _opts) do
    template(slot)
  end

  defp dispatch(_other, _opts) do
    Mix.raise(
      "用法：\n" <>
        "  mix voy.content lint\n" <>
        "  mix voy.content preview [baishui|heishui|xuanguan|nuoliang|mahao|tingshan|all] [--seed N]\n" <>
        "  mix voy.content template <gazetteer|official_report|tech_note|council_document>"
    )
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

  defp preview(target, opts) do
    case Voy.World.Content.preview(target, opts) do
      {:ok, output} ->
        IO.puts(output)

      {:error, message} ->
        Mix.raise(message)
    end
  end

  defp template(slot) do
    case Voy.World.Content.template(slot) do
      {:ok, output} ->
        IO.write(output)

      {:error, message} ->
        Mix.raise(message)
    end
  end
end
