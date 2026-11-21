"""Package exactly one unchanged JSON animation in a reproducible dotLottie v2 container."""
import argparse
import hashlib
import io
import json
from pathlib import Path
import zipfile
import zlib


def package(source):
    target = io.BytesIO()
    manifest = b'{"version":"2","animations":[{"id":"circle"}],"initial":{"animation":"circle"}}'
    with zipfile.ZipFile(target, "w") as archive:
        for name, data in (("manifest.json", manifest), ("a/circle.json", source)):
            entry = zipfile.ZipInfo(name, (1980, 1, 1, 0, 0, 0))
            entry.create_system = 3
            entry.external_attr = 0o100644 << 16
            archive.writestr(entry, data, compress_type=zipfile.ZIP_DEFLATED, compresslevel=9)
    with zipfile.ZipFile(target) as archive:
        assert archive.read("a/circle.json") == source
        assert json.loads(archive.read("manifest.json"))["version"] == "2"
    return target.getvalue()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("out", type=Path)
    args = parser.parse_args()
    source = args.source.read_bytes()
    blob = package(source)
    args.out.parent.mkdir(parents=True, exist_ok=True)
    args.out.write_bytes(blob)
    print("format,bytes,zlib_level_9_bytes,sha256")
    for name, data in (("json", source), ("dotlottie-v2", blob)):
        print(name, len(data), len(zlib.compress(data, 9)), hashlib.sha256(data).hexdigest(), sep=",")
