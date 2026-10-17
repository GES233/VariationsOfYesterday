defmodule Voy.World.CausalityTest do
  use ExUnit.Case, async: true
  use Voy.Kanren

  alias Voy.World.Causality

  test "abduction recovers blasting and distinguishes Strained from Oscillating" do
    results = causal_results(:premature_excitation_spike)

    assert length(results) == 2

    assert results |> Enum.map(&get_in(&1, [:propagation, :regime])) |> MapSet.new() ==
             MapSet.new([:Strained, :Oscillating])

    for result <- results do
      assert get_in(result, [:initial, :actor]) == :survey_team_302
      assert get_in(result, [:initial, :phase]) == :RelativeRefractory
      assert get_in(result, [:propagation, :from]) == :boar_gully
      assert get_in(result, [:propagation, :via]) == :broken_stele_pass
      assert get_in(result, [:propagation, :to]) == :baishui_flat
    end
  end

  test "lenses share facts while respecting their narrative taboos" do
    results =
      run_all(fn q ->
        fresh [initial, propagation, outcome, lens] do
          Causality.causal_stageso(initial, propagation, outcome)

          propso(outcome,
            settlement: :baishui_flat,
            outcome: :premature_excitation_spike
          )

          Causality.lens_projectiono(lens, initial, propagation, outcome, q)
        end
      end)
      |> terms()

    assert length(results) == 4

    for result <- results do
      assert result[:trigger_artifact] == "2号岩石硝铵炸药点炮"

      refute result[:lens] == :OfficialHQ and
               result[:adopted_cause_tag] in [
                 :c0_collapsed_and_vulnerable_blasting,
                 :secondary_ignition_in_oscillation,
                 :mountain_god_breath_disturbed
               ]
    end

    assert results |> Enum.filter(&(&1[:regime_context] == :Strained)) |> length() == 3
    assert [oscillating] = Enum.filter(results, &(&1[:regime_context] == :Oscillating))
    assert oscillating[:lens] == :TechInner
  end

  test "ritual synthesis finds the assimilated crew without preselecting an actor" do
    results =
      run_all(fn q ->
        fresh [initial, propagation, outcome, actor, site, phase, item] do
          Causality.causal_stageso(initial, propagation, outcome)
          propso(initial, actor: actor, action_site: site, phase: phase, action_item: item)

          propso(outcome,
            settlement: :baishui_flat,
            outcome: :spike_delayed_by_8_6
          )

          eq(q, actor: actor, site: site, phase: phase, item: item)
        end
      end)
      |> terms()

    assert [result] = results
    assert result[:actor] == :assimilated_brigade_04
    assert result[:site] == :broken_stele_pass
    assert result[:phase] == :AbsoluteRefractory
    assert result[:item][:name] == "红绸包裹的东方红-54气缸体敲击礼"
  end

  defp causal_results(outcome_tag) do
    run_all(fn q ->
      fresh [initial, propagation, outcome] do
        Causality.causal_stageso(initial, propagation, outcome)
        propso(outcome, settlement: :baishui_flat, outcome: outcome_tag)
        eq(q, initial: initial, propagation: propagation, outcome: outcome)
      end
    end)
    |> terms()
  end
end
