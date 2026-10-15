#!/bin/sh
# Renders the parity scenes with the website's app.js in headless Chromium.
# usage: tools/parity/render-web.sh path/to/vinyl/js/app.js out-dir
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
APPJS=$(realpath "$1")
OUT=$2
mkdir -p "$OUT"
python3 - "$HERE" "$APPJS" <<'PY'
import base64, sys
here, appjs = sys.argv[1], sys.argv[2]
page = open(here + '/harness.html').read()
page = page.replace('APPJS', 'file://' + appjs)
page = page.replace('SCENES', open(here + '/scenes.json').read())
page = page.replace('COVER', base64.b64encode(open(here + '/cover.png', 'rb').read()).decode())
open(here + '/.harness-run.html', 'w').write(page)
PY
chromium --headless=new --disable-gpu --allow-file-access-from-files --virtual-time-budget=60000 \
  --dump-dom "file://$HERE/.harness-run.html" 2>/dev/null > "$OUT/dom.html"
rm -f "$HERE/.harness-run.html"
python3 - "$OUT" <<'PY'
import base64, html, json, re, sys
out = sys.argv[1]
dom = open(out + '/dom.html').read()
m = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
data = json.loads(html.unescape(m.group(1)))
if 'error' in data: sys.exit('harness error: ' + data['error'])
for name, url in data.items():
    open(f'{out}/{name}-web.png', 'wb').write(base64.b64decode(url.split(',', 1)[1]))
    print('wrote', name)
PY
