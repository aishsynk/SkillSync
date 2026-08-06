/**
 * SkillEdge Agent Copilot (Workstream 3) — server-backed chat + briefing.
 *
 * Unlike the client-side `manager-copilot.js` rule engine, this module talks to
 * the deterministic agent that runs server-side in backend/agentic:
 *   GET  /api/agent/briefing   → daily briefing (issues, opportunities, actions)
 *   POST /api/agent/ask       → natural-language Q&A against the live payload
 *   GET  /api/agent/learning  → learning-loop status
 *
 * Renders a self-contained chat panel and a briefing strip. Any host page can
 * mount them by calling `window.SkillEdgeAgent.mountChat(el, {...})`.
 */
window.SkillEdgeAgent = (function () {
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }
  function toneFor(conf) {
    const n = parseFloat(conf);
    if (isNaN(n)) return 'bg-gray-500';
    return n >= 90 ? 'bg-green' : n >= 70 ? 'bg-warning text-dark' : 'bg-red';
  }
  function inlineMarkdown(text) {
    return String(text || '')
      .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
      .replace(/\*\*(.+?)\*\*/g, '<b>$1</b>')
      .replace(/`([^`]+)`/g, '<code>$1</code>');
  }

  const SUGGESTIONS = [
    'how is my team doing',
    'who is free right now',
    'what should I do today',
    'what blockers exist',
    'certification gaps',
    'any unallocated demand',
  ];

  // ── Chat panel ──────────────────────────────────────────────────────────────
  function mountChat(container, opts) {
    const el = typeof container === 'string' ? document.getElementById(container) : container;
    if (!el) return null;
    const onError = opts.onError || (() => {});
    el.innerHTML = `
      <div class="sk-agent-chat d-flex flex-column" style="min-height:300px;">
        <div class="sk-agent-log p-3" data-role="log" style="flex:1;max-height:340px;overflow:auto;background:#0b1622;border-radius:8px;">
          <div class="text-gray-500 fs-12px mb-2">Ask anything about your team, readiness, demand, or risks. Answers are evidence-backed from your live intelligence payload.</div>
        </div>
        <div class="ti-chat-suggest my-2" data-role="suggest"></div>
        <div class="input-group input-group-sm">
          <input type="text" class="form-control" data-role="q" placeholder="e.g. who can teach Power BI?" autocomplete="off" />
          <button class="btn btn-theme" data-role="send" type="button"><i class="bi bi-send me-1"></i>Ask</button>
        </div>
      </div>`;
    const log = el.querySelector('[data-role="log"]');
    const input = el.querySelector('[data-role="q"]');
    const send = el.querySelector('[data-role="send"]');
    const suggestBox = el.querySelector('[data-role="suggest"]');
    suggestBox.innerHTML = SUGGESTIONS.map(s => `<button type="button" class="btn btn-xs btn-outline-theme me-1 mb-1" data-s="1">${esc(s)}</button>`).join('');
    suggestBox.querySelectorAll('[data-s]').forEach(b => b.addEventListener('click', () => ask(b.textContent)));
    send.addEventListener('click', () => ask(input.value));
    input.addEventListener('keydown', e => { if (e.key === 'Enter') ask(input.value); });

    function bubble(html, cls) {
      const row = document.createElement('div');
      row.className = `mb-2 fs-12px ${cls || ''}`;
      row.innerHTML = html;
      log.appendChild(row);
      log.scrollTop = log.scrollHeight;
    }
    function ask(text) {
      const q = String(text || '').trim();
      if (!q) return;
      input.value = '';
      bubble(`<div class="text-end"><span class="badge bg-indigo">${esc(q)}</span></div>`);
      bubble(`<div class="text-gray-500"><span class="spinner-border spinner-border-sm me-1"></span>Consulting team data…</div>`);
      API.agentAsk(q)
        .then(r => {
          log.querySelector(':scope > div:last-child').remove();
          bubble(`
            <div class="sk-agent-answer" style="background:#132130;border-radius:8px;padding:10px 12px;">
              <div class="d-flex flex-wrap gap-1 align-items-center mb-1">
                <span class="badge ${toneFor(r.confidence)}">Confidence ${esc(r.confidence)}%</span>
                <span class="badge bg-gray-500">intent: ${esc(r.intent)}</span>
                <span class="badge bg-gray-500">tool: ${esc(r.tool_used)}</span>
              </div>
              <div class="text-white">${inlineMarkdown(r.answer)}</div>
              ${(r.sources && r.sources.length) ? `<div class="text-gray-500 fs-11px mt-1">Sources: ${esc(r.sources.join(', '))}</div>` : ''}
            </div>`);
        })
        .catch(e => {
          log.querySelector(':scope > div:last-child').remove();
          bubble(`<div class="badge bg-danger">Error: ${esc(e.message || e)}</div>`);
          if (API.isAuthError(e)) { onError(); API.handleAuthFailure(); }
        });
    }
    return { ask };
  }

  // ── Briefing strip ──────────────────────────────────────────────────────────
  function mountBriefing(container, opts) {
    const el = typeof container === 'string' ? document.getElementById(container) : container;
    if (!el) return null;
    const onError = opts.onError || (() => {});
    function paint(data) {
      const issues = data.issues || [];
      const opps = data.opportunities || [];
      const actions = data.next_actions || [];
      const tone = p => ({ Critical: 'bg-danger', High: 'bg-danger', Medium: 'bg-warning text-dark', Low: 'bg-green' }[p] || 'bg-gray-500');
      el.innerHTML = `
        <div class="row g-2">
          <div class="col-xl-4">
            <div class="border rounded p-2 h-100">
              <div class="fs-11px text-uppercase text-muted mb-1"><i class="bi bi-activity me-1"></i>Watch</div>
              ${issues.length ? issues.map(i => `<div class="d-flex align-items-start gap-2 mb-1"><span class="badge ${tone(i.priority)}">${esc(i.priority)}</span><span class="fs-12px">${inlineMarkdown(i.text)}</span></div>`).join('') : '<div class="text-muted fs-12px">No flagged issues.</div>'}
            </div>
          </div>
          <div class="col-xl-4">
            <div class="border rounded p-2 h-100">
              <div class="fs-11px text-uppercase text-muted mb-1"><i class="bi bi-lightning me-1"></i>Opportunities</div>
              ${opps.length ? opps.map(o => `<div class="fs-12px mb-1"><i class="bi bi-plus-circle text-teal me-1"></i>${inlineMarkdown(o.text)}</div>`).join('') : '<div class="text-muted fs-12px">None detected.</div>'}
            </div>
          </div>
          <div class="col-xl-4">
            <div class="border rounded p-2 h-100">
              <div class="fs-11px text-uppercase text-muted mb-1"><i class="bi bi-list-check me-1"></i>Next actions</div>
              ${actions.length ? actions.slice(0, 5).map(a => `<div class="d-flex align-items-start gap-2 mb-1"><span class="badge ${tone(a.priority)}">${esc(a.priority)}</span><a class="fs-12px text-body text-decoration-none" href="actions.html">${esc(a.action_type)} · ${esc(a.trainer_name)}</a></div>`).join('') : '<div class="text-muted fs-12px">Nothing open.</div>'}
            </div>
          </div>
        </div>`;
    }
    return API.agentBriefing()
      .then(data => { paint(data); return data; })
      .catch(e => {
        el.innerHTML = `<div class="text-muted fs-12px"><i class="bi bi-info-circle me-1"></i>Briefing unavailable: ${esc(e.message || e)}</div>`;
        if (API.isAuthError(e)) onError();
        return null;
      });
  }

  return { mountChat, mountBriefing, SUGGESTIONS };
})();
