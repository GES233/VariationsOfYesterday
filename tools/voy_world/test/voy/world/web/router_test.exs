defmodule Voy.World.Web.RouterTest do
  use ExUnit.Case, async: true
  import Plug.Test
  import Plug.Conn

  alias Voy.World.Web.Router

  @opts Router.init([])

  defp json_post(path, body) do
    conn(:post, path, JSON.encode!(body))
    |> put_req_header("content-type", "application/json")
    |> Router.call(@opts)
  end

  defp get_req(path) do
    conn(:get, path) |> Router.call(@opts)
  end

  test "GET / serves index.html and static assets load" do
    conn = get_req("/")
    assert conn.status == 200
    assert String.contains?(conn.resp_body, "Voy.World")

    css = get_req("/static/app.css")
    assert css.status == 200
    assert String.contains?(css.resp_body, ".workspace")

    js = get_req("/static/app.js")
    assert js.status == 200
    assert String.contains?(js.resp_body, "/api/overview")
  end

  test "GET /api/overview and /api/contract return contract metadata and tables" do
    ov = get_req("/api/overview")
    assert ov.status == 200
    data = JSON.decode!(ov.resp_body)
    assert data["app"] == "voy_world"
    assert length(data["presets"]) == 6
    assert data["content_lint"]["error_count"] == 0

    ct = get_req("/api/contract")
    assert ct.status == 200
    cdata = JSON.decode!(ct.resp_body)
    assert is_number(cdata["hopf"]["v_c"])
    assert is_list(cdata["outcome_table"])
    assert Enum.any?(cdata["pulses"], &(&1["pulse"] == "alpha_metal_resonance" and &1["safe_ticks"] != []))
  end

  test "POST /api/chronicle/compile and /api/chronicle/reverse" do
    conn =
      json_post("/api/chronicle/compile", %{
        up_terrain: "steep_fissure_headwater",
        pass_terrain: "canyon_pass",
        down_terrain: "alluvial_flat",
        up_geology: "active_fault_vein",
        accessibility: "tractor_road_accessible"
      })

    assert conn.status == 200
    res = JSON.decode!(conn.resp_body)
    assert res["ok"] == true
    assert hd(res["slices"])["era3"]["fate"] == "Fate_Assimilated_NewZhai"
    assert length(res["interventions"]) == 4

    bad =
      json_post("/api/chronicle/compile", %{
        up_terrain: "steep_fissure_headwater",
        pass_terrain: "canyon_pass",
        down_terrain: "alluvial_flat",
        up_geology: "deep_stress_cavity",
        accessibility: "tractor_road_accessible"
      })

    assert bad.status == 422

    rev = json_post("/api/chronicle/reverse", %{fate: "Fate_Ascended_SpatialRift"})
    assert rev.status == 200
    rdata = JSON.decode!(rev.resp_body)
    assert rdata["count"] > 0
  end

  test "POST /api/dossier, /api/dossier/settle, and /api/pathway/ablate" do
    open_conn =
      json_post("/api/dossier", %{
        relic: "ancestral_bronze_zheng",
        fate: "Fate_Assimilated_NewZhai",
        seed: 101,
        horizon: "open"
      })

    assert open_conn.status == 200
    open_data = JSON.decode!(open_conn.resp_body)
    dossier = open_data["dossier"]
    assert dossier["horizon"] == "open"
    assert dossier["settled"] == false
    refute Enum.any?(dossier["documents"], &(&1["role"] == "main" and &1["hop"] == 5))

    settle_conn =
      json_post("/api/dossier/settle", %{
        dossier: dossier,
        intervention: "reanchor_and_dampen"
      })

    assert settle_conn.status == 200
    settled = JSON.decode!(settle_conn.resp_body)["dossier"]
    assert settled["settled"] == true
    assert settled["fate"] == "Fate_Ancestral_ControlGroup"
    assert settled["binding"]["batch_id"] == dossier["binding"]["batch_id"]
    assert settled["binding"]["safe_tick"] == dossier["binding"]["safe_tick"]
    assert Enum.any?(settled["documents"], &(&1["role"] == "main" and &1["hop"] == 5))

    full_ablate =
      json_post("/api/pathway/ablate", %{
        relic: "ancestral_bronze_zheng",
        fate: "Fate_Assimilated_NewZhai",
        enabled_hops: [1, 2, 3, 4, 5],
        include_decoys: ["material_mismatch", "destination_dead_end"]
      })

    assert JSON.decode!(full_ablate.resp_body)["connected_count"] == 1

    broken_ablate =
      json_post("/api/pathway/ablate", %{
        relic: "ancestral_bronze_zheng",
        fate: "Fate_Assimilated_NewZhai",
        enabled_hops: [1, 2, 4, 5],
        include_decoys: ["material_mismatch", "destination_dead_end"]
      })

    assert JSON.decode!(broken_ablate.resp_body)["connected_count"] == 0
  end

  test "POST /api/ascribe/analyze and GET /api/canon/lint" do
    asc =
      json_post("/api/ascribe/analyze", %{
        c0_state: "c0_collapsed",
        pulse_kind: "alpha_shock",
        safe_phase_window: "relative",
        resolution: 0.25,
        disabled_keys: []
      })

    assert asc.status == 200
    adata = JSON.decode!(asc.resp_body)
    assert adata["explain"]["status"] == "unique"
    assert adata["explain"]["hypothesis"]["pulse_kind"] == "alpha_shock"
    assert adata["refutations"]["autumn_rain"]["status"] == "excluded"
    assert adata["minimal_sufficient_sets"] != []

    canon = get_req("/api/canon/lint")
    assert canon.status == 200
    cdata = JSON.decode!(canon.resp_body)
    assert cdata["content"]["ok"] == true
    assert cdata["timeline"]["ok"] == true
    assert cdata["fates"]["ok"] == true
    assert cdata["pathways"]["ok"] == true
  end
end
