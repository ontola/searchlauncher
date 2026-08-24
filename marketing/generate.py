#!/usr/bin/env python3
"""
Build store listing images: a headline over a framed device screenshot.

Everything that decides how an image looks lives in listing.json, so changing the
copy or the palette does not mean touching this file.

    python3 -m venv .venv && .venv/bin/pip install -r requirements.txt
    .venv/bin/python generate.py                 # every set
    .venv/bin/python generate.py phone           # one set

Sources are the plain device screenshots in ../screenshots. Output lands in out/<set>/.
"""

import json
import os
import sys
from PIL import Image, ImageDraw, ImageFont, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG = os.path.join(HERE, "listing.json")

# macOS ships this; the fallbacks cover Linux CI. First one that exists wins.
FONT_CANDIDATES = [
    "/System/Library/Fonts/SFNS.ttf",
    "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSans-Bold.ttf",
]


def font_path():
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return path
    raise SystemExit(
        "No usable font found. Add one to FONT_CANDIDATES in generate.py."
    )


def font(size, weight="Bold"):
    f = ImageFont.truetype(font_path(), size)
    try:
        # SFNS is a variable font; named instances give us real weights rather than
        # a faked bold. Fonts without them simply keep their single weight.
        f.set_variation_by_name(weight)
    except Exception:
        pass
    return f


def gradient(w, h, palette):
    top, bottom = tuple(palette["top"]), tuple(palette["bottom"])
    img = Image.new("RGB", (w, h))
    px = img.load()
    for y in range(h):
        t = (y / max(1, h - 1)) ** 0.9
        row = tuple(int(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
        for x in range(w):
            px[x, y] = row

    # A soft pool of light behind the headline, so the text is not sitting on a flat field.
    glow = Image.new("L", (w, h), 0)
    ImageDraw.Draw(glow).ellipse([-w // 3, -h // 4, w + w // 3, int(h * 0.55)], fill=120)
    glow = glow.filter(ImageFilter.GaussianBlur(radius=max(1, w // 6)))
    tint = Image.new("RGB", (w, h), tuple(palette["glow"]))
    return Image.composite(Image.blend(img, tint, 0.45), img, glow)


def wrap(draw, text, fnt, max_w):
    lines, cur = [], ""
    for word in text.split():
        trial = (cur + " " + word).strip()
        if draw.textlength(trial, font=fnt) <= max_w or not cur:
            cur = trial
        else:
            lines.append(cur)
            cur = word
    if cur:
        lines.append(cur)
    return lines


def rounded(img, radius):
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).rounded_rectangle([0, 0, img.size[0], img.size[1]], radius, fill=255)
    out = img.convert("RGBA")
    out.putalpha(mask)
    return out


def device(shot_path, width, radius_ratio=0.085, bezel_ratio=0.016):
    """The screenshot in a plain dark body, sized to the given width."""
    shot = Image.open(shot_path).convert("RGB")
    h = int(width * shot.height / shot.width)
    bezel = max(4, int(width * bezel_ratio))
    radius = int(width * radius_ratio)
    body = Image.new("RGBA", (width + bezel * 2, h + bezel * 2), (0, 0, 0, 0))
    ImageDraw.Draw(body).rounded_rectangle(
        [0, 0, body.size[0], body.size[1]], radius + bezel, fill=(12, 14, 12, 255)
    )
    body.alpha_composite(rounded(shot.resize((width, h), Image.LANCZOS), radius), (bezel, bezel))
    return body


def shadow_under(canvas, box, radius, blur, alpha=150):
    layer = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(layer).rounded_rectangle(box, radius, fill=(0, 0, 0, alpha))
    canvas.alpha_composite(layer.filter(ImageFilter.GaussianBlur(radius=blur)))


def compose(shot_path, headline, sub, out_path, spec, palette):
    W, H = spec["size"]
    landscape = W > H
    canvas = gradient(W, H, palette).convert("RGBA")
    draw = ImageDraw.Draw(canvas)
    text_col = tuple(palette["text"])
    sub_col = tuple(palette["subtext"])

    # Portrait stacks headline over device; landscape puts the text beside it, because a
    # wide canvas with a centred column of text leaves the sides empty.
    text_w = int(W * (0.42 if landscape else 0.84))
    title_px = int((W if not landscape else W * 0.5) * spec.get("title_scale", 0.072))
    ft = font(title_px, "Bold")
    fs = font(int(title_px * 0.5), "Regular")

    title_lines = wrap(draw, headline, ft, text_w)
    sub_lines = wrap(draw, sub, fs, text_w) if sub else []
    block_h = len(title_lines) * int(title_px * 1.18) + len(sub_lines) * int(title_px * 0.66)

    if landscape:
        tx = int(W * 0.07)
        y = (H - block_h) // 2
        align = "left"
    else:
        tx = None
        y = int(H * 0.058)
        align = "center"

    for line in title_lines:
        x = tx if align == "left" else (W - draw.textlength(line, font=ft)) / 2
        draw.text((x, y), line, font=ft, fill=text_col)
        y += int(title_px * 1.18)
    if sub_lines:
        y += int(title_px * 0.12)
        for line in sub_lines:
            x = tx if align == "left" else (W - draw.textlength(line, font=fs)) / 2
            draw.text((x, y), line, font=fs, fill=sub_col)
            y += int(title_px * 0.66)

    if landscape:
        # Fit to the canvas height, not its width: a portrait screenshot sized by width
        # overflows a wide canvas top and bottom, whatever the fraction.
        with Image.open(shot_path) as probe:
            aspect = probe.width / probe.height
        target_h = int(H * spec.get("device_height", 0.84))
        body = device(shot_path, max(1, int(target_h * aspect)), spec.get("radius", 0.085))
        dev_x = int(W - body.size[0] - W * 0.08)
        dev_y = (H - body.size[1]) // 2
    else:
        body = device(shot_path, int(W * spec["device_width"]), spec.get("radius", 0.085))
        dev_x = (W - body.size[0]) // 2
        dev_y = int(y + H * 0.035)

    shadow_under(
        canvas,
        [dev_x, dev_y + int(H * 0.012), dev_x + body.size[0], dev_y + body.size[1]],
        int(W * spec.get("radius", 0.085) * 1.1),
        int(W * 0.028),
    )
    canvas.alpha_composite(body, (dev_x, dev_y))
    canvas.convert("RGB").save(out_path, "PNG")
    return f"{os.path.relpath(out_path, HERE)}  {W}x{H}"


def main():
    cfg = json.load(open(CONFIG))
    wanted = sys.argv[1:] or [s["name"] for s in cfg["sets"]]
    for spec_set in cfg["sets"]:
        if spec_set["name"] not in wanted:
            continue
        device_spec = cfg["devices"][spec_set["device"]]
        palette = cfg["palettes"][spec_set["palette"]]
        src = os.path.join(HERE, spec_set["source"])
        out_dir = os.path.join(HERE, "out", spec_set["name"])
        os.makedirs(out_dir, exist_ok=True)

        missing = []
        for i, item in enumerate(spec_set["images"], start=1):
            shot = os.path.join(src, item["shot"])
            if not os.path.exists(shot):
                missing.append(item["shot"])
                continue
            stem = os.path.splitext(item["shot"])[0]
            out = os.path.join(out_dir, f"{i:02d}_{stem.split('_', 1)[-1]}.png")
            print(compose(shot, item["headline"], item.get("sub"), out, device_spec, palette))
        if missing:
            print(f"  ! {spec_set['name']}: no source screenshot for {', '.join(missing)}")


if __name__ == "__main__":
    main()
