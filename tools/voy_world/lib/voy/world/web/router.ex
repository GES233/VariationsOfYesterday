defmodule Voy.World.Web.Router do
  @moduledoc """
  Plug router for the `Voy.World` interactive sandbox WebUI and JSON API.
  """

  use Plug.Router

  alias Voy.World.{
    Ascribe,
    Canon,
    Contract,
    Content,
    Observation,
    Pathway,
    Schema,
    Slice,
    Timeline,
    Witness
  }

  @static_dir Path.expand("../../../../priv/static", __DIR__)

  plug Plug.Static,
    at: "/static",
    from: @static_dir

  plug :match

  plug Plug.Parsers,
    parsers: [:json],
    pass: ["application/json"],
    json_decoder: JSON

  plug :dispatch

  get "/" do
    index_path = Path.join(@static_dir, "index.html")

    conn
    |> put_resp_content_type("text/html; charset=utf-8")
    |> send_resp(200, File.read!(index_path))
  end

  get "/api/overview" do
    contract = Contract.load!()
    lint = Content.lint()

    presets =
      Enum.map(Content.presets(), fn {id, topo} ->
        %{
          id: id,
          topo: Map.new(topo)
        }
      end)

    feature_domains =
      Map.new(Schema.feature_keys(), fn key ->
        {key, Schema.feature_domain(key)}
      end)

    payload = %{
      app: "voy_world",
      contract_path: Path.basename(contract.path),
      source_digest: Contract.source_digest(contract),
      presets: presets,
      schemas: %{
        feature_keys: Schema.feature_keys(),
        feature_domains: feature_domains,
        relics: Schema.relics(),
        c0_states: Schema.c0_states(),
        pulses: Contract.pulses(contract),
        world_pulses: Contract.world_pulses(contract),
        fates: Schema.fates(),
        fate_specs: Schema.fate_specs(),
        horizons: Schema.horizons(),
        interventions: Schema.interventions(),
        regimes: Schema.regimes(),
        windows: [:absolute, :relative, :late]
      },
      timeline: %{
        anchors: Timeline.anchors(),
        years: Timeline.years()
      },
      content_lint: %{
        error_count: length(lint.errors),
        warning_count: length(lint.warnings)
      }
    }

    send_json(conn, 200, payload)
  end

  get "/api/contract" do
    contract = Contract.load!()

    c0_truth =
      Map.new([:c0_collapsed, :c0_intact], fn state ->
        {:ok, truth} = Contract.c0_truth(contract, state)
        {state, truth}
      end)

    pulses =
      Enum.map(Contract.pulses(contract), fn kind ->
        {:ok, p} = Contract.pulse(contract, kind)

        safe_ticks =
          case Contract.safe_ticks(contract, kind) do
            {:ok, ticks} -> ticks
            :error -> []
          end

        Map.put(p, :safe_ticks, safe_ticks)
      end)

    windows =
      Map.new(Contract.windows(contract), fn {k, {lo, hi}} ->
        {k, [lo, hi]}
      end)

    payload = %{
      provenance: Contract.provenance(contract),
      hopf: Contract.hopf(contract),
      relaxation: Contract.relaxation(contract),
      oscillator: Contract.oscillator(contract),
      onset_comparison: Contract.onset_comparison(contract),
      period: Contract.period(contract),
      period_rows: Contract.period_rows(contract),
      period_spread: Contract.period_spread(contract),
      fire_threshold: %{
        area: Contract.fire_threshold(contract),
        declared_alpha_shock_area: Contract.declared_alpha_shock_area(contract)
      },
      c0_truth: c0_truth,
      pulses: pulses,
      outcome_table: Contract.outcome_table(contract),
      windows: windows,
      regime_samples: Contract.regime_samples(contract),
      claims: Contract.claims(contract),
      chain: Contract.chain(contract)
    }

    send_json(conn, 200, payload)
  end

  post "/api/chronicle/compile" do
    params = conn.body_params

    with {:ok, topo} <- build_topology(params),
         {:ok, slices} <- Voy.World.compile_branch(topo),
         {:ok, interventions} <- Voy.World.solve_interventions(topo) do
      send_json(conn, 200, %{
        ok: true,
        topography: Map.new(topo),
        slices: slices,
        interventions: interventions
      })
    else
      {:error, reason} ->
        send_json(conn, 422, %{ok: false, error: format_reason(reason)})
    end
  end

  post "/api/chronicle/reverse" do
    fate = Map.get(conn.body_params, "fate")

    case Voy.World.reverse_worldgen(fate) do
      {:ok, slices} ->
        send_json(conn, 200, %{ok: true, fate: fate, count: length(slices), slices: slices})

      {:error, reason} ->
        send_json(conn, 422, %{ok: false, error: format_reason(reason)})
    end
  end

  post "/api/chronicle/interventions" do
    params = conn.body_params
    source = Map.get(params, "source") || params
    opts = Map.get(params, "filters", %{})

    case Voy.World.solve_interventions(source, opts) do
      {:ok, transitions} ->
        send_json(conn, 200, %{ok: true, transitions: transitions})

      {:error, reason} ->
        send_json(conn, 422, %{ok: false, error: format_reason(reason)})
    end
  end

  post "/api/dossier" do
    params = conn.body_params

    with {:ok, relic} <- Schema.normalize_relic(Map.get(params, "relic", "ancestral_bronze_zheng")),
         {:ok, fate} <- Schema.normalize_fate(Map.get(params, "fate", "Fate_Assimilated_NewZhai")),
         {:ok, horizon} <- Schema.normalize_horizon(Map.get(params, "horizon", "locked")),
         {:ok, seed} <- parse_int(Map.get(params, "seed", 101), :invalid_seed) do
      branch_id = Map.get(params, "branch_id", "branch_sandbox")

      names = %{
        up: non_empty_or(Map.get(params, "up_name"), "野猪箐裂隙"),
        pass: non_empty_or(Map.get(params, "pass_name"), "断碑垭口"),
        down: non_empty_or(Map.get(params, "down_name"), "白水坝")
      }

      dossier = Witness.render_dossier(seed, branch_id, relic, fate, names, horizon: horizon)
      {:ok, %{chain: chain, decoys: decoys}} = Pathway.compile_chain(relic, fate)

      send_json(conn, 200, %{
        ok: true,
        dossier: dossier,
        pathway: %{
          chain: Enum.map(chain, &serialize_edge/1),
          decoys: Enum.map(decoys, &serialize_edge/1)
        }
      })
    else
      {:error, reason} ->
        send_json(conn, 422, %{ok: false, error: format_reason(reason)})
    end
  end

  post "/api/dossier/settle" do
    params = conn.body_params
    dossier = Map.get(params, "dossier")
    intervention = Map.get(params, "intervention")

    names =
      case Map.get(params, "names") do
        %{} = map -> map
        _ -> []
      end

    case Voy.World.settle_open_dossier(dossier, intervention, names: names) do
      {:ok, settled} ->
        send_json(conn, 200, %{ok: true, dossier: settled})

      {:error, reason} ->
        send_json(conn, 422, %{ok: false, error: format_reason(reason)})
    end
  end

  post "/api/pathway/ablate" do
    params = conn.body_params

    with {:ok, relic} <- Schema.normalize_relic(Map.get(params, "relic", "ancestral_bronze_zheng")),
         {:ok, fate} <- Schema.normalize_fate(Map.get(params, "fate", "Fate_Assimilated_NewZhai")) do
      enabled_hops =
        params
        |> Map.get("enabled_hops", [1, 2, 3, 4, 5])
        |> Enum.filter(&(&1 in 1..5))

      include_decoys =
        params
        |> Map.get("include_decoys", [])
        |> Enum.map(&to_string/1)

      {:ok, %{chain: chain, decoys: decoys}} = Pathway.compile_chain(relic, fate)

      selected_main = Enum.filter(chain, &(&1.hop in enabled_hops))

      selected_decoys =
        Enum.filter(decoys, fn edge ->
          to_string(edge.attrs[:decoy_class]) in include_decoys
        end)

      pool = selected_main ++ selected_decoys
      connections = Pathway.connect_edges(pool)

      send_json(conn, 200, %{
        ok: true,
        enabled_hops: enabled_hops,
        include_decoys: include_decoys,
        pool_size: length(pool),
        connected_count: length(connections),
        connections:
          Enum.map(connections, fn c ->
            %{
              start: inspect(c.start),
              finish: inspect(c.finish),
              vessel: inspect(c.vessel),
              hops: Enum.map(c.path, & &1.hop)
            }
          end)
      })
    else
      {:error, reason} ->
        send_json(conn, 422, %{ok: false, error: format_reason(reason)})
    end
  end

  post "/api/ascribe/analyze" do
    params = conn.body_params
    contract = Contract.load!()

    with {:ok, c0} <- Schema.normalize_c0(Map.get(params, "c0_state", "c0_collapsed")),
         {:ok, pulse} <- normalize_world_pulse(contract, Map.get(params, "pulse_kind", "alpha_shock")),
         {:ok, window} <- normalize_window(Map.get(params, "safe_phase_window", "relative")),
         {:ok, resolution} <- parse_positive_float(Map.get(params, "resolution", 0.25)) do
      hidden = %{c0_state: c0, pulse_kind: pulse, safe_phase_window: window}
      trace = Slice.trace(hidden, contract)
      observed = Observation.observe_all(trace, resolution: resolution)
      full_evidence = Observation.evidence(observed)

      disabled_keys =
        params
        |> Map.get("disabled_keys", [])
        |> MapSet.new(fn
          %{"lens" => l, "field" => f} -> {to_string(l), to_string(f)}
          _ -> {nil, nil}
        end)

      active_evidence =
        Enum.reject(full_evidence, fn rec ->
          MapSet.member?(disabled_keys, {Atom.to_string(rec.lens), Atom.to_string(rec.field)})
        end)

      explain_res = Ascribe.explain(active_evidence, contract: contract, resolution: resolution)

      ablation =
        if active_evidence == [] do
          %{baseline: explain_res, items: []}
        else
          Ascribe.ablate(active_evidence, contract: contract, resolution: resolution)
        end

      minimal_sets =
        Ascribe.minimal_sufficient_sets(active_evidence,
          contract: contract,
          resolution: resolution
        )

      refutations = %{
        autumn_rain: format_refutation(Ascribe.refute(active_evidence, :autumn_rain)),
        natural_collapse: format_refutation(Ascribe.refute(active_evidence, :natural_collapse))
      }

      official_only = format_explain(Ascribe.official_only(active_evidence))

      identifiability =
        trace
        |> Ascribe.identifiability([0.05, 0.1, 0.25, 0.5, 1.0, 2.0])
        |> Enum.map(fn {res, outcome} ->
          %{resolution: res, result: format_explain(outcome)}
        end)

      send_json(conn, 200, %{
        ok: true,
        hidden: hidden,
        resolution: resolution,
        trace: %{
          node: trace.node,
          site: trace.site,
          period: trace.period,
          settled_period: trace.settled_period,
          oscillating: trace.oscillating?,
          shift: trace.shift,
          surge_lag: Slice.surge_lag(trace),
          outcome: trace.outcome,
          impacts: trace.impacts,
          stress: trace.stress,
          frame_slams: trace.frame_slams,
          direction: trace.direction,
          shape: trace.shape,
          rain_lag: trace.rain_lag,
          signs_before_rain: Slice.signs_before_rain?(trace)
        },
        lenses: observed,
        active_count: length(active_evidence),
        total_count: length(full_evidence),
        explain: format_explain(explain_res),
        ablation: %{
          baseline: format_explain(ablation.baseline),
          items:
            Enum.map(ablation.items, fn item ->
              %{
                lens: item.lens,
                field: item.field,
                value: item.value,
                necessary: item.necessary?,
                irrelevant: item.irrelevant?,
                without: format_explain(item.without)
              }
            end)
        },
        minimal_sufficient_sets: minimal_sets,
        refutations: refutations,
        official_only: official_only,
        identifiability: identifiability
      })
    else
      {:error, reason} ->
        send_json(conn, 422, %{ok: false, error: format_reason(reason)})
    end
  end

  get "/api/canon/lint" do
    content_lint = Content.lint()
    timeline_res = Canon.check_timeline()
    texts_res = Canon.check_texts()
    fates_res = Canon.check_fates()
    pathways_res = Canon.check_pathways([101, 202])

    corpus =
      all_topographies()
      |> Canon.check_corpus()

    send_json(conn, 200, %{
      ok: true,
      content: %{
        ok: content_lint.errors == [],
        errors: Enum.map(content_lint.errors, &Content.format_issue/1),
        warnings: Enum.map(content_lint.warnings, &Content.format_issue/1)
      },
      timeline: format_check(timeline_res),
      texts: format_check(texts_res),
      fates: format_check(fates_res),
      pathways: format_check(pathways_res),
      corpus: %{
        checked: corpus.checked,
        ok: corpus.ok,
        unsupported: corpus.unsupported,
        issue_count: length(corpus.issues)
      }
    })
  end

  match _ do
    send_json(conn, 404, %{ok: false, error: "not_found"})
  end

  # ==== helpers ==== //

  defp send_json(conn, status, data) do
    body = data |> Schema.to_public() |> JSON.encode!()

    conn
    |> put_resp_content_type("application/json; charset=utf-8")
    |> send_resp(status, body)
  end

  defp build_topology(params) do
    try do
      topo =
        Schema.topology(
          branch_id: normalize_branch_id(Map.get(params, "branch_id", "branch_custom")),
          up_name: non_empty_or(Map.get(params, "up_name"), "上游源头"),
          pass_name: non_empty_or(Map.get(params, "pass_name"), "峡谷隘口"),
          down_name: non_empty_or(Map.get(params, "down_name"), "下游坝寨"),
          up_terrain: Map.fetch!(params, "up_terrain"),
          pass_terrain: Map.fetch!(params, "pass_terrain"),
          down_terrain: Map.fetch!(params, "down_terrain"),
          up_geology: Map.fetch!(params, "up_geology"),
          accessibility: Map.fetch!(params, "accessibility")
        )

      {:ok, topo}
    rescue
      e in [KeyError, ArgumentError] -> {:error, Exception.message(e)}
    end
  end

  defp normalize_branch_id(val) when is_atom(val), do: val

  defp normalize_branch_id(val) when is_binary(val) do
    trimmed = String.trim(val)
    if trimmed == "", do: :branch_custom, else: trimmed
  end

  defp normalize_branch_id(_), do: :branch_custom

  defp non_empty_or(val, default) when is_binary(val) do
    if String.trim(val) == "", do: default, else: val
  end

  defp non_empty_or(_, default), do: default

  defp parse_int(val, _err) when is_integer(val), do: {:ok, val}

  defp parse_int(val, err) when is_binary(val) do
    case Integer.parse(String.trim(val)) do
      {n, ""} -> {:ok, n}
      _ -> {:error, err}
    end
  end

  defp parse_int(_, err), do: {:error, err}

  defp parse_positive_float(val) when is_number(val) and val > 0, do: {:ok, val * 1.0}

  defp parse_positive_float(val) when is_binary(val) do
    case Float.parse(String.trim(val)) do
      {n, ""} when n > 0 -> {:ok, n}
      _ -> {:error, :invalid_resolution}
    end
  end

  defp parse_positive_float(_), do: {:error, :invalid_resolution}

  defp normalize_world_pulse(contract, val) do
    pulses = Contract.world_pulses(contract)

    cond do
      is_atom(val) and val in pulses ->
        {:ok, val}

      is_binary(val) ->
        case Enum.find(pulses, &(Atom.to_string(&1) == val)) do
          nil -> {:error, :invalid_pulse}
          found -> {:ok, found}
        end

      true ->
        {:error, :invalid_pulse}
    end
  end

  defp normalize_window(val) when val in [:absolute, :relative, :late], do: {:ok, val}

  defp normalize_window(val) when is_binary(val) do
    case val do
      "absolute" -> {:ok, :absolute}
      "relative" -> {:ok, :relative}
      "late" -> {:ok, :late}
      _ -> {:error, :invalid_window}
    end
  end

  defp normalize_window(_), do: {:error, :invalid_window}

  defp serialize_edge(edge) do
    %{
      hop: edge.hop,
      doc_kind: edge.doc_kind,
      lens: edge.lens,
      relation: edge.relation,
      from_term: inspect(edge.from),
      to_term: inspect(edge.to),
      attrs: Map.new(edge.attrs, fn {k, v} -> {k, inspect_if_tuple(v)} end)
    }
  end

  defp inspect_if_tuple(v) when is_tuple(v), do: inspect(v)
  defp inspect_if_tuple(v), do: v

  defp format_explain({:unique, h}) do
    %{status: "unique", hypothesis: h}
  end

  defp format_explain({:multiple, %{candidates: cands, differing: diff}}) do
    %{
      status: "multiple",
      candidates: cands,
      differing:
        Enum.map(diff, fn d ->
          %{
            lens: d.lens,
            field: d.field,
            by_hypothesis:
              Enum.map(d.by_hypothesis, fn {h, v} ->
                %{hypothesis: h, value: v}
              end)
          }
        end)
    }
  end

  defp format_explain({:incomplete, %{candidates: cands, missing: missing}}) do
    %{status: "incomplete", candidates: cands, missing: missing}
  end

  defp format_explain({:none, %{contradictions: contradictions, missing: missing}}) do
    %{
      status: "none",
      missing: missing,
      contradictions:
        Enum.map(contradictions, fn {h, detail} ->
          %{hypothesis: h, detail: detail}
        end)
    }
  end

  defp format_refutation({:excluded, reasons}), do: %{status: "excluded", reasons: reasons}
  defp format_refutation({:not_excluded, survivors}), do: %{status: "not_excluded", survivors: survivors}
  defp format_refutation(:unknown_alternative), do: %{status: "unknown"}

  defp format_check(:ok), do: %{ok: true, issues: []}
  defp format_check({:error, issues}), do: %{ok: false, issues: Enum.map(issues, &inspect/1)}

  defp all_topographies do
    base = [branch_id: :t, up_name: "U", pass_name: "P", down_name: "D"]

    for up <- Schema.feature_domain(:up_terrain),
        pass <- Schema.feature_domain(:pass_terrain),
        down <- Schema.feature_domain(:down_terrain),
        geo <- Schema.feature_domain(:up_geology),
        access <- Schema.feature_domain(:accessibility) do
      Schema.topology(
        base ++
          [
            up_terrain: up,
            pass_terrain: pass,
            down_terrain: down,
            up_geology: geo,
            accessibility: access
          ]
      )
    end
  end

  defp format_reason(reason) when is_atom(reason), do: Atom.to_string(reason)
  defp format_reason(reason) when is_binary(reason), do: reason
  defp format_reason(reason), do: inspect(reason)
end
