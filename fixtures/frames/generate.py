"""Recreate reference PNGs from the circle equation using only Python's standard library."""
from pathlib import Path
import struct
import zlib


def chunk(tag, data):
    checksum = zlib.crc32(tag + data) & 0xffffffff
    return struct.pack(">I", len(data)) + tag + data + struct.pack(">I", checksum)


def write_circle(filename, cx):
    width, height, radius, samples = 100, 40, 6, 16
    offsets = [(s + 0.5) / samples for s in range(samples)]
    data = bytearray()
    for y in range(height):
        data.append(0)
        for x in range(width):
            covered = sum((x + dx - cx)**2 + (y + dy - 20)**2 <= radius**2
                          for dx in offsets for dy in offsets)
            alpha = round(255 * covered / samples**2)
            data.extend((255 if alpha else 0, 0, 0, alpha))
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    Path(__file__).with_name(filename).write_bytes(
        b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
        + chunk(b"IDAT", zlib.compress(data)) + chunk(b"IEND", b""))


if __name__ == "__main__":
    for progress, center in [(0, 20), (50, 50), (100, 78)]:
        write_circle(f"moving-circle-{progress:03d}.png", center)
