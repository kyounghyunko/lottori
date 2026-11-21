#!/usr/bin/env python3
"""Launch one fresh benchmark process per scenario on an emulator or simulator."""
import argparse
import datetime
import json
import math
import os
from pathlib import Path
import statistics
import subprocess
import time
import uuid

BUNDLE = "com.ycgc.lottori.sample.perf"

def scenarios(rounds, compare):
    cases = [0, 1, 10]
    engines = ["lottori", "compottie-lite"] if compare else ["lottori"]
    for index in range(rounds):
        offset = index % len(cases)
        for icons in cases[offset:] + cases[:offset]:
            for engine in (reversed(engines) if index % 2 else engines):
                yield index + 1, icons, engine

def command(*args, check=True, env=None):
    return subprocess.run(args, env=env, text=True, check=check, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE).stdout.strip()

def runner_android(serial, artifact):
    if not serial.startswith("emulator-"):
        raise ValueError("Specify an Android emulator serial (emulator-N).")
    adb = [str(Path(os.environ["ANDROID_HOME"]) / "platform-tools/adb"), "-s", serial]
    if command(*adb, "shell", "getprop", "ro.boot.qemu") != "1" and command(
            *adb, "shell", "getprop", "ro.kernel.qemu") != "1":
        raise ValueError("Selected target does not report an emulator.")
    if artifact:
        command(*adb, "install", "-r", str(artifact))
    identity = {k: command(*adb, "shell", "getprop", k) for k in
                ("ro.product.model", "ro.build.version.release", "ro.product.cpu.abi", "ro.build.fingerprint")}
    def run(icons, nonce, engine="lottori"):
        command(*adb, "shell", "am", "force-stop", BUNDLE)
        command(*adb, "shell", "am", "start", "-W", "-n", BUNDLE + "/com.ycgc.lottori.sample.MainActivity",
                "--ei", "icons", str(icons), "--es", "run", nonce, "--es", "engine", engine)
        pid = command(*adb, "shell", "pidof", BUNDLE)
        for _ in range(100):
            lines = command(*adb, "logcat", "-d", "--pid=" + pid, "-v", "raw", "-s", "LottoriPerf:I").splitlines()
            if "DONE" in lines:
                chunks = [s.split(" ", 2)[2] for s in lines if s.startswith("PART ")]
                result = json.loads("".join(chunks))
                if result["id"] == nonce:
                    return result
            time.sleep(.25)
        raise RuntimeError("Android benchmark timed out: " + command(*adb, "logcat", "-d", "--pid=" + pid,
                                                                     "-t", "100")[-4000:])
    return identity, run

def runner_ios(device, artifact, trace_malloc=False):
    simctl = ["xcrun", "simctl"]
    devices = json.loads(command(*simctl, "list", "devices", "booted", "--json"))["devices"]
    selected = [(runtime, d) for runtime, items in devices.items() for d in items if d["udid"] == device]
    if len(selected) != 1:
        raise ValueError("Specify a booted iOS simulator UUID.")
    if artifact:
        command(*simctl, "install", device, str(artifact))
    documents = Path(command(*simctl, "get_app_container", device, BUNDLE, "data")) / "Documents"
    runtime, info = selected[0]
    def run(icons, nonce, engine="lottori"):
        command(*simctl, "terminate", device, BUNDLE, check=False)
        env = dict(os.environ, SIMCTL_CHILD_LOTTORI_ICONS=str(icons), SIMCTL_CHILD_LOTTORI_RUN=nonce,
                   SIMCTL_CHILD_LOTTORI_ENGINE=engine)
        if trace_malloc:
            env["SIMCTL_CHILD_MallocStackLoggingNoCompact"] = "1"
            env.pop("SIMCTL_CHILD_MallocStackLogging", None)
        launch = command(*simctl, "launch", device, BUNDLE, env=env)
        for _ in range(100):
            try:
                result = json.loads((documents / "lottori-perf.json").read_text())
                if result["id"] == nonce:
                    if trace_malloc:
                        result["pid"] = int(launch.rsplit(":", 1)[1])
                    return result
            except (FileNotFoundError, json.JSONDecodeError):
                pass
            time.sleep(.25)
        raise RuntimeError("iOS benchmark timed out.")
    return {"model":info["name"], "runtime":runtime, "udid":device}, run

def quantile(values, p):
    if not values:
        return None
    return sorted(values)[math.ceil(len(values)*p)-1]

def validate_run(result, nonce, icons, engine, compare):
    if (result["id"] != nonce or result["icons"] != icons
            or result.get("engine", None if compare else "lottori") != engine):
        raise ValueError(f"Unexpected result for {engine}, {icons} icons, run {nonce}")
    frames = result["frames"]
    if (icons and frames < 90) or (not icons and frames not in (0, 1)):
        raise ValueError(f"{engine}, {icons} icons: observed {frames} frames")
    if compare or "iconDraws" in result:
        if result.get("iconDraws") != [frames] * max(1, icons):
            raise ValueError(f"{engine}, {icons} icons: inconsistent per-icon draws {result.get('iconDraws')}")
    if (result["decodeNs"] <= 0 or result["firstDrawNs"] < result["decodeNs"]
            or len(result["clockNs"]) != 120 or sum(result["clockNs"]) <= 0
            or len(result["paintNs"]) != frames or len(result["intervalNs"]) != max(0, frames - 1)):
        raise ValueError(f"Incomplete timing data for {engine}, {icons} icons, run {nonce}")

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["android","ios"])
    parser.add_argument("device")
    parser.add_argument("--install", type=Path, help="Release APK or .app from the perf build")
    parser.add_argument("--rounds", type=int, default=5)
    parser.add_argument("--compare", action="store_true", help="Pair each Lottori run with Compottie lite")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--resume", action="store_true", help="Continue a partial run on the same device")
    args = parser.parse_args()
    if args.rounds < 1:
        parser.error("--rounds must be positive")
    identity, run = (runner_android if args.platform == "android" else runner_ios)(args.device,args.install)
    results = []
    if args.resume and args.output.exists():
        previous = json.loads(args.output.read_text())
        if (previous["device"] != identity or previous["rounds"] != args.rounds
                or previous.get("compare", False) != args.compare):
            parser.error("The partial run uses a different device, round count, or comparison mode")
        results = previous["results"]
    schedule = list(scenarios(args.rounds, args.compare))
    if len(results) > len(schedule):
        parser.error("The partial run contains more results than scheduled")
    for index, (round_num, icons, engine) in enumerate(schedule):
        if index < len(results):
            saved = results[index]
            validate_run(saved, saved["id"], icons, engine, args.compare)
            continue
        nonce = uuid.uuid4().hex
        result = run(icons, nonce, engine)
        validate_run(result, nonce, icons, engine, args.compare)
        results.append(result)
        args.output.parent.mkdir(parents=True,exist_ok=True)
        args.output.write_text(json.dumps({"device":identity,"timestamp":datetime.datetime.now(
            datetime.timezone.utc).isoformat(),"rounds":args.rounds,"compare":args.compare,"results":results}) + "\n")
        print(args.platform, round_num, engine, icons, result["frames"], result["firstDrawNs"], flush=True)
    for engine in (["lottori", "compottie-lite"] if args.compare else ["lottori"]):
        print(engine)
        summarize([r for r in results if r.get("engine", "lottori") == engine])

def summarize(results):
    for icons in (0,1,10):
        rows = [r for r in results if r["icons"] == icons]
        for field,p in [("decodeNs",0.5),("firstDrawNs",0.5),("intervalNs",0.5),("intervalNs",0.95),
                        ("paintNs",0.5),("paintNs",0.95),("clockNs",0.95)]:
            data = [quantile(r[field],p) if isinstance(r[field],list) else r[field] for r in rows]
            data = [n/1e6 for n in data if n is not None]
            if data:
                print(icons,field,p,"ms median [min,max]",*[round(n,3) for n in
                      (statistics.median(data),min(data),max(data))])

if __name__ == "__main__":
    main()
