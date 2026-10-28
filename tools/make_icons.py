#!/usr/bin/env python3
"""Icon artwork for 数学实验室 Spatial — one design, every format.

The motif: (a+b)³ — a cube exploded into its 8 blocks (a³, three a²b slabs, three ab² rods, b³), the app's
signature "algebra you can walk around", on deep-indigo graph paper, with the spectrum orbit that marks the
family (物理 / 化学 / 数学 / AI 实验室 Spatial).

Outputs (run from MathSpatial/): PICO 3-D icon layers + relief maps, round launcher, adaptive foreground,
docs/store/icon_1024.png (same file set as the sister apps' tools/make_icons.py).
"""
import colorsys
import math
import os

import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from scipy.ndimage import distance_transform_edt

SS = 2
N = 1024 * SS
Y, X = (np.mgrid[0:N, 0:N].astype(np.float32) + 0.5) / SS
rng = np.random.default_rng(20261028)


def lin(c):
    c = np.asarray(c, np.float32) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def srgb(l):
    l = np.clip(l, 0, 1)
    return np.where(l <= 0.0031308, l * 12.92, 1.055 * l ** (1 / 2.4) - 0.055)


def over(dst, dst_a, src, src_a):
    a = src_a[..., None]
    out_a = src_a + dst_a * (1 - src_a)
    return (src * a + dst * dst_a[..., None] * (1 - a)) / np.maximum(out_a[..., None], 1e-6), out_a


def blur(a, r):
    im = Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8))
    return np.asarray(im.filter(ImageFilter.GaussianBlur(r * SS)), np.float32) / 255.0


def background():
    r = np.hypot(X - 512, Y - 450) / 724.0
    c0, c1, c2 = lin([52, 40, 120]), lin([20, 16, 52]), lin([6, 5, 16])
    t = np.clip(r, 0, 1)[..., None]
    rgb = np.where(t < 0.45, c0 + (c1 - c0) * (t / 0.45), c1 + (c2 - c1) * ((t - 0.45) / 0.55))
    # graph paper, fading from the centre
    g = np.zeros((N, N), np.float32)
    step = 64 * SS
    for k in range(0, N, step):
        g[:, k:k + SS] = 1; g[k:k + SS, :] = 1
    fade = np.exp(-(((X - 512) ** 2 + (Y - 520) ** 2) / (2 * 400.0 ** 2)))
    rgb = rgb + lin([150, 140, 255]) * (g * fade * 0.07)[..., None]
    for _ in range(40):
        sx, sy = rng.uniform(40, 984, 2); sr = rng.uniform(0.6, 1.4); br = rng.uniform(0.3, 0.8)
        rgb = rgb + lin([220, 215, 255]) * (np.exp(-((X - sx) ** 2 + (Y - sy) ** 2) / (2 * sr ** 2)) * br * 0.7)[..., None]
    return rgb


def orbit(cx=512.0, cy=560.0, a=430.0, b=150.0, rot=math.radians(-12)):
    c, s = math.cos(-rot), math.sin(-rot)
    xr = (X - cx) * c - (Y - cy) * s; yr = (X - cx) * s + (Y - cy) * c
    F = (xr / a) ** 2 + (yr / b) ** 2 - 1
    G = 2 * np.sqrt((xr / a ** 2) ** 2 + (yr / b ** 2) ** 2)
    dist = np.abs(F / np.maximum(G, 1e-6))
    al = np.clip((6.0 - dist) * SS + 0.5, 0, 1)
    sfrac = (np.arctan2(yr / b, xr / a) % (2 * np.pi)) / (2 * np.pi)
    hue = 0.76 * (1 - np.abs(2 * sfrac - 1))
    lut = np.array([colorsys.hsv_to_rgb(h, 0.78, 1.0) for h in np.linspace(0, 0.76, 256)], np.float32)
    rgb = lin(lut[np.clip((hue / 0.76 * 255).astype(int), 0, 255)] * 255)
    front = yr > 0
    return rgb, al * np.where(front, 0, 0.6), al * np.where(front, 1, 0), blur(al, 8) * 0.8


# ------------------------------------------------------------------ the exploded (a+b)^3 cube (isometric)
A, B, GAP, S = 0.62, 0.38, 0.12, 320.0          # edge parts, explode gap, pixels per unit
CX, CY = 512.0, 560.0
COS30, SIN30 = math.cos(math.radians(30)), math.sin(math.radians(30))
COL = {"a3": [70, 120, 245], "b3": [250, 185, 60], "a2b": [40, 205, 185], "ab2": [205, 80, 230]}


def proj(x, y, z):
    return (CX + (x - y) * COS30 * S, CY - z * S + (x + y) * SIN30 * S)


def blocks():
    """The 8 blocks of (a+b)^3, the a-parts on the side nearest the viewer (so a^3 is the big front block) and
    every b-part pulled away from it by GAP — the exploded view."""
    out = []
    for ix in (0, 1):
        for iy in (0, 1):
            for iz in (0, 1):
                nb = ix + iy + iz
                kind = {0: "a3", 1: "a2b", 2: "ab2", 3: "b3"}[nb]
                # along each axis: a-part = [B, A+B] (near), b-part = [0, B] (far), pushed back by GAP
                x0 = B if ix == 0 else -GAP; y0 = B if iy == 0 else -GAP; z0 = B if iz == 0 else -GAP
                sx = A if ix == 0 else B; sy = A if iy == 0 else B; sz = A if iz == 0 else B
                out.append((x0 - 0.50, y0 - 0.50, z0 - 0.42, sx, sy, sz, kind))
    out.sort(key=lambda b: (b[0] + b[3] / 2) + (b[1] + b[4] / 2) + (b[2] + b[5] / 2))   # far (small sum) first
    return out


def draw_cube():
    im = Image.new("RGBA", (N * 2, N * 2), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    k = 2 * SS
    for (x, y, z, sx, sy, sz, kind) in blocks():
        base = np.array(COL[kind], np.float32)
        top = [proj(x, y, z + sz), proj(x + sx, y, z + sz), proj(x + sx, y + sy, z + sz), proj(x, y + sy, z + sz)]
        right = [proj(x + sx, y, z), proj(x + sx, y + sy, z), proj(x + sx, y + sy, z + sz), proj(x + sx, y, z + sz)]
        left = [proj(x, y + sy, z), proj(x + sx, y + sy, z), proj(x + sx, y + sy, z + sz), proj(x, y + sy, z + sz)]
        for face, f in ((left, 0.62), (right, 0.84), (top, 1.12)):
            c = tuple(int(min(255, v * f)) for v in base) + (255,)
            d.polygon([(px * k, py * k) for px, py in face], fill=c)
            d.line([(px * k, py * k) for px, py in face + [face[0]]], fill=(255, 255, 255, 90), width=2 * SS)
    im = im.resize((N, N), Image.LANCZOS)
    a = np.asarray(im, np.float32) / 255.0
    return lin(a[..., :3] * 255) * 1.0, a[..., 3]


def sdf_png(dist_inside):
    v = np.clip(128 + 2.55 * dist_inside, 0, 255).astype(np.uint8)
    return Image.fromarray(np.dstack([v, v, v, v]))


def to_img(rgb, a=None):
    s = (srgb(rgb) * 255 + 0.5).astype(np.uint8)
    im = Image.fromarray(s) if a is None else Image.fromarray(np.dstack([s, (np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8)]))
    return im.resize((1024, 1024), Image.LANCZOS)


def main():
    os.makedirs("docs/store", exist_ok=True)
    disc_a = np.clip((512 - np.hypot(X - 512, Y - 512)) * SS + 0.5, 0, 1)
    one = np.ones((N, N), np.float32)
    back = background()
    o_rgb, o_back, o_front, o_glow = orbit()
    back = back + o_rgb * (o_glow * 0.5)[..., None]
    back, _ = over(back, one, o_rgb, o_back)
    g = np.exp(-((X - 512) ** 2 + (Y - 540) ** 2) / (2 * 260.0 ** 2))
    back = back + lin([140, 120, 255]) * (g * 0.16)[..., None]
    c_rgb, c_a = draw_cube()
    front, front_a = over(np.zeros((N, N, 3), np.float32), np.zeros((N, N), np.float32), c_rgb, c_a)
    front, front_a = over(front, front_a, o_rgb, o_front)
    to_img(back, disc_a).save("app/src/main/res/drawable/icon_3d_layer_0.png", optimize=True)
    to_img(front, front_a).save("app/src/main/res/drawable/icon_3d_layer_1.png", optimize=True)
    g1 = np.mgrid[0:1024, 0:1024].astype(np.float32) + 0.5
    sdf_png(512 - np.hypot(g1[1] - 512, g1[0] - 512)).save("app/src/main/res/drawable/icon_3d_sdf_0.png", optimize=True)
    sil = np.asarray(Image.fromarray((c_a * 255).astype(np.uint8)).resize((1024, 1024), Image.LANCZOS)) > 127
    sdf_png(np.where(sil, distance_transform_edt(sil), -distance_transform_edt(~sil))).save("app/src/main/res/drawable/icon_3d_sdf_1.png", optimize=True)
    flat, _ = over(back, one, front, front_a)
    to_img(flat, disc_a).save("app/src/main/res/mipmap-anydpi/ic_spatial_launcher.png", optimize=True)
    sq = to_img(flat); sq.save("docs/store/icon_1024.png", optimize=True)
    sq.resize((1424, 1424), Image.LANCZOS).save("app/src/main/res/drawable/ic_launcher_foreground.png", optimize=True)
    print("icons written")


if __name__ == "__main__":
    main()
