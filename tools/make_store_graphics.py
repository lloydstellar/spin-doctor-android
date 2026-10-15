"""Builds the Play Store graphics from real phone screenshots, in the look of
lloydstellar.nl: the void with its nebula, Orbitron headings, an amber stroke.

  tools/shots/raw/*.png   screenshots from the phone (1080x2400), record.png
  tools/store/screen-*.png  9:16 phone screenshots with a caption, for Play
  tools/store/feature-1024x500.png  the feature graphic

Play wants phone screenshots no more than twice as tall as wide, so the status
and navigation bars are cut off and the screen sits under a caption.
Rendered with headless Chromium so the fonts are the app's own.
Run: python3 tools/make_store_graphics.py
"""
import os
import subprocess
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RAW = os.path.join(ROOT, 'tools/shots/raw')
STORE = os.path.join(ROOT, 'tools/store')
FONTS = os.path.join(ROOT, 'app/src/main/assets/fonts')

# order is the order in the store; the first two are what most people see
SCREENS = [
    ('05-style.png', 'Your release on a spinning record'),
    ('04-segment.png', 'Pick the part people should hear'),
    ('02-crop.png', 'Your artwork becomes the label'),
    ('03-tracks.png', 'A whole EP or album at once'),
    ('06-progress.png', 'A 30 second clip in about 15 seconds'),
    ('07-results.png', 'Straight to your gallery, ready for Reels and TikTok'),
    ('01-home.png', 'Free, without a watermark or an account'),
]

BASE_CSS = f"""
@font-face {{ font-family: Orbitron; src: url(file://{FONTS}/orbitron.ttf); font-weight: 400 900; }}
@font-face {{ font-family: Mono; src: url(file://{FONTS}/share_tech_mono.ttf); }}
* {{ margin: 0; padding: 0; box-sizing: border-box; }}
html, body {{ width: 100%; height: 100%; overflow: hidden; }}
body {{
  background:
    radial-gradient(900px 620px at 85% -10%, rgba(139, 92, 246, 0.16), transparent 62%),
    radial-gradient(760px 540px at -12% 18%, rgba(77, 224, 255, 0.12), transparent 60%),
    radial-gradient(1100px 760px at 50% 115%, rgba(255, 77, 219, 0.08), transparent 65%),
    #01030c;
  color: #CDF4FF;
}}
h1 {{
  font-family: Orbitron; font-weight: 800; letter-spacing: 1px;
  background: linear-gradient(180deg, #ffffff, #CDF4FF 55%, #4DE0FF);
  -webkit-background-clip: text; background-clip: text; color: transparent;
  filter: drop-shadow(0 0 10px rgba(77, 224, 255, 0.35));
}}
.stroke {{ height: 4px; background: linear-gradient(90deg, #FFB000, transparent); }}
.mono {{ font-family: Mono; color: #56b8d8; letter-spacing: 3px; }}
"""


def render(html, width, height, out):
    with tempfile.NamedTemporaryFile('w', suffix='.html', delete=False) as f:
        f.write(html)
        page = f.name
    try:
        subprocess.run([
            'chromium', '--headless=new', '--disable-gpu', '--hide-scrollbars',
            '--allow-file-access-from-files', '--force-device-scale-factor=1',
            f'--window-size={width},{height}', f'--screenshot={out}', '--virtual-time-budget=4000',
            f'file://{page}',
        ], check=True, capture_output=True)
    finally:
        os.unlink(page)
    print('wrote', os.path.relpath(out, ROOT))


def screen(name, caption, index):
    shot = os.path.join(RAW, name)
    # 1080x2400 phone screen without status bar (120 px) and navigation bar (120 px)
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>{BASE_CSS}
    .cap {{ position: absolute; left: 80px; right: 80px; top: 110px; }}
    .cap h1 {{ font-size: 64px; line-height: 1.18; }}
    .cap .stroke {{ width: 180px; margin-top: 30px; }}
    .phone {{
      position: absolute; left: 50%; bottom: 0; width: 780px; height: 1440px; transform: translateX(-50%);
      overflow: hidden; border: 2px solid rgba(77, 224, 255, 0.45); border-bottom: none;
      clip-path: polygon(34px 0, 100% 0, 100% 100%, 0 100%, 0 34px);
      box-shadow: 0 0 60px rgba(77, 224, 255, 0.12);
    }}
    .phone img {{ display: block; width: 780px; margin-top: -87px; }}
    </style></head><body>
      <div class="cap"><h1>{caption}</h1><div class="stroke"></div></div>
      <div class="phone"><img src="file://{shot}"></div>
    </body></html>"""
    render(html, 1080, 1920, os.path.join(STORE, f'screen-{index:02d}.png'))


def feature():
    record = os.path.join(RAW, 'record.png')
    html = f"""<!doctype html><html><head><meta charset="utf-8"><style>{BASE_CSS}
    .text {{ position: absolute; left: 64px; top: 128px; width: 520px; }}
    .kicker {{ font-size: 22px; letter-spacing: 8px; }}
    h1 {{ font-size: 86px; line-height: 0.98; margin-top: 10px; }}
    .stroke {{ width: 150px; margin: 22px 0 22px; }}
    .line {{ font-family: Mono; font-size: 27px; color: #CDF4FF; letter-spacing: 0.5px; line-height: 1.3; }}
    .rec {{ position: absolute; right: -190px; top: -95px; width: 690px; height: 690px;
            filter: drop-shadow(-18px 22px 40px rgba(0, 0, 0, 0.75)); }}
    </style></head><body>
      <img class="rec" src="file://{record}">
      <div class="text">
        <div class="mono kicker">LLOYD'S</div>
        <h1>SPIN<br>DOCTOR</h1>
        <div class="stroke"></div>
        <div class="line">Spinning vinyl videos<br>for your releases</div>
      </div>
    </body></html>"""
    render(html, 1024, 500, os.path.join(STORE, 'feature-1024x500.png'))


def main():
    os.makedirs(STORE, exist_ok=True)
    for i, (name, caption) in enumerate(SCREENS, start=1):
        screen(name, caption, i)
    feature()


if __name__ == '__main__':
    main()
