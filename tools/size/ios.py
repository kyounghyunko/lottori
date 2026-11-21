"""Archive baseline, player, bundled JSON and bundled dotLottie for iPhoneOS."""
import argparse
import json
import hashlib
import os
import plistlib
import subprocess
from pathlib import Path

from assets import package


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("output", type=Path)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--unsigned", action="store_true", help="record local linked sizes without a provisioning team")
    mode.add_argument("--team", help="use an already provisioned Apple team and export the thinning report")
    parser.add_argument("--variant", default="iPhone18,1")
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[2]
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    fixture = (root / "fixtures/moving-circle.json").read_bytes()
    container = out / "input.lottie"
    container.write_bytes(package(fixture))
    rows = []
    player_framework = None
    for name in ("baseline", "player", "json", "dotlottie"):
        archive = out / (name + ".xcarchive")
        setting = {"ORG_GRADLE_PROJECT_lottoriSampleMode": name,
                   "ORG_GRADLE_PROJECT_lottoriSampleAsset": str(container)}
        signing = ["CODE_SIGNING_ALLOWED=NO"] if args.unsigned else [f"DEVELOPMENT_TEAM={args.team}"]
        subprocess.run(["xcodebuild", "-project", "sample/iosApp/LottoriSample.xcodeproj",
                        "-scheme", "LottoriSample", "-configuration", "Release", "-destination",
                        "generic/platform=iOS", "-derivedDataPath", out / "DerivedData", "-archivePath", archive,
                        *[f"{k}={v}" for k, v in setting.items()], *signing, "ARCHS=arm64", "archive"],
                       cwd=root, check=True, env={**os.environ, **setting})
        app, = (archive / "Products/Applications").glob("*.app")
        assets = {p.name for p in (app / "Fixtures").glob("*") if p.is_file()}
        expected = {"json": "input.json", "dotlottie": "input.lottie"}.get(name)
        assert assets == ({expected} if expected else set()), (name, assets)
        if expected:
            assert (app / "Fixtures" / expected).read_bytes() == (fixture if name == "json" else container.read_bytes())
        framework = app / "Frameworks/LottoriSampleKit.framework/LottoriSampleKit"
        digest = hashlib.sha256(framework.read_bytes()).hexdigest()
        if name == "player":
            player_framework = digest
        elif args.unsigned and name in ("json", "dotlottie"):
            assert digest == player_framework, "Bundling an asset changed the unsigned player framework"
        executable_name = plistlib.loads((app / "Info.plist").read_bytes())["CFBundleExecutable"]
        rows.append({"mode": name, "local_app_files_bytes": sum(p.stat().st_size for p in app.rglob("*") if p.is_file()),
                     "main_executable_bytes": (app / executable_name).stat().st_size,
                     "framework_bytes": framework.stat().st_size})
        if args.team:
            options = out / "export-options.plist"
            options.write_bytes(plistlib.dumps({"method": "release-testing", "signingStyle": "automatic",
                                                 "teamID": args.team, "thinning": args.variant}))
            export = out / (name + "-export")
            subprocess.run(["xcodebuild", "-exportArchive", "-archivePath", archive, "-exportPath", export,
                            "-exportOptionsPlist", options], check=True)
            report, = export.rglob("App Thinning Size Report.txt")
            print(name, report.read_text(), flush=True)
    (out / "ios-local.json").write_text(json.dumps(rows, indent=2) + "\n")
    print(json.dumps(rows, indent=2))
