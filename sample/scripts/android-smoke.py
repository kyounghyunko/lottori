"""Exercise the installed Android sample. Uses only adb and the Python standard library."""
from pathlib import Path
import argparse
import os
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET

args = argparse.ArgumentParser()
args.add_argument("serial", help="Emulator serial, for example emulator-5554")
args.add_argument("output", type=Path, help="Screenshot directory")
args.add_argument("--adb", default=str(Path(os.environ.get("ANDROID_HOME", "")) / "platform-tools/adb"))
options = args.parse_args()
if not re.fullmatch(r"emulator-\d+", options.serial):
    args.error("An Android emulator serial is required")
options.output.mkdir(parents=True, exist_ok=True)


def adb(*parts):
    return subprocess.check_output([options.adb, "-s", options.serial, *parts])


def locate(label, exact=True):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        result = adb("shell", "uiautomator", "dump", "/data/local/tmp/lottori-window.xml")
        if b"dumped to:" not in result:
            continue
        xml = ET.fromstring(adb("shell", "cat", "/data/local/tmp/lottori-window.xml"))
        for node in xml.iter("node"):
            for text in (node.get("text", ""), node.get("content-desc", "")):
                matches = text == label if exact else label in text
                if matches:
                    return list(map(int, re.findall(r"\d+", node.attrib["bounds"])))
        time.sleep(0.1)
    raise AssertionError(f"Missing accessibility label: {label}")


def tap(label):
    left, top, right, bottom = locate(label)
    adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))


def capture(name):
    (options.output / (name + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))


adb("shell", "am", "start", "-S", "-n", "com.ycgc.lottori.sample/.MainActivity")
locate("Pause")
locate("Animation preview")
tap("Moving circle")
tap("50%")
left, top, right, bottom = locate("Animation preview")
raw = adb("exec-out", "screencap")
width, height, fmt = struct.unpack_from("<III", raw)
assert fmt in (1, 2), f"Unsupported screenshot pixel format {fmt}"
rgba = memoryview(raw)[-width * height * 4:]
red = []
for y in range(top, bottom):
    for x in range(left, right):
        offset = 4 * (width * y + x)
        if rgba[offset] > 220 and rgba[offset + 1] < 30 and rgba[offset + 2] < 30:
            red.append((x, y))
assert len(red) >= 16, "Red circle is missing"
for axis, expected in enumerate(((left + right - 1) / 2, (top + bottom - 1) / 2)):
    assert abs(sum(p[axis] for p in red) / len(red) - expected) < 2, "Circle is off-center"
capture("moving-050")
tap("Trim path")
locate("Static fallback")
locate("TrimPath  $.layers[0].shapes[0].it[1]  checkmark", exact=False)
capture("trim-fallback")
tap("Damaged JSON")
locate("invalid_json", exact=False)
locate("Static status icon")
tap("ZIP")
locate("UnsupportedContainer", exact=False)
tap("Static circle")
locate("Animation preview")
print("Verified the midpoint, static circle, and fallback screens.")
