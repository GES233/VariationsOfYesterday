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

  test "documents render byte-identically to the strings the rules used to carry" do
    assert Texts.validate() == :ok

    assert Texts.render!(:archive_gazetteer_zheng, :excerpt) ==
             "光绪二十年清丈局强收寨门铜钲入库铸钱，是秋山鸣石崩掩没局署；" <>
               "流官惊惧，发还铜钲任山官依古法岁祭叩击，山患遂平（外甲内寨）"

    assert Texts.render!(:archive_gazetteer_stele, :excerpt) =~ "光绪二十八年委员毁峡口分水石碑"

    assert Texts.render!(:archive_cause_c0_blasting_zheng, :text) =~
             "79年收缴铜钲再度断绝阻尼（c0=0）"

    assert Texts.render!(:archive_cause_c0_blasting_stele, :text) =~ "79年拆毁分水石碑"

    assert Texts.render!(:lens_rain, :text) ==
             "十月连日秋雨导致上游常规岩层疲劳滑坡（系自然损耗，与排险爆破无关）"

    assert Texts.render!(:lens_blasting, :text) =~ "79年收走铜钲后阻尼归零"

    assert Texts.render!(:archive_council_joint_council, :title) ==
             "《场部—议事会联席保命规约》"
  end

  test "dates follow the timeline instead of the sentence" do
    assert {:ok, rendered} =
             Texts.render(:archive_gazetteer_zheng, :excerpt,
               reign_date: Timeline.reign_date(:guangxu, 1895)
             )

    assert rendered =~ "光绪二十一年"
    refute rendered =~ "光绪二十年"
  end

  test "every key has payload fields and no placeholder survives rendering" do
    assert length(Texts.keys()) == 21

    for key <- Texts.keys() do
      assert {:ok, fields} = Texts.fields(key)
      assert fields != []

      for field <- fields do
        assert {:ok, rendered} = Texts.render(key, field)
        assert rendered != ""
        refute rendered =~ "%{"
      end
    end
  end

  test "unknown keys, fields and bindings are reported" do
    assert {:error, {:unknown_text, :nope, :title}} = Texts.render(:nope, :title)
    assert {:error, {:unknown_text, :lens_rain, :nope}} = Texts.render(:lens_rain, :nope)
    assert :error = Texts.fields(:nope)
    assert :error = Texts.template(:nope, :text)

    assert {:error, {:unbound_placeholder, "%{other}"}} =
             Texts.render_template("%{other}", month_numeral: "十")

    assert {:ok, "no placeholders"} = Texts.render_template("no placeholders", [])
  end
end
