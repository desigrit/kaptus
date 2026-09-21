"""Check 16 KB ELF LOAD and APK ZIP alignment for 64-bit native libraries.

Usage: python tools/check_native_load_alignment.py path/to/app.apk [another.apk]
"""

import struct
import sys
from pathlib import Path
from zipfile import ZIP_STORED, ZipFile

PAGE_SIZE = 16 * 1024
SUPPORTED_ABIS = {"arm64-v8a", "x86_64"}


def load_alignments(library: bytes) -> list[int]:
    if len(library) < 64 or library[:4] != b"\x7fELF" or library[4] != 2:
        raise ValueError("expected an ELF64 shared library")
    byte_order = {1: "<", 2: ">"}.get(library[5])
    if byte_order is None:
        raise ValueError("unknown ELF byte order")
    program_offset = struct.unpack_from(f"{byte_order}Q", library, 32)[0]
    entry_size, count = struct.unpack_from(f"{byte_order}HH", library, 54)
    if entry_size < 56 or program_offset + entry_size * count > len(library):
        raise ValueError("invalid ELF program headers")
    alignments = []
    for index in range(count):
        offset = program_offset + index * entry_size
        segment_type = struct.unpack_from(f"{byte_order}I", library, offset)[0]
        if segment_type == 1:  # PT_LOAD
            alignments.append(struct.unpack_from(f"{byte_order}Q", library, offset + 48)[0])
    if not alignments:
        raise ValueError("no ELF LOAD segments")
    return alignments


def stored_data_offset(apk_stream, header_offset: int) -> int:
    apk_stream.seek(header_offset)
    header = apk_stream.read(30)
    if len(header) != 30 or struct.unpack_from("<I", header)[0] != 0x04034B50:
        raise ValueError("invalid ZIP local header")
    filename_length, extra_length = struct.unpack_from("<HH", header, 26)
    return header_offset + 30 + filename_length + extra_length


def verify(apk_path: Path) -> list[str]:
    problems = []
    count = 0
    with apk_path.open("rb") as raw_apk, ZipFile(raw_apk) as apk:
        for entry in apk.infolist():
            parts = entry.filename.split("/")
            if len(parts) != 3 or parts[0] != "lib" or parts[1] not in SUPPORTED_ABIS or not parts[2].endswith(".so"):
                continue
            count += 1
            try:
                alignments = load_alignments(apk.read(entry))
                if any(alignment < PAGE_SIZE for alignment in alignments):
                    problems.append(f"{entry.filename}: LOAD alignment {alignments}")
                if entry.compress_type == ZIP_STORED:
                    offset = stored_data_offset(raw_apk, entry.header_offset)
                    if offset % PAGE_SIZE:
                        problems.append(f"{entry.filename}: APK data offset {offset} is not 16 KB aligned")
            except ValueError as exc:
                problems.append(f"{entry.filename}: {exc}")
    if count == 0:
        problems.append("no 64-bit native libraries found")
    print(f"{apk_path}: checked {count} 64-bit native libraries")
    return problems


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    problems = []
    for name in sys.argv[1:]:
        try:
            problems.extend(verify(Path(name)))
        except (OSError, ValueError) as exc:
            problems.append(f"{name}: {exc}")
    for problem in problems:
        print(f"FAIL: {problem}", file=sys.stderr)
    if not problems:
        print("All ELF LOAD segments and stored APK native libraries are 16 KB aligned.")
    return 1 if problems else 0


if __name__ == "__main__":
    raise SystemExit(main())
