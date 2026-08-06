/**
 * SkillEdge Manager Copilot — shared foundation (Phase 0 extraction).
 *
 * This is the exact rule-based, evidence-backed answer engine that used to
 * live only inside trainer-intelligence.html's answerTrainerQuestion() /
 * renderKnowledgeAssistant(). No new AI/backend logic here — it is the same
 * deterministic engine, moved so it can be mounted on other pages later
 * (Dashboard, Allocation Desk, Course Match, Data Health) without forking it.
 *
 * `answer(questionKey, context)` expects `context` in the same shape
 * trainer-intelligence.html's buildTrainerKnowledgeProfile() already builds
 * (trainer/state/decision/ready/near/blocked/allocations/risks/certGaps/
 * vendors/bestAction/sourceDatasets/dataMissing), plus an optional
 * `context.peers` array (replaces the page-global TRAINERS lookup the
 * original function read directly — this is what makes it portable).
 */
window.SkillEdgeCopilot = (function () {
  function esc(s) {
    return String(s == null ? '' : s).replace(/[&<>"]/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c]));
  }
  function num(v) {
    const n = parseFloat(v);
    return isNaN(n) ? null : n;
  }
  function decisionVersionOf(row) {
    return (row && row.decision_contract_version) || '';
  }

  // Suggested-question catalogue — identical set/order to the original page's
  // qmap, now the single source every host page reads from.
  const QUESTION_MAP = [
    ['can_assign_now', 'Can I assign now?'],
    ['can_deliver', 'What can deliver?'],
    ['weak_spot', 'Where weak?'],
    ['missing_certs', 'Which certification blocks this trainer?'],
    ['best_course', 'Best course fit?'],
    ['backup_trainers', 'Who are backup trainers?'],
    ['why_not_first', 'Why is not first choice?'],
    ['compare_trainer', 'Compare with another trainer'],
    ['what_if_oem', 'What if I move to another OEM?'],
    ['invest_oem', 'Which OEM should I invest in?'],
    ['explain_readiness', 'Explain readiness score'],
    ['explain_allocation', 'Explain allocation score'],
    ['why_low_confidence', 'Why is confidence low?'],
    ['next_action', 'What should I do next?'],
    ['missing_data', 'What data is missing?'],
  ];

  function answer(questionKey, context) {
    const p = context || {};
    const t = p.trainer || {};
    const s = p.state || {};
    const decision = p.decision || {};
    const confidence = decision.confidence ?? (p.bestAction && p.bestAction.confidence) ?? ((p.risks || []).length ? 60 : 80);
    const decVersion = decisionVersionOf(decision) || 'Not available';
    const addEvidence = (items = []) => items.filter(Boolean).slice(0, 4).map(x => `<div>• ${esc(x)}</div>`).join('');
    const confidenceLabel = confidence == null ? 'Not available' : `${Math.round(confidence)}%`;

    const ready = p.ready || [];
    const near = p.near || [];
    const blocked = p.blocked || [];
    const allocations = p.allocations || [];
    const risks = p.risks || [];
    const certGaps = p.certGaps || [];
    const vendors = p.vendors || [];
    const peers = p.peers || [];
    const sourceDatasets = p.sourceDatasets || [];
    const dataMissing = p.dataMissing || [];

    const canAssignNow = decision.can_assign_now != null
      ? !!decision.can_assign_now
      : ready.length > 0 && !(t.classification ? t.classification.has_compliance_flag : String(t.hr_risk || '').toLowerCase().includes('high'));
    const bestCourse = ready[0] || near[0] || allocations.slice().sort((a, b) => (b.allocation_score || 0) - (a.allocation_score || 0))[0];
    const backupNames = [...new Set([...(s.backup || []).map(b => b.backup_trainer_name), ...(s.backupFor || []).map(b => b.primary_trainer_name)])].filter(Boolean);
    const missingCerts = certGaps.map(g => g.course || g.vendor).filter(Boolean);
    const weakSignals = [
      ...blocked.slice(0, 2).map(r => `${r.course_name || 'Course'}: ${r.blocker || r.allocation_risk || 'not ready'}`),
      ...risks.slice(0, 2).map(r => `${r.title} (${r.severity})`),
    ];
    const selfKey = (t.official_email || t.trainer_email || t.trainer_key || '').toString().toLowerCase();
    const peer = peers
      .filter(x => (x.official_email || x.trainer_email || x.trainer_key || '').toString().toLowerCase() !== selfKey)
      .sort((a, b) => (num(b.overall_readiness_score) || 0) - (num(a.overall_readiness_score) || 0))[0];

    const answers = {
      can_assign_now: {
        answer: canAssignNow ? `Yes. This trainer can be assigned now for the best available fit.` : `No. This trainer should not be assigned as primary yet.`,
        evidence: addEvidence([bestCourse ? `Best fit: ${bestCourse.course_name} (${Math.round(bestCourse.allocation_score || 0)})` : null, decision.primary_blocker || (risks[0] && risks[0].title) || null, decision.next_manager_action || (p.bestAction && p.bestAction.recommended_action) || null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      can_deliver: {
        answer: ready.length ? `This trainer can deliver ${ready.slice(0, 3).map(r => r.course_name).join(', ')}.` : `This trainer has no clearly ready courses in the current payload.`,
        evidence: addEvidence([ready[0] ? `Ready now: ${ready[0].course_name}` : null, near[0] ? `Near ready: ${near[0].course_name}` : null, (p.bestAction && p.bestAction.linked) ? `Action linked to: ${p.bestAction.linked}` : null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      weak_spot: {
        answer: weakSignals.length ? `Weakest signals are ${weakSignals.join('; ')}.` : `No major weakness is visible in the current decision objects.`,
        evidence: addEvidence(weakSignals),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      missing_certs: {
        answer: missingCerts.length ? `Missing certifications: ${missingCerts.join(', ')}.` : `No certification gap is currently surfaced.`,
        evidence: addEvidence([certGaps[0] ? `${certGaps[0].vendor || 'Vendor'}: ${certGaps[0].course || 'Certification gap'}` : null, (s.certs && s.certs.oem_accreditations && s.certs.oem_accreditations.length) ? `Held accreditations: ${s.certs.oem_accreditations.filter(Boolean).join(' · ')}` : null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      best_course: {
        answer: bestCourse ? `Best course fit is ${bestCourse.course_name}.` : `No best course fit is available yet.`,
        evidence: addEvidence([bestCourse ? `${bestCourse.course_name} (${Math.round(bestCourse.allocation_score || 0)})` : null, bestCourse && (bestCourse.why_selected || bestCourse.reason) || null, bestCourse && (bestCourse.linked_cert || bestCourse.linked_course) || null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      backup_trainers: {
        answer: backupNames.length ? `Backup trainers: ${backupNames.join(', ')}.` : `No backup trainer is identified yet.`,
        evidence: addEvidence([(s.backup && s.backup[0]) ? `Primary backup: ${s.backup[0].backup_trainer_name || 'Not named'}` : null, (s.backupFor && s.backupFor.length) ? `This trainer backs up ${s.backupFor.length} course(s)` : null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      why_not_first: {
        answer: decision.primary_blocker ? `This trainer is not first choice because ${decision.primary_blocker}.` : `This trainer is not first choice because the current fit, risk, or blocker profile is weaker than the best alternative.`,
        evidence: addEvidence([decision.primary_blocker || (risks[0] && risks[0].title) || null, bestCourse ? `Best fit: ${bestCourse.course_name}` : null, peer ? `Nearest peer: ${peer.trainer_name} (${Math.round(num(peer.overall_readiness_score) || 0)})` : null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      compare_trainer: {
        answer: peer ? `Compared with ${peer.trainer_name}, this trainer is ${Math.round(num(t.overall_readiness_score) || 0)} readiness vs ${Math.round(num(peer.overall_readiness_score) || 0)} readiness.` : `No peer comparison is available.`,
        evidence: addEvidence([peer ? `${peer.trainer_name}: readiness ${Math.round(num(peer.overall_readiness_score) || 0)}` : null, `This trainer: readiness ${Math.round(num(t.overall_readiness_score) || 0)}`]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      what_if_oem: {
        answer: `If you move this trainer to another OEM, the likely impact is governed by vendor strength, certification gaps, and roadmap coverage for that OEM.`,
        evidence: addEvidence([vendors[0] ? `Strongest vendor: ${vendors[0].vendor}` : null, certGaps[0] ? `Gap: ${certGaps[0].vendor || certGaps[0].course}` : null, (s.certs && s.certs.oem_accreditations && s.certs.oem_accreditations.length) ? `Current accreditations: ${s.certs.oem_accreditations.filter(Boolean).join(' · ')}` : null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      invest_oem: {
        answer: vendors[0] ? `Invest in ${vendors[0].vendor} first because it is the strongest visible vendor for this trainer.` : `No vendor strength is visible yet for this trainer.`,
        evidence: addEvidence([vendors[0] ? `${vendors[0].vendor} score ${vendors[0].score}` : null, vendors[1] ? `Second vendor: ${vendors[1].vendor}` : null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      explain_readiness: {
        answer: `Readiness reflects allocation fit, delivery signals, risks, and decision-object readiness state where available.`,
        evidence: addEvidence([decision.readiness_status || t.readiness_bucket || null, bestCourse ? `Best fit course: ${bestCourse.course_name}` : null, risks[0] ? `Top risk: ${risks[0].title}` : null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      explain_allocation: {
        answer: `Allocation score is the course-fit signal used to decide whether this trainer can be assigned now, needs prep, or should be blocked.`,
        evidence: addEvidence([bestCourse ? `${bestCourse.course_name}: ${Math.round(bestCourse.allocation_score || 0)}` : null, decision.can_assign_now != null ? `can_assign_now=${decision.can_assign_now}` : null, decision.primary_blocker || (risks[0] && risks[0].title) || null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      why_low_confidence: {
        answer: `Confidence is lower when decision objects, source rows, or supporting evidence are missing or when multiple signals conflict.`,
        evidence: addEvidence([dataMissing.length ? `Missing: ${dataMissing.join(', ')}` : null, sourceDatasets.length ? `Sources: ${sourceDatasets.join(', ')}` : null, decVersion !== 'Not available' ? `Decision version ${decVersion}` : null]),
        source: sourceDatasets, confidence: 'Low', decisionVersion: decVersion,
      },
      biggest_risk: {
        answer: risks.length ? `Biggest risk: ${risks[0].title}.` : `No active risk is currently flagged.`,
        evidence: addEvidence([risks[0] ? `${risks[0].why}` : null, risks[0] ? `Mitigation: ${risks[0].mitigation}` : null, risks[0] && risks[0].evidence || null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      next_action: {
        answer: p.bestAction ? `Next action: ${p.bestAction.recommended_action || p.bestAction.action_type}.` : `No action is currently open.`,
        evidence: addEvidence([p.bestAction ? `${p.bestAction.category} · ${p.bestAction.priority}` : null, p.bestAction && p.bestAction.reason || null, p.bestAction && p.bestAction.linked || null]),
        source: sourceDatasets, confidence: confidenceLabel, decisionVersion: decVersion,
      },
      missing_data: {
        answer: dataMissing.length ? `Missing data: ${dataMissing.join(', ')}.` : `No major data gaps were found from the current payload.`,
        evidence: addEvidence(dataMissing),
        source: sourceDatasets, confidence: 'Low', decisionVersion: decVersion,
      },
    };
    return answers[questionKey] || answers.next_action;
  }

  // Returns the suggested-question catalogue as {key,label} objects — a host
  // page can render these as chips/buttons. Context-aware prioritization can
  // be added later without changing this signature.
  function suggestPrompts(context) {
    return QUESTION_MAP.map(([key, label]) => ({ key, label }));
  }

  // Self-contained evidence-card block for a given answer object (as
  // returned by `answer()`) — confidence badge, decision version, source
  // dataset, and the evidence bullet list.
  function buildEvidenceCards(ans) {
    if (!ans) return '';
    const confStr = String(ans.confidence);
    const pctMatch = /^(\d+)%$/.exec(confStr);
    const confidenceTone = pctMatch
      ? (parseInt(pctMatch[1], 10) >= 90 ? 'green' : parseInt(pctMatch[1], 10) >= 70 ? 'yellow text-dark' : 'red')
      : (confStr === 'Low' ? 'red' : 'teal');
    const sources = Array.isArray(ans.source) ? ans.source : [];
    return `
      <div class="sk-copilot-evidence">
        <div class="d-flex flex-wrap gap-1 align-items-center mb-1">
          <span class="badge bg-${confidenceTone}">Confidence ${esc(ans.confidence)}</span>
          ${ans.decisionVersion ? `<span class="badge bg-gray-500">Decision v${esc(ans.decisionVersion)}</span>` : ''}
          ${sources.length ? `<span class="badge bg-gray-500">Source: ${esc(sources[0])}${sources.length > 1 ? ` +${sources.length - 1}` : ''}</span>` : ''}
        </div>
        <div class="fs-12px">${ans.evidence || '<span class="text-muted">No supporting evidence rows found.</span>'}</div>
      </div>`;
  }

  // Generic, self-contained panel renderer — for pages that don't already
  // have their own bespoke chat layout (Dashboard, Allocation Desk, Course
  // Match, Data Health — none of these are wired yet, this is the shared
  // foundation those pages will call into). container: element or id.
  // options: { getContext: () => contextObject, initialQuestionKey, onAsk }.
  function render(container, options) {
    const el = typeof container === 'string' ? document.getElementById(container) : container;
    if (!el || !options || typeof options.getContext !== 'function') return null;
    let currentKey = options.initialQuestionKey || QUESTION_MAP[0][0];

    function paint() {
      const ctx = options.getContext();
      const ans = answer(currentKey, ctx);
      const label = (QUESTION_MAP.find(([k]) => k === currentKey) || [null, 'Question'])[1];
      el.innerHTML = `
        <div class="sk-copilot-panel">
          <div class="ti-chat-suggest mb-2" data-role="prompts">
            ${QUESTION_MAP.map(([key, lbl]) => `<button type="button" class="btn btn-xs ${key === currentKey ? 'btn-theme' : 'btn-outline-theme'}" data-copilot-ask="${key}">${esc(lbl)}</button>`).join('')}
          </div>
          <div class="ti-qa-answer" data-role="answer">
            <div class="d-flex justify-content-between align-items-start gap-2 mb-1">
              <span class="fw-bold fs-11px">${esc(label)}</span>
            </div>
            <div class="mb-1">${ans.answer}</div>
            ${buildEvidenceCards(ans)}
          </div>
        </div>`;
      el.querySelectorAll('[data-copilot-ask]').forEach(btn => {
        btn.addEventListener('click', () => {
          currentKey = btn.getAttribute('data-copilot-ask');
          paint();
          if (typeof options.onAsk === 'function') options.onAsk(currentKey);
        });
      });
    }
    paint();
    return { refresh: paint, setQuestion: key => { currentKey = key; paint(); } };
  }

  return { answer, render, buildEvidenceCards, suggestPrompts };
})();
