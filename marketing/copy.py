#!/usr/bin/env python3
"""
Write the store text out of store-copy.md into the files the stores read.

F-Droid publishes fastlane/metadata/android/en-US/*.txt straight from the tag, and Play's
console fields are filled from the same text by hand. Keeping one source means the two
cannot say different things about the same app.

    .venv/bin/python copy.py           # write the files, checking the limits first
    .venv/bin/python copy.py --check   # check only, write nothing

Exits non-zero if a limit is broken or a generated file is out of date.
"""

import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
SOURCE = os.path.join(HERE, "store-copy.md")
FASTLANE = os.path.join(HERE, "..", "fastlane", "metadata", "android", "en-US")

# heading in store-copy.md -> (file it becomes, character limit)
FIELDS = {
    "Title": ("title.txt", 30),
    "Short description": ("short_description.txt", 80),
    "Full description": ("full_description.txt", 4000),
}


def parse(text):
    """Every fenced block that directly follows one of the headings we know."""
    found = {}
    for heading in FIELDS:
        pattern = rf"^## {re.escape(heading)}\s*$(.*?)^```\n(.*?)^```"
        match = re.search(pattern, text, re.MULTILINE | re.DOTALL)
        if match:
            found[heading] = match.group(2).strip()
    return found


def main():
    check_only = "--check" in sys.argv
    blocks = parse(open(SOURCE).read())

    problems = []
    for heading, (name, limit) in FIELDS.items():
        if heading not in blocks:
            problems.append(f"{heading}: no fenced block under that heading")
            continue

        value = blocks[heading]
        if len(value) > limit:
            problems.append(f"{name}: {len(value)} characters, limit is {limit}")

        # F-Droid's linter rejects a summary that ends in punctuation.
        if name == "short_description.txt" and value.endswith((".", "!", "?")):
            problems.append(f"{name}: ends with punctuation, F-Droid's linter refuses it")

        path = os.path.join(FASTLANE, name)
        current = open(path).read() if os.path.exists(path) else None
        wanted = value + "\n"

        if check_only:
            if current != wanted:
                problems.append(f"{name}: out of date, run copy.py")
        elif current != wanted:
            with open(path, "w") as f:
                f.write(wanted)
            print(f"wrote {name}  ({len(value)}/{limit} characters)")
        else:
            print(f"{name} unchanged  ({len(value)}/{limit} characters)")

    if problems:
        print("\nProblems:")
        for p in problems:
            print(f"  {p}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
