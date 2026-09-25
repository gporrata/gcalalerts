#!/usr/bin/env python3
"""Generates the Tibetan-bell (ghanta) launcher icon + notification icon as Android vector drawables,
and optional SVG/PNG previews. Run from the repo root: python3 tools/gen_icon.py [preview_dir]"""
import os, subprocess, sys

BG = "#0F4C5C"          # deep teal background
BRASS = "#D4A437"
BRASS_LIGHT = "#F2D27A"
BRASS_DARK = "#9C7020"
BRASS_BAND = "#B8862B"

# (pathData, fill, stroke, strokeWidth, in_silhouette) in a 108x108 viewport (adaptive icon)
SHAPES = [
    # vajra finial: side prongs (stroked) + central prong
    ("M54,22.5 C48.5,24 48,29.5 51.5,32", None, BRASS, 1.8, True),
    ("M54,22.5 C59.5,24 60,29.5 56.5,32", None, BRASS, 1.8, True),
    ("M54,20.5 L56.3,27 L54,32 L51.7,27 Z", BRASS_LIGHT, None, 0, True),
    # collar under vajra
    ("M49.5,31.5 h9 a1.2,1.2 0 0 1 0,2.4 h-9 a1.2,1.2 0 0 1 0,-2.4 z", BRASS, None, 0, True),
    # handle shaft + knob
    ("M52.4,33.9 h3.2 v9.2 h-3.2 z", BRASS, None, 0, True),
    ("M50.8,38.5 a3.2,1.9 0 1 0 6.4,0 a3.2,1.9 0 1 0 -6.4,0 z", BRASS_LIGHT, None, 0, True),
    # dome/crown on top of the bell
    ("M48.5,47 C48.5,43.8 51,42.5 54,42.5 C57,42.5 59.5,43.8 59.5,47 Z", BRASS, None, 0, True),
    # flared bell body
    ("M47,48 C47,46.8 48,46 49.5,46 L58.5,46 C60,46 61,46.8 61,48 L63.2,63 "
     "C64,69 67,72.5 71,73.5 L71,76 L37,76 L37,73.5 C41,72.5 44,69 44.8,63 Z", BRASS, None, 0, True),
    # right-side shading
    ("M56,46 L58.5,46 C60,46 61,46.8 61,48 L63.2,63 C64,69 67,72.5 71,73.5 L71,76 L61,76 "
     "C60.5,68 59,58 56,46 Z", BRASS_DARK, None, 0, False),
    # left highlight
    ("M49.6,49 C49,55 47.6,63 45.6,69 C47.8,66 49.8,58 50.8,49 Z", BRASS_LIGHT, None, 0, False),
    # decorative band
    ("M45.8,56.5 L62.2,56.5 L62.6,59.5 L45.4,59.5 Z", BRASS_BAND, None, 0, False),
    # rim band
    ("M36,72.8 h36 a1.5,1.5 0 0 1 1.5,1.5 v2.2 a1.5,1.5 0 0 1 -1.5,1.5 h-36 "
     "a1.5,1.5 0 0 1 -1.5,-1.5 v-2.2 a1.5,1.5 0 0 1 1.5,-1.5 z", BRASS_BAND, None, 0, True),
    ("M36,72.8 h36 a1.5,1.5 0 0 1 1.5,1.5 v0.3 h-39 v-0.3 a1.5,1.5 0 0 1 1.5,-1.5 z", BRASS_LIGHT, None, 0, False),
    # clapper
    ("M51.8,80.3 a2.2,2.2 0 1 0 4.4,0 a2.2,2.2 0 1 0 -4.4,0 z", BRASS_DARK, None, 0, True),
]


# Launcher foreground: scale the bell to 88% around its center and center it vertically (keeps it in the 66dp safe zone)
SCALE, PIVOT, SHIFT_Y = 0.88, (54, 51.5), 2.5


def vector_xml(size_dp, vw, vh, shapes, mono=None, translate=(0, 0), scaled=False):
    out = [f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
           f'    android:width="{size_dp}dp" android:height="{size_dp}dp"\n'
           f'    android:viewportWidth="{vw}" android:viewportHeight="{vh}">',
           (f'    <group android:pivotX="{PIVOT[0]}" android:pivotY="{PIVOT[1]}" android:scaleX="{SCALE}" '
            f'android:scaleY="{SCALE}" android:translateY="{SHIFT_Y}">' if scaled else
            f'    <group android:translateX="{translate[0]}" android:translateY="{translate[1]}">')]
    for d, fill, stroke, sw, sil in shapes:
        if mono and not sil:
            continue
        attrs = [f'android:pathData="{d}"']
        if fill:
            attrs.append(f'android:fillColor="{mono or fill}"')
        if stroke:
            attrs += [f'android:strokeColor="{mono or stroke}"', f'android:strokeWidth="{sw}"',
                      'android:strokeLineCap="round"']
        out.append("        <path " + "\n            ".join(attrs) + " />")
    out += ["    </group>", "</vector>", ""]
    return "\n".join(out)


def svg(shapes, view="0 0 108 108", bg=True, mono=None, size=512, scaled=True):
    parts = [f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view}" width="{size}" height="{size}">']
    if bg:
        parts.append(f'<rect x="-10" y="-10" width="128" height="128" fill="{BG}"/>')
    if scaled:
        px, py = PIVOT
        parts.append(f'<g transform="translate(0,{SHIFT_Y}) translate({px},{py}) scale({SCALE}) translate({-px},{-py})">')
    for d, fill, stroke, sw, sil in shapes:
        if mono and not sil:
            continue
        parts.append(f'<path d="{d}" fill="{(mono or fill) if fill else "none"}" '
                     + (f'stroke="{mono or stroke}" stroke-width="{sw}" stroke-linecap="round"' if stroke else '') + '/>')
    if scaled:
        parts.append("</g>")
    parts.append("</svg>")
    return "\n".join(parts)


res = "app/src/main/res"
open(f"{res}/drawable/ic_launcher_foreground.xml", "w").write(vector_xml(108, 108, 108, SHAPES, scaled=True))
open(f"{res}/drawable/ic_launcher_monochrome.xml", "w").write(vector_xml(108, 108, 108, SHAPES, mono="#FFFFFFFF", scaled=True))
# Notification small icon: white silhouette, bell spans x 34.5..73.5, y 20.5..82.5 -> 66x66 viewport
open(f"{res}/drawable/ic_bell.xml", "w").write(
    vector_xml(24, 66, 66, SHAPES, mono="#FFFFFFFF", translate=(-21, -18.5)))

if len(sys.argv) > 1:
    d = sys.argv[1]
    os.makedirs(d, exist_ok=True)
    files = {
        "icon_full_layers_512": svg(SHAPES),                               # full 108dp canvas
        "icon_circle_512": svg(SHAPES, view="18 18 72 72"),                # launcher-visible area (circle-masked below)
        "icon_monochrome_512": svg(SHAPES, view="18 18 72 72", mono="#FFFFFF"),
        "notification_icon_96": svg(SHAPES, view="34.5 20.5 39 62", bg=False, mono="#FFFFFF", size=96, scaled=False),
    }
    for name, s in files.items():
        p = os.path.join(d, name + ".svg")
        open(p, "w").write(s)
        subprocess.run(["rsvg-convert", p, "-o", os.path.join(d, name + ".png")], check=True)
        os.remove(p)
    circle = ["(", "-size", "512x512", "xc:none", "-fill", "white", "-draw", "circle 256,256 256,0", ")",
              "-compose", "CopyOpacity", "-composite"]
    for name in ("icon_circle_512", "icon_monochrome_512"):
        p = os.path.join(d, name + ".png")
        extra = ["-fill", "#2B3A42", "-opaque", BG] if "mono" in name else []
        subprocess.run(["convert", p] + extra + circle + [p], check=True)
    p = os.path.join(d, "notification_icon_96.png")
    subprocess.run(["convert", p, "-background", "#333333", "-flatten", p], check=True)
