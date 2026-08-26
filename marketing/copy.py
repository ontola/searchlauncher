#!/usr/bin/env python3
"""
Write the store text out of store-copy.md into the files the stores read.

F-Droid publishes fastlane/metadata/android/en-US/*.txt straight from the tag, and Play's
console fields are filled from the same text by hand. Keeping one source means the two
cannot say different things about the same app.

    python3 copy.py           # write the files, checking the limits first
    python3 copy.py --check   # check only, write nothing

Also checks the changelog for the version about to be released. One file serves both
stores, and Play refuses release notes over 500 characters, so an over-long entry fails
the upload after the tag is already public.

Standard library only, so CI can run it without installing anything.

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


CHANGELOGS = os.path.join(FASTLANE, "changelogs")
GRADLE = os.path.join(HERE, "..", "app", "build.gradle.kts")
CHANGELOG_LIMIT = 500  # Play's cap on release notes, per language


def current_version_code():
    match = re.search(r"^\s*versionCode = (\d+)", open(GRADLE).read(), re.MULTILINE)
    return match.group(1) if match else None


def check_changelogs():
    """The entry about to be released has to fit; older ones are only reported."""
    problems, notes = [], []
    code = current_version_code()
    if code is None:
        return ["could not read versionCode out of app/build.gradle.kts"], notes

    path = os.path.join(CHANGELOGS, f"{code}.txt")
    if not os.path.exists(path):
        notes.append(f"no changelog for versionCode {code} yet")
    else:
        size = len(open(path).read())
        if size > CHANGELOG_LIMIT:
            problems.append(
                f"changelogs/{code}.txt: {size} characters, Play refuses over"
                f" {CHANGELOG_LIMIT}"
            )
        else:
            print(f"changelogs/{code}.txt  ({size}/{CHANGELOG_LIMIT} characters)")

    old = []
    for name in sorted(os.listdir(CHANGELOGS)):
        if not name.endswith(".txt") or name == f"{code}.txt":
            continue
        if len(open(os.path.join(CHANGELOGS, name)).read()) > CHANGELOG_LIMIT:
            old.append(name)
    if old:
        notes.append(
            f"{len(old)} older changelogs are over {CHANGELOG_LIMIT} characters"
            f" ({', '.join(old)}). They already shipped, so they are left alone;"
            " Play only reads the one being released."
        )
    return problems, notes


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

    changelog_problems, notes = check_changelogs()
    problems += changelog_problems

    if notes:
        print()
        for n in notes:
            print(f"note: {n}")

    if problems:
        print("\nProblems:")
        for p in problems:
            print(f"  {p}")
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
