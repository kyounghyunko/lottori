"""Fingerprint the Kotlin player and measurement screens used in the size reports."""
import hashlib
from pathlib import Path

sources = ("lottori/src/commonMain",
           "sample/src/baseline", "sample/src/player")
root = Path(__file__).resolve().parents[2]
digest = hashlib.sha256()
for source in sources:
    for file in sorted((root / source).rglob("*.kt")):
        digest.update(file.relative_to(root).as_posix().encode() + b"\0" + file.read_bytes() + b"\0")
print(digest.hexdigest())
