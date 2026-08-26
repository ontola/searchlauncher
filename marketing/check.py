#!/usr/bin/env python3
"""
Check the generated images against what the stores accept.

Play refuses an upload rather than warning about it, and the rules are easy to miss:
a 2560x1600 tablet canvas looks right but is 16:10, and both tablet slots want 16:9.

    .venv/bin/python check.py

Exits non-zero on the first thing a store would reject, so it can gate a commit.
"""

import glob
import os
import sys
from fractions import Fraction

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))

# Play's stated limits, per form factor. min_count of 2 is Play's floor for phones; the
# tablet slots are marked required in the console once the app is not phone-only.
SETS = {
    "phone": dict(side=(320, 3840), count=(2, 8), ratios=("9:16", "16:9")),
    "tablet7": dict(side=(320, 3840), count=(2, 8), ratios=("9:16", "16:9")),
    "tablet10": dict(side=(1080, 7680), count=(2, 8), ratios=("9:16", "16:9")),
    "feature": dict(exact=(1024, 500), count=(1, 1)),
}
MAX_MB = 8


def ratio(w, h):
    f = Fraction(w, h)
    return f"{f.numerator}:{f.denominator}"


def main():
    failures = []
    for name, rule in SETS.items():
        files = sorted(glob.glob(os.path.join(HERE, "out", name, "*.png")))
        lo, hi = rule["count"]
        if not lo <= len(files) <= hi:
            failures.append(f"{name}: {len(files)} images, must be {lo}-{hi}")

        for path in files:
            label = f"{name}/{os.path.basename(path)}"
            with Image.open(path) as im:
                w, h = im.size

            mb = os.path.getsize(path) / 1024 / 1024
            if mb >= MAX_MB:
                failures.append(f"{label}: {mb:.1f}MB, must be under {MAX_MB}MB")

            if "exact" in rule:
                if (w, h) != rule["exact"]:
                    want = "x".join(str(n) for n in rule["exact"])
                    failures.append(f"{label}: {w}x{h}, must be exactly {want}")
                continue

            if ratio(w, h) not in rule["ratios"]:
                allowed = " or ".join(rule["ratios"])
                failures.append(f"{label}: {w}x{h} is {ratio(w, h)}, must be {allowed}")

            side_lo, side_hi = rule["side"]
            for side in (w, h):
                if not side_lo <= side <= side_hi:
                    failures.append(
                        f"{label}: {w}x{h} has a side outside {side_lo}-{side_hi}px"
                    )
                    break

        print(f"{name}: {len(files)} images")

    if failures:
        print("\nA store would reject:")
        for f in failures:
            print(f"  {f}")
        return 1
    print("\nEverything matches what the stores accept.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
