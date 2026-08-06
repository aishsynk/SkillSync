/**
 * SkillEdge — shared app utilities, layout injection, navigation guard
 */

// ── Auth guard ──────────────────────────────────────────────────────────────
(function authGuard() {
  const page = location.pathname.split('/').pop() || 'login.html';
  const publicPages = ['login.html', ''];
  if (publicPages.includes(page)) return;
  if (!API.getManagerEmail()) {
    location.href = 'login.html';
    return;
  }
  API.session().then(sess => {
    if (sess && sess.email) {
      sessionStorage.setItem('skilledge_manager_email', sess.email);
      return;
    }
    API.handleAuthFailure();
  }).catch(() => {
    API.handleAuthFailure();
  });
})();

// ── Theme panel HTML (full Sean Theme color selector) ───────────────────────
const THEME_PANEL = `
<div class="theme-panel">
  <a href="javascript:;" data-toggle="theme-panel-expand" class="theme-collapse-btn"><i class="bi bi-gear-fill"></i></a>
  <div class="theme-panel-content" data-scrollbar="true" data-height="100%">
    <h5>App Settings</h5>
    <div class="theme-list">
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-red" data-theme-class="theme-red" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Red">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-pink" data-theme-class="theme-pink" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Pink">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-orange" data-theme-class="theme-orange" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Orange">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-yellow" data-theme-class="theme-yellow" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Yellow">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-lime" data-theme-class="theme-lime" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Lime">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-green" data-theme-class="theme-green" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Green">&nbsp;</a></div>
      <div class="theme-list-item active"><a href="javascript:;" class="theme-list-link bg-teal" data-theme-class="" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Default">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-cyan" data-theme-class="theme-cyan" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Cyan">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-blue" data-theme-class="theme-blue" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Blue">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-purple" data-theme-class="theme-purple" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Purple">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-indigo" data-theme-class="theme-indigo" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Indigo">&nbsp;</a></div>
      <div class="theme-list-item"><a href="javascript:;" class="theme-list-link bg-black" data-theme-class="theme-gray-600" data-toggle="theme-selector" data-bs-toggle="tooltip" data-bs-trigger="hover" data-bs-container="body" data-bs-title="Black">&nbsp;</a></div>
    </div>
    <div class="theme-panel-divider"></div>
    <div class="row mt-10px">
      <div class="col-8 control-label text-body fw-bold">
        <div class="d-flex">Dark Mode <span class="badge bg-primary ms-1 position-relative d-flex align-items-center">NEW</span></div>
        <div class="lh-14"><small class="text-body opacity-50">Reduce glare and give your eyes a break.</small></div>
      </div>
      <div class="col-4 d-flex">
        <div class="form-check form-switch ms-auto mb-0">
          <input type="checkbox" class="form-check-input" name="app-theme-dark-mode" id="appThemeDarkMode" value="1" />
          <label class="form-check-label" for="appThemeDarkMode"></label>
        </div>
      </div>
    </div>
    <div class="theme-panel-divider"></div>
    <div class="row mt-10px align-items-center">
      <div class="col-8 control-label text-body fw-bold">Header Fixed</div>
      <div class="col-4 d-flex"><div class="form-check form-switch ms-auto mb-0">
        <input type="checkbox" class="form-check-input" name="app-header-fixed" id="appHeaderFixed" value="1" checked />
        <label class="form-check-label" for="appHeaderFixed"></label>
      </div></div>
    </div>
    <div class="row mt-10px align-items-center">
      <div class="col-8 control-label text-body fw-bold">Header Inverse</div>
      <div class="col-4 d-flex"><div class="form-check form-switch ms-auto mb-0">
        <input type="checkbox" class="form-check-input" name="app-header-inverse" id="appHeaderInverse" value="1" />
        <label class="form-check-label" for="appHeaderInverse"></label>
      </div></div>
    </div>
    <div class="row mt-10px align-items-center">
      <div class="col-8 control-label text-body fw-bold">Sidebar Fixed</div>
      <div class="col-4 d-flex"><div class="form-check form-switch ms-auto mb-0">
        <input type="checkbox" class="form-check-input" name="app-sidebar-fixed" id="appSidebarFixed" value="1" checked />
        <label class="form-check-label" for="appSidebarFixed"></label>
      </div></div>
    </div>
    <div class="row mt-10px align-items-center">
      <div class="col-8 control-label text-body fw-bold">Sidebar Grid</div>
      <div class="col-4 d-flex"><div class="form-check form-switch ms-auto mb-0">
        <input type="checkbox" class="form-check-input" name="app-sidebar-grid" id="appSidebarGrid" value="1" />
        <label class="form-check-label" for="appSidebarGrid"></label>
      </div></div>
    </div>
    <div class="row mt-10px align-items-center">
      <div class="col-8 control-label text-body fw-bold">Gradient Enabled</div>
      <div class="col-4 d-flex"><div class="form-check form-switch ms-auto mb-0">
        <input type="checkbox" class="form-check-input" name="app-gradient-enabled" id="appGradientEnabled" value="1" />
        <label class="form-check-label" for="appGradientEnabled"></label>
      </div></div>
    </div>
    <div class="theme-panel-divider"></div>
    <a href="javascript:;" class="btn btn-default d-block w-100 rounded-pill" data-toggle="reset-local-storage"><b>Reset Local Storage</b></a>
  </div>
</div>`;

const SCROLL_TOP_BTN = `<a href="javascript:;" class="btn btn-icon btn-circle btn-theme btn-scroll-to-top" data-toggle="scroll-to-top"><i class="bi bi-chevron-up"></i></a>`;

// Pages that are actually built and working. Everything else routes to a clean
// "Coming next" placeholder so the sidebar never produces a broken link.
// trainer-detail.html is kept here (not removed) because it now exists purely
// as a redirect shim to trainer-intelligence.html — see frontend/pages/trainer-detail.html.
const BUILT_PAGES = new Set(['index.html', 'team.html', 'trainer-detail.html', 'trainer-intelligence.html', 'capability-builder.html', 'allocation-desk.html', 'custom-course-match.html', 'actions.html', 'risk-takers.html', 'certifications.html', 'data-health.html', 'settings.html', 'coming-soon.html']);

// Manager OS navigation — every first-class surface gets its own persistent
// sidebar entry, grouped by manager workflow. trainer-detail.html is
// deliberately absent: it is a legacy redirect shim to trainer-intelligence.html,
// not a distinct destination.
const MENU_MODEL = [
  { header: 'Manager Workspace', items: [
    ['index.html', 'bi bi-speedometer2', 'Command Center'],
    ['team.html', 'bi bi-people-fill', 'Team & Trainer 360'],
    ['allocation-desk.html', 'bi bi-diagram-3-fill', 'Demand & Allocation'],
    ['capability-builder.html', 'bi bi-graph-up-arrow', 'Capability Planning'],
  ]},
  { header: 'Supporting Workflows', items: [
    ['actions.html', 'bi bi-inbox-fill', 'Manager Actions'],
    ['custom-course-match.html', 'bi bi-file-earmark-arrow-up-fill', 'Upload TOC / Sales Request'],
  ]},
  { header: 'Administration', items: [
    ['data-health.html', 'bi bi-heart-pulse-fill', 'Data Health'],
    ['settings.html', 'bi bi-gear-fill', 'Settings'],
  ]},
];

// Ensure Bootstrap Icons stylesheet is present on every page using the shared layout
// (the theme's FontAwesome webfonts are missing, so we standardise on Bootstrap Icons).
(function ensureBootstrapIcons() {
  if (!document.querySelector('link[href*="bootstrap-icons"]')) {
    const l = document.createElement('link');
    l.rel = 'stylesheet';
    l.href = '/assets/plugins/bootstrap-icons/font/bootstrap-icons.css';
    document.head.appendChild(l);
  }
})();

// ── Layout injection ─────────────────────────────────────────────────────────
function injectLayout(activeNav) {
  const email   = API.getManagerEmail();
  const initial = email ? email[0].toUpperCase() : 'M';

  const header = `
<div id="header" class="app-header">
  <div class="navbar-header">
    <a href="index.html" class="navbar-brand"><span class="navbar-logo"></span><b>Skill</b>Edge</a>
    <button type="button" class="navbar-mobile-toggler" data-toggle="app-sidebar-mobile">
      <span class="icon-bar"></span><span class="icon-bar"></span><span class="icon-bar"></span>
    </button>
  </div>
  <div class="navbar-nav">
    <div class="navbar-item navbar-form">
      <form action="javascript:;" name="search">
        <div class="form-group">
          <input type="text" class="form-control" placeholder="Search trainer / course..." id="globalSearch" />
          <button type="submit" class="btn btn-search"><i class="bi bi-search"></i></button>
        </div>
      </form>
    </div>
    <div class="navbar-item dropdown">
      <a href="#" data-bs-toggle="dropdown" class="navbar-link dropdown-toggle icon">
        <i class="bi bi-bell-fill"></i>
        <span class="badge" id="notifBadge">0</span>
      </a>
      <div class="dropdown-menu media-list dropdown-menu-end" id="notifDropdown">
        <div class="dropdown-header">NOTIFICATIONS</div>
        <div class="dropdown-item text-muted small py-2" id="notifList">Loading alerts…</div>
        <div class="dropdown-footer text-center">
          <a href="actions.html" class="text-decoration-none">Open Decision Inbox</a>
        </div>
      </div>
    </div>
    <div class="navbar-item navbar-user dropdown">
      <a href="#" class="navbar-link dropdown-toggle d-flex align-items-center" data-bs-toggle="dropdown">
        <div class="d-flex align-items-center justify-content-center rounded-circle bg-theme text-white fw-bold me-1"
             style="width:32px;height:32px;font-size:13px;">${initial}</div>
        <span>
          <span class="d-none d-md-inline fw-bold">${email || 'Manager'}</span>
          <b class="caret"></b>
        </span>
      </a>
      <div class="dropdown-menu dropdown-menu-end me-1">
        <a href="settings.html" class="dropdown-item">Settings</a>
        <div class="dropdown-divider"></div>
        <a href="javascript:;" class="dropdown-item" onclick="logout()">Log Out</a>
      </div>
    </div>
  </div>
</div>`;

  const sidebar = `
<div id="sidebar" class="app-sidebar" data-bs-theme="dark">
  <div class="app-sidebar-content" data-scrollbar="true" data-height="100%">
    <div class="menu">
      <div class="menu-profile">
        <a href="javascript:;" class="menu-profile-link">
          <div class="menu-profile-cover with-shadow"></div>
          <div class="menu-profile-image">
            <div class="d-flex align-items-center justify-content-center rounded-circle bg-theme text-white fw-bold"
                 style="width:45px;height:45px;font-size:18px;">${initial}</div>
          </div>
          <div class="menu-profile-info">
            <div class="d-flex align-items-center">
              <div class="flex-grow-1">Manager</div>
            </div>
            <small class="text-truncate d-block" style="max-width:140px;">${email || ''}</small>
          </div>
        </a>
      </div>
      ${renderMenuSections(activeNav)}
      <div class="menu-header">Account</div>
      <div class="menu-item">
        <a href="javascript:;" class="menu-link" onclick="logout()">
          <div class="menu-icon"><i class="bi bi-box-arrow-right"></i></div>
          <div class="menu-text">Logout</div>
        </a>
      </div>
    </div>
  </div>
</div>`;

  const appEl = document.getElementById('app');
  if (appEl) {
    appEl.insertAdjacentHTML('afterbegin', sidebar + header);
    appEl.insertAdjacentHTML('beforeend', THEME_PANEL + SCROLL_TOP_BTN);
  }

  // Load notification alerts after layout injected
  _loadNotifications();
}

function navItem(href, icon, label, activeNav) {
  const built  = BUILT_PAGES.has(href);
  const target = built ? href : `coming-soon.html?title=${encodeURIComponent(label)}`;
  const active = (activeNav === href) ? ' active' : '';
  const soon   = built ? '' : `<span class="badge bg-white bg-opacity-10 text-white-50 rounded-pill ms-auto" style="font-size:9px;">soon</span>`;
  return `
  <div class="menu-item${active}">
    <a href="${target}" class="menu-link">
      <div class="menu-icon"><i class="${icon}"></i></div>
      <div class="menu-text d-flex align-items-center w-100">${label}${soon}</div>
    </a>
  </div>`;
}

function renderMenuSections(activeNav) {
  return MENU_MODEL.map(sec => {
    const items = sec.items.map(([href, icon, label]) => navItem(href, icon, label, activeNav)).join('');
    return `<div class="menu-header">${sec.header}</div>${items}`;
  }).join('');
}

function renderPageTableShell({ title, subtitle = '', tableId, columns = [], toolbarHtml = '', footerHtml = '' }) {
  return `
    <div class="panel panel-inverse">
      <div class="panel-heading">
        <div class="panel-heading-btn">
          <a href="javascript:;" class="btn btn-xs btn-icon btn-circle btn-default" data-toggle="panel-expand"><i class="bi bi-arrows-fullscreen"></i></a>
          <a href="javascript:;" class="btn btn-xs btn-icon btn-circle btn-warning" data-toggle="panel-collapse"><i class="bi bi-dash-lg"></i></a>
        </div>
        <h4 class="panel-title">${title}${subtitle ? ` <small>${subtitle}</small>` : ''}</h4>
      </div>
      ${toolbarHtml ? `<div class="panel-toolbar">${toolbarHtml}</div>` : ''}
      <div class="panel-body p-0">
        <div class="table-responsive">
          <table id="${tableId}" class="table table-hover table-panel text-nowrap align-middle mb-0 w-100">
            <thead><tr>${columns.map(c => `<th${c.width ? ` style="width:${c.width};"` : ''}>${c.label}</th>`).join('')}</tr></thead>
            <tbody></tbody>
          </table>
        </div>
      </div>
      ${footerHtml ? `<div class="panel-footer">${footerHtml}</div>` : ''}
    </div>`;
}

function logout() {
  API.clearTokenCache();
  fetch('/auth/logout', { credentials: 'same-origin' }).catch(() => {});
  sessionStorage.removeItem('skilledge_manager_email');
  sessionStorage.removeItem('skilledge_team');
  sessionStorage.removeItem('skilledge_reportee_count');
  location.href = 'login.html';
}

// Load bell notifications from the unified manager-intelligence payload.
// Surfaces every actionable signal type — critical/high decisions, open
// review flags, blocked allocations, certification gaps, plus a data-freshness
// alert — as SeanTheme media-list notification rows. Self-contained: it fetches
// the per-manager payload (served from the server cache) so the bell works on
// every page regardless of what that page has independently loaded.
async function _loadNotifications() {
  const badge = document.getElementById('notifBadge');
  const list  = document.getElementById('notifList');
  if (!badge || !list) return;
  try {
    if (!API.getManagerEmail || !API.getManagerEmail()) return;
    const data = await API.getUnifiedManagerIntelligence();
    const rows = _buildManagerNotifications(data);
    badge.textContent = rows.length;
    badge.style.display = rows.length ? '' : 'none';
    if (!rows.length) {
      list.innerHTML = '<div class="dropdown-item text-muted small py-3 text-center"><i class="bi bi-check2-circle text-success me-1"></i>All clear — nothing needs attention</div>';
      return;
    }
    list.innerHTML = rows.map(r => `
      <a href="${r.href}" class="dropdown-item media">
        <div class="media-left"><i class="bi ${r.icon} media-object ${r.tone}"></i></div>
        <div class="media-body">
          <h6 class="media-heading">${r.heading}</h6>
          <div class="text-muted fs-10px">${r.sub}</div>
        </div>
      </a>`).join('');
  } catch (_) { /* silent — bell keeps its placeholder rather than hard-failing */ }
}

// Turn a unified payload into a typed list of manager notifications. Reads only
// fields already computed by the backend (decision objects, classification,
// freshness metadata) — it invents no new signals and only shows a category
// when it actually has something to report.
function _buildManagerNotifications(d) {
  const low = s => String(s == null ? '' : s).toLowerCase();
  const rows = [];
  if (!d || typeof d !== 'object') return rows;
  const trainers = Array.isArray(d.trainer_operations_df) ? d.trainer_operations_df : [];
  const actions = (Array.isArray(d.manager_action_objects) && d.manager_action_objects.length)
    ? d.manager_action_objects
    : (Array.isArray(d.manager_action_df) ? d.manager_action_df : []);

  const hot = actions.filter(a => /critical|high/.test(low(a.priority)) && !/done|closed|resolved/.test(low(a.status))).length;
  if (hot) rows.push({ icon: 'bi-fire', tone: 'bg-danger', href: 'actions.html',
    heading: `${hot} decision${hot === 1 ? '' : 's'} need attention`, sub: 'Critical &amp; high-priority items in the Decision Inbox' });

  const flags = trainers.filter(t => t.classification && t.classification.review_flag_type && t.classification.review_flag_type !== 'none').length;
  if (flags) rows.push({ icon: 'bi-flag-fill', tone: 'bg-warning', href: 'risk-takers.html',
    heading: `${flags} review flag${flags === 1 ? '' : 's'} open`, sub: 'Compliance flags &amp; delivery concerns to review' });

  const alloc = Array.isArray(d.allocation_decision_objects) ? d.allocation_decision_objects : [];
  const blocked = alloc.filter(a => a.blocker || low(a.allocation_status) === 'blocked').length;
  if (blocked) rows.push({ icon: 'bi-slash-circle-fill', tone: 'bg-orange', href: 'allocation-desk.html',
    heading: `${blocked} allocation${blocked === 1 ? '' : 's'} blocked`, sub: 'Courses that cannot be assigned yet' });

  const gaps = Array.isArray(d.certification_gap_df) ? d.certification_gap_df.length : 0;
  if (gaps) rows.push({ icon: 'bi-award-fill', tone: 'bg-indigo', href: 'certifications.html',
    heading: `${gaps} certification gap${gaps === 1 ? '' : 's'}`, sub: 'Trainers missing required OEM accreditation' });

  const status = d.cache_status || 'live';
  if (status !== 'live') {
    const failed = status === 'refresh_failed';
    const age = d.cache_age_minutes != null ? ` (${Math.round(d.cache_age_minutes)} min old)` : '';
    rows.push({
      icon: failed ? 'bi-exclamation-octagon-fill' : 'bi-hourglass-split',
      tone: failed ? 'bg-danger' : 'bg-secondary', href: 'data-health.html',
      heading: failed ? 'Live refresh failed' : `Showing cached data${age}`,
      sub: failed ? 'Serving the last good snapshot — open Data Health' : 'RMS not reachable for a live refresh right now',
    });
  }
  return rows;
}

function _intelScore(t) {
  const q  = avgScore(t.details);
  const c  = Math.min(100, certCount(t.vendorCerts) * 20);
  const f  = Math.max(0, 100 - negFbCount(t.negativeFeedback) * 20);
  const hr = t.hrIncidents ? (() => {
    const pos = parseInt((t.hrIncidents[0]||{}).PositiveCount||0);
    const neg = parseInt((t.hrIncidents[0]||{}).NegativeCount||0);
    return pos + neg > 0 ? (pos / (pos + neg)) * 100 : 100;
  })() : 100;
  if (q === null) return null;
  return q * 0.30 + c * 0.20 + f * 0.20 + 75 * 0.15 + hr * 0.15;
}

// ── Helpers ──────────────────────────────────────────────────────────────────

function showToast(msg, type = 'success') {
  const id  = 'toast_' + Date.now();
  const map = { success: 'bg-green', danger: 'bg-danger', warning: 'bg-warning text-dark', info: 'bg-blue' };
  const cls = map[type] || 'bg-green';
  const html = `
  <div id="${id}" class="position-fixed bottom-0 end-0 p-3" style="z-index:9999;">
    <div class="toast show align-items-center text-white ${cls} border-0" role="alert">
      <div class="d-flex">
        <div class="toast-body">${msg}</div>
        <button type="button" class="btn-close btn-close-white me-2 m-auto" onclick="document.getElementById('${id}').remove()"></button>
      </div>
    </div>
  </div>`;
  document.body.insertAdjacentHTML('beforeend', html);
  setTimeout(() => { const el = document.getElementById(id); if (el) el.remove(); }, 4000);
}

function showLoader(containerId, msg = 'Loading…') {
  const el = document.getElementById(containerId);
  if (el) el.innerHTML = `
    <div class="text-center py-5 text-muted">
      <div class="spinner-border text-theme mb-3" style="width:3rem;height:3rem;"></div>
      <div>${msg}</div>
    </div>`;
}

function scoreColor(score) {
  if (score >= 80) return 'text-success';
  if (score >= 60) return 'text-warning';
  return 'text-danger';
}

function scoreBadge(score) {
  if (score === null || score === undefined || score === '') return '<span class="badge bg-secondary">N/A</span>';
  const n   = parseFloat(score);
  const cls = n >= 80 ? 'bg-success' : n >= 60 ? 'bg-warning' : 'bg-danger';
  return `<span class="badge ${cls}">${n.toFixed(1)}%</span>`;
}

function progressBar(pct, cls = 'bg-theme') {
  const p = Math.min(100, Math.max(0, parseFloat(pct) || 0));
  return `<div class="progress" style="height:6px;">
    <div class="progress-bar ${cls}" style="width:${p}%"></div>
  </div>`;
}

function avgScore(details) {
  if (!Array.isArray(details) || !details.length) return null;
  const scores = details.map(d => parseFloat(d.QubitsScore)).filter(n => !isNaN(n));
  if (!scores.length) return null;
  return scores.reduce((a, b) => a + b, 0) / scores.length;
}

function certCount(vendorCerts) {
  if (!Array.isArray(vendorCerts)) return 0;
  return vendorCerts.length;
}

function negFbCount(negativeFeedback) {
  if (!Array.isArray(negativeFeedback)) return 0;
  return negativeFeedback.reduce((s, r) => s + (parseInt(r.Total) || 0), 0);
}

function fmtDate(d) {
  if (!d) return '—';
  try { return new Date(d).toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' }); }
  catch { return d; }
}

function cacheTeam(data) { sessionStorage.setItem('skilledge_team', JSON.stringify(data)); }
function getCachedTeam() {
  try { return JSON.parse(sessionStorage.getItem('skilledge_team') || 'null'); } catch { return null; }
}

// ── Shared KPI strip + dataset coverage renderer ─────────────────────────────
// One rendering path for every page's KPI row instead of a copy-pasted
// template literal per page, and one place to compute the "Dataset coverage"
// table instead of the identical block duplicated across every Data
// Confidence modal.
window.SkillEdge = window.SkillEdge || {};

function _skEsc(v) {
  return String(v == null ? '' : v).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}

// kpis: array of { title, value, subtitle, icon, tone, href, onClick, tooltip }.
// onClick is a real function reference (event delegated after render), not the
// former `window.KPI_ACTIONS[i]` global-array indirection every page repeated.
// options: { colClass, loading, loadingMessage, emptyMessage }
window.SkillEdge.renderKpiStrip = function (container, kpis, options = {}) {
  const el = typeof container === 'string' ? document.getElementById(container) : container;
  if (!el) return;
  const colClass = options.colClass || 'col-xl-3 col-md-6';
  if (options.loading) {
    el.innerHTML = `<div class="col-12 text-muted small py-3">${_skEsc(options.loadingMessage || 'Loading…')}</div>`;
    return;
  }
  if (!Array.isArray(kpis) || !kpis.length) {
    el.innerHTML = `<div class="col-12 text-muted small py-3">${_skEsc(options.emptyMessage || 'No KPI data available.')}</div>`;
    return;
  }
  el.innerHTML = kpis.map((k, i) => {
    const tone = k.tone || 'bg-teal';
    const icon = k.icon || 'bi-graph-up';
    const subtitle = k.subtitle != null ? k.subtitle : 'open drill-down';
    const tooltipAttr = k.tooltip ? ` title="${_skEsc(k.tooltip)}"` : '';
    const inner = `<div class="stats-icon stats-icon-lg"><i class="bi ${icon}"></i></div><div class="stats-content"><div class="stats-title">${_skEsc(k.title)}</div><div class="stats-number">${_skEsc(k.value)}</div><div class="stats-desc">${_skEsc(subtitle)}</div></div>`;
    if (k.href) {
      return `<div class="${colClass}"><a href="${k.href}" class="text-decoration-none"${tooltipAttr}><div class="widget widget-stats mini-kpi ${tone}">${inner}</div></a></div>`;
    }
    const clickable = typeof k.onClick === 'function';
    return `<div class="${colClass}"><div class="widget widget-stats mini-kpi ${tone}"${clickable ? ' style="cursor:pointer"' : ''} data-kpi-idx="${i}"${tooltipAttr}>${inner}</div></div>`;
  }).join('');
  kpis.forEach((k, i) => {
    if (typeof k.onClick === 'function') {
      const node = el.querySelector(`[data-kpi-idx="${i}"]`);
      if (node) node.addEventListener('click', k.onClick);
    }
  });
};

// requiredKeys: page-specific list of unified-payload dataset / decision-object
// keys to check for presence (this list legitimately differs per page — each
// page depends on a different slice of the payload — only the rendering was
// duplicated, so that's the only part centralized here). Returns the
// "Dataset coverage" heading + table HTML.
window.SkillEdge.renderDatasetCoverage = function (requiredKeys, data) {
  const status = (requiredKeys || []).map(key => {
    const rows = Array.isArray(data && data[key]) ? data[key] : null;
    const state = rows === null ? 'missing' : rows.length ? 'ready' : 'partial';
    return { key, count: rows ? rows.length : 0, state };
  });
  const stateBadge = s => s === 'ready'
    ? '<span class="badge bg-success">Ready</span>'
    : s === 'partial'
      ? '<span class="badge bg-warning text-dark">Partial (empty)</span>'
      : '<span class="badge bg-gray-500">Missing</span>';
  const rowsHtml = status.map(s => `<tr><td><code>${_skEsc(s.key)}</code></td><td>${s.count}</td><td>${stateBadge(s.state)}</td></tr>`).join('');
  const readyCount = status.filter(s => s.state === 'ready').length;
  return `
    <h6 class="fs-12px fw-bold text-uppercase text-muted mb-2">Dataset coverage (${readyCount}/${status.length} ready)</h6>
    <div class="table-responsive"><table class="table table-panel table-striped table-hover mb-0 fs-12px">
      <thead><tr><th>Dataset</th><th>Rows</th><th>Status</th></tr></thead>
      <tbody>${rowsHtml}</tbody>
    </table></div>`;
};

// ── Freshness badge / banner (Phase 0) ───────────────────────────────────────
// Reads the honest freshness metadata backend/app.py now attaches to every
// unified-payload response (cache_status/cache_age_minutes/last_refresh_*) —
// never recomputed client-side, only displayed.
const _FRESHNESS_INFO = {
  live:           { label: 'Live',           tone: 'success', icon: 'bi-broadcast' },
  cached:         { label: 'Cached',         tone: 'teal',    icon: 'bi-hdd-stack-fill' },
  stale:          { label: 'Stale',          tone: 'warning', icon: 'bi-hourglass-split' },
  refresh_failed: { label: 'Refresh Failed', tone: 'danger',  icon: 'bi-exclamation-octagon-fill' },
  demo:           { label: 'Demo',           tone: 'purple',  icon: 'bi-flask' },
};

// Small persistent badge (header/KPI-strip area) — always renders, even when live.
window.SkillEdge.renderFreshnessBadge = function (container, data) {
  const el = typeof container === 'string' ? document.getElementById(container) : container;
  if (!el) return;
  const status = (data && data.cache_status) || 'live';
  const info = _FRESHNESS_INFO[status] || _FRESHNESS_INFO.live;
  const ageText = (data && data.cache_age_minutes != null) ? ` &middot; ${data.cache_age_minutes} min old` : '';
  el.innerHTML = `<span class="badge bg-${info.tone}${info.tone === 'warning' ? ' text-dark' : ''} fs-11px" title="Data freshness: ${_skEsc(status)}"><i class="bi ${info.icon} me-1"></i>${_skEsc(info.label)}${ageText}</span>`;
};

// Larger dismissable-by-navigation banner — only renders (and only takes
// space) when data is NOT live, so it never clutters the common case.
window.SkillEdge.renderFreshnessBanner = function (container, data) {
  const el = typeof container === 'string' ? document.getElementById(container) : container;
  if (!el) return;
  const status = (data && data.cache_status) || 'live';
  if (status === 'live') { el.innerHTML = ''; el.style.display = 'none'; return; }
  el.style.display = '';
  const toneClass = status === 'refresh_failed' ? 'alert-danger' : status === 'stale' ? 'alert-warning' : 'alert-secondary';
  const successAt = (data && data.last_refresh_success_at) ? new Date(data.last_refresh_success_at).toLocaleString() : 'Not available';
  const ageText = (data && data.cache_age_minutes != null) ? ` (${data.cache_age_minutes} min ago)` : '';
  const label = status === 'refresh_failed' ? 'Refresh failed — showing cached data' : status === 'stale' ? 'Data is stale — showing cached data' : 'Showing cached data';
  const errorLine = (data && data.last_refresh_error) ? `<div class="mt-1"><b>Last error:</b> ${_skEsc(data.last_refresh_error)}</div>` : '';
  el.innerHTML = `<div class="alert ${toneClass} py-2 fs-12px mb-2"><b>${_skEsc(label)}.</b> Last successful refresh: ${_skEsc(successAt)}${ageText}.${errorLine}</div>`;
};

// ── SweetAlert-based lifecycle dialogs (Phase 0 — replaces window.prompt()) ──
// The bundled plugin (frontend/assets/plugins/sweetalert) is the t4t5/sweetalert
// v2 fork: `swal(opts)` returns a Promise, custom content is passed via
// `content` (a DOM element or `{element, attributes}`), and buttons are passed
// as a `[cancelLabel, confirmLabel]` array — it resolves to the input's value
// on confirm, or `null` on cancel/dismiss. If the plugin isn't loaded on a
// page for some reason, degrade to window.confirm() (never to window.prompt()
// again) so nothing hard-breaks.
window.SkillEdge.promptLifecycleNote = async function (opts) {
  opts = opts || {};
  if (typeof swal !== 'function') {
    const ok = window.confirm(opts.text || opts.title || 'Confirm?');
    return ok ? { confirmed: true, note: '' } : { confirmed: false };
  }
  const value = await swal({
    title: opts.title || 'Confirm',
    text: opts.text || '',
    content: {
      element: 'input',
      attributes: { placeholder: opts.placeholder || 'Optional note', type: 'text' },
    },
    buttons: ['Cancel', opts.confirmText || 'Confirm'],
  });
  if (value === null || value === undefined) return { confirmed: false };
  return { confirmed: true, note: value || '' };
};

// Reassign collects an assignee (required) and an optional note in one
// dialog: a required "assignee" input plus an optional note textarea, built
// as a small DOM fragment passed via `content`. Re-prompts in place (via a
// visible inline error) if the assignee is left blank instead of closing and
// re-opening the dialog.
window.SkillEdge.promptReassign = async function (opts) {
  opts = opts || {};
  if (typeof swal !== 'function') {
    const assignee = window.prompt('Reassign to (name or email):', '');
    if (!assignee) return { confirmed: false };
    const note = window.prompt('Optional note:', '') || '';
    return { confirmed: true, assignee, note };
  }

  const wrap = document.createElement('div');
  wrap.className = 'sk-reassign-fields text-left';
  wrap.innerHTML = `
    <label class="fs-12px mb-1 d-block">Assignee (name or email)</label>
    <input type="text" class="form-control form-control-sm sk-reassign-assignee" placeholder="Assignee (name or email)">
    <div class="sk-reassign-error text-danger fs-12px mt-1" style="display:none;">Assignee is required.</div>
    <label class="fs-12px mb-1 mt-2 d-block">Note (optional)</label>
    <input type="text" class="form-control form-control-sm sk-reassign-note" placeholder="Optional note">
  `;
  const assigneeInput = wrap.querySelector('.sk-reassign-assignee');
  const noteInput = wrap.querySelector('.sk-reassign-note');
  const errorLine = wrap.querySelector('.sk-reassign-error');

  while (true) {
    const confirmed = await swal({
      title: opts.title || 'Reassign',
      text: opts.text || 'Choose a new assignee.',
      content: wrap,
      buttons: ['Cancel', opts.confirmText || 'Reassign'],
    });
    if (!confirmed) return { confirmed: false };
    const assignee = (assigneeInput.value || '').trim();
    if (!assignee) {
      errorLine.style.display = 'block';
      continue;
    }
    return { confirmed: true, assignee, note: (noteInput.value || '').trim() };
  }
};
