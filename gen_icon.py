#!/usr/bin/env python3
"""生成「民宿记账」App 图标（纯手工 PNG，无第三方素材）。

- 启动器图标：ic_launcher.png（5 个密度）
设计：金黄色圆角底 + 白色账本卡片 + 绿色 ¥ 符号。
画布按 SS 倍超采样再降采样，得到抗锯齿边缘。

用法：
    python3 gen_icon.py            # 写 res/drawable-* 下的 PNG
    python3 gen_icon.py --preview  # 终端打印 ASCII 预览（本环境没有图像通道，靠它自检）
"""
import os
import sys
import struct
import zlib
import math

SS = 4

LAUNCH = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]

WHITE = (255, 255, 255, 255)
C_GOLD = (240, 180, 41, 255)     # #F0B429 底
C_MONEY = (46, 158, 126, 255)    # #2E9E7E ¥ 符号


def over(px, x, y, rgba):
    sr, sg, sb, sa = rgba
    if sa == 0:
        return
    dr, dg, db, da = px[y][x]
    a = sa / 255.0
    d = da / 255.0
    na = a + d * (1 - a)
    if na <= 0:
        px[y][x] = (0, 0, 0, 0)
        return
    px[y][x] = (
        int(round((sr * a + dr * d * (1 - a)) / na)),
        int(round((sg * a + dg * d * (1 - a)) / na)),
        int(round((sb * a + db * d * (1 - a)) / na)),
        int(round(na * 255)),
    )


class Canvas(object):
    def __init__(self, n, bg=None, radius_ratio=0.22):
        self.n = n
        self.px = [[(0, 0, 0, 0) for _ in range(n)] for _ in range(n)]
        if bg is not None:
            self.rrect(0.0, 0.0, float(n), float(n), n * radius_ratio, bg)

    def rrect(self, x0, y0, x1, y1, r, color):
        n = self.n
        for y in range(max(0, int(y0)), min(n, int(math.ceil(y1)))):
            cy = y + 0.5
            for x in range(max(0, int(x0)), min(n, int(math.ceil(x1)))):
                cx = x + 0.5
                dx = max(x0 + r - cx, 0.0, cx - (x1 - r))
                dy = max(y0 + r - cy, 0.0, cy - (y1 - r))
                if dx * dx + dy * dy <= r * r:
                    over(self.px, x, y, color)

    def disc(self, cx, cy, r, color):
        n = self.n
        for y in range(max(0, int(cy - r - 1)), min(n, int(cy + r + 2))):
            for x in range(max(0, int(cx - r - 1)), min(n, int(cx + r + 2))):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r:
                    over(self.px, x, y, color)

    def line(self, x0, y0, x1, y1, w, color):
        n = self.n
        half = w / 2.0
        for y in range(max(0, int(min(y0, y1) - half - 1)),
                       min(n, int(math.ceil(max(y0, y1) + half + 1)))):
            for x in range(max(0, int(min(x0, x1) - half - 1)),
                           min(n, int(math.ceil(max(x0, x1) + half + 1)))):
                px, py = x + 0.5, y + 0.5
                dx, dy = x1 - x0, y1 - y0
                L2 = dx * dx + dy * dy
                t = 0.0 if L2 == 0 else max(0.0, min(1.0, ((px - x0) * dx + (py - y0) * dy) / L2))
                if (px - x0 - t * dx) ** 2 + (py - y0 - t * dy) ** 2 <= half * half:
                    over(self.px, x, y, color)


def downsample(px, ss):
    n = len(px)
    m = n // ss
    out = [[(0, 0, 0, 0) for _ in range(m)] for _ in range(m)]
    area = ss * ss
    for oy in range(m):
        for ox in range(m):
            ar = ag = ab = aa = 0.0
            for sy in range(ss):
                row = px[oy * ss + sy]
                for sx in range(ss):
                    r, g, b, a = row[ox * ss + sx]
                    af = a / 255.0
                    ar += r * af
                    ag += g * af
                    ab += b * af
                    aa += af
            if aa <= 0.0:
                out[oy][ox] = (0, 0, 0, 0)
            else:
                out[oy][ox] = (
                    int(round(ar / aa)),
                    int(round(ag / aa)),
                    int(round(ab / aa)),
                    int(round(aa / area * 255)),
                )
    return out


def write_png(path, px):
    h = len(px)
    w = len(px[0])
    raw = b"".join(
        b"\x00" + b"".join(struct.pack("BBBB", *px[y][x]) for x in range(w))
        for y in range(h)
    )

    def chunk(tag, data):
        return struct.pack(">I", len(data)) + tag + data + struct.pack(
            ">I", zlib.crc32(tag + data) & 0xFFFFFFFF)

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)


# ============================ 图标设计 ============================
# 基准：192×192 网格。金色圆角底 + 白色账本 + 绿色 ¥。

def ico_launcher(cv):
    cv.rrect(0, 0, cv.n, cv.n, cv.n * 0.22, C_GOLD)
    s = cv.n / 192.0

    def S(v):
        return v * s

    # 白色账本卡片
    cv.rrect(S(46), S(38), S(146), S(154), S(12), WHITE)
    # 书脊
    cv.rrect(S(46), S(38), S(62), S(154), S(12), (172, 188, 204, 255))
    # ¥ 符号（两臂 + 竖笔 + 两横）
    cv.line(S(76), S(62), S(96), S(86), S(11), C_MONEY)
    cv.line(S(116), S(62), S(96), S(86), S(11), C_MONEY)
    cv.line(S(96), S(78), S(96), S(134), S(11), C_MONEY)
    cv.line(S(74), S(98), S(118), S(98), S(8), C_MONEY)
    cv.line(S(74), S(114), S(118), S(114), S(8), C_MONEY)


ICONS = [("ic_launcher", ico_launcher, LAUNCH)]


def render(fn, size, ss=SS):
    cv = Canvas(size * ss)
    fn(cv)
    return downsample(cv.px, ss)


def preview(name, px, cols=44):
    """ASCII 打印，用于人工核对形状。"""
    n = len(px)
    rows = cols // 2
    print("-- %s --" % name)
    for oy in range(rows):
        line = ""
        for ox in range(cols):
            acc = [0, 0, 0, 0]
            cnt = 0
            for sy in range(oy * n // rows, (oy + 1) * n // rows):
                for sx in range(ox * n // cols, (ox + 1) * n // cols):
                    r, g, b, a = px[sy][sx]
                    acc[0] += r
                    acc[1] += g
                    acc[2] += b
                    acc[3] += a
                    cnt += 1
            r, g, b, a = [v // max(cnt, 1) for v in acc]
            if a < 60:
                line += " "
            elif r > 195 and g > 195 and b > 195:
                line += "#"
            else:
                line += "."
        print(line)
    print()


def main():
    proj = os.path.dirname(os.path.abspath(__file__))
    want_preview = "--preview" in sys.argv

    for name, fn, sizes in ICONS:
        if want_preview:
            preview(name, render(fn, 96))
            continue
        for dens, size in sizes:
            px = render(fn, size)
            path = os.path.join(proj, "res", "drawable-" + dens, name + ".png")
            write_png(path, px)
            print("  %-26s %3dpx" % (os.path.relpath(path, proj), size))
    if not want_preview:
        print("  图标生成完毕")


if __name__ == "__main__":
    main()
