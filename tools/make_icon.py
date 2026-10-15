"""Draws the Spin Doctor icon from plain geometry: a black record on the
lloydstellar.nl navy, the two soft light fans of the app's renderer as flat
wedges, and an amber label. No artwork, no glow, no gradients.

Writes the adaptive icon layers and the notification icon as vector
drawables, plus tools/store/icon.svg and (with rsvg-convert) icon-512.png.
Run: python3 tools/make_icon.py
"""
import math
import os
import subprocess

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, 'app/src/main/res/drawable')
STORE = os.path.join(ROOT, 'tools/store')

SKY = '#05122E'        # between --crt-bg #04102a and --crt-bg-soft #061530
DISC = '#06080C'
GROOVE = '#151B24'
FAN = '#121B29'
LABEL = '#FFB000'      # --crt-amber
LABEL_RING = '#C98A00'
HOLE = '#01030C'       # --crt-bg-void

C = 54.0               # centre of the 108 dp adaptive canvas
R = 31.0               # record radius, inside the 66 dp safe zone
LIGHT = math.atan2(-0.2, -0.6)   # VINYL_LIGHT_ANGLE


def circle(cx, cy, r):
    return (f'M{cx - r:.3f},{cy:.3f} a{r:.3f},{r:.3f} 0 1,0 {2 * r:.3f},0 '
            f'a{r:.3f},{r:.3f} 0 1,0 {-2 * r:.3f},0 Z')


def ring(cx, cy, outer, inner):
    return circle(cx, cy, outer) + ' ' + circle(cx, cy, inner)


def wedge(cx, cy, r0, r1, a0, a1, steps=24):
    pts = []
    for i in range(steps + 1):
        a = a0 + (a1 - a0) * i / steps
        pts.append((cx + math.cos(a) * r1, cy + math.sin(a) * r1))
    for i in range(steps, -1, -1):
        a = a0 + (a1 - a0) * i / steps
        pts.append((cx + math.cos(a) * r0, cy + math.sin(a) * r0))
    return 'M' + ' L'.join(f'{x:.3f},{y:.3f}' for x, y in pts) + ' Z'


def shapes(cx=C, cy=C, r=R):
    """(fill, path, evenodd) in drawing order."""
    out = [(DISC, circle(cx, cy, r), False)]
    spread = math.radians(17)
    for axis in (LIGHT, LIGHT + math.pi):
        out.append((FAN, wedge(cx, cy, r * 0.40, r * 0.97, axis - spread, axis + spread), False))
    for f in (0.86, 0.72, 0.58):
        out.append((GROOVE, ring(cx, cy, r * f + 0.45, r * f - 0.45), True))
    out.append((LABEL, circle(cx, cy, r * 0.36), False))
    out.append((LABEL_RING, ring(cx, cy, r * 0.30 + 0.35, r * 0.30 - 0.35), True))
    out.append((HOLE, circle(cx, cy, r * 0.055), False))
    return out


def vector(paths, size=108, viewport=108, tint=None):
    body = []
    for fill, d, evenodd in paths:
        rule = ' android:fillType="evenOdd"' if evenodd else ''
        body.append(f'    <path android:fillColor="{fill}"{rule} android:pathData="{d}" />')
    t = f'\n    android:tint="{tint}"' if tint else ''
    return (f'<?xml version="1.0" encoding="utf-8"?>\n'
            f'<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            f'    android:width="{size}dp" android:height="{size}dp"\n'
            f'    android:viewportWidth="{viewport}" android:viewportHeight="{viewport}"{t}>\n'
            + '\n'.join(body) + '\n</vector>\n')


def write(name, text):
    with open(os.path.join(RES, name), 'w') as f:
        f.write(text)


def main():
    os.makedirs(RES, exist_ok=True)
    os.makedirs(STORE, exist_ok=True)

    write('ic_launcher_background.xml', vector([(SKY, 'M0,0 H108 V108 H0 Z', False)]))
    write('ic_launcher_foreground.xml', vector(shapes()))

    # monochrome: the record as a cut-out, the system tints it
    mono = [('#FFFFFFFF', ring(C, C, R, R * 0.36) + ' ' + circle(C, C, R * 0.2), True)]
    write('ic_launcher_monochrome.xml', vector(mono))

    # notification: 24 dp, white, a record with its label and hole
    n = 12.0
    note = [('#FFFFFFFF', ring(n, n, 10.5, 4.6) + ' ' + ring(n, n, 3.4, 1.0), True)]
    write('ic_notification.xml', vector(note, size=24, viewport=24))

    # store icon: full bleed square, Play adds the rounded mask itself
    svg = [f'<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108">',
           f'<rect width="108" height="108" fill="{SKY}"/>']
    for fill, d, evenodd in shapes(C, C, R * 1.3):
        rule = ' fill-rule="evenodd"' if evenodd else ''
        svg.append(f'<path fill="{fill}"{rule} d="{d}"/>')
    svg.append('</svg>')
    path = os.path.join(STORE, 'icon.svg')
    with open(path, 'w') as f:
        f.write('\n'.join(svg))
    try:
        subprocess.run(['rsvg-convert', '-w', '512', '-h', '512', path, '-o', os.path.join(STORE, 'icon-512.png')], check=True)
    except (OSError, subprocess.CalledProcessError) as e:
        print('rsvg-convert not available, only the SVG was written:', e)


if __name__ == '__main__':
    main()
