#!/usr/bin/env python3
"""Scheduled upstream app update checker for Morphe Patches (Extra Apps).

Parses target versions from Constants.kt, queries upstream public sources
(APKMirror, Uptodown, and APKPure), and reports whether updates are available.
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import unittest
import urllib.error
import urllib.request
from pathlib import Path
from typing import Any, Callable, Dict, List, Optional, Tuple

USER_AGENT = "Discordbot/2.0; +https://discordapp.com"

HTTP_TIMEOUT_SECONDS = 15

APP_SPECS: List[Dict[str, Any]] = [
    # ==============================================================================
    # ADD NEW APP HERE - TEMPLATE
    # ==============================================================================
    # To monitor a new app, copy-paste-fill this template into APP_SPECS below.
    # A maintainer adding an app touches ONLY this block and adds one fixture test
    # in TestUpdateChecker (see test_apkpure_extraction_newer_download_version).
    #
    # {
    #     "id": "my-app",                           # 1. Unique runner/issue identifier (matches PatchExecutionTest id)
    #     "name": "My App",                         # 2. Display name shown in logs, issue titles, and alerts
    #     "package_name": "com.example.app",        # 3. Android application package name
    #     "constant_key": "MY_APP_TARGET_VERSION",  # 4. Target version constant defined in Constants.kt
    #     "source_type": "apkpure",                 # 5. Upstream host type: "apkpure", "uptodown", or "apkmirror"
    #     "source_name": "APKPure",                 # 6. Display name of upstream host, must match the entry's source
    #     "source_url": "https://apkpure.com/slug/com.example.app",  # 7. Canonical listing URL (HTTP 200 verified live)
    #     "allow_beta": False,                      # Pre-release filter (typically False for stable patch releases)
    #     # Optional keys: "path_prefix" (required for apkmirror; release- vs directory-level prefix both work), "run_patch_test_id" (defaults to id); no entry uses require_variant_marker (mechanism absent in this copy)
    # },
    # ==============================================================================
    {
        "id": "instagram",
        "name": "Instagram",
        "package_name": "com.instagram.android",
        "constant_key": "INSTAGRAM_TARGET_VERSION",
        "run_patch_test_id": "instagram",
        "source_type": "apkmirror",
        "source_name": "APKMirror",
        "source_url": "https://www.apkmirror.com/apk/instagram/instagram-instagram/",
        "path_prefix": "/apk/instagram/instagram-instagram/instagram-",
        "allow_beta": False,
    },
    {
        "id": "twitter",
        "name": "Twitter",
        "package_name": "com.twitter.android",
        "constant_key": "TWITTER_TARGET_VERSION",
        "run_patch_test_id": "twitter",
        "source_type": "apkmirror",
        "source_name": "APKMirror",
        "source_url": "https://www.apkmirror.com/apk/x-corp/twitter/",
        "path_prefix": "/apk/x-corp/twitter/x-",
        "allow_beta": False,
    },
    {
        "id": "moovit",
        "name": "Moovit",
        "package_name": "com.tranzmate",
        "constant_key": "MOOVIT_TARGET_VERSION",
        "run_patch_test_id": "moovit",
        "source_type": "apkmirror",
        "source_name": "APKMirror",
        "source_url": "https://www.apkmirror.com/apk/moovit/moovit-bus-train-live-info/",
        "path_prefix": "/apk/moovit/moovit-bus-train-live-info/moovit-",
        "allow_beta": False,
    },
    {
        "id": "powerpoint",
        "name": "PowerPoint",
        "package_name": "com.microsoft.office.powerpoint",
        "constant_key": "POWERPOINT_TARGET_VERSION",
        "run_patch_test_id": "powerpoint",
        "source_type": "apkmirror",
        "source_name": "APKMirror",
        "source_url": "https://www.apkmirror.com/apk/microsoft-corporation/powerpoint/",
        "path_prefix": "/apk/microsoft-corporation/powerpoint/microsoft-powerpoint-",
        "allow_beta": False,
    },
    {
        "id": "lrmobile",
        "name": "Lightroom",
        "package_name": "com.adobe.lrmobile",
        "constant_key": "LRMOBILE_TARGET_VERSION",
        "run_patch_test_id": "lrmobile",
        "source_type": "uptodown",
        "source_name": "Uptodown",
        "source_url": "https://adobe-lightroom-mobile.en.uptodown.com/android",
        "allow_beta": False,
    },
    {
        "id": "capcut",
        "name": "CapCut",
        "package_name": "com.lemon.lvoverseas",
        "constant_key": "CAPCUT_TARGET_VERSION",
        "run_patch_test_id": "capcut",
        "source_type": "apkmirror",
        "source_name": "APKMirror",
        "source_url": "https://www.apkmirror.com/apk/bytedance-pte-ltd/capcut/",
        "path_prefix": "/apk/bytedance-pte-ltd/capcut/capcut-",
        "allow_beta": False,
    },
    {
        "id": "officehub",
        "name": "Office Hub",
        "package_name": "com.microsoft.office.officehubrow",
        "constant_key": "OFFICEHUB_TARGET_VERSION",
        "run_patch_test_id": "officehub",
        "source_type": "apkmirror",
        "source_name": "APKMirror",
        "source_url": "https://www.apkmirror.com/apk/microsoft-corporation/office-mobile-2/",
        "path_prefix": "/apk/microsoft-corporation/office-mobile-2/",
        "allow_beta": False,
    },
    {
        "id": "hevy",
        "name": "Hevy",
        "package_name": "com.hevy",
        "constant_key": "HEVY_TARGET_VERSION",
        "source_type": "apkmirror",
        "source_name": "APKMirror",
        "source_url": "https://www.apkmirror.com/apk/hevy-gym-workout-tracker/hevy-gym-log-workout-tracker/",
        "path_prefix": "/apk/hevy-gym-workout-tracker/hevy-gym-log-workout-tracker/hevy-gym-log-workout-tracker-",
        "allow_beta": False,
    },
    {
        "id": "nokoprint",
        "name": "NokoPrint",
        "package_name": "com.nokoprint",
        "constant_key": "NOKOPRINT_TARGET_VERSION",
        "source_type": "apkpure",
        "source_name": "APKPure",
        "source_url": "https://apkpure.com/nokoprint-wifi-bluetooth-usb/com.nokoprint",
        "allow_beta": False,
    },
    {
        "id": "xiaomi-earbuds",
        "name": "Xiaomi Earbuds",
        "package_name": "com.mi.earphone",
        "constant_key": "XIAOMI_EARBUDS_TARGET_VERSION",
        "source_type": "apkpure",
        "source_name": "APKPure",
        "source_url": "https://apkpure.com/xiaomi-earbuds/com.mi.earphone",
        "allow_beta": False,
    },
    # --------------------------------------------------------------------------
    # SUBE (APKPure) - copy-paste-filled from "ADD NEW APP HERE" template
    # --------------------------------------------------------------------------
    {
        "id": "sube",
        "name": "SUBE",
        "package_name": "com.sube.app",
        "constant_key": "SUBE_TARGET_VERSION",
        "source_type": "apkpure",
        "source_name": "APKPure",
        "source_url": "https://apkpure.com/sube/com.sube.app",
        "allow_beta": False,
    },
]


def resolve_default_constants_path() -> Path:
    """Resolve default path to Constants.kt relative to repository root."""
    repo_root = Path(__file__).resolve().parent.parent
    return (
        repo_root
        / "patches"
        / "src"
        / "main"
        / "kotlin"
        / "app"
        / "morphe"
        / "patches"
        / "shared"
        / "Constants.kt"
    )


def parse_constants(constants_path: Path) -> Dict[str, str]:
    """Parse target version constants from Constants.kt with regex.

    Matches constants such as:
        const val INSTAGRAM_TARGET_VERSION = "447.0.0.55.81"
        const val TWITTER_TARGET_VERSION = "12.34.0-prod.01"
    """
    if not constants_path.is_file():
        print(f"[WARN] Constants file not found: {constants_path}", file=sys.stderr)
        return {}

    content = constants_path.read_text(encoding="utf-8")
    pattern = r'const\s+val\s+([A-Z0-9_]+_TARGET_VERSION)\s*=\s*"([^"]+)"'
    return dict(re.findall(pattern, content))


def parse_version(v_str: str) -> Tuple[Tuple[int, ...], str]:
    """Parse a version string into a numeric core tuple and suffix.

    Examples:
        '19.7.0' -> ((19, 7, 0), '')
        '12.34.0-prod.01' -> ((12, 34, 0), 'prod.01')
        '16.0.20527.20034' -> ((16, 0, 20527, 20034), '')
        '11.6.01' -> ((11, 6, 1), '')
        '447.0.0.55.81' -> ((447, 0, 0, 55, 81), '')
    """
    v = v_str.strip().lstrip("vV")
    m = re.match(r"^(\d+(?:\.\d+)*)(?:([a-zA-Z]+)|[-_\s]+(.*))?$", v)
    if not m:
        return (), v

    core_str = m.group(1)
    letter_suffix = m.group(2) or ""
    rest_suffix = m.group(3) or ""
    suffix = (letter_suffix + rest_suffix).strip()
    core = tuple(int(x) for x in core_str.split("."))
    return core, suffix


def compare_versions(local_ver: str, remote_ver: str) -> str:
    """Compare local vs remote version.

    Returns:
        'current', 'update-available', or 'unknown'
    """
    if not local_ver or not remote_ver:
        return "unknown"

    local_core, local_sfx = parse_version(local_ver)
    remote_core, remote_sfx = parse_version(remote_ver)

    if not local_core or not remote_core:
        return "unknown"

    max_len = max(len(local_core), len(remote_core))
    padded_local = local_core + (0,) * (max_len - len(local_core))
    padded_remote = remote_core + (0,) * (max_len - len(remote_core))

    if padded_remote > padded_local:
        return "update-available"
    if padded_remote < padded_local:
        return "current"

    # Numeric core parts are equal; check suffixes.
    clean_local_sfx = re.sub(r"-(arm64-v8a|armeabi-v7a|x86_64|x86)", "", local_sfx).strip()
    clean_remote_sfx = re.sub(r"-(arm64-v8a|armeabi-v7a|x86_64|x86)", "", remote_sfx).strip()

    if clean_local_sfx == clean_remote_sfx:
        return "current"

    # Exact string match preserves identical versions like 11.6.01
    if local_ver.strip() == remote_ver.strip():
        return "current"

    if clean_remote_sfx > clean_local_sfx:
        return "update-available"

    return "current"


def fetch_html(url: str) -> Optional[str]:
    """Fetch HTML content from a public URL using standard urllib."""
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
        },
    )
    try:
        with urllib.request.urlopen(req, timeout=HTTP_TIMEOUT_SECONDS) as resp:
            return resp.read().decode("utf-8", errors="ignore")
    except (urllib.error.URLError, urllib.error.HTTPError, OSError) as exc:
        print(f"[WARN] HTTP error fetching {url}: {exc}", file=sys.stderr)
        return None
    except Exception as exc:
        print(f"[WARN] Unexpected error fetching {url}: {exc}", file=sys.stderr)
        return None


def extract_apkmirror_version(
    html: str,
    path_prefix: str,
    allow_beta: bool = False,
) -> Optional[str]:
    """Extract latest remote version from APKMirror listing HTML.

    Host: APKMirror (https://www.apkmirror.com/apk/...)
    Precedence: Release links matching path_prefix sorted descending by numeric
    core and suffix, filtering out beta/alpha/preview/rc when allow_beta=False.
    Fixture test: test_apkmirror_extraction_and_beta_filter
    """
    candidates: List[Tuple[Tuple[int, ...], str, str, str]] = []

    link_pattern = r'<a[^>]*class="[^"]*fontBlack[^"]*"[^>]*href="([^"]+)"[^>]*>(.*?)</a>'
    for m in re.finditer(link_pattern, html, re.DOTALL):
        href = m.group(1)
        title = m.group(2).strip()

        if not href.startswith(path_prefix):
            continue

        is_beta = bool(
            re.search(r"\b(beta|alpha|preview|rc)\b", title, re.I)
            or re.search(r"-(beta|alpha|rc)\d*", href, re.I)
        )
        if is_beta and not allow_beta:
            continue

        v_match = re.search(
            r"(\d+(?:\.\d+)+(?:[a-zA-Z0-9._-]+)?)",
            title,
            re.I,
        )
        if v_match:
            raw_v = v_match.group(1).strip()
            core, sfx = parse_version(raw_v)
            if core:
                candidates.append((core, sfx, raw_v, href))

    if not candidates:
        return None

    # Sort descending by numeric core then suffix
    candidates.sort(key=lambda c: (c[0], c[1]), reverse=True)
    return candidates[0][2]


def extract_uptodown_version(html: str) -> Optional[str]:
    """Extract latest remote version from Uptodown page HTML.

    Host: Uptodown (https://<slug>.en.uptodown.com/android)
    Precedence: Download-link version (/download button) beats metadata
    (div.version) and JSON-LD schema (softwareVersion/version). If no downloadable
    version can be confirmed, returns None (reported as unknown).
    Fixture tests: test_uptodown_download_link_beats_metadata,
                   test_uptodown_unknown_when_empty_or_corrupt
    """
    if not html:
        return None

    # 1. Download-link version on page (e.g. <a href=".../download"...><p class="version">X.Y.Z</p>)
    dl_version: Optional[str] = None
    dl_match = re.search(
        r'<a[^>]*href="[^"]*/download"[^>]*>.*?<(?:p|span)[^>]*class="[^"]*version[^"]*"[^>]*>([^<]+)</(?:p|span)>',
        html,
        re.DOTALL | re.I,
    )
    if dl_match:
        dl_version = dl_match.group(1).strip()
    else:
        dl_btn = re.search(
            r'id="button-download-page-link"[^>]*>.*?(?:v(?:ersion)?\s*)?(\d+(?:\.\d+)+(?:[a-zA-Z0-9._-]+)?)',
            html,
            re.DOTALL | re.I,
        )
        if dl_btn:
            dl_version = dl_btn.group(1).strip()

    # 2. Page metadata version (e.g. <div class="version">X.Y.Z</div>)
    meta_version: Optional[str] = None
    meta_match = re.search(
        r'<div[^>]*class="[^"]*version[^"]*"[^>]*>([^<]+)</div>',
        html,
        re.I,
    )
    if meta_match:
        meta_version = meta_match.group(1).strip()

    # 3. schema.org JSON-LD
    json_ld_version: Optional[str] = None
    for m in re.finditer(
        r'<script[^>]*type="application/ld\+json"[^>]*>(.*?)</script>',
        html,
        re.DOTALL,
    ):
        try:
            data = json.loads(m.group(1))
            items = data if isinstance(data, list) else [data]
            for item in items:
                v = item.get("softwareVersion") or item.get("version")
                if v and isinstance(v, str):
                    json_ld_version = v.strip()
                    break
            if json_ld_version:
                break
        except Exception:
            pass

    # Resolution: download-link version takes precedence over metadata
    if dl_version:
        core_dl, _ = parse_version(dl_version)
        if core_dl:
            return dl_version

    if meta_version:
        core_meta, _ = parse_version(meta_version)
        if core_meta:
            return meta_version

    if json_ld_version:
        core_ld, _ = parse_version(json_ld_version)
        if core_ld:
            return json_ld_version

    return None


def extract_apkpure_version(html: str) -> Optional[str]:
    """Extract latest remote version from APKPure listing HTML.

    Host: APKPure (https://apkpure.com/<slug>/<package>)
    Precedence: Download-link version (data-dt-version on download button,
    version-name header, or title) beats page metadata and JSON-LD. If no
    downloadable version can be confirmed, returns None (reported as unknown).
    Fixture tests: test_apkpure_extraction_newer_download_version,
                   test_apkpure_download_link_beats_metadata,
                   test_apkpure_unknown_when_empty_or_no_download_link
    """
    if not html:
        return None

    # 1. Download button tags: look for <a> tags with download intent (e.g. dt-main-download-btn,
    # dt-secondary-download-btn, or href containing /download) having data-dt-version.
    # Exclude Aegon (APKPure's companion app) and generic downloader tools.
    for m in re.finditer(r"<a\b([^>]*)>", html, re.I):
        attrs = m.group(1)
        if "com.apkpure.aegon" in attrs or "apk-downloader" in attrs:
            continue

        is_dl = bool(
            re.search(
                r'class="[^"]*(?:dt-main-download-btn|normal-download-btn|dt-secondary-download-btn)[^"]*"',
                attrs,
                re.I,
            )
            or re.search(r'href="[^"]*/download\b', attrs, re.I)
        )
        if is_dl:
            v_match = re.search(r'data-dt-version="([^"]+)"', attrs, re.I)
            if v_match:
                v_cand = v_match.group(1).strip()
                core, _ = parse_version(v_cand)
                if core:
                    return v_cand

    # 2. Version name inside download link container (e.g. dt-version-name-link)
    m_h3 = re.search(
        r'<a\b[^>]*href="[^"]*/download\b[^"]*"[^>]*>.*?<h3[^>]*class="[^"]*version-name[^"]*"[^>]*>([^<]+)</h3>',
        html,
        re.DOTALL | re.I,
    )
    if m_h3:
        v_match = re.search(r"(\d+(?:\.\d+)+(?:[a-zA-Z0-9._-]+)?)", m_h3.group(1))
        if v_match:
            v_cand = v_match.group(1).strip()
            core, _ = parse_version(v_cand)
            if core:
                return v_cand

    # 3. Explicit version in download link title attribute
    m_title = re.search(
        r'<a\b[^>]*href="[^"]*/download\b[^"]*"[^>]*title="[^"]*?\bv?(\d+(?:\.\d+)+(?:[a-zA-Z0-9._-]+)?)\b[^"]*"',
        html,
        re.I,
    )
    if m_title:
        v_cand = m_title.group(1).strip()
        core, _ = parse_version(v_cand)
        if core:
            return v_cand

    # Hard rule 1: If no downloadable version can be confirmed on the page,
    # report unknown (None) even if metadata / JSON-LD specifies a version.
    return None


def run_update_checks(
    constants_path: Path,
    fetch_fn: Optional[Callable[[str], Optional[str]]] = None,
) -> List[Dict[str, Any]]:
    """Run update checks for all configured apps."""
    constants = parse_constants(constants_path)
    results: List[Dict[str, Any]] = []
    fetcher = fetch_fn if fetch_fn is not None else fetch_html

    for spec in APP_SPECS:
        app_id = spec["id"]
        app_name = spec["name"]
        package_name = spec["package_name"]
        constant_key = spec["constant_key"]
        run_patch_test_id = spec.get("run_patch_test_id", app_id)
        source_type = spec["source_type"]
        source_name = spec.get("source_name") or {
            "apkmirror": "APKMirror",
            "uptodown": "Uptodown",
            "apkpure": "APKPure",
        }.get(source_type, source_type.title())
        source_url = spec["source_url"]
        allow_beta = spec.get("allow_beta", False)

        local_version = constants.get(constant_key)
        if not local_version:
            print(
                f"[WARN] Constant {constant_key} not found in {constants_path}",
                file=sys.stderr,
            )
            local_version = "unknown"

        remote_version: Optional[str] = None
        html = fetcher(source_url)
        if html:
            if source_type == "apkmirror":
                remote_version = extract_apkmirror_version(
                    html,
                    spec["path_prefix"],
                    allow_beta=allow_beta,
                )
            elif source_type == "uptodown":
                remote_version = extract_uptodown_version(html)
            elif source_type == "apkpure":
                remote_version = extract_apkpure_version(html)

        if not remote_version:
            status = "unknown"
        else:
            status = compare_versions(local_version, remote_version)

        display_remote = remote_version if remote_version else "unknown"
        print(
            f"[UPDATE-CHECK] {app_name}: local={local_version} remote={display_remote} status={status}"
        )

        results.append(
            {
                "id": app_id,
                "name": app_name,
                "package_name": package_name,
                "local_version": local_version,
                "remote_version": display_remote,
                "status": status,
                "source_name": source_name,
                "source_url": source_url,
                "run_patch_test_id": run_patch_test_id,
            }
        )

    return results


# Built-in fixture test suite
class TestUpdateChecker(unittest.TestCase):
    """Fixture tests covering version comparison, conflicting metadata, and extraction."""

    def test_version_schemes(self) -> None:
        """Verify non-plain version schemes (twitter prod suffix, microsoft 4-part, lightroom)."""
        # Twitter prod suffix
        self.assertEqual(compare_versions("12.34.0-prod.01", "12.34.0-prod.01"), "current")
        self.assertEqual(compare_versions("12.34.0-prod.01", "12.35.0-prod.01"), "update-available")
        self.assertEqual(compare_versions("12.34.0-prod.01", "12.34.0-prod.02"), "update-available")
        self.assertEqual(compare_versions("12.34.0-prod.02", "12.34.0-prod.01"), "current")

        # Microsoft 4-part builds
        self.assertEqual(compare_versions("16.0.20527.20034", "16.0.20527.20034"), "current")
        self.assertEqual(compare_versions("16.0.20527.20034", "16.0.20527.20035"), "update-available")
        self.assertEqual(compare_versions("16.0.20527.20035", "16.0.20527.20034"), "current")

        # Instagram 5-part builds
        self.assertEqual(compare_versions("447.0.0.55.81", "447.0.0.55.81"), "current")
        self.assertEqual(compare_versions("447.0.0.55.81", "448.0.0.1.1"), "update-available")

        # Lightroom 11.6.01
        self.assertEqual(compare_versions("11.6.01", "11.6.01"), "current")
        self.assertEqual(compare_versions("11.6.01", "11.6.10"), "update-available")

    def test_uptodown_download_link_beats_metadata(self) -> None:
        """Download-link version must take precedence over contradictory metadata."""
        fixture_html = """
        <html>
          <head>
            <script type="application/ld+json">{"softwareVersion": "12.0.0"}</script>
          </head>
          <body>
            <div class="version">11.9.0</div>
            <a id="button-download-page-link" href="https://example.uptodown.com/android/download" class="button latest">
              <span class="download-text">Download</span>
              <p class="version">11.6.01</p>
            </a>
          </body>
        </html>
        """
        ver = extract_uptodown_version(fixture_html)
        self.assertEqual(ver, "11.6.01")

    def test_uptodown_unknown_when_empty_or_corrupt(self) -> None:
        """Report None (unknown) when no valid version can be parsed."""
        self.assertIsNone(extract_uptodown_version(""))
        self.assertIsNone(extract_uptodown_version("<html><body>No version here</body></html>"))

    def test_apkmirror_extraction_and_beta_filter(self) -> None:
        """Verify APKMirror listing extraction filters out betas when allow_beta=False."""
        fixture_html = """
        <div>
          <a class="fontBlack" href="/apk/moovit/moovit-bus-train-live-info/moovit-5-201-6-1811-release/">
            Moovit: Your Transit Tracker 5.201.6.1811 beta
          </a>
          <a class="fontBlack" href="/apk/moovit/moovit-bus-train-live-info/moovit-5-201-1-1809-release/">
            Moovit: Your Transit Tracker 5.201.1.1809
          </a>
          <a class="fontBlack" href="/apk/other-dev/other-app/other-release/">
            Other App 1.0.0
          </a>
        </div>
        """
        ver = extract_apkmirror_version(
            fixture_html,
            path_prefix="/apk/moovit/moovit-bus-train-live-info/moovit-",
            allow_beta=False,
        )
        self.assertEqual(ver, "5.201.1.1809")

    def test_apkpure_extraction_newer_download_version(self) -> None:
        """Verify APKPure extracts newer downloadable version from download button."""
        fixture_html = """
        <html>
          <body>
            <a class="btn normal-download-btn da dt-main-download-btn"
               data-dt-package_name="com.sube.app"
               data-dt-version="2.8.0"
               href="https://apkpure.com/sube/com.sube.app/download"
               title="Download SUBE latest version apk">
              <strong class="text one-line">Download APK (45.1 MB)</strong>
            </a>
          </body>
        </html>
        """
        ver = extract_apkpure_version(fixture_html)
        self.assertEqual(ver, "2.8.0")

    def test_apkpure_download_link_beats_metadata(self) -> None:
        """Download-link version must take precedence over contradictory metadata."""
        fixture_html = """
        <html>
          <head>
            <script type="application/ld+json">{"@context": "https://schema.org", "@type": "MobileApplication", "name": "SUBE", "version": "3.0.0"}</script>
          </head>
          <body>
            <span class="version one-line">2.9.0</span>
            <a class="btn normal-download-btn da dt-main-download-btn"
               data-dt-package_name="com.sube.app"
               data-dt-version="2.7.25"
               href="https://apkpure.com/sube/com.sube.app/download"
               title="Download SUBE latest version apk">
              <strong class="text one-line">Download APK (45.1 MB)</strong>
            </a>
          </body>
        </html>
        """
        ver = extract_apkpure_version(fixture_html)
        self.assertEqual(ver, "2.7.25")
        self.assertNotEqual(ver, "3.0.0")
        self.assertNotEqual(ver, "2.9.0")

    def test_apkpure_unknown_when_empty_or_no_download_link(self) -> None:
        """Report None (unknown) when no downloadable version can be confirmed."""
        self.assertIsNone(extract_apkpure_version(""))
        self.assertIsNone(extract_apkpure_version("<html><body>No version here</body></html>"))
        metadata_only_html = """
        <html>
          <head>
            <script type="application/ld+json">{"@type": "MobileApplication", "version": "3.0.0"}</script>
          </head>
          <body>
            <span class="version one-line">2.9.0</span>
          </body>
        </html>
        """
        self.assertIsNone(extract_apkpure_version(metadata_only_html))


def main() -> int:
    """CLI entrypoint."""
    parser = argparse.ArgumentParser(
        description="Check upstream app versions against Morphe target constants."
    )
    parser.add_argument(
        "--check",
        action="store_true",
        default=True,
        help="Report update check status (default: True)",
    )
    parser.add_argument(
        "--json",
        dest="json_path",
        type=str,
        default=None,
        help="Path to write machine-readable JSON report",
    )
    parser.add_argument(
        "--constants",
        dest="constants_path",
        type=str,
        default=None,
        help="Path to Constants.kt (default: auto-detected)",
    )
    parser.add_argument(
        "--test",
        action="store_true",
        default=False,
        help="Run built-in unit and fixture tests",
    )

    args = parser.parse_args()

    if args.test:
        suite = unittest.TestLoader().loadTestsFromTestCase(TestUpdateChecker)
        runner = unittest.TextTestRunner(verbosity=2)
        result = runner.run(suite)
        return 0 if result.wasSuccessful() else 1

    constants_path = (
        Path(args.constants_path)
        if args.constants_path
        else resolve_default_constants_path()
    )

    results = run_update_checks(constants_path)

    if args.json_path:
        out_path = Path(args.json_path)
        out_path.parent.mkdir(parents=True, exist_ok=True)
        report_data = {"apps": results}
        out_path.write_text(json.dumps(report_data, indent=2), encoding="utf-8")
        print(f"[INFO] Saved JSON report to {args.json_path}")

    return 0


if __name__ == "__main__":
    sys.exit(main())
