const state = {
  eras: [],
  nodes: [],
  edges: [],
  translations: [],
  audit: null,
  wires: [], // [{id, from_edge_id, to_edge_id, valid, bridge, warnings, issues}]
  selectedCardId: null,
  selectedWireId: null,
  highlightedCardIds: new Set(),
  zoom: 1,
  dirty: false,
  dragCard: null,
  dragWire: null,
};

function esc(v) {
  return String(v ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function api(path, body = null) {
  const opts =
    body === null
      ? { method: "GET" }
      : {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(body),
        };
  const res = await fetch(path, opts);
  const data = await res.json();
  if (!res.ok) throw new Error(data.error || `HTTP ${res.status}`);
  return data;
}

function toast(msg, kind = "") {
  const el = document.getElementById("toast-msg");
  el.textContent = msg;
  el.className = `hud-pill ${kind}`;
}

function markDirty(flag = true) {
  state.dirty = flag;
  const btn = document.getElementById("btn-save");
  btn.textContent = flag ? "保存到文件 *" : "保存到文件";
}

function eraById(id) {
  return state.eras.find((e) => e.id === id);
}

function nodeById(id) {
  return state.nodes.find((n) => n.id === id);
}

function edgeById(id) {
  return state.edges.find((e) => e.id === id);
}

function nodeLabel(id) {
  const n = nodeById(id);
  return n ? `${n.label}` : id;
}

// ==== Subtabs ====
document.querySelectorAll(".subtab").forEach((btn) => {
  btn.addEventListener("click", () => {
    switchSubtab(btn.dataset.subtab);
  });
});

function switchSubtab(name) {
  document.querySelectorAll(".subtab").forEach((b) => b.classList.toggle("active", b.dataset.subtab === name));
  document.querySelectorAll(".subpanel").forEach((p) => p.classList.toggle("active", p.id === `panel-${name}`));
}

// ==== Init ====
async function init() {
  const world = await api("/api/world");
  loadWorldState(world);
  autoWireValidOnBoard();
  bindTopbar();
  bindBoardInteractions();
}

function loadWorldState(world) {
  state.eras = world.eras;
  state.nodes = world.nodes;
  state.edges = world.edges;
  state.translations = world.translations || [];
  state.audit = world.audit;
  markDirty(false);
  renderAll();
}

function renderAll() {
  renderAuditSummary();
  renderEraFilters();
  renderLeftDrawers();
  renderBoardCards();
  renderWires();
  renderInspector();
  renderEraEditor();
  renderAliasesPanel();
  renderPathfinderSelects();
}

function renderAuditSummary() {
  const s = state.audit?.summary;
  if (!s) return;
  document.getElementById("audit-summary").textContent =
    `节点 ${s.nodes} · 卡片 ${s.edges} · 转译桥 ${s.translations ?? state.translations.length} · 合规 ${s.ok_edges} · 冲突 ${s.error_edges} · 合法桥接 ${s.valid_bridges}`;
}

function renderEraFilters() {
  const filter = document.getElementById("filter-era");
  const cur = filter.value || "all";
  filter.innerHTML =
    `<option value="all">全部时代</option>` +
    state.eras.map((e) => `<option value="${esc(e.id)}">${esc(e.name)}</option>`).join("");
  filter.value = cur;
}

function renderLeftDrawers() {
  const filterEra = document.getElementById("filter-era").value || "all";
  const reports = state.audit?.edge_reports || {};

  const filteredEdges = state.edges.filter((e) => filterEra === "all" || e.era === filterEra);
  document.getElementById("card-library").innerHTML = filteredEdges
    .map((edge) => {
      const rep = reports[edge.id] || { status: "ok" };
      const era = eraById(edge.era);
      const statusBadge =
        rep.status === "error"
          ? `<span class="badge err">冲突</span>`
          : rep.status === "warn"
          ? `<span class="badge warn">词表外</span>`
          : `<span class="badge ok">合规</span>`;
      const boardBtn = edge.on_board
        ? `<button class="btn small toggle-board" data-id="${esc(edge.id)}">收起</button>`
        : `<button class="btn small primary toggle-board" data-id="${esc(edge.id)}">+ 上桌</button>`;
      return `
        <div class="lib-item ${esc(rep.status)}" data-select-card="${esc(edge.id)}">
          <div class="lib-item-top">
            <strong style="overflow:hidden;text-overflow:ellipsis;white-space:nowrap;">${esc(edge.title)}</strong>
            ${statusBadge}
          </div>
          <div class="muted" style="font-size:0.72rem;">${esc(era ? era.name : edge.era)} · ${esc(edge.observer)}</div>
          <div class="inline-row" style="justify-content:space-between;">
            <span class="mono" style="font-size:0.68rem;color:var(--muted);">${esc(edge.id)}</span>
            ${boardBtn}
          </div>
        </div>
      `;
    })
    .join("");

  document.querySelectorAll(".toggle-board").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const edge = edgeById(btn.dataset.id);
      if (!edge) return;
      edge.on_board = !edge.on_board;
      markDirty();
      renderLeftDrawers();
      renderBoardCards();
      renderWires();
    });
  });

  document.querySelectorAll("[data-select-card]").forEach((el) => {
    el.addEventListener("click", () => {
      const edge = edgeById(el.dataset.selectCard);
      if (!edge) return;
      if (!edge.on_board) edge.on_board = true;
      state.selectedCardId = edge.id;
      state.selectedWireId = null;
      switchSubtab("inspector");
      renderLeftDrawers();
      renderBoardCards();
      renderWires();
      renderInspector();
    });
  });

  document.getElementById("node-count").textContent = String(state.nodes.length);
  document.getElementById("node-library").innerHTML = state.nodes
    .map(
      (n) => `
      <div class="lib-item">
        <div class="lib-item-top">
          <strong>${esc(n.label)}</strong>
          <span class="badge">${esc(n.kind)}</span>
        </div>
        <div class="mono" style="font-size:0.69rem;color:var(--muted);">${esc(n.id)} · ${esc(n.era)}</div>
      </div>`
    )
    .join("");
}

function renderBoardCards() {
  const layer = document.getElementById("cards-layer");
  const reports = state.audit?.edge_reports || {};
  const boardEdges = state.edges.filter((e) => e.on_board);

  layer.innerHTML = boardEdges
    .map((edge) => {
      const era = eraById(edge.era);
      const color = era?.color || "#394b54";
      const rep = reports[edge.id] || { status: "ok", errors: [], warnings: [] };
      const selected = state.selectedCardId === edge.id ? "selected" : "";
      const highlighted = state.highlightedCardIds.has(edge.id) ? "highlighted" : "";
      const statusCls = `status-${rep.status}`;
      const forbiddenSet = new Set(era?.forbidden_concepts || []);

      const statusBadge =
        rep.status === "error"
          ? `<span class="badge err">✕ 冲突</span>`
          : rep.status === "warn"
          ? `<span class="badge warn">! 词表外</span>`
          : `<span class="badge ok">✓ 合规</span>`;

      const errBox =
        rep.errors && rep.errors.length > 0
          ? `<div class="card-errors">${rep.errors.map((x) => esc(x.message)).join("<br>")}</div>`
          : "";

      return `
        <article class="board-card ${selected} ${highlighted} ${statusCls}" data-card-id="${esc(edge.id)}" style="left:${edge.x}px;top:${edge.y}px;">
          <div class="card-stripe" style="background:${esc(color)};"></div>
          <div class="card-head" data-drag-handle="${esc(edge.id)}">
            <span class="badge">${esc(era ? era.name.split("·")[0].trim() : edge.era)} · ${esc(edge.observer)}</span>
            <div class="inline-row" style="gap:0.25rem;">
              ${statusBadge}
              <button class="btn small hide-card-btn" data-hide-card="${esc(edge.id)}" title="收起回抽屉">×</button>
            </div>
          </div>
          <div class="card-title">${esc(edge.title || edge.id)}</div>
          <div class="card-body">${esc(edge.body)}</div>
          <div class="card-ports">
            <div class="port-row">
              <div class="port-wrap in" title="输入节点: ${esc(edge.from)}">
                <div class="port-dot in" data-port-in="${esc(edge.id)}"></div>
                <span class="port-label">${esc(nodeLabel(edge.from))}</span>
              </div>
              <span class="port-rel">${esc(edge.rel)}</span>
            </div>
            <div class="port-row out-row">
              <div class="port-wrap out" title="输出节点: ${esc(edge.to)}">
                <div class="port-dot out" data-port-out="${esc(edge.id)}"></div>
                <span class="port-label">${esc(nodeLabel(edge.to))}</span>
              </div>
            </div>
          </div>
          <div class="card-concepts">
            ${(edge.concepts || [])
              .map((c) => `<span class="tag ${forbiddenSet.has(c) ? "err" : ""}">${esc(c)}</span>`)
              .join("")}
            ${edge.retrospective ? `<span class="badge accent">追认</span>` : ""}
          </div>
          ${errBox}
        </article>
      `;
    })
    .join("");

  // Bind card selection, dragging, hide, and port wiring
  layer.querySelectorAll(".board-card").forEach((cardEl) => {
    cardEl.addEventListener("mousedown", (ev) => {
      if (ev.target.closest(".port-dot") || ev.target.closest(".hide-card-btn")) return;
      state.selectedCardId = cardEl.dataset.cardId;
      state.selectedWireId = null;
      switchSubtab("inspector");
      renderInspector();
      layer.querySelectorAll(".board-card").forEach((el) => el.classList.toggle("selected", el.dataset.cardId === state.selectedCardId));
      renderWires();
    });
  });

  layer.querySelectorAll("[data-drag-handle]").forEach((handle) => {
    handle.addEventListener("mousedown", (ev) => {
      if (ev.target.closest(".hide-card-btn")) return;
      ev.preventDefault();
      const id = handle.dataset.dragHandle;
      const edge = edgeById(id);
      if (!edge) return;
      const pt = clientToBoard(ev.clientX, ev.clientY);
      state.dragCard = { id, offsetX: pt.x - edge.x, offsetY: pt.y - edge.y };
    });
  });

  layer.querySelectorAll("[data-hide-card]").forEach((btn) => {
    btn.addEventListener("click", (ev) => {
      ev.stopPropagation();
      const id = btn.dataset.hideCard;
      const edge = edgeById(id);
      if (!edge) return;
      edge.on_board = false;
      state.wires = state.wires.filter((w) => w.from_edge_id !== id && w.to_edge_id !== id);
      if (state.selectedCardId === id) state.selectedCardId = null;
      markDirty();
      renderLeftDrawers();
      renderBoardCards();
      renderWires();
      renderInspector();
    });
  });

  layer.querySelectorAll("[data-port-out]").forEach((dot) => {
    dot.addEventListener("mousedown", (ev) => {
      ev.stopPropagation();
      ev.preventDefault();
      const fromId = dot.dataset.portOut;
      const pos = getPortPos(fromId, "out");
      state.dragWire = { fromId, x1: pos.x, y1: pos.y, x2: pos.x, y2: pos.y };
      updateDragWireSvg();
    });
  });
}

function getPortPos(edgeId, side) {
  const card = document.querySelector(`.board-card[data-card-id="${CSS.escape(edgeId)}"]`);
  const edge = edgeById(edgeId);
  if (!card || !edge) return { x: (edge?.x || 0) + (side === "out" ? 304 : 0), y: (edge?.y || 0) + 120 };

  const dot = card.querySelector(side === "out" ? "[data-port-out]" : "[data-port-in]");
  if (!dot) return { x: edge.x + (side === "out" ? 304 : 0), y: edge.y + 120 };

  const stageRect = document.getElementById("board-stage").getBoundingClientRect();
  const dotRect = dot.getBoundingClientRect();
  return {
    x: (dotRect.left + dotRect.width / 2 - stageRect.left) / state.zoom,
    y: (dotRect.top + dotRect.height / 2 - stageRect.top) / state.zoom,
  };
}

function wireGeometry(x1, y1, x2, y2) {
  // Forward flow: left-to-right gentle cubic Bezier
  if (x2 >= x1 + 30) {
    const dx = Math.max((x2 - x1) * 0.45, 55);
    const d = `M ${x1} ${y1} C ${x1 + dx} ${y1}, ${x2 - dx} ${y2}, ${x2} ${y2}`;
    return { d, lx: (x1 + x2) / 2, ly: (y1 + y2) / 2 - 4 };
  }
  // Row-wrap / backward flow: route smoothly through the vertical gap between rows
  const midY = Math.abs(y2 - y1) > 120 ? (y1 + y2) / 2 : y1 + 135;
  const d = `M ${x1} ${y1} C ${x1 + 95} ${y1 + 50}, ${x1 + 75} ${midY}, ${(x1 + x2) / 2} ${midY} S ${x2 - 95} ${y2 - 50}, ${x2} ${y2}`;
  return { d, lx: (x1 + x2) / 2, ly: midY };
}

function renderWires() {
  const layer = document.getElementById("wires-layer");
  const visibleWires = state.wires.filter((w) => edgeById(w.from_edge_id)?.on_board && edgeById(w.to_edge_id)?.on_board);

  layer.innerHTML = visibleWires
    .map((w) => {
      const p1 = getPortPos(w.from_edge_id, "out");
      const p2 = getPortPos(w.to_edge_id, "in");
      const geom = wireGeometry(p1.x, p1.y, p2.x, p2.y);
      const isTr = w.valid && w.bridge?.kind === "translation";
      const cls = !w.valid ? "err" : isTr ? "tr" : "ok";
      const sel = state.selectedWireId === w.id ? "selected" : "";
      const label = !w.valid
        ? w.issues[0]?.code || "冲突"
        : isTr
        ? `⇄ ${nodeLabel(w.bridge.from_node)}`
        : nodeLabel(w.bridge?.from_node || w.bridge);
      const pillW = Math.min(210, Math.max(70, label.length * 9.5 + 16));
      return `
        <g data-wire-id="${esc(w.id)}">
          <path class="wire-hit" d="${geom.d}" />
          <path class="wire-path ${cls} ${sel}" d="${geom.d}" marker-end="url(#arrow-${cls})" />
          <rect class="wire-label-bg ${cls}" x="${geom.lx - pillW / 2}" y="${geom.ly - 11}" width="${pillW}" height="20" rx="10" />
          <text class="wire-label" x="${geom.lx}" y="${geom.ly + 3}" text-anchor="middle">${esc(label)}</text>
        </g>
      `;
    })
    .join("");

  layer.querySelectorAll("[data-wire-id]").forEach((g) => {
    g.addEventListener("click", (ev) => {
      ev.stopPropagation();
      state.selectedWireId = g.dataset.wireId;
      state.selectedCardId = null;
      switchSubtab("inspector");
      renderBoardCards();
      renderWires();
      renderInspector();
    });
  });
}

function updateDragWireSvg() {
  const path = document.getElementById("drag-wire");
  if (!state.dragWire) {
    path.style.display = "none";
    return;
  }
  path.style.display = "block";
  const geom = wireGeometry(state.dragWire.x1, state.dragWire.y1, state.dragWire.x2, state.dragWire.y2);
  path.setAttribute("d", geom.d);
}

function clientToBoard(cx, cy) {
  const rect = document.getElementById("board-stage").getBoundingClientRect();
  return {
    x: (cx - rect.left) / state.zoom,
    y: (cy - rect.top) / state.zoom,
  };
}

function bindBoardInteractions() {
  window.addEventListener("mousemove", (ev) => {
    if (state.dragCard) {
      const edge = edgeById(state.dragCard.id);
      if (!edge) return;
      const pt = clientToBoard(ev.clientX, ev.clientY);
      edge.x = Math.max(10, Math.round(pt.x - state.dragCard.offsetX));
      edge.y = Math.max(10, Math.round(pt.y - state.dragCard.offsetY));
      const el = document.querySelector(`.board-card[data-card-id="${CSS.escape(edge.id)}"]`);
      if (el) {
        el.style.left = `${edge.x}px`;
        el.style.top = `${edge.y}px`;
      }
      renderWires();
      markDirty();
    } else if (state.dragWire) {
      const pt = clientToBoard(ev.clientX, ev.clientY);
      state.dragWire.x2 = pt.x;
      state.dragWire.y2 = pt.y;
      updateDragWireSvg();
    }
  });

  window.addEventListener("mouseup", async (ev) => {
    if (state.dragCard) {
      state.dragCard = null;
    }
    if (state.dragWire) {
      const fromId = state.dragWire.fromId;
      state.dragWire = null;
      updateDragWireSvg();
      const targetDot = ev.target?.closest?.("[data-port-in]");
      const targetCard = targetDot ? null : ev.target?.closest?.(".board-card");
      const toId = targetDot ? targetDot.dataset.portIn : targetCard?.dataset?.cardId;
      if (toId && toId !== fromId) {
        await addAndValidateWire(fromId, toId);
      }
    }
  });

  document.getElementById("filter-era").addEventListener("change", renderLeftDrawers);

  document.getElementById("btn-zoom-in").addEventListener("click", () => setZoom(state.zoom + 0.1));
  document.getElementById("btn-zoom-out").addEventListener("click", () => setZoom(state.zoom - 0.1));
}

function setZoom(z) {
  state.zoom = Math.min(1.4, Math.max(0.6, Number(z.toFixed(2))));
  document.getElementById("board-stage").style.transform = `scale(${state.zoom})`;
  document.getElementById("zoom-label").textContent = `${Math.round(state.zoom * 100)}%`;
  renderWires();
}

async function addAndValidateWire(fromId, toId) {
  const res = await api("/api/world/wire", {
    eras: state.eras,
    nodes: state.nodes,
    edges: state.edges,
    translations: state.translations,
    from_edge_id: fromId,
    to_edge_id: toId,
  });

  const wireId = `${fromId}->${toId}`;
  state.wires = state.wires.filter((w) => w.id !== wireId);
  const wire = {
    id: wireId,
    from_edge_id: fromId,
    to_edge_id: toId,
    valid: res.valid,
    bridge: res.bridge,
    warnings: res.warnings || [],
    issues: res.issues || [],
  };
  state.wires.push(wire);
  state.selectedWireId = wireId;
  state.selectedCardId = null;

  if (wire.valid) {
    if (wire.bridge?.kind === "translation") {
      toast(`跨时代转译接线成立：${wire.bridge.gloss}`, "ok");
    } else {
      toast(`接线成立：通过「${nodeLabel(wire.bridge?.from_node)}」直连。`, "ok");
    }
  } else {
    toast(`接线冲突：${wire.issues.map((i) => i.message).join("；")}`, "err");
  }

  switchSubtab("inspector");
  renderWires();
  renderInspector();
}

async function autoWireValidOnBoard() {
  const boardEdges = state.edges.filter((e) => e.on_board);
  const validBridges = (state.audit?.bridges || []).filter((b) => b.ok);
  const newWires = [];
  for (const b of validBridges) {
    const fromOn = boardEdges.some((e) => e.id === b.from_edge);
    const toOn = boardEdges.some((e) => e.id === b.to_edge);
    if (fromOn && toOn) {
      newWires.push({
        id: `${b.from_edge}->${b.to_edge}`,
        from_edge_id: b.from_edge,
        to_edge_id: b.to_edge,
        valid: true,
        bridge: b.bridge,
        warnings: b.issues || [],
        issues: [],
      });
    }
  }
  state.wires = newWires;
  renderWires();
}

async function reauditWorld() {
  const res = await api("/api/world/audit", {
    eras: state.eras,
    nodes: state.nodes,
    edges: state.edges,
    translations: state.translations,
  });
  state.audit = res.audit;

  // Re-validate all active manual/auto wires
  const updatedWires = [];
  for (const w of state.wires) {
    if (!edgeById(w.from_edge_id) || !edgeById(w.to_edge_id)) continue;
    const wr = await api("/api/world/wire", {
      eras: state.eras,
      nodes: state.nodes,
      edges: state.edges,
      translations: state.translations,
      from_edge_id: w.from_edge_id,
      to_edge_id: w.to_edge_id,
    });
    updatedWires.push({
      id: w.id,
      from_edge_id: w.from_edge_id,
      to_edge_id: w.to_edge_id,
      valid: wr.valid,
      bridge: wr.bridge,
      warnings: wr.warnings || [],
      issues: wr.issues || [],
    });
  }
  state.wires = updatedWires;
  renderAll();
}

// ==== Inspector ====
function renderInspector() {
  const box = document.getElementById("inspector-body");
  if (state.selectedWireId) {
    const w = state.wires.find((x) => x.id === state.selectedWireId);
    if (!w) {
      box.innerHTML = `<p class="muted">连线已移除。</p>`;
      return;
    }
    const issuesHtml = w.valid
      ? w.bridge?.kind === "translation"
        ? `<div class="box ok"><strong>跨时代认知转译成立 (aliaso)</strong><br><code>${esc(w.bridge.from_node)}</code> ⇄ <code>${esc(w.bridge.to_node)}</code><br><div style="margin-top:0.35rem;">${esc(w.bridge.gloss)}</div></div>`
        : `<div class="box ok"><strong>同节点直连成立</strong><br>桥接节点：<code>${esc(w.bridge?.from_node)}</code>（${esc(nodeLabel(w.bridge?.from_node))}）</div>`
      : `<div class="box err"><strong>接线被拒绝</strong><ul style="margin:0.35rem 0 0;padding-left:1.1rem;">${w.issues
          .map((i) => `<li>${esc(i.message)}</li>`)
          .join("")}</ul></div>`;

    box.innerHTML = `
      <div class="stack-gap">
        <div class="mono" style="font-size:0.78rem;">${esc(w.from_edge_id)} → ${esc(w.to_edge_id)}</div>
        ${issuesHtml}
        <button id="btn-remove-wire" class="btn danger">断开此连线</button>
      </div>
    `;
    document.getElementById("btn-remove-wire").addEventListener("click", () => {
      state.wires = state.wires.filter((x) => x.id !== w.id);
      state.selectedWireId = null;
      renderWires();
      renderInspector();
    });
    return;
  }

  if (state.selectedCardId) {
    const edge = edgeById(state.selectedCardId);
    if (!edge) {
      box.innerHTML = `<p class="muted">找不到选中卡片。</p>`;
      return;
    }
    const rep = state.audit?.edge_reports?.[edge.id] || { errors: [], warnings: [] };
    const nodeOpts = state.nodes
      .map((n) => `<option value="${esc(n.id)}">${esc(n.label)} (${esc(n.id)})</option>`)
      .join("");
    const eraOpts = state.eras
      .map((e) => `<option value="${esc(e.id)}">${esc(e.name)}</option>`)
      .join("");

    box.innerHTML = `
      <form id="card-edit-form" class="form-stack">
        <div class="inline-row" style="justify-content:space-between;">
          <span class="mono" style="font-size:0.76rem;">ID: ${esc(edge.id)}</span>
          <button type="button" id="btn-delete-card" class="btn small danger">删除卡片</button>
        </div>
        <label>卡片标题 <input type="text" id="ed-title" value="${esc(edge.title)}" /></label>
        <label>所属时代 <select id="ed-era">${eraOpts}</select></label>
        <label>观察者 / 视角 (observer) <input type="text" id="ed-observer" value="${esc(edge.observer)}" /></label>
        <label>输入节点 (from) <select id="ed-from">${nodeOpts}</select></label>
        <label>关系动词 (rel) <input type="text" id="ed-rel" value="${esc(edge.rel)}" /></label>
        <label>输出节点 (to) <select id="ed-to">${nodeOpts}</select></label>
        <label style="flex-direction:row;align-items:center;gap:0.45rem;">
          <input type="checkbox" id="ed-retro" ${edge.retrospective ? "checked" : ""} />
          <span>允许跨时代历史追认 (retrospective)</span>
        </label>
        <label>涉及概念词（逗号分隔）
          <input type="text" id="ed-concepts" value="${esc((edge.concepts || []).join(", "))}" />
        </label>
        <label>档案正文
          <textarea id="ed-body">${esc(edge.body)}</textarea>
        </label>
        <button type="submit" class="btn primary">应用修改并重检图谱</button>
      </form>
      <div class="stack-gap" style="margin-top:0.65rem;">
        ${(rep.errors || []).map((e) => `<div class="box err">${esc(e.message)}</div>`).join("")}
        ${(rep.warnings || []).map((w) => `<div class="box">${esc(w.message)}</div>`).join("")}
      </div>
    `;

    document.getElementById("ed-era").value = edge.era;
    document.getElementById("ed-from").value = edge.from;
    document.getElementById("ed-to").value = edge.to;

    document.getElementById("card-edit-form").addEventListener("submit", async (ev) => {
      ev.preventDefault();
      edge.title = document.getElementById("ed-title").value.trim();
      edge.era = document.getElementById("ed-era").value;
      edge.observer = document.getElementById("ed-observer").value.trim() || "unspecified";
      edge.from = document.getElementById("ed-from").value;
      edge.rel = document.getElementById("ed-rel").value.trim() || "relates_to";
      edge.to = document.getElementById("ed-to").value;
      edge.retrospective = document.getElementById("ed-retro").checked;
      edge.concepts = document
        .getElementById("ed-concepts")
        .value.split(/[,，]/)
        .map((s) => s.trim())
        .filter(Boolean);
      edge.body = document.getElementById("ed-body").value;

      markDirty();
      await reauditWorld();
      toast(`已更新卡片「${edge.title}」并完成关系重检。`, "ok");
    });

    document.getElementById("btn-delete-card").addEventListener("click", async () => {
      state.edges = state.edges.filter((e) => e.id !== edge.id);
      state.wires = state.wires.filter((w) => w.from_edge_id !== edge.id && w.to_edge_id !== edge.id);
      state.selectedCardId = null;
      markDirty();
      await reauditWorld();
    });
    return;
  }

  box.innerHTML = `<p class="muted">点击画布上的卡片或连线进行编辑与检查；也可切换到「时代认知模型」直接修改各时代允许/禁止的概念和防火墙规则。</p>`;
}

// ==== Era Epistemic Editor ====
function renderEraEditor() {
  const select = document.getElementById("era-editor-select");
  const prev = select.value;
  select.innerHTML = state.eras.map((e) => `<option value="${esc(e.id)}">${esc(e.name)}</option>`).join("");
  if (prev && eraById(prev)) select.value = prev;

  const era = eraById(select.value) || state.eras[0];
  const body = document.getElementById("era-editor-body");
  if (!era) {
    body.innerHTML = "";
    return;
  }

  const taboosHtml = (era.taboos || [])
    .map(
      (t, idx) => `
      <div class="box" style="display:flex;flex-direction:column;gap:0.25rem;">
        <div class="inline-row" style="justify-content:space-between;">
          <span class="mono" style="font-size:0.72rem;">neq(${esc(t.observer)}, ${esc(t.rel)}, ${esc(t.target_kind)})</span>
          <button type="button" class="btn small danger del-taboo" data-idx="${idx}">删</button>
        </div>
        <div class="muted" style="font-size:0.74rem;">${esc(t.reason)}</div>
      </div>`
    )
    .join("");

  body.innerHTML = `
    <form id="era-edit-form" class="form-stack" style="margin-top:0.55rem;">
      <label>时代名称 <input type="text" id="er-name" value="${esc(era.name)}" /></label>
      <label>年代顺位 (order) <input type="number" id="er-order" value="${esc(era.order)}" min="1" /></label>
      <label>建模粒度说明 (granularity)
        <textarea id="er-granularity">${esc(era.granularity || "")}</textarea>
      </label>
      <label>允许概念词表（逗号分隔）
        <textarea id="er-allowed">${esc((era.allowed_concepts || []).join(", "))}</textarea>
      </label>
      <label>时代禁止词汇 / 防越界（逗号分隔）
        <textarea id="er-forbidden">${esc((era.forbidden_concepts || []).join(", "))}</textarea>
      </label>
      <button type="submit" class="btn primary">更新此时代模型并重检全图</button>
    </form>

    <div style="margin-top:0.75rem;">
      <div class="drawer-header">
        <strong style="font-size:0.82rem;">机构认知防火墙 (neq/2)</strong>
        <button type="button" id="btn-add-taboo" class="btn small">+ 加规则</button>
      </div>
      <div class="stack-gap">${taboosHtml || `<span class="muted">此时代暂无 neq 防火墙规则。</span>`}</div>
    </div>
  `;

  select.onchange = renderEraEditor;

  document.getElementById("era-edit-form").addEventListener("submit", async (ev) => {
    ev.preventDefault();
    era.name = document.getElementById("er-name").value.trim() || era.id;
    era.order = Number(document.getElementById("er-order").value || 1);
    era.granularity = document.getElementById("er-granularity").value;
    era.allowed_concepts = splitTerms(document.getElementById("er-allowed").value);
    era.forbidden_concepts = splitTerms(document.getElementById("er-forbidden").value);

    markDirty();
    await reauditWorld();
    toast(`已更新时代「${era.name}」认知模型，全图已完成重新校验。`, "ok");
  });

  body.querySelectorAll(".del-taboo").forEach((btn) => {
    btn.addEventListener("click", async () => {
      era.taboos.splice(Number(btn.dataset.idx), 1);
      markDirty();
      await reauditWorld();
    });
  });

  document.getElementById("btn-add-taboo").addEventListener("click", async () => {
    const observer = prompt("观察者 (observer)，如 official_hq：", "official_hq");
    if (!observer) return;
    const rel = prompt("禁止的关系动词 (rel)，如 caused_by：", "caused_by");
    if (!rel) return;
    const targetKind = prompt("禁止的目标节点类型 (target_kind)，如 engineering_disturbance：", "engineering_disturbance");
    if (!targetKind) return;
    const reason = prompt("原因说明：", "认知防火墙阻断此归因") || "认知防火墙阻断此归因";

    era.taboos = era.taboos || [];
    era.taboos.push({ observer, rel, target_kind: targetKind, reason });
    markDirty();
    await reauditWorld();
  });
}

function splitTerms(str) {
  return str
    .split(/[,，\n]/)
    .map((s) => s.trim())
    .filter(Boolean);
}

// ==== Aliases / Translations Panel ====
function renderAliasesPanel() {
  const box = document.getElementById("aliases-list");
  if (!box) return;

  if (state.translations.length === 0) {
    box.innerHTML = `<div class="muted">尚未声明跨时代同物转译，点击「+ 新转译」添加。</div>`;
    return;
  }

  box.innerHTML = state.translations
    .map(
      (tr, idx) => `
      <div class="box ok" style="border-left-color:#2b5b84;">
        <div class="inline-row" style="justify-content:space-between;margin-bottom:0.25rem;">
          <span class="mono" style="font-size:0.74rem;color:#2b5b84;">${esc(tr.id)}</span>
          <div class="inline-row" style="gap:0.25rem;">
            ${tr.reversible ? `<span class="badge">双向</span>` : `<span class="badge">单向</span>`}
            ${tr.retrospective ? `<span class="badge">追认</span>` : ""}
            <button type="button" class="btn small danger del-alias" data-idx="${idx}">删</button>
          </div>
        </div>
        <div class="mono" style="font-size:0.75rem;margin-bottom:0.25rem;">
          ${esc(nodeLabel(tr.from_node))} (${esc(tr.from_node)})<br>
          ⇄ ${esc(nodeLabel(tr.to_node))} (${esc(tr.to_node)})
        </div>
        <div style="font-size:0.78rem;color:var(--ink);">${esc(tr.gloss)}</div>
      </div>`
    )
    .join("");

  box.querySelectorAll(".del-alias").forEach((btn) => {
    btn.addEventListener("click", async () => {
      state.translations.splice(Number(btn.dataset.idx), 1);
      markDirty();
      await reauditWorld();
      toast("已删除跨时代转译规则，所有受影响连线已重检。", "");
    });
  });
}

// ==== Pathfinder ====
function renderPathfinderSelects() {
  const s = document.getElementById("pf-start");
  const t = document.getElementById("pf-target");
  const prevS = s.value || "node_ritual_origin";
  const prevT = t.value || "node_defibrillation";
  const opts = state.nodes.map((n) => `<option value="${esc(n.id)}">${esc(n.label)} (${esc(n.id)})</option>`).join("");
  s.innerHTML = opts;
  t.innerHTML = opts;
  if (nodeById(prevS)) s.value = prevS;
  if (nodeById(prevT)) t.value = prevT;
}

async function runPathfinder() {
  const startNode = document.getElementById("pf-start").value;
  const targetNode = document.getElementById("pf-target").value;
  const res = await api("/api/world/paths", {
    eras: state.eras,
    nodes: state.nodes,
    edges: state.edges,
    translations: state.translations,
    start_node: startNode,
    target_node: targetNode,
  });

  const box = document.getElementById("pf-results");
  const validHtml =
    res.valid_paths.length === 0
      ? `<div class="box err">未找到满足全部时代认知约束的通路。</div>`
      : res.valid_paths
          .map(
            (p, idx) => `
          <div class="box ok">
            <strong>合法通路 #${idx + 1} (${p.length} 跳)</strong>
            <div class="mono" style="font-size:0.74rem;margin:0.25rem 0;">${p.map(esc).join(" → ")}</div>
            <button type="button" class="btn small primary highlight-path" data-path="${esc(p.join(","))}">在画布高亮并连线</button>
          </div>`
          )
          .join("");

  const rejHtml = res.rejected_paths
    .map(
      (r) => `
      <div class="box err">
        <strong>被时代/防火墙拦截的通路</strong>
        <div class="mono" style="font-size:0.72rem;margin:0.2rem 0;">${r.path.map(esc).join(" → ")}</div>
        <div style="font-size:0.74rem;">${r.reasons.map((x) => esc(x.message)).join("<br>")}</div>
      </div>`
    )
    .join("");

  box.innerHTML = validHtml + rejHtml;

  box.querySelectorAll(".highlight-path").forEach((btn) => {
    btn.addEventListener("click", async () => {
      const ids = btn.dataset.path.split(",");
      state.highlightedCardIds = new Set(ids);
      ids.forEach((id) => {
        const edge = edgeById(id);
        if (edge) edge.on_board = true;
      });
      state.wires = [];
      for (let i = 0; i < ids.length - 1; i++) {
        await addAndValidateWire(ids[i], ids[i + 1]);
      }
      renderLeftDrawers();
      renderBoardCards();
      renderWires();
      toast(`已高亮并连线合法通路：${ids.join(" → ")}`, "ok");
    });
  });
}

// ==== Topbar & Creation Actions ====
function bindTopbar() {
  document.getElementById("btn-auto-wire").addEventListener("click", async () => {
    await autoWireValidOnBoard();
    toast("已自动连接当前桌面上所有合规且端口匹配的卡片。", "ok");
  });

  document.getElementById("btn-clear-wires").addEventListener("click", () => {
    state.wires = [];
    state.highlightedCardIds.clear();
    state.selectedWireId = null;
    renderBoardCards();
    renderWires();
    renderInspector();
  });

  document.getElementById("btn-board-all").addEventListener("click", async () => {
    state.edges.forEach((e) => (e.on_board = true));
    markDirty();
    renderLeftDrawers();
    renderBoardCards();
    renderWires();
    toast("已将全部卡片（含干扰卡、穿帮与防火墙测试卡）摆上桌面。", "");
  });

  document.getElementById("btn-board-main").addEventListener("click", () => {
    state.edges.forEach((e) => {
      e.on_board = e.id.startsWith("card_0");
    });
    markDirty();
    renderLeftDrawers();
    renderBoardCards();
    autoWireValidOnBoard();
  });

  document.getElementById("btn-save").addEventListener("click", async () => {
    const res = await api("/api/world/save", {
      eras: state.eras,
      nodes: state.nodes,
      edges: state.edges,
      translations: state.translations,
    });
    state.audit = res.audit;
    markDirty(false);
    renderAuditSummary();
    toast("已保存到 world/eras.json 与 world/graph.json。", "ok");
  });

  document.getElementById("btn-new-alias").addEventListener("click", async () => {
    const fromNode = prompt("源节点 ID（如 node_zheng）：", state.nodes[0]?.id || "");
    if (!fromNode || !nodeById(fromNode)) {
      if (fromNode) toast(`找不到节点 ${fromNode}`, "err");
      return;
    }
    const toNode = prompt("目标节点 ID（如 node_era2a_old_bronze）：", state.nodes[1]?.id || "");
    if (!toNode || !nodeById(toNode)) {
      if (toNode) toast(`找不到节点 ${toNode}`, "err");
      return;
    }
    const gloss = prompt("转译说明：", `${nodeLabel(fromNode)} 即后世所称 ${nodeLabel(toNode)}`) || "";
    const reversible = confirm("是否设为双向等价？（确定=双向，取消=单向正序转译）");
    const retrospective = confirm("是否属于历史追认转译？（确定=允许跨时代追认）");

    state.translations.push({
      id: `tr_${Date.now().toString().slice(-4)}`,
      from_node: fromNode,
      to_node: toNode,
      gloss,
      reversible,
      retrospective,
    });
    markDirty();
    await reauditWorld();
    toast("已新增跨时代同物转译桥。", "ok");
  });

  document.getElementById("btn-reset").addEventListener("click", async () => {
    const res = await api("/api/world/reset", {});
    state.highlightedCardIds.clear();
    loadWorldState(res);
    autoWireValidOnBoard();
    toast("已从磁盘重新加载世界档案。", "");
  });

  document.getElementById("btn-new-card").addEventListener("click", async () => {
    const id = `card_${Date.now().toString().slice(-4)}`;
    const firstNode = state.nodes[0]?.id || "node_zheng";
    const secondNode = state.nodes[1]?.id || firstNode;
    const firstEra = state.eras[0]?.id || "era1_ancestral";
    const newCard = {
      id,
      from: firstNode,
      rel: "relates_to",
      to: secondNode,
      era: firstEra,
      observer: "archivist",
      retrospective: false,
      title: "新档案卡",
      body: "在此编写事件与认知记录……",
      concepts: [],
      on_board: true,
      x: 160 + (state.edges.length % 4) * 50,
      y: 160 + (state.edges.length % 4) * 40,
    };
    state.edges.push(newCard);
    state.selectedCardId = id;
    state.selectedWireId = null;
    markDirty();
    await reauditWorld();
    switchSubtab("inspector");
  });

  document.getElementById("btn-new-node").addEventListener("click", async () => {
    const id = prompt("新节点 ID（英文标识，如 node_mycelium_core）：");
    if (!id) return;
    if (nodeById(id)) {
      toast(`节点 ${id} 已存在。`, "err");
      return;
    }
    const label = prompt("节点显示名称：", id) || id;
    const kind = prompt("节点类型 (kind)，如 relic / omen / protocol / engineering_disturbance：", "entity") || "entity";
    const era = state.eras[0]?.id || "era1_ancestral";
    state.nodes.push({ id, kind, era, label, note: "" });
    markDirty();
    await reauditWorld();
    toast(`已新增节点「${label}」(${id})。`, "ok");
  });

  document.getElementById("btn-new-era").addEventListener("click", async () => {
    const id = prompt("新时代 ID（如 era4_recovery）：");
    if (!id) return;
    if (eraById(id)) {
      toast(`时代 ${id} 已存在。`, "err");
      return;
    }
    const name = prompt("时代显示名：", id) || id;
    state.eras.push({
      id,
      name,
      order: state.eras.length + 1,
      color: "#394b54",
      granularity: "在此描述该时代对山体建模的粒度。",
      allowed_concepts: [],
      forbidden_concepts: [],
      taboos: [],
    });
    markDirty();
    await reauditWorld();
    document.getElementById("era-editor-select").value = id;
    renderEraEditor();
  });

  document.getElementById("btn-find-paths").addEventListener("click", runPathfinder);
}

init().catch((err) => {
  toast(`加载失败：${err.message}`, "err");
  console.error(err);
});
