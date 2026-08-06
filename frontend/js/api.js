/**
 * SkillEdge API Client
 * All Koenig Solutions API calls go through the SERVER at /rms/<api>.
 * Credentials live server-side only and are NEVER shipped to the browser.
 * Team data is cached for 4 hours so page navigation never re-hits the API.
 *
 * CONFIRMED RESPONSE FIELD NAMES (from official API instruction sheets):
 *   Reportees    : TrainerName, TrainerId, EmpId, OffEmail, TrainerPlus, IsdirectReportee, Designation
 *   TrainerDetails: CourseName, VendorName, QubitsScore, SkillLevel, OfficiallyApproved,
 *                   "Is Future Skill", "Future Skill Date", DM, techcallrating,
 *                   "Course Assignment", RoamingOffDates, InternationaRoamingOffDates,
 *                   NightILOffDates, MorningILOffDates, EveningILOffDates
 *   TrainerSkills : employee_name, employee_code, course_id, course_name,
 *                   is_duplicate_course, is_discontinue_course
 *   NegFeedback  : Trainer, Email, Total
 *   HRIncidents  : Trainer, EmailId, "Positive Count", "Negative Count"  ← spaces in field names!
 *   AssignmentAPI: AssignmentID, CourseID, Course, TrainerName, OffEmailId
 *   CourseList   : Course, Courseid, vendor_name, vendor_id, course_url
 */
const API = (() => {

  // No BASE/token/data endpoints and NO credentials in the browser anymore —
  // every RMS call is proxied server-side via POST /rms/<api>.
  const CACHE_TTL_MS    = 4 * 60 * 60 * 1000;   // 4 hours
  const TEAM_CACHE_KEY  = 'skilledge_team';
  const TEAM_TS_KEY     = 'skilledge_team_ts';
  const COURSE_CACHE_KEY= 'skilledge_courses';
  const INFLIGHT_TEAM_KEY = '__skilledge_team_inflight__';
  const INFLIGHT_COURSE_KEY = '__skilledge_course_inflight__';
  function teamCacheKey(email) {
    return `${TEAM_CACHE_KEY}_${String(email || getManagerEmail() || 'default').toLowerCase().replace(/[^a-z0-9]+/g, '_')}`;
  }
  function teamTsKey(email) {
    return `${TEAM_TS_KEY}_${String(email || getManagerEmail() || 'default').toLowerCase().replace(/[^a-z0-9]+/g, '_')}`;
  }

  // ── RMS access via the server (NO secrets in the browser) ───────────────────
  async function call(apiName, requestBody) {
    const res = await fetch('/rms/' + encodeURIComponent(apiName), {
      method:  'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body:    JSON.stringify(requestBody || {}),
    });
    let json = {};
    try { json = await res.json(); } catch {}
    if (res.status === 401 || res.status === 403) {
      throw makeAuthError(json.error || `HTTP ${res.status}`);
    }
    // Server already unwraps envelopes; content is a clean array/object.
    return json.content ?? [];
  }

  function clearTokenCache() {
    // Tokens are managed server-side now; clear any legacy client token cache.
    try {
      Object.keys(sessionStorage).forEach(k => {
        if (k.indexOf('skilledge_token_') === 0) sessionStorage.removeItem(k);
      });
    } catch {}
  }

  // ── Manager email ───────────────────────────────────────────────────────────
  function getManagerEmail()    { return sessionStorage.getItem('skilledge_manager_email') || ''; }
  function setManagerEmail(e)   { sessionStorage.setItem('skilledge_manager_email', e); }

  // ── Public API calls ────────────────────────────────────────────────────────
  async function getReportees(email) {
    return call('reportees', { email: email || getManagerEmail() });
  }
  async function getTrainerDetails(email) {
    return call('trainerDetails', { email });
  }
  async function getTrainerSkills(employeeId) {
    return call('trainerSkills', { employee_id: String(employeeId) });
  }
  async function getUtilization(email) {
    return call('utilization', { email });
  }
  async function getVendorCerts(email) {
    return call('vendorCerts', { email });
  }
  async function getNegativeFeedback(email) {
    return call('negativeFeedback', { email });
  }
  async function getHRIncidents(email) {
    return call('hrIncidents', { email });
  }
  async function getCourseList() {
    return call('courseList', {});
  }
  async function getAssignments(email, page = 1, pageSize = 100) {
    return call('assignments', {
      TrainerEmailAddres: email,
      PageNumber: String(page),
      PageSize:   String(pageSize),
    });
  }

  async function getUnifiedManagerIntelligence(opts = {}) {
    const email = opts.email || getManagerEmail();
    if (!email) throw new Error('No manager email in session');
    const params = new URLSearchParams({ email });
    if (opts.refresh) params.set('refresh', '1');
    if (opts.course_outline) params.set('course_outline', opts.course_outline);
    if (opts.course_title) params.set('course_title', opts.course_title);
    if (opts.course_vendor) params.set('course_vendor', opts.course_vendor);
    if (opts.course_domain) params.set('course_domain', opts.course_domain);
    if (opts.course_level) params.set('course_level', opts.course_level);
    if (opts.delivery_type) params.set('delivery_type', opts.delivery_type);
    const qs = `?${params.toString()}`;
    const res = await fetch('/data/unified-manager-intelligence' + qs, { cache: 'no-store', credentials: 'same-origin' });
    const data = await res.json();
    if (res.status === 401 || res.status === 403) {
      throw makeAuthError(data.error || `HTTP ${res.status}`);
    }
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function getRefreshStatus() {
    const res = await fetch('/api/refresh/status', { cache: 'no-store', credentials: 'same-origin' });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function runRefresh() {
    const res = await fetch('/api/refresh/run', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  // ── Action / review-flag lifecycle (Phase 4 persistence) ────────────────────
  async function updateActionState(actionId, verb, body) {
    const res = await fetch(`/api/actions/${encodeURIComponent(actionId)}/${verb}`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {}),
    });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 || res.status === 403) throw makeAuthError(data.error || `HTTP ${res.status}`);
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function updateReviewFlagState(flagId, verb, body) {
    const res = await fetch(`/api/review-flags/${encodeURIComponent(flagId)}/${verb}`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {}),
    });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 || res.status === 403) throw makeAuthError(data.error || `HTTP ${res.status}`);
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function searchKnowledge(q) {
    const url = '/api/knowledge/search?' + new URLSearchParams({ q: q || '' }).toString();
    const res = await fetch(url, { cache: 'no-store', credentials: 'same-origin' });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  // ── Agentic copilot (Workstream 3) ─────────────────────────────────────────
  async function agentAsk(question) {
    const res = await fetch('/api/agent/ask', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ question }),
    });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 || res.status === 403) throw makeAuthError(data.error || `HTTP ${res.status}`);
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function agentBriefing() {
    const res = await fetch('/api/agent/briefing', { cache: 'no-store', credentials: 'same-origin' });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 || res.status === 403) throw makeAuthError(data.error || `HTTP ${res.status}`);
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function learningStatus() {
    const res = await fetch('/api/agent/learning', { cache: 'no-store', credentials: 'same-origin' });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 || res.status === 403) throw makeAuthError(data.error || `HTTP ${res.status}`);
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function learningFeedback(body) {
    const res = await fetch('/api/agent/feedback', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body || {}),
    });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 || res.status === 403) throw makeAuthError(data.error || `HTTP ${res.status}`);
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function learningTune() {
    const res = await fetch('/api/agent/tune', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: '{}',
    });
    const data = await res.json().catch(() => ({}));
    if (res.status === 401 || res.status === 403) throw makeAuthError(data.error || `HTTP ${res.status}`);
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function login(email) {
    const res = await fetch('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      credentials: 'same-origin',
      body: JSON.stringify({ email }),
    });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  async function session() {
    const res = await fetch('/auth/session', { credentials: 'same-origin' });
    const data = await res.json().catch(() => ({}));
    if (!res.ok || data.error) throw new Error(data.error || `HTTP ${res.status}`);
    return data;
  }

  function makeAuthError(message) {
    const err = new Error(message || 'Session expired, please log in again.');
    err.auth = true;
    return err;
  }

  function isAuthError(err) {
    return !!(err && (err.auth || String(err.message || err).toLowerCase().includes('session expired') || String(err.message || err).toLowerCase().includes('authentication required')));
  }

  function handleAuthFailure(message = 'Session expired, please log in again.') {
    try {
      API.clearTokenCache();
      sessionStorage.removeItem('skilledge_manager_email');
      sessionStorage.removeItem('skilledge_team');
      sessionStorage.removeItem('skilledge_reportee_count');
    } catch {}
    const existing = document.getElementById('authExpiredBanner');
    if (!existing) {
      const banner = document.createElement('div');
      banner.id = 'authExpiredBanner';
      banner.style.cssText = 'position:fixed;inset:0;z-index:20000;background:rgba(15,23,42,.72);display:flex;align-items:center;justify-content:center;padding:24px;';
      banner.innerHTML = `
        <div style="max-width:440px;width:100%;background:#fff;border-radius:18px;padding:24px 22px;box-shadow:0 20px 80px rgba(0,0,0,.28);text-align:center;">
          <div style="font-size:18px;font-weight:700;margin-bottom:8px;">${message}</div>
          <div style="color:#64748b;font-size:14px;margin-bottom:18px;">Your session has ended. Please log in again to continue.</div>
          <a href="login.html?expired=1" class="btn btn-theme w-100">Go to login</a>
        </div>`;
      document.body.appendChild(banner);
      setTimeout(() => { if (location.pathname.endsWith('login.html')) return; location.href = 'login.html?expired=1'; }, 1800);
    }
  }

  // ── Course list (session-cached forever) ────────────────────────────────────
  async function getCourseListCached() {
    const cached = sessionStorage.getItem(COURSE_CACHE_KEY);
    if (cached) { try { return JSON.parse(cached); } catch {} }
    if (window[INFLIGHT_COURSE_KEY]) return window[INFLIGHT_COURSE_KEY];
    window[INFLIGHT_COURSE_KEY] = (async () => {
      const data = await getCourseList();
      if (Array.isArray(data) && data.length) {
        sessionStorage.setItem(COURSE_CACHE_KEY, JSON.stringify(data));
      }
      return data;
    })().finally(() => { window[INFLIGHT_COURSE_KEY] = null; });
    const data = await window[INFLIGHT_COURSE_KEY];
    if (Array.isArray(data) && data.length) {
      sessionStorage.setItem(COURSE_CACHE_KEY, JSON.stringify(data));
    }
    return data;
  }

  // ── Team data cache (4-hour TTL) ────────────────────────────────────────────
  function getCachedTeam() {
    try {
      const key = teamCacheKey();
      const tsKey = teamTsKey();
      const ts   = parseInt(sessionStorage.getItem(tsKey) || '0');
      if (Date.now() - ts > CACHE_TTL_MS) return null;
      return JSON.parse(sessionStorage.getItem(key) || 'null');
    } catch { return null; }
  }

  function cacheTeam(data, managerEmail) {
    sessionStorage.setItem(teamCacheKey(managerEmail), JSON.stringify(data));
    sessionStorage.setItem(teamTsKey(managerEmail),    String(Date.now()));
  }

  function clearTeamCache(managerEmail) {
    sessionStorage.removeItem(teamCacheKey(managerEmail));
    sessionStorage.removeItem(teamTsKey(managerEmail));
    sessionStorage.removeItem(COURSE_CACHE_KEY);
  }

  // ── Field normalisers (maps confirmed API field names → clean names) ─────────
  function normaliseDetail(d) {
    if (!d) return d;
    return {
      ...d,
      CourseName:       d.CourseName || '',
      VendorName:       d.VendorName || '',
      QubitsScore:      parseFloat(d.QubitsScore) || 0,
      SkillLevel:       d.SkillLevel || '',
      OfficiallyApproved: d.OfficiallyApproved,
      IsFutureSkill:    d['Is Future Skill'],
      FutureSkillDate:  d['Future Skill Date'],
      DM:               d.DM || '',
      TechCallRating:   parseFloat(d.techcallrating) || 0,
      CourseAssignment: parseInt(d['Course Assignment']) || 0,
      RoamingOffDates:  d.RoamingOffDates || '',
    };
  }

  function normaliseSkill(s) {
    if (!s) return s;
    return {
      ...s,
      CourseName:   s.course_name || s.CourseName || '',
      CourseId:     s.course_id   || s.CourseId   || '',
      EmployeeName: s.employee_name || '',
      EmployeeCode: s.employee_code || '',
    };
  }

  function normaliseHRIncident(r) {
    if (!r) return r;
    return {
      ...r,
      PositiveCount: parseInt(r['Positive Count'] || r.PositiveCount || 0),
      NegativeCount: parseInt(r['Negative Count'] || r.NegativeCount || 0),
    };
  }

  function normaliseNegFeedback(r) {
    if (!r) return r;
    return { ...r, Total: parseInt(r.Total) || 0 };
  }

  function normaliseAssignment(a) {
    if (!a) return a;
    return {
      ...a,
      AssignmentID: a.AssignmentID,
      CourseID:     a.CourseID,
      Course:       a.Course || '',
      TrainerEmail: a.OffEmailId || '',
    };
  }

  // ── Safe array unwrap ────────────────────────────────────────────────────────
  function safeArr(settled, normFn) {
    if (settled.status !== 'fulfilled') return [];
    let v = settled.value;
    if (!v) return [];
    if (!Array.isArray(v)) v = v.Data || v.data || v.Result || v.result || v.Items || v.items || [];
    if (!Array.isArray(v)) return [];
    return normFn ? v.map(normFn) : v;
  }

  // ── loadFullTeamData — ONE call per session, results cached 4 hours ─────────
  async function loadFullTeamData(managerEmail) {
    if (window[INFLIGHT_TEAM_KEY]) return window[INFLIGHT_TEAM_KEY];
    window[INFLIGHT_TEAM_KEY] = (async () => {
    const raw = await getReportees(managerEmail);

    // Normalise envelope — some responses wrap in { Data: [...] }
    let list = Array.isArray(raw) ? raw
             : (raw?.Data || raw?.data || raw?.Reportees || raw?.reportees || []);

    if (!list.length) {
      console.warn('[SkillEdge] loadFullTeamData: no reportees for', managerEmail, raw);
      return [];
    }
    console.log('[SkillEdge] Got', list.length, 'reportees. Keys:', Object.keys(list[0]));

    const enriched = await Promise.allSettled(
      list.map(async trainer => {
        const email = trainer.OffEmail || trainer.EmailID || trainer.Email || trainer.email || '';
        const empId = String(trainer.EmpId || trainer.TrainerId || trainer.EmployeeId || '');

        if (!email) console.warn('[SkillEdge] Trainer has no email:', trainer);

        const [details, skills, util, certs, negFb, hr] = await Promise.allSettled([
          email ? getTrainerDetails(email)   : Promise.resolve([]),
          empId ? getTrainerSkills(empId)    : Promise.resolve([]),
          email ? getUtilization(email)      : Promise.resolve([]),
          email ? getVendorCerts(email)      : Promise.resolve([]),
          email ? getNegativeFeedback(email) : Promise.resolve([]),
          email ? getHRIncidents(email)      : Promise.resolve([]),
        ]);

        if (details.status === 'rejected') console.warn('[SkillEdge] details failed', email, details.reason?.message);
        if (skills.status  === 'rejected') console.warn('[SkillEdge] skills failed',  empId, skills.reason?.message);
        if (util.status    === 'rejected') console.warn('[SkillEdge] util failed',    email, util.reason?.message);
        if (certs.status   === 'rejected') console.warn('[SkillEdge] certs failed',   email, certs.reason?.message);
        if (negFb.status   === 'rejected') console.warn('[SkillEdge] negFb failed',   email, negFb.reason?.message);
        if (hr.status      === 'rejected') console.warn('[SkillEdge] hr failed',      email, hr.reason?.message);

        const detailsArr = safeArr(details, normaliseDetail);
        const skillsArr  = safeArr(skills,  normaliseSkill);
        const hrArr      = safeArr(hr,      normaliseHRIncident);
        const negFbArr   = safeArr(negFb,   normaliseNegFeedback);

        // Derive aggregates here so every page can use them without re-computing
        const qScores   = detailsArr.map(d => d.QubitsScore).filter(n => n > 0);
        const avgQubit  = qScores.length ? qScores.reduce((a,b)=>a+b,0)/qScores.length : 0;
        const totalAssignments = detailsArr.reduce((s,d) => s + d.CourseAssignment, 0);
        const hrRec     = hrArr[0] || {};
        const negTotal  = negFbArr.reduce((s,r) => s + r.Total, 0);
        const directFlag = trainer.IsdirectReportee === true || trainer.IsdirectReportee === 'true' || trainer.IsdirectReportee === 1;
        const courseKeySet = new Set();
        detailsArr.forEach(d => {
          if (d.CourseName) courseKeySet.add(String(d.CourseName).toLowerCase().trim());
        });
        skillsArr.forEach(s => {
          if (s.CourseId) courseKeySet.add(String(s.CourseId).toLowerCase().trim());
          if (s.CourseName) courseKeySet.add(String(s.CourseName).toLowerCase().trim());
        });

        // Derive utilization percentage — the content is a string that was parsed
        const utilArr   = safeArr(util);
        const utilPct   = (() => {
          if (!utilArr.length) return 0;
          const u = utilArr[0];
          return parseFloat(u.UtilizationPercentage || u.Utilization || u.utilization_pct || u.TotalUtilization || 0);
        })();

        return {
          // ── Identity (from reportees API) ──
          TrainerName:      trainer.TrainerName || '',
          EmpId:            empId,
          OffEmail:         email,
          IsdirectReportee: trainer.IsdirectReportee,
          isDirectReportee: directFlag,
          Designation:      trainer.Designation || '',
          TrainerPlus:      trainer.TrainerPlus,
          emailKey:         email.toLowerCase(),

          // ── Raw API arrays ──
          details:          detailsArr,
          skills:           skillsArr,
          utilization:      utilArr,
          vendorCerts:      safeArr(certs),
          negativeFeedback: negFbArr,
          hrIncidents:      hrArr,

          // ── Pre-derived aggregates (avoids repeated computation) ──
          avgQubitsScore:    avgQubit,
          totalAssignments:  totalAssignments,
          certCount:         safeArr(certs).length,
          negFeedbackTotal:  negTotal,
          hrPositiveCount:   hrRec.PositiveCount || 0,
          hrNegativeCount:   hrRec.NegativeCount || 0,
          utilizationPct:    utilPct,
          courseCount:       detailsArr.length,
          skillCount:        skillsArr.filter(s => !s.is_discontinue_course).length,
          futureCourses:     detailsArr.filter(d => d.IsFutureSkill).length,
          officialCount:     detailsArr.filter(d => d.OfficiallyApproved).length,
          courseKeySet:     Array.from(courseKeySet),
        };
      })
    );

    const result = enriched
      .filter(r => r.status === 'fulfilled')
      .map(r => r.value);

    console.log('[SkillEdge] loadFullTeamData complete:', result.length, 'trainers');
    return result;
    })().finally(() => { window[INFLIGHT_TEAM_KEY] = null; });
    return window[INFLIGHT_TEAM_KEY];
  }

  // ── getTeamData — entry point for all pages ─────────────────────────────────
  // Returns cached data instantly if available; otherwise fetches and caches.
  async function getTeamData(managerEmail) {
    const email = managerEmail || getManagerEmail();
    const cached = (() => {
      try {
        const ts = parseInt(sessionStorage.getItem(teamTsKey(email)) || '0');
        if (Date.now() - ts > CACHE_TTL_MS) return null;
        return JSON.parse(sessionStorage.getItem(teamCacheKey(email)) || 'null');
      } catch { return null; }
    })();
    if (cached && cached.length) return cached;
    const data = await loadFullTeamData(email);
    if (data.length) cacheTeam(data, email);
    return data;
  }

  // Warm the workspace once at login so later pages read from cache instead of
  // repeating the heavy API fan-out on every page load.
  async function warmWorkspace(managerEmail) {
    const email = managerEmail || getManagerEmail();
    if (!email) throw new Error('Manager email is required to warm the workspace.');
    const [team, courses, assignments] = await Promise.all([
      getTeamData(email),
      getCourseListCached(),
      getAssignments(email, 1, 100).catch(() => []),
    ]);

    try {
      sessionStorage.setItem('skilledge_assignments_preview', JSON.stringify(Array.isArray(assignments) ? assignments : []));
    } catch {}

    return { team, courses, assignments };
  }

    return {
      getManagerEmail, setManagerEmail, clearTokenCache, clearTeamCache,
    getReportees, getTrainerDetails, getTrainerSkills,
    getUtilization, getVendorCerts, getNegativeFeedback,
    getHRIncidents, getCourseList, getCourseListCached, getAssignments, getUnifiedManagerIntelligence,
      login, session, isAuthError, handleAuthFailure,
      getRefreshStatus, runRefresh, searchKnowledge,
      agentAsk, agentBriefing, learningStatus, learningFeedback, learningTune,
      loadFullTeamData, getTeamData, warmWorkspace,
    getCachedTeam, cacheTeam,
      normaliseAssignment,
      updateActionState, updateReviewFlagState,
    };
  })();
