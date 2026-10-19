defmodule Voy.World.ContentTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.{Chronicle, Content}

  import Voy.World.TestFixtures

  test "the shipped pack loads and is lint-clean" do
    assert {:ok, pack} = Content.load()

    assert map_size(pack.claims) == 8
    assert length(pack.documents) == 17
    assert Content.lint() == %{errors: [], warnings: []}

    assert Content.slots() |> Enum.sort() == [
             :council_document,
             :gazetteer,
             :official_report,
             :tech_note
           ]
  end

  test "pack assembly reproduces the archives the rules build" do
    pack = Content.load!()

    pairs = for topo <- all_topographies(), [slice] <- [raw_slices(topo)], do: {topo, slice}

    assert length(pairs) == 8

    for {topo, slice} <- pairs do
      assert {:ok, archives} = Content.archives_for(slice, pack)

      assert archives == slice[:archives],
             "archives differ for #{inspect(Keyword.take(topo, Voy.World.Schema.feature_keys()))}"
    end
  end

  test "the assembled archives keep the two ledgers apart" do
    pack = Content.load!()

    for topo <- all_topographies(), [slice] <- [raw_slices(topo)] do
      assert {:ok, archives} = Content.archives_for(slice, pack)

      official = Keyword.fetch!(archives, :official_report)
      tech = Keyword.fetch!(archives, :tech_note)

      refute official[:claimed_cause] == tech[:true_cause]
      refute official[:summary] == tech[:note]
    end
  end

  test "bodies interpolate placeholders and refuse unknown ones" do
    tokens = Content.date_tokens()

    assert tokens["date.era2a_zheng"] == "光绪二十年"
    assert tokens["date.era2a_stele"] == "光绪二十八年"
    assert tokens["date.era2b_short"] == "79"

    assert Content.render("{{place.down}}", [{"place.down", "白水坝"}]) == "白水坝"
    assert Content.render("{{date.era2b_short}}年", [{"date.era2b_short", "79"}]) == "79年"

    assert_raise ArgumentError, ~r/unresolved placeholder/, fn ->
      Content.render("{{place.river}}", [])
    end
  end

  describe "the linter catches author mistakes" do
    test "an unknown front-matter field" do
      assert error_messages([
               {:replace, "documents/council/joint_council.md", "governance:", "govenance:"}
             ])
             |> Enum.any?(&(&1 =~ "未知字段"))
    end

    test "an illegal fate value" do
      assert error_messages([
               {:replace, "documents/official/rain_excuse.md", "Fate_Assimilated_NewZhai",
                "Fate_Nope"}
             ])
             |> Enum.any?(&(&1 =~ "fate 里的"))
    end

    test "a citation of a claim that does not exist" do
      assert error_messages([
               {:replace, "documents/tech/blasting_zheng.md",
                "claim.c0_removed_and_vulnerable_window_blasting", "claim.nope"}
             ])
             |> Enum.any?(&(&1 =~ "不存在的归因卡"))
    end

    test "a citation the lens is forbidden to use" do
      assert error_messages([
               {:replace, "documents/tech/blasting_zheng.md",
                "claim.c0_removed_and_vulnerable_window_blasting", "claim.seasonal_rain_mudslide"}
             ])
             |> Enum.any?(&(&1 =~ "不能引用"))
    end

    test "two documents competing for the same history" do
      assert error_messages([
               {:replace, "documents/tech/blasting_zheng.md", "relic: ancestral_bronze_zheng\n",
                ""}
             ])
             |> Enum.any?(&(&1 =~ "适用条件重叠"))
    end

    test "a slot with no document for some history" do
      assert error_messages([{:delete, "documents/council/sealed_zone.md"}])
             |> Enum.any?(&(&1 =~ "没有任何 council_document 文档适用"))
    end

    test "an unregistered placeholder" do
      assert error_messages([
               {:replace, "documents/gazetteer/zheng_case.md", "{{date.era2a_zheng}}",
                "{{date.era9}}"}
             ])
             |> Enum.any?(&(&1 =~ "无法解析的占位符"))
    end

    test "a document without the front-matter fence" do
      assert error_messages([
               {:replace, "documents/gazetteer/zheng_case.md", "---\nslot:", "slot:"}
             ])
             |> Enum.any?(&(&1 =~ "必须以 --- 开头"))
    end

    test "a claims table with an unknown column" do
      assert error_messages([{:replace, "claims.tsv", "allowed_for", "allowed"}])
             |> Enum.any?(&(&1 =~ "未知列"))
    end

    test "Windows line endings and a BOM are accepted" do
      assert %{errors: []} = lint_with([{:rewrite_crlf, "documents/gazetteer/zheng_case.md"}])
    end
  end

  defp raw_slices(topo) do
    run_all(fn query -> Chronicle.compile_branch_sliceo(topo, query) end) |> terms()
  end

  defp error_messages(mutations) do
    %{errors: errors} = lint_with(mutations)
    Enum.map(errors, & &1.message)
  end

  defp lint_with(mutations) do
    dir = Path.join(System.tmp_dir!(), "voy_content_#{System.unique_integer([:positive])}")
    dest = Path.join(dir, "content")
    File.mkdir_p!(dir)
    File.cp_r!(Content.pack_dir(), dest)

    try do
      Enum.each(mutations, &apply_mutation(dest, &1))
      Content.lint(dest)
    after
      File.rm_rf!(dir)
    end
  end

  defp apply_mutation(dest, {:replace, relative, from, to}) do
    path = Path.join(dest, relative)
    File.write!(path, String.replace(File.read!(path), from, to))
  end

  defp apply_mutation(dest, {:delete, relative}) do
    File.rm!(Path.join(dest, relative))
  end

  defp apply_mutation(dest, {:rewrite_crlf, relative}) do
    path = Path.join(dest, relative)
    content = path |> File.read!() |> String.replace("\n", "\r\n")
    File.write!(path, "\uFEFF" <> content)
  end
end
