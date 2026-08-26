"""
Prove the service account key can actually talk to the Play API, using nothing but the
standard library: sign a JWT by hand, swap it for an access token, then ask Play about
this app. No secrets are printed.
"""

import base64
import json
import os
import tempfile
import subprocess
import sys
import time
import urllib.parse
import urllib.request

KEY = sys.argv[1] if len(sys.argv) > 1 else sys.exit(
    "usage: check-play-key.py <service-account-key.json>"
)
PACKAGE = "com.searchlauncher.app"
SCOPE = "https://www.googleapis.com/auth/androidpublisher"


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=")


def main():
    key = json.load(open(KEY))
    now = int(time.time())
    header = b64(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    claims = b64(
        json.dumps(
            {
                "iss": key["client_email"],
                "scope": SCOPE,
                "aud": "https://oauth2.googleapis.com/token",
                "iat": now,
                "exp": now + 3600,
            }
        ).encode()
    )
    signing_input = header + b"." + claims

    # openssl rather than a pip dependency, so this runs on a bare machine. The key goes
    # through a temp file because openssl reads the payload from stdin.
    with tempfile.NamedTemporaryFile(delete=False) as f:
        f.write(key["private_key"].encode())
        keyfile = f.name
    try:
        signature = subprocess.run(
            ["openssl", "dgst", "-sha256", "-sign", keyfile, "-binary"],
            input=signing_input,
            capture_output=True,
            check=True,
        ).stdout
    finally:
        os.unlink(keyfile)

    jwt = (signing_input + b"." + b64(signature)).decode()

    body = urllib.parse.urlencode(
        {"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer", "assertion": jwt}
    ).encode()
    try:
        with urllib.request.urlopen("https://oauth2.googleapis.com/token", body) as r:
            token = json.load(r)["access_token"]
    except urllib.error.HTTPError as e:
        detail = json.loads(e.read() or b"{}")
        print("FAILED to get a token:", detail.get("error_description", detail))
        print("\nThe key itself is rejected. Check the Google Play Android Developer API")
        print("is enabled in project", key.get("project_id"))
        return 1
    print("token: obtained")

    # An edit is the cheapest call that proves Play accepts this account for THIS app.
    req = urllib.request.Request(
        f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits",
        data=b"{}",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req) as r:
            edit = json.load(r)
        print(f"play api:  ok, opened edit {edit['id']} on {PACKAGE}")
        delete = urllib.request.Request(
            f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}/edits/{edit['id']}",
            headers={"Authorization": f"Bearer {token}"},
            method="DELETE",
        )
        urllib.request.urlopen(delete)
        print("cleanup:   edit discarded")
        print("\nCI can upload. Tag a release and the play job will do the rest.")
        return 0
    except urllib.error.HTTPError as e:
        detail = json.loads(e.read() or b"{}")
        message = detail.get("error", {}).get("message", detail)
        print(f"play api:  HTTP {e.code} — {message}")
        if e.code in (401, 403):
            print("\nThe token works, so the key is fine, but Play is refusing this app.")
            print("In Play Console > Users and permissions, invite")
            print(f"  {key['client_email']}")
            print("and give it access to this app with 'Release to testing tracks'.")
            print("Permissions take a few minutes to apply.")
        elif e.code == 404:
            print(f"\n{PACKAGE} does not exist in this Play account yet, or the account")
            print("cannot see it. Create the app in the console first.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
