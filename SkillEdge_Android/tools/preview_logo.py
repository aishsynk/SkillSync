"""Rasterise the generated logo geometry with PIL so it can be eyeballed."""
from PIL import Image, ImageDraw
import gen_logo as G

S = 3  # supersample factor


def render(size, facet_cfg, net_cfg, scale, tx, ty, out, bg=(255, 255, 255, 255)):
    W = size * S
    img = Image.new("RGBA", (W, W), bg)
    d = ImageDraw.Draw(img)

    def T(p):
        return (p[0] * scale * S + tx * S, p[1] * scale * S + ty * S)

    for tri, shade in G.facet_tris(*facet_cfg):
        d.polygon([T(p) for p in tri], fill=shade)

    edges, nodes = G.net_geom(*net_cfg)
    for ax, ay, bx, by, w in edges:
        d.line([T((ax, ay)), T((bx, by))], fill=G.CYAN, width=max(1, round(w * scale * S)))
    for nx, ny, r in nodes:
        cx, cy = T((nx, ny)); rr = r * scale * S
        d.ellipse([cx - rr, cy - rr, cx + rr, cy + rr], fill=G.CYAN)

    img.resize((size * 2, size * 2), Image.LANCZOS).save(out)
    print("wrote", out)


render(512, G.FULL_FACETS, G.FULL_NET, *G.FULL_TF, "preview_full.png")
render(108, G.ICON_FACETS, G.ICON_NET, *G.ICON_TF, "preview_icon.png")
