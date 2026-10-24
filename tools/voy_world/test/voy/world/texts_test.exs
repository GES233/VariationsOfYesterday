defmodule Voy.World.TextsTest do
  use ExUnit.Case, async: true

  alias Voy.World.{Texts, Timeline}

  test "the timeline renders the date forms the canon uses" do
    assert Timeline.validate() == :ok
    assert Timeline.year(:era2a_zheng_case) == 1894
    assert Timeline.year(:era2a_stele_case) == 1902
    assert Timeline.year(:era2b_reclamation) == 1979

    assert Timeline.reign_ordinal(:guangxu, 1894) == 20
    assert Timeline.reign_ordinal(:guangxu, 1902) == 28
    assert Timeline.reign_date(:guangxu, 1894) == "光绪二十年"
    assert Timeline.reign_date(:guangxu, 1902) == "光绪二十八年"
    assert Timeline.short_year(1979) == "79"
    assert Timeline.short_year(2004) == "04"
    assert Timeline.month(:flood_month) == 10

    assert Timeline.chinese_numeral(0) == "零"
    assert Timeline.chinese_numeral(1) == "一"
    assert Timeline.chinese_numeral(9) == "九"
    assert Timeline.chinese_numeral(10) == "十"
    assert Timeline.chinese_numeral(11) == "十一"
    assert Timeline.chinese_numeral(20) == "二十"
    assert Timeline.chinese_numeral(21) == "二十一"
    assert Timeline.chinese_numeral(28) == "二十八"
    assert Timeline.chinese_numeral(99) == "九十九"
  end

  test "code-side structure and lens templates render with unified {{...}} placeholders" do
    assert Texts.validate() == :ok

    assert Texts.render!(:structure_assimilated, :upstream) ==
             "302队废弃爆破钻探台（常驻脉冲源）"

    assert Texts.render!(:structure_assimilated, :pass_zheng) ==
             "峡谷隘口·原铜钲基座改挂红绸东方红气缸体的木架 + 验山垂架"

    assert Texts.render!(:structure_ancestral, :pass_stele) ==
             "完好如初的古寨石雕分水堰（c0 维持源完好）"

    assert Texts.render!(:lens_rain, :text) ==
             "十月连日秋雨导致上游常规岩层疲劳滑坡（系自然损耗，与排险爆破无关）"

    assert Texts.render!(:lens_blasting, :text) =~ "79年收走铜钲后阻尼归零"
  end

  test "archive entries have been retired in favour of the authoring pack" do
    for key <- Texts.keys() do
      refute Atom.to_string(key) |> String.starts_with?("archive_")
    end
  end

  test "dates follow the timeline instead of the sentence" do
    assert {:ok, rendered} =
             Texts.render(:lens_blasting, :text, "date.era2b_short": Timeline.short_year(1981))

    assert rendered =~ "81年"
    refute rendered =~ "79年"
  end

  test "every key has payload fields and no placeholder survives rendering" do
    assert length(Texts.keys()) == 10

    for key <- Texts.keys() do
      assert {:ok, fields} = Texts.fields(key)
      assert fields != []

      for field <- fields do
        assert {:ok, rendered} = Texts.render(key, field)
        assert rendered != ""
        refute rendered =~ "{{"
      end
    end
  end

  test "unknown keys, fields and bindings are reported" do
    assert {:error, {:unknown_text, :nope, :title}} = Texts.render(:nope, :title)
    assert {:error, {:unknown_text, :lens_rain, :nope}} = Texts.render(:lens_rain, :nope)
    assert :error = Texts.fields(:nope)
    assert :error = Texts.template(:nope, :text)

    assert {:error, {:unbound_placeholder, "{{other}}"}} =
             Texts.render_template("{{other}}", %{"date.flood_month" => "十"})

    assert {:ok, "no placeholders"} = Texts.render_template("no placeholders", [])
  end
end
