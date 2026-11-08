defmodule Voy.World.ContentTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Content, Pathway, Schema, Witness}

  @branch :branch_01_baishui
  @names %{up: "野猪箐", pass: "断碑垭口", down: "白水坝"}

  test "the shipped five-hop pack loads and is lint-clean" do
    assert {:ok, pack} = Content.load()
    assert length(pack.documents) == 36
    assert Content.lint() == %{errors: [], warnings: []}
    assert Enum.count(pack.documents, &(&1.role == :main)) == 32
    assert Enum.count(pack.documents, &(&1.role == :decoy)) == 4
  end

  test "every relic and fate selects five local cards and two decoys" do
    pack = Content.default_pack()

    for relic <- Pathway.relics(), fate <- Schema.fates() do
      dossier = Witness.render_dossier(101, @branch, relic, fate, @names, pack: pack)
      assert Enum.count(dossier.documents, &(&1.role == :main)) == 5
      assert Enum.count(dossier.documents, &(&1.role == :decoy)) == 2
      refute Enum.any?(dossier.documents, &String.contains?(&1.body, "{{"))
    end
  end

  test "author edits change Witness output without touching Elixir" do
    pack = Content.load!()

    documents =
      Enum.map(pack.documents, fn document ->
        if document.id == "main/hop1/zheng" do
          %{document | body: "【作者改稿】{{place.down}}仅存此一跳。"}
        else
          document
        end
      end)

    dossier =
      Witness.render_dossier(
        101,
        @branch,
        :ancestral_bronze_zheng,
        :Fate_Assimilated_NewZhai,
        @names,
        pack: %{pack | documents: documents}
      )

    assert hd(dossier.documents).body == "【作者改稿】白水坝仅存此一跳。"
  end

  test "card selection reports missing and ambiguous cards" do
    pack = Content.load!()

    criteria = %{
      role: :main,
      hop: 1,
      relic: :ancestral_bronze_zheng,
      fate: nil,
      c0_status: nil,
      decoy_class: nil
    }

    assert {:ok, _} = Content.card_for(criteria, pack)
    assert {:error, {:no_document, _}} = Content.card_for(%{criteria | hop: 2}, pack)

    {:ok, duplicate} = Content.card_for(criteria, pack)

    assert {:error, {:ambiguous_documents, _, _}} =
             Content.card_for(criteria, %{pack | documents: [duplicate | pack.documents]})
  end

  test "preview contains only the five-hop narrative and supports seeds" do
    assert Content.preview_presets() == [
             "baishui",
             "heishui",
             "xuanguan",
             "nuoliang",
             "mahao",
             "tingshan"
           ]

    assert {:ok, text} = Content.preview("baishui", seed: 202)
    assert text =~ "【五跳微链卷宗（seed=202，含2份同车间干扰单据）】"
    assert text =~ "真链 Hop 5"
    refute text =~ "① 旧州府残卷"
    refute text =~ "② 垦殖总场官样通报"

    assert {:ok, all} = Content.preview("all", seed: 404)
    assert length(String.split(all, "【五跳微链卷宗（seed=404，含2份同车间干扰单据）】")) == 7
    assert {:error, _} = Content.preview("baishui", seed: "202")
  end

  test "templates use the five-hop card format" do
    assert {:ok, hop2} = Content.template("hop2")
    assert hop2 =~ "c0_status: c0_collapsed"
    assert {:ok, decoy} = Content.template("decoy")
    assert decoy =~ "decoy_class: material_mismatch"
    assert {:error, _} = Content.template("tech_note")
  end

  describe "the linter catches author mistakes" do
    test "unknown fields and illegal vocabulary" do
      assert error_messages([{:replace, "documents/main/hop1/zheng.md", "hop: 1", "hpo: 1"}])
             |> Enum.any?(&(&1 =~ "未知字段"))

      assert error_messages([
               {:replace, "documents/main/hop4/zheng_assimilated.md", "Fate_Assimilated_NewZhai",
                "Fate_Nope"}
             ])
             |> Enum.any?(&(&1 =~ "不合法"))
    end

    test "missing c0 and a missing required card" do
      assert error_messages([
               {:replace, "documents/main/hop2/zheng_collapsed.md", "c0_status: c0_collapsed\n",
                ""}
             ])
             |> Enum.any?(&(&1 =~ "c0_status"))

      assert error_messages([{:delete, "documents/main/hop4/zheng_assimilated.md"}])
             |> Enum.any?(&(&1 =~ "五跳卡片选择失败"))
    end

    test "duplicate applicable cards are rejected" do
      assert error_messages([
               {:duplicate, "documents/main/hop1/zheng.md", "documents/main/hop1/zheng_copy.md"}
             ])
             |> Enum.any?(&(&1 =~ "ambiguous_documents"))
    end

    test "unknown placeholders and hardcoded years are rejected" do
      assert error_messages([
               {:replace, "documents/main/hop1/zheng.md", "{{place.pass}}", "{{place.pas}}"}
             ])
             |> Enum.any?(&(&1 =~ "无法解析的占位符"))

      assert error_messages([{:replace, "documents/main/hop1/zheng.md", "光绪年间", "1979年"}])
             |> Enum.any?(&(&1 =~ "不能直接写死年份"))
    end

    test "a document without the front-matter fence is rejected" do
      assert error_messages([{:replace, "documents/main/hop1/zheng.md", "---\nhop:", "hop:"}])
             |> Enum.any?(&(&1 =~ "平铺头部"))
    end

    test "Windows line endings and a BOM are accepted" do
      assert %{errors: []} = lint_with([{:rewrite_crlf, "documents/main/hop1/zheng.md"}])
    end
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

  defp apply_mutation(dest, {:delete, relative}), do: File.rm!(Path.join(dest, relative))

  defp apply_mutation(dest, {:duplicate, source, target}) do
    File.cp!(Path.join(dest, source), Path.join(dest, target))
  end

  defp apply_mutation(dest, {:rewrite_crlf, relative}) do
    path = Path.join(dest, relative)
    content = path |> File.read!() |> String.replace("\r\n", "\n") |> String.replace("\n", "\r\n")
    File.write!(path, "\uFEFF" <> content)
  end
end
