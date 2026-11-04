import importlib.util
import io
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
import urllib.request
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("play_apk", Path(__file__).parents[1] / "download-play-apk.py")
play = importlib.util.module_from_spec(spec)
spec.loader.exec_module(play)
CERT = "c0" * 32


def entry(cert=CERT, download_id="universal", universal=True):
    result = {"certificateSha256Hash": cert}
    if universal:
        result["generatedUniversalApk"] = {"downloadId": download_id}
    return result


class SelectionTest(unittest.TestCase):
    def test_never_falls_back_to_upload_key_or_split_apks(self):
        metadata = {"generatedApks": [entry(play.UPLOAD_CERT), entry(universal=False)]}
        self.assertIsNone(play.select_universal(metadata))

    def test_selects_universal_and_normalizes_pin(self):
        metadata = {"generatedApks": [entry(), entry("a1" * 32, "rotated")]}
        with self.assertRaisesRegex(ValueError, "Multiple Play signing keys"):
            play.select_universal(metadata)
        self.assertEqual(("universal", CERT), play.select_universal(metadata, ":".join(["C0"] * 32)))
        self.assertIsNone(play.select_universal(metadata, "a2" * 32))

    def test_rejects_malformed_certificate(self):
        with self.assertRaises(ValueError):
            play.select_universal({"generatedApks": [entry("c016abdc")]})


class ApkTest(unittest.TestCase):
    def verify(self, cert=CERT, package=play.PACKAGE, version=304, split=""):
        signing = subprocess.CompletedProcess([], 0, f"Signer #1 certificate SHA-256 digest: {cert}\n")
        manifest = subprocess.CompletedProcess([], 0, f"package: name='{package}' versionCode='{version}' versionName='test'{split}\n")
        with patch.object(play.subprocess, "run", side_effect=[signing, manifest]):
            play.verify_apk(Path("test.apk"), 304, CERT, "apksigner", "aapt2")

    def test_accepts_matching_package_version_and_google_certificate(self):
        self.verify()

    def test_rejects_wrong_signature_package_version_and_splits(self):
        for args in [{"cert": play.UPLOAD_CERT}, {"package": play.PACKAGE + ".debug"},
                     {"version": 303}, {"split": " split='config.en'"}]:
            with self.subTest(args=args), self.assertRaises(ValueError):
                self.verify(**args)

    def test_partial_download_never_replaces_existing_verified_file(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "app-play.apk"
            output.write_bytes(b"previous verified APK")
            metadata = io.BytesIO(json.dumps({"generatedApks": [entry()]}).encode())
            binary = io.BytesIO(b"not an APK")
            with patch.object(play, "access_token", return_value="test"), \
                 patch.object(play.urllib.request, "build_opener") as opener, \
                 patch.object(play, "verify_apk", side_effect=ValueError("wrong signature")):
                opener.return_value.open.side_effect = [metadata, binary]
                with self.assertRaisesRegex(ValueError, "wrong signature"):
                    play.download(304, output, {}, "apksigner", "aapt2", wait_seconds=0)
            self.assertEqual(b"previous verified APK", output.read_bytes())
            self.assertFalse(output.with_suffix(".json").exists())

    def test_missing_universal_fails_without_publishing_fallback(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / "app-play.apk"
            with patch.object(play, "access_token", return_value="test"), \
                 patch.object(play.urllib.request, "build_opener") as opener:
                opener.return_value.open.return_value = io.BytesIO(b'{"generatedApks": []}')
                with self.assertRaisesRegex(RuntimeError, "refusing an incompatible fallback"):
                    play.download(304, output, {}, "apksigner", "aapt2", wait_seconds=0)
            self.assertFalse(output.exists())


class RedirectTest(unittest.TestCase):
    def test_does_not_forward_bearer_token_to_download_host(self):
        request = urllib.request.Request("https://androidpublisher.googleapis.com/file", headers={"Authorization": "Bearer test"})
        redirected = play.SafeRedirect().redirect_request(request, None, 302, "", {}, "https://storage.googleapis.com/file")
        self.assertIsNone(redirected.get_header("Authorization"))

    def test_refuses_http_download_redirect(self):
        request = urllib.request.Request("https://androidpublisher.googleapis.com/file")
        with self.assertRaisesRegex(ValueError, "non-HTTPS"):
            play.SafeRedirect().redirect_request(request, None, 302, "", {}, "http://example.com/file")


if __name__ == "__main__":
    unittest.main()
