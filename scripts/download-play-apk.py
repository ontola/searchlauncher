#!/usr/bin/env python3
"""Download and verify Google's signed universal APK. Does not publish or change Play tracks."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"
PACKAGE = "com.searchlauncher.app"
UPLOAD_CERT = "553f628023ac5739ed10cfdfad26e9c79ad5ef669cd187c8effa97c733b1d6b0"


def fingerprint(value):
    value = value.replace(":", "").lower()
    if not re.fullmatch(r"[0-9a-f]{64}", value):
        raise ValueError("Invalid SHA-256 signing certificate fingerprint")
    return value


def select_universal(metadata, expected=None):
    expected = fingerprint(expected) if expected else None
    candidates = []
    for entry in metadata.get("generatedApks", []):
        apk = entry.get("generatedUniversalApk", {})
        if not apk.get("downloadId"):
            continue
        cert = fingerprint(entry["certificateSha256Hash"])
        if cert == UPLOAD_CERT:
            continue  # Never substitute our incompatible upload/F-Droid signature.
        if expected is None or cert == expected:
            candidates.append((apk["downloadId"], cert))
    if len(candidates) > 1:
        raise ValueError("Multiple Play signing keys; set PLAY_APP_SIGNING_SHA256 explicitly")
    return candidates[0] if candidates else None


def access_token(service_account):
    def b64(value):
        return base64.urlsafe_b64encode(value).rstrip(b"=")

    now = int(time.time())
    claim = {
        "iss": service_account["client_email"],
        "scope": "https://www.googleapis.com/auth/androidpublisher",
        "aud": "https://oauth2.googleapis.com/token",
        "iat": now,
        "exp": now + 3600,
    }
    payload = b64(b'{"alg":"RS256","typ":"JWT"}') + b"." + b64(json.dumps(claim).encode())
    with tempfile.NamedTemporaryFile() as key:
        key.write(service_account["private_key"].encode())
        key.flush()
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", key.name],
            input=payload, capture_output=True, check=True,
        ).stdout
    body = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": (payload + b"." + b64(signature)).decode(),
    }).encode()
    with urllib.request.urlopen("https://oauth2.googleapis.com/token", body, timeout=60) as response:
        return json.load(response)["access_token"]


class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        redirected = super().redirect_request(req, fp, code, msg, headers, newurl)
        if redirected is not None:
            if urllib.parse.urlparse(newurl).scheme != "https":
                raise ValueError("Refusing a non-HTTPS APK redirect")
            if urllib.parse.urlparse(req.full_url).netloc != urllib.parse.urlparse(newurl).netloc:
                redirected.remove_header("Authorization")
        return redirected


def verify_apk(apk, version, cert, apksigner, aapt2):
    signing = subprocess.run(
        [apksigner, "verify", "--print-certs", str(apk)],
        capture_output=True, text=True, check=True,
    ).stdout
    signers = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]+)", signing)
    if [fingerprint(s) for s in signers] != [cert]:
        raise ValueError("Downloaded APK does not match the Play signing certificate")
    badging = subprocess.run(
        [aapt2, "dump", "badging", str(apk)], capture_output=True, text=True, check=True,
    ).stdout
    package = re.search(r"^package: name='([^']+)' versionCode='(\d+)'", badging, re.MULTILINE)
    if package is None or package.groups() != (PACKAGE, str(version)):
        raise ValueError("Downloaded APK has the wrong application ID or version code")
    if re.search(r"^package:.*\bsplit=", badging, re.MULTILINE):
        raise ValueError("Expected a universal APK, received a split APK")


def download(version, output, service_account, apksigner, aapt2, expected=None, wait_seconds=600):
    token = access_token(service_account)
    opener = urllib.request.build_opener(SafeRedirect())
    base = f"{API}/{PACKAGE}/generatedApks/{version}"

    def get(url):
        request = urllib.request.Request(url, headers={"Authorization": f"Bearer {token}"})
        return opener.open(request, timeout=120)

    deadline = time.monotonic() + wait_seconds
    while True:
        try:
            with get(base) as response:
                selected = select_universal(json.load(response), expected)
        except urllib.error.HTTPError as error:
            if error.code not in (404, 429, 500, 502, 503, 504):
                raise
            selected = None
        if selected is not None:
            break
        if time.monotonic() >= deadline:
            raise RuntimeError("Play has no matching signed universal APK; refusing an incompatible fallback")
        print("Waiting for Play to generate the signed universal APK…", flush=True)
        time.sleep(min(15, max(0, deadline - time.monotonic())))
    download_id, cert = selected
    output.parent.mkdir(parents=True, exist_ok=True)
    # Never leave a partial/unverified file at the public artifact path.
    with tempfile.TemporaryDirectory(dir=output.parent) as temp:
        apk = Path(temp) / "verified.apk"
        url = base + "/downloads/" + urllib.parse.quote(download_id, safe="") + ":download?alt=media"
        with get(url) as response, apk.open("wb") as target:
            shutil.copyfileobj(response, target, 1024 * 1024)
        verify_apk(apk, version, cert, apksigner, aapt2)
        digest = hashlib.sha256()
        with apk.open("rb") as source:
            while chunk := source.read(1024 * 1024):
                digest.update(chunk)
        metadata = {
            "packageName": PACKAGE, "versionCode": version,
            "signingCertificateSha256": cert, "apkSha256": digest.hexdigest(),
            "bytes": apk.stat().st_size,
        }
        apk.replace(output)
        output.with_suffix(".json").write_text(json.dumps(metadata, indent=2) + "\n")
    print(f"Verified Play-signed APK for {PACKAGE}, version {version}, certificate {cert}", flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version-code", type=int)
    parser.add_argument("--output", type=Path, default=Path("play-apk/app-play.apk"))
    parser.add_argument("--apksigner", required=True)
    parser.add_argument("--aapt2", required=True)
    parser.add_argument("--wait-seconds", type=int, default=600)
    args = parser.parse_args()
    version = args.version_code
    if version is None:
        match = re.search(r"^\s*versionCode\s*=\s*(\d+)\s*$", Path("app/build.gradle.kts").read_text(), re.MULTILINE)
        if match is None:
            raise ValueError("No literal versionCode in app/build.gradle.kts")
        version = int(match[1])
    key = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON")
    if not key:
        raise ValueError("PLAY_SERVICE_ACCOUNT_JSON is required; no incompatible APK fallback")
    download(version, args.output, json.loads(key), args.apksigner, args.aapt2,
             os.environ.get("PLAY_APP_SIGNING_SHA256"), args.wait_seconds)


if __name__ == "__main__":
    try:
        main()
    except urllib.error.HTTPError as error:
        raise SystemExit(f"Google Play API returned HTTP {error.code}; no APK published") from None
    except (ValueError, RuntimeError, subprocess.CalledProcessError) as error:
        raise SystemExit(str(error)) from None
