const state = {
  overview: null,
  contract: null,
  currentDossier: null,
  currentPathway: null,
  enabledHops: new Set([1, 2, 3, 4, 5]),
  enabledDecoys: new Set(["material_mismatch", "destination_dead_end"]),
  disabledEvidence: new Set(),
  lastAscribe: null,
};

const LABELS = {
  // terrain
  steep_fissure_headwater: "陡峻裂隙源头",
  karst_sinkhole_headwater: "喀斯特漏斗源头",
  canyon_pass: "峡谷隘口",
  karst_gorge: "岩溶暗峡",
  alluvial_flat: "冲积坝地",
  mid_terrace: "半山台地",
  active_fault_vein: "活动断裂脉",
  deep_stress_cavity: "深部高应力溶腔",
  minor_fracture: "次级微裂隙",
  tractor_road_accessible: "拖拉机路可达",
  cliff_isolated: "绝壁孤寨（不可通车）",
  // relics & pulses & c0
  ancestral_bronze_zheng: "镇山古铜钲",
  carved_diversion_stele: "刻字分水石堰",
  c0_collapsed: "c0_collapsed（维持源崩塌）",
  c0_intact: "c0_intact（维持源完好）",
  alpha_shock: "alpha_shock（周期浅层爆破）",
  OverwhelmingNegativeSuppression: "OverwhelmingNegativeSuppression（超高压注浆抑制）",
  heavy_kick: "heavy_kick（低烈度林工微扰）",
  alpha_metal_resonance: "alpha_metal_resonance（三次金属谐振）",
  alpha_shock_weak: "alpha_shock_weak（弱对照脉冲）",
  // fates
  Fate_Assimilated_NewZhai: "同化新寨（剧烈振荡）",
  Fate_Annihilated_BurnoutZone: "绝灭封山（白化枯竭）",
  Fate_Ascended_SpatialRift: "飞升裂隙（高能守山）",
  Fate_Ancestral_ControlGroup: "祖制对照（平静回归）",
  Fate_Deferred_ChronicSeep: "迁延渗漏（慢性紧绷）",
  Fate_Tolerated_FalseAlarm: "耐受虚警（晚期吸收）",
  // interventions
  no_intervention: "不作干预（维持基线）",
  reanchor_vessel: "重锚耦合载体（恢复 c0）",
  dampen_headwater: "压抑源头脉冲（降为 heavy_kick）",
  reanchor_and_dampen: "重锚载体 + 压抑源头",
  // lenses
  official_hq: "总指挥部（OfficialHQ · 月度行政）",
  tech_inner: "技术内层（TechInner · 垂架/测钟）",
  old_zhai: "老寨（OldZhai · 物候口述）",
  // outcomes
  premature: "Premature（早发）",
  delayed: "Delayed（推迟）",
  absorbed: "Absorbed（吸收）",
  disrupted: "Disrupted（紊乱）",
  unresolved: "Unresolved（未决）",
  // windows
  absolute: "absolute（φ≈0.15 绝对不应期）",
  relative: "relative（φ≈0.50 相对不应期）",
  late: "late（φ≈0.80 晚期恢复）",
};

function labelOf(k) {
  return LABELS[k] ? `${LABELS[k]} (${k})` : String(k ?? "");
}

function shortLabel(k) {
  return LABELS[k] || String(k ?? "");
}

function esc(v) {
  return String(v ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function api(path, body = null) {
  const opts = body === null
    ? { method: "GET" }
    : {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(body),
      };
  const res = await fetch(path, opts);
  const data = await res.json();
  if (!res.ok) {
    throw new Error(data.error || `HTTP ${res.status}`);
  }
  return data;
}

function regimeBadgeClass(regime) {
  if (regime === "Calm") return "ok";
  if (regime === "Burnout") return "err";
  return "warn";
}

function formatTermValue(v) {
  if (Array.isArray(v)) return v.join(" · ");
  if (v && typeof v === "object") return JSON.stringify(v);
  return String(v);
}

// ==== Tabs ====
document.querySelectorAll(".tab").forEach((btn) => {
  btn.addEventListener("click", () => {
    document.querySelectorAll(".tab").forEach((b) => b.classList.remove("active"));
    document.querySelectorAll(".panel").forEach((p) => p.classList.remove("active"));
    btn.classList.add("active");
    document.getElementById(`tab-${btn.dataset.tab}`).classList.add("active");
  });
});

// ==== Bootstrap ====
async function init() {
  const overview = await api("/api/overview");
  state.overview = overview;

  document.getElementById("header-meta").textContent =
    `contract=${overview.contract_path} · digest=${overview.source_digest.slice(0, 12)}… · content_lint=${overview.content_lint.error_count} err`;

  setupChronicleTab(overview);
  setupPathwayTab(overview);
  setupAscribeTab(overview);

  // Run initial panels in parallel
  await Promise.all([
    compileChronicle(),
    loadDossier(),
    runAscribe(),
    loadContractAndCanon(),
  ]);
}

// ==== TAB 1: CHRONICLE ====
function setupChronicleTab(overview) {
  const { feature_domains, fates } = overview.schemas;
  fillSelect("ch-up-terrain", feature_domains.up_terrain);
  fillSelect("ch-pass-terrain", feature_domains.pass_terrain);
  fillSelect("ch-down-terrain", feature_domains.down_terrain);
  fillSelect("ch-up-geology", feature_domains.up_geology);
  fillSelect("ch-accessibility", feature_domains.accessibility);
  fillSelect("reverse-fate-select", fates);

  const presetStrip = document.getElementById("chronicle-presets");
  presetStrip.innerHTML = overview.presets
    .map((p) => `<button type="button" class="chip-btn" data-preset="${esc(p.id)}">${esc(p.topo.down_name)} (${esc(p.id)})</button>`)
    .join("");

  presetStrip.querySelectorAll("button").forEach((btn) => {
    btn.addEventListener("click", () => {
      const preset = overview.presets.find((p) => p.id === btn.dataset.preset);
      if (!preset) return;
      presetStrip.querySelectorAll("button").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const t = preset.topo;
      document.getElementById("ch-up-name").value = t.up_name;
      document.getElementById("ch-pass-name").value = t.pass_name;
      document.getElementById("ch-down-name").value = t.down_name;
      document.getElementById("ch-up-terrain").value = t.up_terrain;
      document.getElementById("ch-pass-terrain").value = t.pass_terrain;
      document.getElementById("ch-down-terrain").value = t.down_terrain;
      document.getElementById("ch-up-geology").value = t.up_geology;
      document.getElementById("ch-accessibility").value = t.accessibility;
      compileChronicle();
    });
  });

  document.getElementById("chronicle-form").addEventListener("submit", (e) => {
    e.preventDefault();
    compileChronicle();
  });

  document.getElementById("reverse-fate-btn").addEventListener("click", runReverseWorldgen);
}

async function compileChronicle() {
  const out = document.getElementById("chronicle-output");
  const payload = {
    branch_id: "branch_webui",
    up_name: document.getElementById("ch-up-name").value,
    pass_name: document.getElementById("ch-pass-name").value,
    down_name: document.getElementById("ch-down-name").value,
    up_terrain: document.getElementById("ch-up-terrain").value,
    pass_terrain: document.getElementById("ch-pass-terrain").value,
    down_terrain: document.getElementById("ch-down-terrain").value,
    up_geology: document.getElementById("ch-up-geology").value,
    accessibility: document.getElementById("ch-accessibility").value,
  };

  try {
    const res = await api("/api/chronicle/compile", payload);
    const slice = res.slices[0];
    const era1 = slice.era1;
    const era2a = slice.era2a;
    const era2b = slice.era2b;
    const era3 = slice.era3;
    const st = slice.structures;

    out.innerHTML = `
      <div class="card">
        <div class="card-head">
          <h2>四纪演化切片：${esc(payload.up_name)} → ${esc(payload.pass_name)} → ${esc(payload.down_name)}</h2>
          <span class="badge ${regimeBadgeClass(era3.final_regime)}">${esc(era3.fate)} · ${esc(era3.final_regime)}</span>
        </div>
        <div class="era-grid">
          <div class="era-box">
            <h4>第一纪 · 山官古制 (Era 1)</h4>
            <dl class="kv-list">
              <dt>polity</dt><dd>${esc(era1.polity)}</dd>
              <dt>relic</dt><dd>${esc(labelOf(era1.guardian_relic))}</dd>
              <dt>damping</dt><dd>${esc(era1.damping_mode)}</dd>
              <dt>c0</dt><dd>${esc(era1.initial_c0)} (${esc(era1.initial_regime)})</dd>
            </dl>
          </div>
          <div class="era-box">
            <h4>第二纪甲 · 流官试探 (Era 2a)</h4>
            <dl class="kv-list">
              <dt>attempt</dt><dd>${esc(era2a.attempt)}</dd>
              <dt>backlash</dt><dd>${esc(era2a.historical_backlash)}</dd>
              <dt>polity</dt><dd>${esc(era2a.resulting_polity)}</dd>
              <dt>precedent</dt><dd>${esc(era2a.gazetteer_precedent)}</dd>
            </dl>
          </div>
          <div class="era-box">
            <h4>第二纪乙 · 垦殖入山 (Era 2b)</h4>
            <dl class="kv-list">
              <dt>org</dt><dd>${esc(era2b.org_after_reorg)}</dd>
              <dt>team</dt><dd>${esc(era2b.stationed_team)}</dd>
              <dt>c0_status</dt><dd>${esc(era2b.c0_status)}</dd>
              <dt>pulse</dt><dd>${esc(era2b.pulse)}</dd>
            </dl>
          </div>
          <div class="era-box era3">
            <h4>第三纪 · 撤出与现状 (Era 3)</h4>
            <dl class="kv-list">
              <dt>fate</dt><dd><strong>${esc(shortLabel(era3.fate))}</strong></dd>
              <dt>regime</dt><dd>${esc(era3.final_regime)}</dd>
              <dt>phase_rule</dt><dd>${esc(era3.phase_rule)}</dd>
              <dt>polity</dt><dd>${esc(era3.post_withdrawal_polity)}</dd>
            </dl>
          </div>
        </div>
        <div style="margin-top:0.85rem;">
          <h4 style="margin:0 0 0.45rem;font-size:0.9rem;">三处遗址结构投影（Texts.compile_structureso）</h4>
          <table class="data-table">
            <thead><tr><th>点位</th><th>地名</th><th>结构表象</th></tr></thead>
            <tbody>
              <tr><td>上游源头</td><td>${esc(st.upstream_site[0])}</td><td>${esc(st.upstream_site[1])}</td></tr>
              <tr><td>隘口枢纽</td><td>${esc(st.pass_site[0])}</td><td>${esc(st.pass_site[1])}</td></tr>
              <tr><td>下游坝寨</td><td>${esc(st.downstream_site[0])}</td><td>${esc(st.downstream_site[1])}</td></tr>
            </tbody>
          </table>
        </div>
      </div>

      <div class="card">
        <div class="card-head">
          <h2>玩家干预可达未来求解 · Chronicle.solve_interventions/2</h2>
          <span class="badge">${res.interventions.length} 条可达迁移</span>
        </div>
        <table class="data-table">
          <thead>
            <tr><th>干预动作</th><th>有效维持源 (c0)</th><th>有效脉冲</th><th>目标结局</th><th>慢变量分区</th><th>相位规则</th></tr>
          </thead>
          <tbody>
            ${res.interventions
              .map(
                (tr) => `
              <tr>
                <td><strong>${esc(shortLabel(tr.intervention))}</strong><br><span class="mono muted">${esc(tr.intervention)}</span></td>
                <td class="mono">${esc(tr.effective_c0)}</td>
                <td class="mono">${esc(tr.effective_pulse)}</td>
                <td>${esc(shortLabel(tr.target_fate))}</td>
                <td><span class="badge ${regimeBadgeClass(tr.target_regime)}">${esc(tr.target_regime)}</span></td>
                <td class="mono">${esc(tr.phase_rule)}</td>
              </tr>`
              )
              .join("")}
          </tbody>
        </table>
      </div>
    `;
  } catch (err) {
    out.innerHTML = `
      <div class="card">
        <div class="card-head">
          <h2>地形组合不受支持</h2>
          <span class="badge warn">${esc(err.message)}</span>
        </div>
        <p class="muted">microKanren 仅允许配对的地质与源头地形（例如 <code>steep_fissure_headwater</code> 配 <code>active_fault_vein</code> 或 <code>minor_fracture</code>；<code>karst_sinkhole_headwater</code> 配 <code>deep_stress_cavity</code>）。</p>
      </div>
    `;
  }
}

async function runReverseWorldgen() {
  const fate = document.getElementById("reverse-fate-select").value;
  const box = document.getElementById("reverse-results");
  box.innerHTML = `<span class="muted">正在执行 Voy.World.reverse_worldgen(${esc(fate)})…</span>`;
  try {
    const res = await api("/api/chronicle/reverse", { fate });
    if (res.slices.length === 0) {
      box.innerHTML = `<span class="badge err">无匹配地形</span>`;
      return;
    }
    box.innerHTML = `
      <div class="badge ok" style="align-self:flex-start;">共找到 ${res.count} 组可演化为「${esc(shortLabel(fate))}」的地形</div>
      <table class="data-table">
        <thead><tr><th>#</th><th>up_terrain</th><th>pass_terrain</th><th>down_terrain</th><th>up_geology</th><th>accessibility</th></tr></thead>
        <tbody>
          ${res.slices
            .map((s, i) => {
              const t = s.topography;
              return `<tr>
                <td>${i + 1}</td>
                <td>${esc(shortLabel(t.up_terrain))}</td>
                <td>${esc(shortLabel(t.pass_terrain))}</td>
                <td>${esc(shortLabel(t.down_terrain))}</td>
                <td>${esc(shortLabel(t.up_geology))}</td>
                <td>${esc(shortLabel(t.accessibility))}</td>
              </tr>`;
            })
            .join("")}
        </tbody>
      </table>
    `;
  } catch (err) {
    box.innerHTML = `<span class="badge err">${esc(err.message)}</span>`;
  }
}

// ==== TAB 2: PATHWAY & DOSSIER ====
function setupPathwayTab(overview) {
  fillSelect("pw-relic", overview.schemas.relics);
  fillSelect("pw-fate", overview.schemas.fates);

  const presetStrip = document.getElementById("dossier-presets");
  presetStrip.innerHTML = overview.presets
    .map((p) => `<button type="button" class="chip-btn" data-preset="${esc(p.id)}">${esc(p.topo.down_name)}</button>`)
    .join("");

  presetStrip.querySelectorAll("button").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const preset = overview.presets.find((p) => p.id === btn.dataset.preset);
      if (!preset) return;
      presetStrip.querySelectorAll("button").forEach((b) => b.classList.remove("active"));
      btn.classList.add("active");
      const compiled = await api("/api/chronicle/compile", preset.topo);
      const slice = compiled.slices[0];
      document.getElementById("pw-relic").value = slice.era1.guardian_relic;
      document.getElementById("pw-fate").value = slice.era3.fate;
      await loadDossier(preset.topo);
    });
  });

  document.getElementById("pw-load-btn").addEventListener("click", () => loadDossier());
}

async function loadDossier(topoOverride = null) {
  const relic = document.getElementById("pw-relic").value;
  const fate = document.getElementById("pw-fate").value;
  const seed = Number(document.getElementById("pw-seed").value || 101);
  const horizon = document.getElementById("pw-horizon").value;

  const body = {
    relic,
    fate,
    seed,
    horizon,
    branch_id: topoOverride ? topoOverride.branch_id : "branch_webui",
    up_name: topoOverride ? topoOverride.up_name : document.getElementById("ch-up-name").value,
    pass_name: topoOverride ? topoOverride.pass_name : document.getElementById("ch-pass-name").value,
    down_name: topoOverride ? topoOverride.down_name : document.getElementById("ch-down-name").value,
  };

  const res = await api("/api/dossier", body);
  state.currentDossier = res.dossier;
  state.currentPathway = res.pathway;
  state.enabledHops = new Set([1, 2, 3, 4, 5]);
  state.enabledDecoys = new Set(["material_mismatch", "destination_dead_end"]);

  renderDossierView();
  await runPathwayAblation();
}

function renderDossierView() {
  const dossier = state.currentDossier;
  const pathway = state.currentPathway;
  renderPathwaySvg(pathway);
  renderPathwayToggles(pathway);
  renderOpenSettlement(dossier);

  const b = dossier.binding;
  const headerBanner = `
    <div class="doc-card" style="grid-column:1/-1;border-left-color:var(--slate);background:#fff;">
      <div class="doc-card-head">
        <strong>种子物证绑定摘要 (seed=${esc(dossier.seed)} · horizon=${esc(dossier.horizon)} · settled=${esc(dossier.settled)})</strong>
        <span class="badge ${dossier.settled ? "ok" : "warn"}">${esc(shortLabel(dossier.fate))}</span>
      </div>
      <div class="muted mono" style="font-size:0.82rem;">
        批号 batch_id: <strong>${esc(b.batch_id)}</strong> |
        工业载体: <strong>${esc(b.vessel_name)} (${esc(b.vessel_code)})</strong> |
        实测勘定格: <strong>第 ${esc(b.safe_tick)} 格</strong> (φ≈${Number(b.safe_phase).toFixed(2)}, Δ=${esc(b.safe_delay)} 刻)
      </div>
    </div>
  `;

  const cardsHtml = dossier.documents
    .map((doc) => {
      const isDecoy = doc.role === "decoy";
      const tag = isDecoy ? `干扰 · ${doc.decoy_class}` : `真链 Hop ${doc.hop}`;
      return `
        <article class="doc-card ${isDecoy ? "decoy" : ""}">
          <div class="doc-card-head">
            <span class="badge ${isDecoy ? "warn" : "ok"}">${esc(tag)}</span>
            <span class="badge">${esc(doc.lens)} · ${esc(doc.doc_kind)}</span>
          </div>
          <h4>${esc(doc.title)}</h4>
          <p>${esc(doc.body)}</p>
        </article>
      `;
    })
    .join("");

  document.getElementById("dossier-cards-grid").innerHTML = headerBanner + cardsHtml;
}

function renderOpenSettlement(dossier) {
  const box = document.getElementById("open-settlement-box");
  if (dossier.horizon !== "open") {
    box.innerHTML = "";
    return;
  }

  if (dossier.settled) {
    box.innerHTML = `
      <div class="card">
        <div class="card-head">
          <h2>开放视界已结算 · Hop 5 议事会公契已解锁</h2>
          <span class="badge ok">intervention=${esc(dossier.applied_intervention)} → ${esc(shortLabel(dossier.fate))}</span>
        </div>
        <p class="muted">种子绑定的批号 <code>${esc(dossier.binding.batch_id)}</code>、载体编号 <code>${esc(dossier.binding.vessel_code)}</code> 与勘定格 <code>#${esc(dossier.binding.safe_tick)}</code> 在干预结算后保持不变。</p>
      </div>
    `;
    return;
  }

  const transitions = dossier.reachable_transitions || [];
  box.innerHTML = `
    <div class="card">
      <div class="card-head">
        <h2>开放视界待结算（Hop 1..4 已就绪，选择干预解锁 Hop 5）</h2>
        <span class="badge warn">horizon=open · settled=false</span>
      </div>
      <div class="preset-strip" style="margin-bottom:0;">
        ${transitions
          .map(
            (t) => `<button type="button" class="btn settle-btn" data-intervention="${esc(t.intervention)}">
              ${esc(shortLabel(t.intervention))} → ${esc(shortLabel(t.target_fate))} [${esc(t.target_regime)}]
            </button>`
          )
          .join("")}
      </div>
    </div>
  `;

  box.querySelectorAll(".settle-btn").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const res = await api("/api/dossier/settle", {
        dossier: state.currentDossier,
        intervention: btn.dataset.intervention,
        names: {
          up: document.getElementById("ch-up-name").value,
          pass: document.getElementById("ch-pass-name").value,
          down: document.getElementById("ch-down-name").value,
        },
      });
      state.currentDossier = res.dossier;
      renderDossierView();
    });
  });
}

function renderPathwaySvg(pathway) {
  const w = 980;
  const h = 230;
  const xs = [60, 235, 410, 585, 760];

  const mainNodes = pathway.chain.map((edge, idx) => {
    const x = xs[idx];
    const enabled = state.enabledHops.has(edge.hop);
    const stroke = enabled ? "#1d6f66" : "#b9ae98";
    const fill = enabled ? "#e2f1ee" : "#f0eadc";
    const opacity = enabled ? 1 : 0.45;
    const arrow = idx < 4
      ? `<line x1="${x + 145}" y1="68" x2="${xs[idx + 1] - 4}" y2="68" stroke="${enabled && state.enabledHops.has(edge.hop + 1) ? "#1d6f66" : "#c7beb0"}" stroke-width="2.5" marker-end="url(#arrow)" stroke-dasharray="${enabled && state.enabledHops.has(edge.hop + 1) ? "" : "4 4"}"/>`
      : "";

    return `
      ${arrow}
      <g class="svg-hop" data-hop="${edge.hop}" style="cursor:pointer;opacity:${opacity}">
        <rect x="${x}" y="34" width="145" height="68" rx="6" fill="${fill}" stroke="${stroke}" stroke-width="2" />
        <text x="${x + 10}" y="54" font-size="12" font-weight="700" fill="#1b2326">Hop ${edge.hop} · ${esc(edge.lens)}</text>
        <text x="${x + 10}" y="72" font-size="11" fill="#394b54">${esc(edge.doc_kind)}</text>
        <text x="${x + 10}" y="90" font-size="10" font-family="monospace" fill="#5b666a">${esc(String(edge.relation).slice(0, 20))}</text>
      </g>
    `;
  }).join("");

  const decoys = pathway.decoys.map((decoy, idx) => {
    const cls = decoy.attrs.decoy_class;
    const x = idx === 0 ? 235 : 410;
    const enabled = state.enabledDecoys.has(cls);
    return `
      <g class="svg-decoy" data-decoy="${esc(cls)}" style="cursor:pointer;opacity:${enabled ? 1 : 0.4}">
        <rect x="${x}" y="142" width="210" height="58" rx="6" fill="${enabled ? "#fbf0da" : "#f0eadc"}" stroke="${enabled ? "#a86b12" : "#b9ae98"}" stroke-width="1.8" stroke-dasharray="5 3" />
        <text x="${x + 10}" y="164" font-size="11.5" font-weight="700" fill="#82510a">干扰卡: ${esc(cls)}</text>
        <text x="${x + 10}" y="183" font-size="10.5" fill="#5b666a">breaks_at ${esc(decoy.attrs.breaks_at)} · ${esc(decoy.lens)}</text>
      </g>
    `;
  }).join("");

  const wrap = document.getElementById("pathway-graph-wrap");
  wrap.innerHTML = `
    <svg viewBox="0 0 ${w} ${h}" width="100%" style="max-height:240px;">
      <defs>
        <marker id="arrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse">
          <path d="M 0 1 L 10 5 L 0 9 z" fill="#1d6f66" />
        </marker>
      </defs>
      ${mainNodes}
      ${decoys}
    </svg>
  `;

  wrap.querySelectorAll(".svg-hop").forEach((el) => {
    el.addEventListener("click", () => toggleHop(Number(el.dataset.hop)));
  });
  wrap.querySelectorAll(".svg-decoy").forEach((el) => {
    el.addEventListener("click", () => toggleDecoy(el.dataset.decoy));
  });
}

function renderPathwayToggles(pathway) {
  const container = document.getElementById("pathway-toggles");
  const hopPills = pathway.chain
    .map((e) => {
      const on = state.enabledHops.has(e.hop);
      return `<button type="button" class="toggle-pill ${on ? "active-main" : ""}" data-hop="${e.hop}">
        ${on ? "✓" : "✕"} Hop ${e.hop} (${esc(e.doc_kind)})
      </button>`;
    })
    .join("");

  const decoyPills = pathway.decoys
    .map((d) => {
      const cls = d.attrs.decoy_class;
      const on = state.enabledDecoys.has(cls);
      return `<button type="button" class="toggle-pill ${on ? "active-decoy" : ""}" data-decoy="${esc(cls)}">
        ${on ? "+" : "○"} 混入干扰 ${esc(cls)}
      </button>`;
    })
    .join("");

  container.innerHTML = hopPills + decoyPills;
  container.querySelectorAll("[data-hop]").forEach((b) =>
    b.addEventListener("click", () => toggleHop(Number(b.dataset.hop)))
  );
  container.querySelectorAll("[data-decoy]").forEach((b) =>
    b.addEventListener("click", () => toggleDecoy(b.dataset.decoy))
  );
}

function toggleHop(hop) {
  if (state.enabledHops.has(hop)) state.enabledHops.delete(hop);
  else state.enabledHops.add(hop);
  renderPathwaySvg(state.currentPathway);
  renderPathwayToggles(state.currentPathway);
  runPathwayAblation();
}

function toggleDecoy(cls) {
  if (state.enabledDecoys.has(cls)) state.enabledDecoys.delete(cls);
  else state.enabledDecoys.add(cls);
  renderPathwaySvg(state.currentPathway);
  renderPathwayToggles(state.currentPathway);
  runPathwayAblation();
}

async function runPathwayAblation() {
  const relic = document.getElementById("pw-relic").value;
  const fate = document.getElementById("pw-fate").value;
  const res = await api("/api/pathway/ablate", {
    relic,
    fate,
    enabled_hops: Array.from(state.enabledHops).sort(),
    include_decoys: Array.from(state.enabledDecoys),
  });
  const badge = document.getElementById("ablate-status-badge");
  if (res.connected_count === 1) {
    badge.className = "badge ok";
    badge.textContent = `连通成功：1 条唯一主链 [${res.connections[0].hops.join("→")}] (pool=${res.pool_size})`;
  } else {
    badge.className = "badge err";
    badge.textContent = `断链：0 条可合一路线 (pool=${res.pool_size})`;
  }
}

// ==== TAB 3: OBSERVATION & ASCRIBE ====
function setupAscribeTab(overview) {
  fillSelect("as-c0", overview.schemas.c0_states);
  fillSelect("as-pulse", overview.schemas.world_pulses);
  fillSelect("as-window", overview.schemas.windows);
  document.getElementById("as-window").value = "relative";

  document.getElementById("as-run-btn").addEventListener("click", () => runAscribe());
  document.getElementById("as-reset-ev-btn").addEventListener("click", () => {
    state.disabledEvidence.clear();
    runAscribe();
  });
}

function evKey(lens, field) {
  return `${lens}:${field}`;
}

async function runAscribe() {
  const payload = {
    c0_state: document.getElementById("as-c0").value,
    pulse_kind: document.getElementById("as-pulse").value,
    safe_phase_window: document.getElementById("as-window").value,
    resolution: Number(document.getElementById("as-resolution").value),
    disabled_keys: Array.from(state.disabledEvidence).map((k) => {
      const [lens, field] = k.split(":");
      return { lens, field };
    }),
  };

  const res = await api("/api/ascribe/analyze", payload);
  state.lastAscribe = res;
  renderAscribeVerdict(res);
  renderLensesGrid(res);
  renderMinimalAndAblation(res);
  renderRefuteAndIdentifiability(res);
}

function renderAscribeVerdict(res) {
  const ex = res.explain;
  let badgeCls = "ok";
  let title = "";
  let detail = "";

  if (ex.status === "unique") {
    badgeCls = "ok";
    const h = ex.hypothesis;
    title = `唯一锁定隐藏真相 (:unique) · 启用证据 ${res.active_count}/${res.total_count}`;
    detail = `c0_state = <code>${esc(h.c0_state)}</code> · pulse_kind = <code>${esc(h.pulse_kind)}</code> · safe_phase_window = <code>${esc(h.safe_phase_window)}</code>`;
  } else if (ex.status === "incomplete") {
    badgeCls = "warn";
    title = `缺证待补 (:incomplete) · 剩余 ${ex.candidates.length} 个候选`;
    detail = `缺少关键鉴别字段：${ex.missing.map((m) => `<code>${esc(m)}</code>`).join("、")}`;
  } else if (ex.status === "multiple") {
    badgeCls = "warn";
    title = `仪器分辨率极限多解 (:multiple) · 剩余 ${ex.candidates.length} 个不可分候选`;
    detail = `现有测钟分辨率 <code>${esc(res.resolution)}</code> 下仍余候选：${ex.candidates
      .map((c) => `<code>(${esc(c.c0_state)}, ${esc(c.pulse_kind)}, ${esc(c.safe_phase_window)})</code>`)
      .join(" / ")}`;
  } else {
    badgeCls = "err";
    title = `无可满足假说 (:none)`;
    detail = `与所有 24 个候选假说均存在矛盾。`;
  }

  document.getElementById("ascribe-verdict-box").innerHTML = `
    <div class="card">
      <div class="card-head">
        <h2>${title}</h2>
        <span class="badge ${badgeCls}">${esc(ex.status.toUpperCase())}</span>
      </div>
      <p style="margin:0 0 0.4rem;">${detail}</p>
      <p class="muted mono" style="margin:0;font-size:0.8rem;">
        底层数值轨迹：T_ref=${Number(res.trace.period).toFixed(2)}, surge_lag=${Number(res.trace.surge_lag).toFixed(2)}, shift=${Number(res.trace.shift).toFixed(2)} (${esc(res.trace.outcome)}), impacts=${esc(res.trace.impacts)}, stress=${Number(res.trace.stress).toFixed(3)}, blows=${esc(res.trace.frame_slams)}
      </p>
    </div>
  `;
}

function renderLensesGrid(res) {
  const ablateMap = new Map(
    (res.ablation.items || []).map((it) => [evKey(it.lens, it.field), it])
  );
  const grid = document.getElementById("lenses-grid");

  grid.innerHTML = ["official_hq", "tech_inner", "old_zhai"]
    .map((lensKey) => {
      const report = res.lenses[lensKey];
      const rows = report.records
        .map((rec) => {
          const key = evKey(rec.lens, rec.field);
          const enabled = !state.disabledEvidence.has(key);
          const ab = ablateMap.get(key);
          const necTag = ab && ab.necessary
            ? `<span class="badge err" title="删去即失去唯一性">必要</span>`
            : "";

          return `
            <label class="record-row ${enabled ? "" : "disabled"}">
              <input type="checkbox" class="ev-toggle" data-key="${esc(key)}" ${enabled ? "checked" : ""} />
              <span>
                <strong>${esc(rec.field)}</strong> = <code>${esc(formatTermValue(rec.value))}</code>
                ${necTag}
              </span>
              <span class="badge">${esc(rec.provenance)}</span>
            </label>
          `;
        })
        .join("");

      return `
        <div class="card lens-card">
          <div class="card-head">
            <h3>${esc(shortLabel(lensKey))}</h3>
            <span class="badge">${report.records.length} 条记录</span>
          </div>
          ${rows}
          <details style="font-size:0.8rem;color:var(--muted);">
            <summary style="cursor:pointer;">保留 / 丢失通道说明</summary>
            <p style="margin:0.35rem 0 0;"><strong>保留：</strong>${report.retains.map(esc).join("；")}</p>
            <p style="margin:0.25rem 0 0;"><strong>丢失：</strong>${report.loses.map(esc).join("；")}</p>
          </details>
        </div>
      `;
    })
    .join("");

  grid.querySelectorAll(".ev-toggle").forEach((cb) => {
    cb.addEventListener("change", () => {
      const key = cb.dataset.key;
      if (cb.checked) state.disabledEvidence.delete(key);
      else state.disabledEvidence.add(key);
      runAscribe();
    });
  });
}

function renderMinimalAndAblation(res) {
  const box = document.getElementById("ascribe-minimal-box");
  const sets = res.minimal_sufficient_sets || [];
  const setsHtml = sets.length === 0
    ? `<p class="muted">当前启用证据尚未唯一锁定单一假说，故不存在最小充分子集。</p>`
    : `<div class="preset-strip">
        ${sets
          .map(
            (set, idx) => `<button type="button" class="chip-btn apply-minset" data-idx="${idx}">
              集合 ${idx + 1} (${set.length} 项): ${set.map((k) => `${k.lens}.${k.field}`).join(" + ")}
            </button>`
          )
          .join("")}
      </div>`;

  const ablateRows = (res.ablation.items || [])
    .map((it) => {
      const tag = it.necessary
        ? `<span class="badge err">必要 (删去→${esc(it.without.status)})</span>`
        : it.irrelevant
        ? `<span class="badge">可由他证替代</span>`
        : `<span class="badge warn">${esc(it.without.status)}</span>`;
      return `<tr><td class="mono">${esc(it.lens)}.${esc(it.field)}</td><td>${tag}</td></tr>`;
    })
    .join("");

  box.innerHTML = `
    <p class="hint">点击任意「最小充分证据集」芯片，可只勾选该最小集合验证 <code>abduceo/4</code> 仍保持唯一锁定：</p>
    ${setsHtml}
    <table class="data-table">
      <thead><tr><th>证据项</th><th>留一消融 (Leave-One-Out)</th></tr></thead>
      <tbody>${ablateRows}</tbody>
    </table>
  `;

  box.querySelectorAll(".apply-minset").forEach((btn) => {
    btn.addEventListener("click", () => {
      const chosen = sets[Number(btn.dataset.idx)];
      const keepSet = new Set(chosen.map((k) => evKey(k.lens, k.field)));
      state.disabledEvidence.clear();
      for (const lensKey of ["official_hq", "tech_inner", "old_zhai"]) {
        for (const rec of res.lenses[lensKey].records) {
          const key = evKey(rec.lens, rec.field);
          if (!keepSet.has(key)) state.disabledEvidence.add(key);
        }
      }
      runAscribe();
    });
  });
}

function renderRefuteAndIdentifiability(res) {
  const box = document.getElementById("ascribe-refute-box");
  const ref = res.refutations;
  const official = res.official_only;

  const renderRef = (name, r) => {
    if (r.status === "excluded") {
      return `<div><span class="badge ok">已排除 ${esc(name)}</span> — 矛盾字段：${r.reasons
        .map((x) => `<code>${esc(x.lens)}.${esc(x.field)}</code> (预期 ${esc(x.expected)} vs 实测 ${esc(x.recorded)})`)
        .join("；")}</div>`;
    }
    return `<div><span class="badge warn">未能排除 ${esc(name)}</span>（当前证据不足以反驳）</div>`;
  };

  const ladderRows = res.identifiability
    .map(
      (step) => `
      <tr>
        <td class="mono">Δt = ${esc(step.resolution)}</td>
        <td><span class="badge ${step.result.status === "unique" ? "ok" : "warn"}">${esc(step.result.status)}</span></td>
        <td class="mono">${step.result.status === "unique" ? esc(step.result.hypothesis.pulse_kind + " / " + step.result.hypothesis.safe_phase_window) : `${(step.result.candidates || []).length} candidates`}</td>
      </tr>`
    )
    .join("");

  box.innerHTML = `
    <div class="stack-gap" style="gap:0.55rem;margin-bottom:0.85rem;">
      ${renderRef("秋雨自然侵蚀 (autumn_rain)", ref.autumn_rain)}
      ${renderRef("纯自然蚀空 (natural_collapse)", ref.natural_collapse)}
      <div>
        <span class="badge warn">对照组 official_only</span> — 仅凭行政 <code>cause_tag</code> 总是给出：<code>${esc(JSON.stringify(official.hypothesis || official.missing))}</code>
      </div>
    </div>
    <h4 style="margin:0 0 0.4rem;font-size:0.88rem;">测钟分辨率阶梯 · Ascribe.identifiability/2</h4>
    <table class="data-table">
      <thead><tr><th>分辨率</th><th>反演状态</th><th>结论 / 候选数</th></tr></thead>
      <tbody>${ladderRows}</tbody>
    </table>
  `;
}

// ==== TAB 4: CONTRACT & CANON ====
async function loadContractAndCanon() {
  const [contract, canon] = await Promise.all([api("/api/contract"), api("/api/canon/lint")]);
  state.contract = contract;
  renderCanonGrid(canon);
  renderDutyCycleSvg(contract);
  renderSafeTicksSvg(contract);
  renderOutcomesTable(contract);
  renderBenchmarksAndClaims(contract);

  document.getElementById("canon-run-btn").addEventListener("click", async () => {
    const fresh = await api("/api/canon/lint");
    renderCanonGrid(fresh);
  });
}

function renderCanonGrid(canon) {
  const items = [
    { label: "内容包检查 (Content.lint)", ok: canon.content.ok, detail: `${canon.content.errors.length} 错 / ${canon.content.warnings.length} 警` },
    { label: "年代锚点 (Timeline)", ok: canon.timeline.ok, detail: canon.timeline.ok ? "1894 / 1902 / 1979 一致" : canon.timeline.issues.join(", ") },
    { label: "结构模板 (Texts)", ok: canon.texts.ok, detail: canon.texts.ok ? "6 结局描述无悬空占位符" : canon.texts.issues.join(", ") },
    { label: "结局可达性 (reverse_worldgen)", ok: canon.fates.ok, detail: canon.fates.ok ? "6/6 结局均由合法地形可达" : canon.fates.issues.join(", ") },
    { label: "五跳微链与消融 (Pathways)", ok: canon.pathways.ok, detail: canon.pathways.ok ? "12 条五跳链 + 局部性全通过" : canon.pathways.issues.join(", ") },
    { label: "全地形语料库 (48 组合)", ok: canon.corpus.issue_count === 0, detail: `${canon.corpus.ok} 合法 / ${canon.corpus.unsupported} 地质不配对` },
  ];

  document.getElementById("canon-audit-grid").innerHTML = items
    .map(
      (it) => `
      <div class="kpi">
        <div class="label">${esc(it.label)}</div>
        <div class="val"><span class="badge ${it.ok ? "ok" : "err"}">${it.ok ? "PASS" : "FAIL"}</span></div>
        <div class="muted" style="font-size:0.78rem;margin-top:0.25rem;">${esc(it.detail)}</div>
      </div>`
    )
    .join("");
}

function renderDutyCycleSvg(c) {
  const onset = c.onset_comparison;
  const settled = c.oscillator;
  const settledPeriod = c.period;

  const bar = (y, label, period, frac, outcome, delta, color) => {
    const totalW = 420;
    const platW = Math.round(totalW * frac);
    const pct = (frac * 100).toFixed(1);
    return `
      <text x="16" y="${y}" font-size="12" font-weight="700" fill="#1b2326">${esc(label)}</text>
      <text x="16" y="${y + 16}" font-size="11" font-family="monospace" fill="#5b666a">T=${Number(period).toFixed(2)} · 激发高台=${pct}% · φ=0.5 敲击→${esc(outcome)} (Δ=${delta > 0 ? "+" : ""}${Number(delta).toFixed(2)})</text>
      <rect x="16" y="${y + 24}" width="${totalW}" height="24" rx="4" fill="#e9e2d2" stroke="#b9ae98" />
      <rect x="16" y="${y + 24}" width="${platW}" height="24" rx="4" fill="${color}" />
      <line x1="${16 + totalW / 2}" y1="${y + 20}" x2="${16 + totalW / 2}" y2="${y + 52}" stroke="#1b2326" stroke-width="2" stroke-dasharray="3 2" />
      <text x="${16 + totalW / 2 + 5}" y="${y + 40}" font-size="10.5" font-weight="700" fill="#1b2326">φ=0.50</text>
    `;
  };

  const settledMid = c.outcome_table.find((r) => r.pulse === "alpha_shock" && r.window === "relative");
  document.getElementById("duty-cycle-svg").innerHTML = `
    <svg viewBox="0 0 470 180" width="100%">
      ${bar(22, "近 Hopf 起始段 (a=0.40, s=0.30)", onset.period, onset.plateau_fraction, onset.mid_cycle_outcome, onset.mid_cycle_delta, "#b43c2c")}
      ${bar(106, "稳态深渊极限环 (a=-0.30, s=1.00)", settledPeriod, settled.plateau_fraction, settledMid.outcome, settledMid.delta, "#1d6f66")}
    </svg>
  `;
}

function renderSafeTicksSvg(c) {
  const pulse = c.pulses.find((p) => p.pulse === "alpha_metal_resonance");
  const ticks = pulse ? pulse.safe_ticks : [];
  const x0 = 28;
  const span = 415;

  const marks = ticks
    .map((t) => {
      const x = x0 + t.phase * span;
      return `
        <g>
          <line x1="${x.toFixed(1)}" y1="42" x2="${x.toFixed(1)}" y2="86" stroke="#1d6f66" stroke-width="2.5" />
          <circle cx="${x.toFixed(1)}" cy="42" r="4.5" fill="#1d6f66">
            <title>tick ${t.tick} | φ=${t.phase.toFixed(3)} | Δ=${t.delta.toFixed(2)} (${t.window})</title>
          </circle>
          <text x="${x.toFixed(1)}" y="102" font-size="10" font-family="monospace" text-anchor="middle" fill="#1b2326">${t.tick}</text>
        </g>
      `;
    })
    .join("");

  document.getElementById("safe-ticks-svg").innerHTML = `
    <svg viewBox="0 0 470 135" width="100%">
      <text x="${x0}" y="20" font-size="12" font-weight="700" fill="#1b2326">共 ${ticks.length} 个实测安全刻度 (Delayed · 悬停圆点查看相移 Δ)</text>
      <line x1="${x0}" y1="64" x2="${x0 + span}" y2="64" stroke="#8b8270" stroke-width="2" />
      <text x="${x0}" y="122" font-size="10" font-family="monospace" fill="#5b666a">φ=0.00</text>
      <text x="${x0 + span / 2}" y="122" font-size="10" font-family="monospace" text-anchor="middle" fill="#5b666a">φ=0.50</text>
      <text x="${x0 + span}" y="122" font-size="10" font-family="monospace" text-anchor="end" fill="#5b666a">φ=1.00</text>
      ${marks}
    </svg>
  `;
}

function renderOutcomesTable(c) {
  const byPulse = new Map();
  for (const row of c.outcome_table) {
    if (!byPulse.has(row.pulse)) byPulse.set(row.pulse, {});
    byPulse.get(row.pulse)[row.window] = row;
  }

  const cell = (row) => {
    if (!row) return "<td>—</td>";
    const cls = row.outcome === "delayed" ? "ok" : row.outcome === "premature" ? "err" : "warn";
    return `<td><span class="badge ${cls}">${esc(row.outcome)}</span> <span class="mono">Δ=${row.delta > 0 ? "+" : ""}${Number(row.delta).toFixed(2)}</span></td>`;
  };

  const rows = Array.from(byPulse.entries())
    .map(([pulse, map]) => `
      <tr>
        <td><strong>${esc(shortLabel(pulse))}</strong><br><span class="mono muted">${esc(pulse)}</span></td>
        ${cell(map.absolute)}
        ${cell(map.relative)}
        ${cell(map.late)}
      </tr>`)
    .join("");

  document.getElementById("contract-outcomes-wrap").innerHTML = `
    <table class="data-table">
      <thead><tr><th>脉冲类型</th><th>absolute (φ≈0.15)</th><th>relative (φ≈0.50)</th><th>late (φ≈0.80)</th></tr></thead>
      <tbody>${rows}</tbody>
    </table>
  `;
}

function renderBenchmarksAndClaims(c) {
  document.getElementById("contract-benchmarks-wrap").innerHTML = `
    <dl class="kv-list" style="margin-bottom:0.8rem;">
      <dt>Hopf 点</dt><dd class="mono">v_c=${c.hopf.v_c}, a_c=${c.hopf.a_c}, s_c=${c.hopf.s_c}</dd>
      <dt>Relaxation 极限</dt><dd class="mono">T_rel=${c.relaxation.period} (a=${c.relaxation.a}) vs 实测 T=${c.period}</dd>
      <dt>步长离散度</dt><dd class="mono">${(c.period_spread * 100).toFixed(4)}% (${c.period_rows.map((r) => `dt=${r.dt}→${r.measured}`).join(", ")})</dd>
      <dt>点火阈值</dt><dd class="mono">area=${c.fire_threshold.area} (alpha_shock=${c.fire_threshold.declared_alpha_shock_area})</dd>
    </dl>
    <table class="data-table">
      <thead><tr><th>采样 stress</th><th>activity (q)</th><th>Regime</th></tr></thead>
      <tbody>
        ${c.regime_samples
          .map(
            (s) => `<tr><td class="mono">${s.stress}</td><td class="mono">${s.activity}</td><td><span class="badge ${regimeBadgeClass(s.regime)}">${esc(s.regime)}</span></td></tr>`
          )
          .join("")}
      </tbody>
    </table>
  `;

  document.getElementById("contract-claims-wrap").innerHTML = `
    <table class="data-table">
      <thead><tr><th>命题 ID</th><th>裁决</th><th>说明</th></tr></thead>
      <tbody>
        ${c.claims
          .map(
            (cl) => `<tr>
              <td class="mono">${esc(cl.id)}</td>
              <td><span class="badge ${cl.verdict === "confirmed" ? "ok" : "warn"}">${esc(cl.verdict)}</span></td>
              <td>${esc(cl.summary || cl.note || "")}</td>
            </tr>`
          )
          .join("")}
      </tbody>
    </table>
  `;
}

function fillSelect(id, options) {
  const el = document.getElementById(id);
  el.innerHTML = options
    .map((opt) => `<option value="${esc(opt)}">${esc(labelOf(opt))}</option>`)
    .join("");
}

init().catch((err) => {
  document.getElementById("header-meta").textContent = `初始化失败: ${err.message}`;
  console.error(err);
});
