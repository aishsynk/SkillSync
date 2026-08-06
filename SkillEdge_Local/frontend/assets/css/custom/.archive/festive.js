/**
 * festive.js — CertReady Auto-Festive Engine
 *
 * Runs once on DOMContentLoaded. Detects today's date against a festival
 * calendar, applies a body CSS class, injects a banner + canvas particles,
 * and decorates the sidebar. Zero dependencies — pure vanilla JS.
 *
 * Festivals:
 *   holi          — Holi (variable ~March, ±1 day window)
 *   diwali        — Diwali (variable Oct/Nov, ±1 day window)
 *   eid           — Eid al-Fitr (variable, ±2 day window)
 *   independence  — Independence Day, 15 Aug (fixed)
 *   republic      — Republic Day, 26 Jan (fixed)
 *   christmas     — Christmas, 24-26 Dec (fixed window)
 */
(function () {
    'use strict';

    /* ─────────────────────────────────────────────────────────────
       1. FESTIVAL CALENDAR
       Variable-date festivals are pre-computed for 2025–2032.
       Each entry: [month (1-12), day]
    ───────────────────────────────────────────────────────────── */
    var CALENDAR = {
        holi: {
            2025: [3, 14],  2026: [3,  3],  2027: [3, 22],
            2028: [3, 11],  2029: [3,  1],  2030: [3, 20],
            2031: [3,  9],  2032: [3, 28]
        },
        diwali: {
            2025: [10, 20], 2026: [11,  8], 2027: [10, 29],
            2028: [10, 17], 2029: [11,  5], 2030: [10, 26],
            2031: [11, 14], 2032: [11,  2]
        },
        eid: {
            2025: [3, 30],  2026: [3, 20],  2027: [3,  9],
            2028: [2, 26],  2029: [2, 14],  2030: [2,  5],
            2031: [1, 25],  2032: [1, 14]
        }
    };

    /* ─────────────────────────────────────────────────────────────
       2. FESTIVAL METADATA
    ───────────────────────────────────────────────────────────── */
    var META = {
        holi: {
            emoji: '🌈',
            label: 'Happy Holi!',
            message: '🎨 Wishing you a colourful and joyful Holi! 🌈',
            particles: 'holi'
        },
        diwali: {
            emoji: '🪔',
            label: 'Happy Diwali!',
            message: '🪔 Wishing you light, prosperity & joy this Diwali! ✨',
            particles: 'diwali'
        },
        eid: {
            emoji: '🌙',
            label: 'Eid Mubarak!',
            message: '🌙 Eid Mubarak! Wishing you peace, happiness and blessings. ⭐',
            particles: 'eid'
        },
        independence: {
            emoji: '🇮🇳',
            label: 'Happy Independence Day!',
            message: '🇮🇳 Happy 78th Independence Day! Jai Hind! 🕊️',
            particles: 'tricolor'
        },
        republic: {
            emoji: '🇮🇳',
            label: 'Happy Republic Day!',
            message: '🇮🇳 Happy Republic Day! Celebrating the spirit of our Constitution. 🕊️',
            particles: 'tricolor'
        },
        christmas: {
            emoji: '🎄',
            label: 'Merry Christmas!',
            message: '🎄 Merry Christmas & Happy Holidays from all of us! 🎅',
            particles: 'snow'
        }
    };

    /* ─────────────────────────────────────────────────────────────
       3. DATE DETECTION
    ───────────────────────────────────────────────────────────── */
    function detectFestival() {
        // DEV PREVIEW — change to any festival to test, set null to disable
        // Options: 'holi' | 'diwali' | 'eid' | 'independence' | 'republic' | 'christmas'
        var DEV_FORCE = 'diwali';
        if (DEV_FORCE) return DEV_FORCE;

        var now = new Date();
        var y = now.getFullYear();
        var m = now.getMonth() + 1;  // 1–12
        var d = now.getDate();

        // Fixed dates
        if (m === 1  && d === 26)              return 'republic';
        if (m === 8  && d === 15)              return 'independence';
        if (m === 12 && d >= 24 && d <= 26)    return 'christmas';

        // Variable dates — check ±window
        function near(table, window) {
            if (!table[y]) return false;
            var ref = table[y];
            return ref[0] === m && Math.abs(ref[1] - d) <= window;
        }

        if (near(CALENDAR.holi,   1)) return 'holi';
        if (near(CALENDAR.diwali, 1)) return 'diwali';
        if (near(CALENDAR.eid,    2)) return 'eid';

        return null;
    }

    /* ─────────────────────────────────────────────────────────────
       4. BANNER INJECTION
    ───────────────────────────────────────────────────────────── */
    function injectBanner(festival, meta) {
        var banner = document.createElement('div');
        banner.id = 'festive-banner';
        banner.setAttribute('role', 'banner');
        banner.setAttribute('aria-label', meta.label);
        banner.innerHTML = '<span>' + meta.emoji + '</span>'
            + '<span>' + meta.message + '</span>'
            + '<button class="festive-close" aria-label="Close festival banner" title="Close">✕</button>';

        // Special: Republic/Independence — add spinning Ashoka chakra
        if (festival === 'independence' || festival === 'republic') {
            var chakra = document.createElement('span');
            chakra.className = 'ashoka-chakra';
            chakra.innerHTML = '&#9784;'; // ☸
            banner.insertBefore(chakra, banner.firstChild);
        }

        document.body.insertBefore(banner, document.body.firstChild);

        // Close button
        banner.querySelector('.festive-close').addEventListener('click', function () {
            banner.style.animation = 'festive-banner-in 0.3s reverse forwards';
            setTimeout(function () {
                banner.remove();
                document.body.classList.remove('festive-active');
                document.getElementById('app') &&
                    (document.getElementById('app').style.paddingTop = '');
            }, 300);
            try { sessionStorage.setItem('festive_dismissed', festival); } catch(e) {}
        });
    }

    /* ─────────────────────────────────────────────────────────────
       5. SIDEBAR STRIP
    ───────────────────────────────────────────────────────────── */
    function injectSidebarStrip(festival) {
        var sidebar = document.getElementById('sidebar');
        if (!sidebar) return;
        sidebar.style.position = 'relative';
        var strip = document.createElement('div');
        strip.className = 'festive-sidebar-strip ' + festival;
        sidebar.insertBefore(strip, sidebar.firstChild);
    }

    /* ─────────────────────────────────────────────────────────────
       6. HEADER BADGE
    ───────────────────────────────────────────────────────────── */
    function injectHeaderBadge(meta) {
        var brand = document.querySelector('.navbar-brand, .app-brand, .app-header-brand, .header-brand');
        if (!brand) return;
        var badge = document.createElement('span');
        badge.className = 'festive-header-badge';
        badge.textContent = meta.emoji + ' ' + meta.label;
        brand.parentNode.insertBefore(badge, brand.nextSibling);
    }

    /* ─────────────────────────────────────────────────────────────
       7. CANVAS PARTICLE ENGINE
    ───────────────────────────────────────────────────────────── */
    function startParticles(type) {
        var canvas = document.createElement('canvas');
        canvas.id = 'festive-canvas';
        document.body.appendChild(canvas);

        var ctx = canvas.getContext('2d');
        var W, H, particles = [];

        function resize() {
            W = canvas.width  = window.innerWidth;
            H = canvas.height = window.innerHeight;
        }
        resize();
        window.addEventListener('resize', resize);

        /* ── PARTICLE FACTORIES ── */
        var factories = {

            // HOLI — brightly coloured powder dots bursting upward
            holi: function () {
                var colors = ['#FF4E50','#FC913A','#F9D62E','#62D96B','#5BCBF5','#8B5CF6','#FF69B4','#FF6347'];
                for (var i = 0; i < 80; i++) {
                    particles.push({
                        x: Math.random() * W,
                        y: H + Math.random() * 200,
                        r: 4 + Math.random() * 8,
                        color: colors[Math.floor(Math.random() * colors.length)],
                        vx: (Math.random() - 0.5) * 2.5,
                        vy: -(1.5 + Math.random() * 3),
                        alpha: 0.7 + Math.random() * 0.3,
                        decay: 0.003 + Math.random() * 0.003,
                        wobble: Math.random() * Math.PI * 2,
                        wobbleSpeed: 0.03 + Math.random() * 0.04
                    });
                }
            },

            // DIWALI — golden sparks / firecracker trails
            diwali: function () {
                var colors = ['#FFD700','#FF8C00','#FF4500','#FFA500','#FFEC8B','#FF6347'];
                for (var i = 0; i < 60; i++) {
                    particles.push({
                        x: Math.random() * W,
                        y: H + Math.random() * 100,
                        r: 2 + Math.random() * 5,
                        color: colors[Math.floor(Math.random() * colors.length)],
                        vx: (Math.random() - 0.5) * 2,
                        vy: -(2 + Math.random() * 4),
                        alpha: 0.9,
                        decay: 0.004 + Math.random() * 0.005,
                        trail: [],
                        trailLen: 4 + Math.floor(Math.random() * 5),
                        wobble: Math.random() * Math.PI * 2,
                        wobbleSpeed: 0.05 + Math.random() * 0.05
                    });
                }
            },

            // EID — white/gold stars drifting down gently
            eid: function () {
                var colors = ['#FFD700','#ffffff','#c8f5c8','#FFE4B5'];
                for (var i = 0; i < 50; i++) {
                    particles.push({
                        x: Math.random() * W,
                        y: -Math.random() * H,
                        r: 1 + Math.random() * 4,
                        color: colors[Math.floor(Math.random() * colors.length)],
                        vx: (Math.random() - 0.5) * 0.5,
                        vy: 0.4 + Math.random() * 1.2,
                        alpha: 0.5 + Math.random() * 0.5,
                        decay: 0,
                        star: true,
                        wobble: Math.random() * Math.PI * 2,
                        wobbleSpeed: 0.01 + Math.random() * 0.02
                    });
                }
            },

            // TRICOLOR — confetti in saffron / white / green
            tricolor: function () {
                var colors = ['#FF9933','#FF9933','#ffffff','#138808','#138808','#000080'];
                for (var i = 0; i < 70; i++) {
                    particles.push({
                        x: Math.random() * W,
                        y: -Math.random() * H,
                        r: 5,
                        w: 10 + Math.random() * 6,
                        h: 4 + Math.random() * 3,
                        color: colors[Math.floor(Math.random() * colors.length)],
                        vx: (Math.random() - 0.5) * 2,
                        vy: 1.2 + Math.random() * 2,
                        alpha: 0.8,
                        decay: 0,
                        rotation: Math.random() * Math.PI * 2,
                        rotSpeed: (Math.random() - 0.5) * 0.15,
                        confetti: true
                    });
                }
            },

            // SNOW — gentle white snowflakes drifting down
            snow: function () {
                for (var i = 0; i < 80; i++) {
                    particles.push({
                        x: Math.random() * W,
                        y: -Math.random() * H,
                        r: 2 + Math.random() * 5,
                        color: '#ffffff',
                        vx: (Math.random() - 0.5) * 0.8,
                        vy: 0.5 + Math.random() * 1.5,
                        alpha: 0.4 + Math.random() * 0.5,
                        decay: 0,
                        wobble: Math.random() * Math.PI * 2,
                        wobbleSpeed: 0.01 + Math.random() * 0.02
                    });
                }
            }
        };

        /* ── DRAW HELPERS ── */
        function drawStar(cx, cy, spikes, outerR, innerR, color, alpha) {
            ctx.save();
            ctx.globalAlpha = alpha;
            ctx.fillStyle = color;
            ctx.beginPath();
            var rot = (Math.PI / 2) * 3;
            var step = Math.PI / spikes;
            ctx.moveTo(cx, cy - outerR);
            for (var i = 0; i < spikes; i++) {
                ctx.lineTo(cx + Math.cos(rot) * outerR, cy + Math.sin(rot) * outerR);
                rot += step;
                ctx.lineTo(cx + Math.cos(rot) * innerR, cy + Math.sin(rot) * innerR);
                rot += step;
            }
            ctx.lineTo(cx, cy - outerR);
            ctx.closePath();
            ctx.fill();
            ctx.restore();
        }

        /* ── ANIMATION LOOP ── */
        function tick() {
            ctx.clearRect(0, 0, W, H);
            var toRespawn = [];

            for (var i = particles.length - 1; i >= 0; i--) {
                var p = particles[i];

                // Update position
                p.wobble += p.wobbleSpeed;
                p.x += p.vx + Math.sin(p.wobble) * 0.5;
                p.y += p.vy;
                if (p.alpha > 0) p.alpha -= p.decay;
                if (p.rotation !== undefined) p.rotation += p.rotSpeed;

                // Trail for diwali sparks
                if (p.trail !== undefined) {
                    p.trail.unshift({ x: p.x, y: p.y });
                    if (p.trail.length > p.trailLen) p.trail.pop();
                }

                // Draw
                ctx.save();
                ctx.globalAlpha = Math.max(0, p.alpha);

                if (p.confetti) {
                    ctx.translate(p.x, p.y);
                    ctx.rotate(p.rotation);
                    ctx.fillStyle = p.color;
                    ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
                } else if (p.star) {
                    drawStar(p.x, p.y, 4, p.r, p.r * 0.4, p.color, p.alpha);
                    ctx.globalAlpha = 0; // already drawn inside helper
                } else {
                    // Draw trail for diwali
                    if (p.trail && p.trail.length > 1) {
                        for (var t = 0; t < p.trail.length; t++) {
                            ctx.globalAlpha = (p.alpha * (1 - t / p.trail.length)) * 0.5;
                            ctx.fillStyle = p.color;
                            ctx.beginPath();
                            ctx.arc(p.trail[t].x, p.trail[t].y, p.r * (1 - t / p.trail.length), 0, Math.PI * 2);
                            ctx.fill();
                        }
                        ctx.globalAlpha = Math.max(0, p.alpha);
                    }
                    ctx.fillStyle = p.color;
                    ctx.beginPath();
                    ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
                    ctx.fill();
                }
                ctx.restore();

                // Respawn logic
                var offscreen = (p.vy > 0 && p.y > H + 20) || (p.vy < 0 && p.y < -20) || p.alpha <= 0;
                if (offscreen) { toRespawn.push(i); }
            }

            // Respawn dead particles
            for (var j = 0; j < toRespawn.length; j++) {
                var old = particles[toRespawn[j]];
                if (type === 'holi') {
                    var colors = ['#FF4E50','#FC913A','#F9D62E','#62D96B','#5BCBF5','#8B5CF6','#FF69B4'];
                    old.x = Math.random() * W;
                    old.y = H + 10;
                    old.alpha = 0.7 + Math.random() * 0.3;
                    old.color = colors[Math.floor(Math.random() * colors.length)];
                    old.vx = (Math.random() - 0.5) * 2.5;
                    old.vy = -(1.5 + Math.random() * 3);
                    if (old.trail) old.trail = [];
                } else if (type === 'diwali') {
                    var dColors = ['#FFD700','#FF8C00','#FF4500','#FFA500','#FFEC8B'];
                    old.x = Math.random() * W;
                    old.y = H + 10;
                    old.alpha = 0.9;
                    old.color = dColors[Math.floor(Math.random() * dColors.length)];
                    old.vx = (Math.random() - 0.5) * 2;
                    old.vy = -(2 + Math.random() * 4);
                    if (old.trail) old.trail = [];
                } else if (type === 'eid' || type === 'snow') {
                    old.x = Math.random() * W;
                    old.y = -10;
                    old.alpha = 0.4 + Math.random() * 0.5;
                } else if (type === 'tricolor') {
                    var tColors = ['#FF9933','#FF9933','#ffffff','#138808','#138808'];
                    old.x = Math.random() * W;
                    old.y = -10;
                    old.color = tColors[Math.floor(Math.random() * tColors.length)];
                    old.rotation = Math.random() * Math.PI * 2;
                }
            }

            requestAnimationFrame(tick);
        }

        // Initialise particles for this festival
        if (factories[type]) { factories[type](); }

        // Start loop
        requestAnimationFrame(tick);
    }

    /* ─────────────────────────────────────────────────────────────
       8. MAIN BOOTSTRAP
    ───────────────────────────────────────────────────────────── */
    function init() {
        var festival = detectFestival();
        if (!festival) return;

        // Don't show if user already dismissed this session
        try {
            if (sessionStorage.getItem('festive_dismissed') === festival) return;
        } catch(e) {}

        var meta = META[festival];
        if (!meta) return;

        // Apply body class
        document.body.classList.add('festival-' + festival, 'festive-active');

        // Banner
        injectBanner(festival, meta);

        // Sidebar strip
        injectSidebarStrip(festival);

        // Header badge
        injectHeaderBadge(meta);

        // Particles (slightly delayed to not block first paint)
        setTimeout(function () {
            startParticles(meta.particles);
        }, 400);
    }

    // Run after DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
