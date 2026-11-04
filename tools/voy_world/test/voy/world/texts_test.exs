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
    assert Timeline.month(:flood_month) == 10
    assert Timeline.chinese_numeral(28) == "二十八"
  end

  test "structure templates render cleanly" do
    assert Texts.validate() == :ok
    assert Texts.render!(:structure_assimilated, :upstream) == "302队废弃爆破钻探台（常驻脉冲源）"
    assert Texts.render!(:structure_assimilated, :pass_zheng) == "峡谷隘口·原铜钲基座改挂红绸东方红气缸体的木架 + 验山垂架"
    assert Texts.render!(:structure_ancestral, :pass_stele) == "完好如初的古寨石雕分水堰（c0 维持源完好）"

    assert length(Texts.keys()) == 6

    for key <- Texts.keys() do
      assert Atom.to_string(key) =~ ~r/^structure_/
      assert {:ok, fields} = Texts.fields(key)
      assert fields != []

      for field <- fields do
        assert {:ok, rendered} = Texts.render(key, field)
        assert rendered != ""
        refute rendered =~ "{{"
      end
    end
  end

  test "unknown keys and fields are reported" do
    assert {:error, {:unknown_text, :nope, :title}} = Texts.render(:nope, :title)

    assert {:error, {:unknown_text, :structure_assimilated, :nope}} =
             Texts.render(:structure_assimilated, :nope)

    assert :error = Texts.fields(:nope)
  end
end
