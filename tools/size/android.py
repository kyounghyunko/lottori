"""Build four release bundles and compare bundletool download estimates for one device."""
import argparse
import csv
import io
import hashlib
import json
import shutil
import subprocess
import zipfile
from pathlib import Path

from assets import package


def run(*args, capture=False):
    process = subprocess.run([str(arg) for arg in args], cwd=root, check=True,
                             stdout=subprocess.PIPE if capture else None, text=True)
    return process.stdout


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("bundletool", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--device", type=Path, default=Path(__file__).with_name("device.json"))
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    bundletool = args.bundletool.resolve()
    tool = ["java", "-jar", bundletool]
    print("bundletool", run(*tool, "version", capture=True).strip(), flush=True)
    fixture = (root / "fixtures/moving-circle.json").read_bytes()
    container = out / "input.lottie"
    container.write_bytes(package(fixture))
    rows = []
    player_dex = None
    for mode in ["baseline", "player", "json", "dotlottie"]:
        run(root / "gradlew", ":sample:androidApp:bundleRelease", "--console=plain", "--max-workers=3",
            f"-PlottoriSampleMode={mode}", f"-PlottoriSampleAsset={container}")
        release = root / "sample/androidApp/build/outputs/bundle/release"
        (built,) = release.glob("*.aab")
        bundle = out / (mode + ".aab")
        shutil.copyfile(built, bundle)
        with zipfile.ZipFile(bundle) as z:
            assets = {n for n in z.namelist() if n.startswith("base/assets/") and
                      n.rsplit("/", 1)[1].endswith((".json", ".lottie"))}
            expected = {"json": "base/assets/input.json", "dotlottie": "base/assets/input.lottie"}.get(mode)
            assert assets == ({expected} if expected else set()), (mode, assets)
            if expected:
                assert z.read(expected) == (fixture if mode == "json" else container.read_bytes())
            names = sorted(n for n in z.namelist() if n.startswith("base/dex/") and n.endswith(".dex"))
            dex = [z.read(n) for n in names]
            player = any(b"UnsupportedContainer" in data for data in dex)
            fingerprint = [hashlib.sha256(data).hexdigest() for data in dex]
            if mode == "player":
                player_dex = fingerprint
            elif mode in ("json", "dotlottie"):
                assert fingerprint == player_dex, "The asset-only comparison changed executable code"
            assert player == (mode != "baseline"), f"unexpected optimized decoder contents in {bundle}"
        apks = out / (mode + ".apks")
        run(*tool, "build-apks", f"--bundle={bundle}", f"--output={apks}", "--overwrite",
            f"--device-spec={args.device.resolve()}")
        report = run(*tool, "get-size", "total", f"--apks={apks}", f"--device-spec={args.device.resolve()}",
                     capture=True)
        (out / (mode + ".csv")).write_text(report)
        result, = csv.DictReader(io.StringIO(report.strip()))
        low, high = int(result["MIN"]), int(result["MAX"])
        if low != high:
            raise ValueError(f"Device spec returned a size range for {mode}: {report}")
        rows.append({"mode": mode, "download_bytes": low, "bundle_bytes": bundle.stat().st_size})
    a, b = (row["download_bytes"] for row in rows[:2])
    for row in rows:
        row["delta_from_base"] = row["download_bytes"] - a
        row["delta_from_player"] = row["download_bytes"] - b
    results = {"device": json.loads(args.device.read_text()), "bundletool": run(*tool, "version", capture=True).strip(),
               "rows": rows}
    (out / "android.json").write_text(json.dumps(results, indent=2) + "\n")
    print(json.dumps(results, indent=2))
