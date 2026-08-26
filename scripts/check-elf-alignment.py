"""
Report the LOAD-segment alignment of every .so in an AAB.

Android 15 can use 16 KB memory pages. A shared library whose LOAD segments are aligned
to 4 KB (0x1000) cannot be mapped on such a device, which is what Play is complaining
about. 0x4000 or greater is what it wants.
"""

import struct
import sys
import zipfile
from collections import defaultdict

AAB = sys.argv[1]
WANT = 0x4000


def load_aligns(data):
    """Max p_align across PT_LOAD segments of a 32- or 64-bit little-endian ELF."""
    if data[:4] != b"\x7fELF":
        return None
    is64 = data[4] == 2
    if is64:
        e_phoff = struct.unpack_from("<Q", data, 0x20)[0]
        e_phentsize = struct.unpack_from("<H", data, 0x36)[0]
        e_phnum = struct.unpack_from("<H", data, 0x38)[0]
    else:
        e_phoff = struct.unpack_from("<I", data, 0x1C)[0]
        e_phentsize = struct.unpack_from("<H", data, 0x2A)[0]
        e_phnum = struct.unpack_from("<H", data, 0x2C)[0]

    aligns = []
    for i in range(e_phnum):
        off = e_phoff + i * e_phentsize
        p_type = struct.unpack_from("<I", data, off)[0]
        if p_type != 1:  # PT_LOAD
            continue
        p_align = (
            struct.unpack_from("<Q", data, off + 0x30)[0]
            if is64
            else struct.unpack_from("<I", data, off + 0x1C)[0]
        )
        aligns.append(p_align)
    return max(aligns) if aligns else 0


bad = defaultdict(list)
good = defaultdict(list)

with zipfile.ZipFile(AAB) as z:
    for name in sorted(z.namelist()):
        if not name.endswith(".so"):
            continue
        align = load_aligns(z.read(name))
        lib = name.rsplit("/", 1)[-1]
        abi = name.split("/")[2] if len(name.split("/")) > 2 else "?"
        (good if align and align >= WANT else bad)[lib].append((abi, align))

print(f"{'library':<40} {'alignment':>10}   verdict")
print("-" * 70)
for lib in sorted(set(bad) | set(good)):
    entries = bad.get(lib) or good.get(lib)
    aligns = {hex(a) for _, a in entries}
    verdict = "16 KB ok" if lib in good and lib not in bad else "TOO SMALL"
    print(f"{lib:<40} {'/'.join(sorted(aligns)):>10}   {verdict}")

print()
if bad:
    print("Blocking 16 KB support:")
    for lib in sorted(bad):
        print(f"  {lib}")
else:
    print("Every library is 16 KB ready.")
