#!/usr/bin/env python3
"""Count process-wide native malloc events inside the iOS benchmark's sample window."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import uuid

from run import BUNDLE, command, runner_ios, scenarios, validate_run


ALLOCATION = re.compile(rb"^ALLOC (0x[0-9a-f]+)(?:-0x[0-9a-f]+)? \[size=(\d+)\]:")


def interval_allocations(events, markers):
    start, end = (address.encode() for address in markers)
    active = False
    start_hits = end_hits = count = total = 0
    for line in events:
        if not line.startswith(b"ALLOC "):
            continue
        match = ALLOCATION.match(line)
        if not match:
            raise ValueError(f"Unrecognized allocation event: {line[:100]!r}")
        address = match[1]
        size = int(match[2])
        if address == start and size == 29_999:
            start_hits += 1
            active = True
        elif address == end and size == 39_999:
            if not active:
                raise ValueError("Found the end marker outside the sample window")
            end_hits += 1
            active = False
        elif active:
            total += size
            count += 1
    if start_hits != 1 or end_hits != 1:
        raise ValueError(f"Expected unique malloc markers, found {start_hits} start and {end_hits} end")
    return {"mallocEvents": count, "mallocBytes": total}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("device", help="Booted iOS simulator UUID")
    parser.add_argument("--install", type=Path, help="Release perf .app with allocation markers")
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--compare", action="store_true", help="Pair each Lottori run with Compottie lite")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.rounds < 1:
        parser.error("--rounds must be positive")
    device, run = runner_ios(args.device, args.install, trace_malloc=True)
    report = {"device": device, "tool": "malloc_history -allEvents -noContent",
              "rounds": args.rounds, "compare": args.compare, "results": []}
    try:
        for round_number, icons, engine in scenarios(args.rounds, args.compare):
            nonce = uuid.uuid4().hex
            result = run(icons, nonce, engine)
            validate_run(result, nonce, icons, engine, args.compare)
            frames = result["frames"]
            markers = result.get("mallocMarkers")
            if not markers or len(markers) != 2:
                raise ValueError("Rebuild and install the iOS perf app with allocation markers")
            with subprocess.Popen(["malloc_history", str(result["pid"]), "-allEvents", "-noContent"],
                                  stdout=subprocess.PIPE) as history:
                metrics = interval_allocations(history.stdout, markers)
                if history.wait() != 0:
                    raise RuntimeError("malloc_history failed")
            if icons and metrics["mallocBytes"] == 0:
                raise ValueError("Playback produced no native malloc events")
            row = {"round": round_number, "engine": engine, "id": result["id"], "icons": icons,
                   "frames": frames, **metrics}
            report["results"].append(row)
            print(json.dumps(row), flush=True)
            args.output.parent.mkdir(parents=True, exist_ok=True)
            args.output.write_text(json.dumps(report, indent=2) + "\n")
    finally:
        command("xcrun", "simctl", "terminate", args.device, BUNDLE, check=False)


if __name__ == "__main__":
    main()
