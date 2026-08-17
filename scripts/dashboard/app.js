const SOURCES = [
  { id: "anarchy", label: "Anarchy" },
  { id: "smp", label: "Forever World" },
  { id: "velocity", label: "Velocity" },
  { id: "discord", label: "Discord" },
];

const state = {
  source: "anarchy",
  offsets: { anarchy: -1, smp: -1, velocity: -1, discord: -1 },
  buffers: { anarchy: "", smp: "", velocity: "", discord: "" },
};

const cardsEl = document.getElementById("cards");
const tabsEl = document.getElementById("log-tabs");
const logEl = document.getElementById("log");
const followEl = document.getElementById("follow");
const toastEl = document.getElementById("toast");
const clockEl = document.getElementById("clock");
const rconTarget = document.getElementById("rcon-target");

function toast(message) {
  toastEl.textContent = message;
  toastEl.hidden = false;
  clearTimeout(toastEl._t);
  toastEl._t = setTimeout(() => {
    toastEl.hidden = true;
  }, 2400);
}

function esc(text) {
  return String(text)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;");
}

function colorize(text) {
  return esc(text)
    .split("\n")
    .map((line) => {
      if (/^> /.test(line)) return `<span class="cmd">${line}</span>`;
      if (/\b(ERROR|FATAL|Exception|SEVERE)\b/i.test(line)) {
        return `<span class="err">${line}</span>`;
      }
      if (/\bWARN(ING)?\b/i.test(line)) return `<span class="warn">${line}</span>`;
      if (/\b(Done \(|INFO\]: Done|Started )\b/i.test(line)) {
        return `<span class="ok">${line}</span>`;
      }
      return line;
    })
    .join("\n");
}

function renderLog() {
  const atBottom =
    logEl.scrollHeight - logEl.scrollTop - logEl.clientHeight < 40;
  logEl.innerHTML = colorize(state.buffers[state.source] || "");
  if (followEl.checked || atBottom) {
    logEl.scrollTop = logEl.scrollHeight;
  }
}

function pill(up) {
  return `<span class="pill ${up ? "up" : ""}"><span class="dot"></span>${
    up ? "live" : "down"
  }</span>`;
}

function asList(value) {
  if (!value) return [];
  const list = Array.isArray(value) ? value : [value];
  return list.filter((n) => n);
}

function playerChips(names) {
  const list = asList(names);
  if (!list.length) {
    return `<span class="muted">Empty</span>`;
  }
  return list.map((n) => `<span class="chip">${esc(n)}</span>`).join("");
}

function card(title, sub, up, body, actions) {
  return `<article class="card">
    <div class="card-top">
      <div>
        <h2>${esc(title)}</h2>
        <p class="sub">${esc(sub)}</p>
      </div>
      ${pill(up)}
    </div>
    ${body}
    ${actions ? `<div class="card-actions">${actions}</div>` : ""}
  </article>`;
}

function tpsClass(n) {
  if (n == null || Number.isNaN(n)) return "";
  if (n >= 19.5) return "ok";
  if (n >= 15) return "warn";
  return "err";
}

function fmt(n, digits) {
  if (n == null || Number.isNaN(Number(n))) return "—";
  return Number(n).toFixed(digits);
}

function tickRow(ticks, kind) {
  if (!ticks) {
    return `<p class="muted">Waiting for tick sample…</p>`;
  }
  const tpsLabel = kind === "folia" ? "Region TPS" : "TPS";
  const extra =
    kind === "folia"
      ? `<div class="stat">
          <span class="stat-label">Low / high</span>
          <span class="stat-val">${fmt(ticks.tps5, 1)} / ${fmt(ticks.tps15, 1)}</span>
        </div>
        <div class="stat">
          <span class="stat-label">Regions</span>
          <span class="stat-val">${ticks.regions == null ? "—" : ticks.regions}</span>
        </div>
        <div class="stat">
          <span class="stat-label">Util</span>
          <span class="stat-val">${fmt(ticks.util, 1)}%</span>
        </div>`
      : `<div class="stat">
          <span class="stat-label">5m / 15m</span>
          <span class="stat-val">${fmt(ticks.tps5, 1)} / ${fmt(ticks.tps15, 1)}</span>
        </div>`;
  return `<div class="stats">
    <div class="stat">
      <span class="stat-label">${tpsLabel}</span>
      <span class="stat-val ${tpsClass(ticks.tps)}">${fmt(ticks.tps, 1)}</span>
    </div>
    <div class="stat">
      <span class="stat-label">MSPT</span>
      <span class="stat-val">${fmt(ticks.mspt, 1)}${
        ticks.msptMax != null ? `<span class="stat-sub"> max ${fmt(ticks.msptMax, 1)}</span>` : ""
      }</span>
    </div>
    ${extra}
  </div>`;
}

function renderStatus(s) {
  const a = s.anarchy;
  const f = s.smp;
  const v = s.velocity;
  const d = s.discord;
  const db = s.postgres;
  const rd = s.redis;

  cardsEl.innerHTML = [
    card(
      "Anarchy",
      "anarchy.glyphmc.net · Folia",
      a.up,
      a.up
        ? `${tickRow(a.ticks, "folia")}<div class="players">${playerChips(a.players)}</div>`
        : `<span class="muted">Not listening on :25566</span>`,
      `<button class="btn" data-action="restart-anarchy" ${a.up ? "" : "disabled"}>Restart</button>`
    ),
    card(
      "Forever World",
      "smp.glyphmc.net · Paper",
      f.up,
      f.up
        ? `${tickRow(f.ticks, "paper")}<div class="players">${playerChips(f.players)}</div>`
        : `<span class="muted">Not listening on :25567</span>`,
      `<button class="btn" data-action="restart-smp" ${f.up ? "" : "disabled"}>Restart</button>`
    ),
    card(
      "Velocity",
      "play.glyphmc.net · :25565",
      v.up,
      `<p class="muted">${v.up ? "Public join port" : "Proxy is down — nobody can connect"}</p>`
    ),
    card(
      "Discord",
      "companion bot",
      d.up,
      `<p class="muted">${d.up ? "Process running" : "Bot is not running"}</p>`
    ),
    card(
      "Postgres",
      "shared wallet",
      db.up,
      `<p class="muted">${db.up ? "127.0.0.1:5432" : "Docker DB is down — joins will fail"}</p>`
    ),
    card(
      "Redis",
      "events + cache",
      rd.up,
      `<p class="muted">${rd.up ? "127.0.0.1:6379" : "Redis is down"}</p>`
    ),
  ].join("");
}

async function api(path, options) {
  const res = await fetch(path, options);
  const text = await res.text();
  let data = null;
  try {
    data = text ? JSON.parse(text) : null;
  } catch {
    data = { raw: text };
  }
  if (!res.ok) {
    throw new Error((data && data.error) || res.statusText);
  }
  return data;
}

async function refreshStatus() {
  try {
    renderStatus(await api("/api/status"));
  } catch (err) {
    toast(err.message);
  }
}

async function pollLogs() {
  const source = state.source;
  const offset = state.offsets[source];
  try {
    const data = await api(`/api/logs?source=${source}&offset=${offset}`);
    state.offsets[source] = data.offset;
    if (data.text) {
      state.buffers[source] += data.text;
      if (state.buffers[source].length > 200000) {
        state.buffers[source] = state.buffers[source].slice(-120000);
      }
      if (source === state.source) renderLog();
    }
  } catch {
    // server restarting
  }
}

async function postAction(action) {
  toast(action === "start" ? "Starting stack…" : "Restart sent");
  await api("/api/action", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ action }),
  });
}

tabsEl.innerHTML = SOURCES.map(
  (s, i) =>
    `<button type="button" data-source="${s.id}" class="${
      i === 0 ? "active" : ""
    }">${s.label}</button>`
).join("");

tabsEl.addEventListener("click", (ev) => {
  const btn = ev.target.closest("button[data-source]");
  if (!btn) return;
  state.source = btn.dataset.source;
  rconTarget.value = state.source === "smp" ? "smp" : "anarchy";
  tabsEl.querySelectorAll("button").forEach((b) => b.classList.remove("active"));
  btn.classList.add("active");
  renderLog();
});

cardsEl.addEventListener("click", async (ev) => {
  const btn = ev.target.closest("button[data-action]");
  if (!btn) return;
  btn.disabled = true;
  try {
    await postAction(btn.dataset.action);
  } catch (err) {
    toast(err.message);
  } finally {
    btn.disabled = false;
  }
});

document.getElementById("btn-start").addEventListener("click", async (ev) => {
  ev.currentTarget.disabled = true;
  try {
    await postAction("start");
  } catch (err) {
    toast(err.message);
  } finally {
    ev.currentTarget.disabled = false;
  }
});

document.getElementById("rcon-form").addEventListener("submit", async (ev) => {
  ev.preventDefault();
  const command = document.getElementById("rcon-cmd").value.trim();
  if (!command) return;
  const target = rconTarget.value;
  document.getElementById("rcon-cmd").value = "";
  const dest = target === "smp" ? "smp" : "anarchy";
  state.buffers[dest] += `\n> ${command}\n`;
  if (state.source === dest) renderLog();
  try {
    const data = await api("/api/rcon", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ target, command }),
    });
    if (data.output) {
      state.buffers[dest] += `${data.output}\n`;
      if (state.source === dest) renderLog();
    }
  } catch (err) {
    state.buffers[dest] += `${err.message}\n`;
    if (state.source === dest) renderLog();
  }
});

function tickClock() {
  clockEl.textContent = new Date().toLocaleTimeString();
}

tickClock();
refreshStatus();
pollLogs();
setInterval(tickClock, 1000);
setInterval(refreshStatus, 2500);
setInterval(pollLogs, 800);
