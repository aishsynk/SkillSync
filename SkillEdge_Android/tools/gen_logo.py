"""Generate the SkillSync low-poly brain + neural-network mark as Android VectorDrawables.

The brain is a real Delaunay triangulation over boundary + interior points, so the
silhouette itself is faceted (a clip-path would give a smooth blob edge instead).
Geometry is kept separate from XML emission so preview_logo.py can rasterise the
exact same art for visual checking.
"""
import math, os, random, re
import numpy as np
from scipy.spatial import Delaunay

# Brain profile facing left: frontal lobe bulging forward, rounded crown,
# temporal lobe curving under, and a cerebellar wedge dropping at bottom-right.
BRAIN = (
    "M48,285 "
    "C50,212 92,152 168,112 "
    "C220,85 288,88 326,132 "
    "C348,158 346,196 340,232 "
    "C336,268 344,300 344,332 "
    "C344,378 336,406 320,424 "
    "L300,478 L262,424 "
    "C232,442 182,436 142,412 "
    "C92,382 46,340 48,285 Z"
)
NET_BOX = (300, 84, 498, 442)     # overlaps the brain's right edge so the mesh grows out of it

# Light cyan → deep navy; index is driven by x so the dark wedge lands at the network seam.
PALETTE = ["#4FC8F8", "#3FC3F7", "#29B6F6", "#2196F3", "#1E88E5", "#1976D2", "#1565C0", "#0D47A1", "#0A3880"]
CYAN = "#29B6F6"


# ── Polygon helpers ──────────────────────────────────────────────────────────

def flatten(path, steps=26):
    """Flatten an M/C/L/Z path into a polygon point list."""
    pts, cur, start = [], (0.0, 0.0), (0.0, 0.0)
    for cmd, arg in re.findall(r"([MCLZ])([^MCLZ]*)", path):
        n = [float(v) for v in re.findall(r"-?\d+\.?\d*", arg)]
        if cmd == "M":
            cur = start = (n[0], n[1]); pts.append(cur)
        elif cmd == "L":
            cur = (n[0], n[1]); pts.append(cur)
        elif cmd == "C":
            (x0, y0), (x1, y1), (x2, y2), (x3, y3) = cur, (n[0], n[1]), (n[2], n[3]), (n[4], n[5])
            for i in range(1, steps + 1):
                t, u = i / steps, 1 - i / steps
                pts.append((u**3 * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t**3 * x3,
                            u**3 * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t**3 * y3))
            cur = (x3, y3)
        elif cmd == "Z":
            pts.append(start)
    return pts


def inside(poly, x, y):
    """Ray-casting point-in-polygon test."""
    hit = False
    for i in range(len(poly) - 1):
        (ax, ay), (bx, by) = poly[i], poly[i + 1]
        if (ay > y) != (by > y) and x < (bx - ax) * (y - ay) / (by - ay) + ax:
            hit = not hit
    return hit


def edge_dist(poly, x, y):
    """Shortest distance from a point to the polygon boundary."""
    best = 1e9
    for i in range(len(poly) - 1):
        ax, ay = poly[i]; bx, by = poly[i + 1]
        dx, dy = bx - ax, by - ay
        L = dx * dx + dy * dy
        t = 0.0 if L == 0 else max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / L))
        best = min(best, math.hypot(x - (ax + t * dx), y - (ay + t * dy)))
    return best


def resample(poly, n):
    """n points spaced evenly along the polygon perimeter — these facet the silhouette."""
    seg = [math.dist(poly[i], poly[i + 1]) for i in range(len(poly) - 1)]
    total, acc, out, j = sum(seg), 0.0, [], 0
    for i in range(n):
        target = total * i / n
        while j < len(seg) - 1 and acc + seg[j] < target:
            acc += seg[j]; j += 1
        t = 0.0 if seg[j] == 0 else (target - acc) / seg[j]
        (ax, ay), (bx, by) = poly[j], poly[j + 1]
        out.append((ax + (bx - ax) * t, ay + (by - ay) * t))
    return out


# ── Geometry ─────────────────────────────────────────────────────────────────

def facet_tris(n_edge, spacing, jitter, seed):
    """Delaunay-triangulated low-poly brain → [(3 points, colour)]."""
    rnd = random.Random(seed)
    poly = flatten(BRAIN)
    pts = resample(poly, n_edge)

    xs = [p[0] for p in poly]; ys = [p[1] for p in poly]
    gx, gy = np.arange(min(xs), max(xs), spacing), np.arange(min(ys), max(ys), spacing)
    for y in gy:
        for x in gx:
            px = x + rnd.uniform(-jitter, jitter)
            py = y + rnd.uniform(-jitter, jitter)
            if inside(poly, px, py) and edge_dist(poly, px, py) > spacing * 0.42:
                pts.append((px, py))

    arr = np.array(pts)
    x0, x1 = arr[:, 0].min(), arr[:, 0].max()
    out = []
    for ia, ib, ic in Delaunay(arr).simplices:
        tri = (tuple(arr[ia]), tuple(arr[ib]), tuple(arr[ic]))
        cx = sum(p[0] for p in tri) / 3
        cy = sum(p[1] for p in tri) / 3
        if not inside(poly, cx, cy):
            continue                                    # drops triangles bridging the stem notch
        frac = (cx - x0) / (x1 - x0)
        idx = 0.9 + frac * (len(PALETTE) - 3.4) + rnd.uniform(-1.5, 1.5)
        if frac > 0.70:
            idx += 1.7                                  # dark band where the mesh emerges
        out.append((tri, PALETTE[max(0, min(len(PALETTE) - 1, int(round(idx))))]))
    return out


def net_geom(box, cols, rows, jitter, seed, big_nodes, orphans=0, bold=1.0):
    """Delaunay node/edge graph that dissolves toward the right → (edges, nodes)."""
    rnd = random.Random(seed)
    x0, y0, x1, y1 = box
    raw = []
    for r in range(rows + 1):
        for c in range(cols + 1):
            fx = c / cols
            if fx > 0.5 and rnd.random() < (fx - 0.5) * 1.7:
                continue                                # thin out so the graph visibly dissolves
            raw.append((x0 + (x1 - x0) * fx + rnd.uniform(-jitter, jitter),
                        y0 + (y1 - y0) * r / rows + rnd.uniform(-jitter, jitter)))

    arr = np.array(raw)
    span = x1 - x0
    seen, edges = set(), []
    for simplex in Delaunay(arr).simplices:
        for a, b in ((simplex[0], simplex[1]), (simplex[1], simplex[2]), (simplex[2], simplex[0])):
            key = (min(a, b), max(a, b))
            if key in seen:
                continue
            seen.add(key)
            (ax, ay), (bx, by) = arr[a], arr[b]
            if math.hypot(ax - bx, ay - by) > span * 0.55:
                continue                                # cull the long hull edges Delaunay leaves
            fx = (max(ax, bx) - x0) / span
            if rnd.random() < fx * 0.5:
                continue                                # sparser links to the right
            edges.append((ax, ay, bx, by, (3.2 - fx * 1.9) * bold))

    big = set(rnd.sample(range(len(arr)), min(big_nodes, len(arr))))
    nodes = [(nx, ny, (rnd.uniform(8.0, 11.5) if i in big
                       else rnd.uniform(3.2, 5.6) * (1 - (nx - x0) / span * 0.3)) * bold)
             for i, (nx, ny) in enumerate(arr)]

    # Unconnected motes trailing past the mesh — the tail of the dissolve.
    for _ in range(orphans):
        nodes.append((rnd.uniform(x1 - span * 0.16, x1 + span * 0.10),
                      rnd.uniform(y0, y1),
                      rnd.choice((2.6, 3.4, 4.2, 9.0)) * bold))
    return edges, nodes


# ── XML emission ─────────────────────────────────────────────────────────────

def _circle(cx, cy, r):
    return f"M{cx - r:.1f},{cy:.1f} a{r:.1f},{r:.1f} 0 1,0 {2 * r:.1f},0 a{r:.1f},{r:.1f} 0 1,0 {-2 * r:.1f},0"


def build(size, facet_cfg, net_cfg, scale, tx, ty):
    body = []
    for tri, shade in facet_tris(*facet_cfg):
        d = " ".join(f"{'M' if i == 0 else 'L'}{p[0]:.1f},{p[1]:.1f}" for i, p in enumerate(tri)) + " Z"
        body.append(f'    <path android:fillColor="{shade}" android:pathData="{d}" />')

    edges, nodes = net_geom(*net_cfg)
    for ax, ay, bx, by, w in edges:
        body.append(f'    <path android:strokeColor="{CYAN}" android:strokeWidth="{w:.1f}" '
                    f'android:strokeLineCap="round" android:pathData="M{ax:.1f},{ay:.1f} L{bx:.1f},{by:.1f}" />')
    for nx, ny, r in nodes:
        body.append(f'    <path android:fillColor="{CYAN}" android:pathData="{_circle(nx, ny, r)}" />')

    inner = "\n".join(body)
    return f'''<?xml version="1.0" encoding="utf-8"?>
<!-- SkillSync brand mark — generated by tools/gen_logo.py. Do not hand-edit. -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{size}dp"
    android:height="{size}dp"
    android:viewportWidth="{size}"
    android:viewportHeight="{size}">
  <group
      android:scaleX="{scale}"
      android:scaleY="{scale}"
      android:translateX="{tx}"
      android:translateY="{ty}">
{inner}
  </group>
</vector>
'''


# facet_cfg = (boundary points, interior spacing, jitter, seed)
# net_cfg   = (box, cols, rows, jitter, seed, big nodes, orphan motes, boldness)
FULL_FACETS = (46, 40, 13, 20260806)
FULL_NET    = ((300, 84, 498, 442), 5, 7, 20, 77, 9, 6, 1.0)

# The launcher mark drops to ~48dp, so it carries fewer/larger facets and a compact,
# heavier mesh hugging the brain — the full mark turns to mush at that size.
ICON_FACETS = (20, 74, 16, 41)
ICON_NET    = ((298, 150, 414, 396), 2, 3, 14, 5, 2, 0, 1.9)

RES = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))),
                   "app", "src", "main", "res", "drawable")

# Full art spans x 46..498 (452 wide), y 84..478 (394 tall); icon art ends near x 430.
FULL_TF = (1.0, -14.0, -25.0)                     # centred in a 512 viewport
ICON_TF = (0.178, 11.8, 4.0)                      # 108 viewport, inside the centre safe zone


def main():
    with open(f"{RES}/ic_logo.xml", "w", encoding="utf-8") as f:
        f.write(build(512, FULL_FACETS, FULL_NET, *FULL_TF))
    with open(f"{RES}/ic_launcher_foreground.xml", "w", encoding="utf-8") as f:
        f.write(build(108, ICON_FACETS, ICON_NET, *ICON_TF))
    print("wrote ic_logo.xml + ic_launcher_foreground.xml")


if __name__ == "__main__":
    main()
