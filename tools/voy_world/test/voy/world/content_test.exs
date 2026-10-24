defmodule Voy.World.ContentTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.{Chronicle, Content, Projection}

  import Voy.World.TestFixtures

  test "the shipped pack loads and is lint-clean" do
    assert {:ok, pack} = Content.load()

    assert map_size(pack.claims) == 12
    assert length(pack.documents) == 24
    assert Content.lint() == %{errors: [], warnings: []}

    assert Content.slots() |> Enum.sort() == [
             :council_document,
             :gazetteer,
             :official_report,
             :tech_note
           ]
  end

  test "Projection.compile_archiveso selects directly from the pack and matches archives_for" do
    pack = Content.default_pack()

    pairs = for topo <- all_topographies(), [slice] <- [raw_slices(topo)], do: {topo, slice}

    assert length(pairs) == 24

    for {topo, slice} <- pairs do
      assert {:ok, archives} = Content.archives_for(slice, pack)

      assert archives == slice[:archives],
             "archives differ for #{inspect(Keyword.take(topo, Voy.World.Schema.feature_keys()))}"
    end
  end

  test "Projection.compile_archiveso reflects custom pack edits without code changes" do
    pack = Content.load!()

    edited_documents =
      Enum.map(pack.documents, fn doc ->
        if doc.id == "doc.official.rain_excuse" do
          %{doc | body: "【自定通报】{{place.down}}与{{place.up}}于{{date.era2b}}年秋汛"}
        else
          doc
        end
      end)

    custom_pack = %{pack | documents: edited_documents}
    topo = baishui_topo()
    [slice] = raw_slices(topo)

    [projected] =
      run_all(fn q ->
        Projection.compile_archiveso(
          topo,
          slice[:era1],
          slice[:era2a],
          slice[:era2b],
          slice[:era3],
          q,
          custom_pack
        )
      end)
      |> terms()

    assert projected[:official_report][:summary] == "【自定通报】白水坝与野猪箐裂隙于1979年秋汛"
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

  test "preview renders player-facing dossiers and template scaffolds slots" do
    assert Content.preview_presets() == [
             "baishui",
             "heishui",
             "xuanguan",
             "nuoliang",
             "mahao",
             "tingshan"
           ]

    assert {:ok, text} = Content.preview("baishui")
    assert text =~ "【节点档案预览：baishui（白水坝）】"
    assert text =~ "① 旧州府残卷：《旧州府抚夷清丈残卷·铜钲案》"
    assert text =~ "② 垦殖总场官样通报"
    assert text =~ "③ 技术员手簿批注"
    assert text =~ "④ 第三纪规约：《场部—议事会联席保命规约》"

    assert {:ok, all_text} = Content.preview("all")
    assert all_text =~ "白水坝"
    assert all_text =~ "黑水坪"
    assert all_text =~ "悬棺寨"
    assert all_text =~ "糯良古寨"

    assert {:error, msg} = Content.preview("baishu")
    assert msg =~ "你是不是想写 \"baishui\""

    assert {:ok, tpl} = Content.template("tech_note")
    assert tpl =~ "slot: tech_note"
    assert tpl =~ "{{date.era2b_short}}"

    assert {:error, slot_err} = Content.template("tech_notes")
    assert slot_err =~ "你是不是想写 \"tech_note\""
  end

  describe "the linter catches author mistakes" do
    test "an unknown front-matter field with closest-value suggestion" do
      assert error_messages([
               {:replace, "documents/council/joint_council.md", "governance:", "govenance:"}
             ])
             |> Enum.any?(&(&1 =~ "未知字段" and &1 =~ "你是不是想写 \"governance\""))
    end

    test "an illegal fate value with closest-value suggestion" do
      assert error_messages([
               {:replace, "documents/official/rain_excuse.md", "Fate_Assimilated_NewZhai",
                "Fate_Assimilated_NewZha"}
             ])
             |> Enum.any?(&(&1 =~ "fate 里的" and &1 =~ "你是不是想写 \"Fate_Assimilated_NewZhai\""))
    end

    test "a citation of a claim that does not exist" do
      assert error_messages([
               {:replace, "documents/tech/blasting_zheng.md",
                "claim.c0_removed_and_vulnerable_window_blasting",
                "claim.c0_removed_and_vulnerable_window"}
             ])
             |> Enum.any?(
               &(&1 =~ "不存在的归因卡" and
                   &1 =~ "你是不是想写 \"claim.c0_removed_and_vulnerable_window_blasting\"")
             )
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
                ""},
               {:replace, "documents/tech/blasting_zheng.md",
                "gazetteer_case: case_1894_zheng_return_calmed_slide\n", ""}
             ])
             |> Enum.any?(&(&1 =~ "适用条件重叠"))
    end

    test "a notebook pinned to the wrong precedent no longer matches the history" do
      pack = Content.load!()
      [slice] = raw_slices(baishui_topo())

      mispinned =
        Enum.map(pack.documents, fn doc ->
          if doc.id == "doc.tech.blasting_zheng" do
            %{doc | gazetteer_cases: [:case_mountain_too_steep_for_tithing]}
          else
            doc
          end
        end)

      assert {:error, {:no_document, :tech_note, criteria}} =
               Content.archives_for(slice, %{pack | documents: mispinned})

      assert criteria[:gazetteer_case] == :case_1894_zheng_return_calmed_slide
    end

    test "a slot with no document for some history" do
      assert error_messages([{:delete, "documents/council/sealed_zone.md"}])
             |> Enum.any?(&(&1 =~ "没有任何 council_document 文档适用"))
    end

    test "an unregistered placeholder with closest-value suggestion" do
      assert error_messages([
               {:replace, "documents/gazetteer/zheng_case.md", "{{date.era2a_zheng}}",
                "{{date.era2a_zhen}}"}
             ])
             |> Enum.any?(&(&1 =~ "无法解析的占位符" and &1 =~ "你是不是想写 \"{{date.era2a_zheng}}\""))
    end

    test "a hardcoded year in the body" do
      assert error_messages([
               {:replace, "documents/gazetteer/zheng_case.md", "{{date.era2a_zheng}}", "光绪二十年"}
             ])
             |> Enum.any?(&(&1 =~ "不能直接写死年份"))

      assert error_messages([
               {:replace, "documents/tech/blasting_zheng.md", "{{date.era2b_short}}年", "79年"}
             ])
             |> Enum.any?(&(&1 =~ "不能直接写死年份"))
    end

    test "a document without the front-matter fence" do
      assert error_messages([
               {:replace, "documents/gazetteer/zheng_case.md", "---\nslot:", "slot:"}
             ])
             |> Enum.any?(&(&1 =~ "必须以 --- 开头"))
    end

    test "a claims table with an unknown column" do
      assert error_messages([{:replace, "claims.tsv", "allowed_for", "allowed"}])
             |> Enum.any?(&(&1 =~ "未知列" and &1 =~ "你是不是想写 \"allowed_for\""))
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
    normalized = path |> File.read!() |> String.replace("\r\n", "\n")
    File.write!(path, String.replace(normalized, from, to))
  end

  defp apply_mutation(dest, {:delete, relative}) do
    File.rm!(Path.join(dest, relative))
  end

  defp apply_mutation(dest, {:rewrite_crlf, relative}) do
    path = Path.join(dest, relative)
    content = path |> File.read!() |> String.replace("\r\n", "\n") |> String.replace("\n", "\r\n")
    File.write!(path, "\uFEFF" <> content)
  end
end
