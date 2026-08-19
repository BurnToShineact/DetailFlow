#!/usr/bin/env bash
set -euo pipefail

ADB="${ADB:-$(command -v adb || true)}"
if [[ -z "$ADB" ]]; then
    echo "FAIL: adb not found; add Android platform-tools to PATH or set ADB=/path/to/adb" >&2
    exit 2
fi
PACKAGE="com.detailflow.app"
TMP_DIR="$(mktemp -d /tmp/detailflow-ui-check.XXXXXX)"

"$ADB" shell am force-stop "$PACKAGE"
"$ADB" shell monkey -p "$PACKAGE" -c android.intent.category.LAUNCHER 1 >/dev/null
sleep 1
"$ADB" shell uiautomator dump /sdcard/detailflow-ui-check.xml >/dev/null
"$ADB" pull /sdcard/detailflow-ui-check.xml "$TMP_DIR/ui.xml" >/dev/null

DENSITY_RAW="$("$ADB" shell wm density | tr -d '\r')"
DENSITY="${DENSITY_RAW##*: }"

python3 - "$TMP_DIR/ui.xml" "$DENSITY" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, density_raw = sys.argv[1], sys.argv[2]
density = int(density_raw) / 160
root = ET.parse(xml_path).getroot()

def bounds(node):
    values = [int(v) for v in re.findall(r"\d+", node.attrib.get("bounds", ""))]
    return values if len(values) == 4 else [0, 0, 0, 0]

titles = [n for n in root.iter("node") if n.attrib.get("text") == "Сегодня" and n.attrib.get("clickable") == "false"]
if not titles:
    raise SystemExit("FAIL: main title not found")

title_top = bounds(titles[0])[1]
safe_top = round(24 * density)
screen_nodes = list(root.iter("node"))
screen_height = bounds(screen_nodes[0])[3] if screen_nodes else 0
safe_bottom = screen_height - round(24 * density)
nav_items = [n for n in root.iter("node") if n.attrib.get("content-desc", "").startswith("Открыть раздел")]
nav_with_icons = [n for n in nav_items if any(c.attrib.get("class") == "android.widget.ImageView" for c in n.iter("node"))]
nav_bottom = max((bounds(n)[3] for n in nav_items), default=screen_height)

errors = []
if title_top < safe_top:
    errors.append(f"title overlaps status bar: top={title_top}, expected>={safe_top}")
if len(nav_items) != 5:
    errors.append(f"expected 5 navigation items, found {len(nav_items)}")
if len(nav_with_icons) != 5:
    errors.append(f"navigation icons missing: {len(nav_with_icons)}/5")
if nav_bottom > safe_bottom:
    errors.append(f"navigation overlaps gesture area: bottom={nav_bottom}, expected<={safe_bottom}")

if errors:
    print("FAIL: " + "; ".join(errors))
    raise SystemExit(1)
print(f"PASS: title_top={title_top}; navigation_icons=5/5; navigation_bottom={nav_bottom}")
PY
