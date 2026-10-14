"""
Guard rails for download target variants in Constants.kt.

Every Compatibility block's AppTarget description in Constants.kt must name an
arch token (arm64-v8a, armeabi-v7a, x86, x86_64, or universal) and a
versionCode (keyword followed by 4+ digits), unless the target file type is a single-file APK
with no APKMirror variants (e.g. ApkFileType.APK or ApkFileType.APK_REQUIRED).
Bundle/APKM formats always require both tokens to prevent ambiguous target downloads.
"""

import re
import unittest
from pathlib import Path

CONSTANTS_PATH = (
    Path(__file__).resolve().parents[2]
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

ARCH_PATTERN = re.compile(r"\b(arm64-v8a|armeabi-v7a|x86|x86_64|universal)\b")
VERSION_CODE_PATTERN = re.compile(r"versionCode\s+\d{4,}")

# Single-file APK types that do not have APKMirror split bundles
SINGLE_FILE_TYPES = {"APK", "APK_REQUIRED"}


def parse_compatibility_targets(text: str):
    """
    Parses Constants.kt text to extract Compatibility blocks and their AppTargets.
    Removes comments before matching to avoid template/example blocks.
    """
    clean = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    clean = re.sub(r"//.*", "", clean)

    comp_blocks = re.findall(
        r"(val\s+(COMPATIBILITY_[A-Z0-9_]+)\s*=\s*Compatibility\s*\((?:(?!\bval\s+COMPATIBILITY_).)*)",
        clean,
        re.DOTALL,
    )

    targets = []
    for block, var_name in comp_blocks:
        name_match = re.search(r'name\s*=\s*"([^"]+)"', block)
        app_name = name_match.group(1) if name_match else var_name

        type_match = re.search(r"apkFileType\s*=\s*ApkFileType\.(\w+)", block)
        file_type = type_match.group(1) if type_match else "APK"

        app_targets = []
        cursor = 0
        while cursor < len(block):
            match = re.search(r"AppTarget\s*\(", block[cursor:])
            if not match:
                break
            start = cursor + match.start()
            depth = 0
            end = -1
            for idx in range(cursor + match.end() - 1, len(block)):
                if block[idx] == "(":
                    depth += 1
                elif block[idx] == ")":
                    depth -= 1
                    if depth == 0:
                        end = idx + 1
                        break
            if end != -1:
                app_targets.append(block[start:end])
                cursor = end
            else:
                cursor = cursor + match.end()

        for target_str in app_targets:
            desc_match = re.search(r'description\s*=\s*"([^"]*)"', target_str)
            description = desc_match.group(1) if desc_match else ""
            is_single_file = file_type in SINGLE_FILE_TYPES

            targets.append({
                "var_name": var_name,
                "app_name": app_name,
                "file_type": file_type,
                "description": description,
                "is_single_file": is_single_file,
            })
    return targets


def evaluate_target_conventions(targets):
    """
    Evaluates parsed targets against exact-variant conventions.
    Returns a list of violation strings detailing app name and missing tokens.
    """
    violations = []
    for target in targets:
        if target["is_single_file"]:
            continue

        desc = target["description"]
        has_arch = bool(ARCH_PATTERN.search(desc))
        has_version_code = bool(VERSION_CODE_PATTERN.search(desc))

        missing = []
        if not has_arch:
            missing.append("arch token (arm64-v8a, armeabi-v7a, x86, x86_64, or universal)")
        if not has_version_code:
            missing.append("versionCode (4+ digits)")

        if missing:
            violations.append(
                f"{target['var_name']} ({target['app_name']}) [fileType={target['file_type']}] "
                f"missing {', '.join(missing)} in description: {repr(desc)}"
            )
    return violations


class TestTargetVariantConventions(unittest.TestCase):

    def setUp(self):
        self.assertTrue(CONSTANTS_PATH.is_file(), f"Constants.kt not found at {CONSTANTS_PATH}")
        self.constants_text = CONSTANTS_PATH.read_text(encoding="utf-8")
        self.parsed_targets = parse_compatibility_targets(self.constants_text)

    def test_compatibility_blocks_are_discovered(self):
        """Ensures that the text parser discovers active Compatibility blocks."""
        self.assertGreater(
            len(self.parsed_targets),
            0,
            "Parser failed to discover any Compatibility blocks in Constants.kt",
        )

    def test_single_file_targets_are_identified(self):
        """Ensures single-file APK targets are correctly identified as exempt."""
        single_file = [t for t in self.parsed_targets if t["is_single_file"]]
        self.assertTrue(
            any(t["var_name"] == "COMPATIBILITY_LRMOBILE" for t in single_file),
            "Expected COMPATIBILITY_LRMOBILE to be exempt as a single-file APK",
        )
        self.assertTrue(
            any(t["var_name"] == "COMPATIBILITY_CAPCUT" for t in single_file),
            "Expected COMPATIBILITY_CAPCUT to be exempt as a single-file APK",
        )
        # Warning assertion: an EXEMPT (single-file) description must NOT contain bundle tokens
        exempt_bundle_pattern = re.compile(r"\b(APKM|BUNDLE)\b")
        for target in single_file:
            desc = target["description"]
            matches = exempt_bundle_pattern.findall(desc)
            self.assertFalse(
                matches,
                f"Exempt single-file target {target['var_name']} must NOT contain bundle tokens {matches}: {repr(desc)}",
            )

    def test_bundle_targets_are_enforced(self):
        """Ensures bundle/APKM targets are correctly classified as enforced."""
        enforced = [t for t in self.parsed_targets if not t["is_single_file"]]
        enforced_vars = {t["var_name"] for t in enforced}
        expected_enforced = {
            "COMPATIBILITY_INSTAGRAM",
            "COMPATIBILITY_TWITTER",
            "COMPATIBILITY_MOOVIT",
            "COMPATIBILITY_POWERPOINT",
            "COMPATIBILITY_OFFICEHUB",
        }
        self.assertTrue(
            expected_enforced.issubset(enforced_vars),
            f"Expected enforced targets {expected_enforced} not found in {enforced_vars}",
        )

    def test_parser_evaluates_exact_variant_conventions(self):
        """
        Validates exact-variant rule enforcement logic on bundle targets:
        checks arch token and keyword-anchored 4+ digit versionCode presence.
        """
        valid_sample = """
        val COMPATIBILITY_SAMPLE_BUNDLE = Compatibility(
            name = "Sample",
            packageName = "com.sample",
            apkFileType = ApkFileType.APKM,
            targets = listOf(
                AppTarget(
                    version = "1.0.0",
                    description = "Download com.sample v1.0.0 (APKM) arm64-v8a versionCode 1234567 from APKMirror",
                )
            )
        )
        """
        targets = parse_compatibility_targets(valid_sample)
        violations = evaluate_target_conventions(targets)
        self.assertEqual(violations, [])

    def test_missing_arch_fails_with_exact_app_and_token(self):
        """Ensures missing arch token is detected and reported specifically."""
        sample = """
        val COMPATIBILITY_NO_ARCH = Compatibility(
            name = "NoArchApp",
            packageName = "com.noarch",
            apkFileType = ApkFileType.APKM,
            targets = listOf(
                AppTarget(
                    version = "1.0.0",
                    description = "Download com.noarch v1.0.0 (APKM) versionCode 1234567 from APKMirror",
                )
            )
        )
        """
        targets = parse_compatibility_targets(sample)
        violations = evaluate_target_conventions(targets)
        self.assertEqual(len(violations), 1)
        self.assertIn("NoArchApp", violations[0])
        self.assertIn("arch token", violations[0])

    def test_missing_version_code_fails_with_exact_app_and_token(self):
        """Ensures missing versionCode is detected and reported specifically."""
        sample = """
        val COMPATIBILITY_NO_VC = Compatibility(
            name = "NoVcApp",
            packageName = "com.novc",
            apkFileType = ApkFileType.APKM,
            targets = listOf(
                AppTarget(
                    version = "1.0.0",
                    description = "Download com.novc v1.0.0 (APKM) arm64-v8a from APKMirror",
                )
            )
        )
        """
        targets = parse_compatibility_targets(sample)
        violations = evaluate_target_conventions(targets)
        self.assertEqual(len(violations), 1)
        self.assertIn("NoVcApp", violations[0])
        self.assertIn("versionCode", violations[0])

    def test_exempt_target_with_bundle_tokens_fails_assertion(self):
        """Ensures single-file APK targets containing bundle tokens trigger assertion failure."""
        sample = """
        val COMPATIBILITY_BAD_EXEMPT = Compatibility(
            name = "BadExempt",
            packageName = "com.bad.exempt",
            apkFileType = ApkFileType.APK,
            targets = listOf(
                AppTarget(
                    version = "1.0.0",
                    description = "Download com.bad.exempt v1.0.0 (APKM) arm64-v8a BUNDLE from APKMirror",
                )
            )
        )
        """
        targets = parse_compatibility_targets(sample)
        exempt_targets = [t for t in targets if t["is_single_file"]]
        exempt_bundle_pattern = re.compile(r"\b(APKM|BUNDLE)\b")
        matches = exempt_bundle_pattern.findall(exempt_targets[0]["description"])
        self.assertTrue(bool(matches))

    def test_constants_pinned_variant_compliance(self):
        """
        Verifies that fully pinned bundle targets in Constants.kt comply with the convention.
        Instagram is currently pinned with arch and versionCode.
        Unpinned bundle targets in Constants.kt awaiting upstream triage/pinning
        are audited and tracked.
        """
        instagram_target = [t for t in self.parsed_targets if t["var_name"] == "COMPATIBILITY_INSTAGRAM"]
        self.assertEqual(len(instagram_target), 1)
        violations = evaluate_target_conventions(instagram_target)
        self.assertEqual(
            violations,
            [],
            f"Pinned bundle target failed exact-variant convention: {violations}",
        )

    def test_constants_audit_unpinned_bundle_targets(self):
        """
        Audits existing bundle targets in Constants.kt for missing exact-variant tokens.
        Reports known unpinned targets needing arch and versionCode pinning.
        """
        violations = evaluate_target_conventions(self.parsed_targets)
        # All bundle targets are currently pinned with arch and versionCode in Constants.kt
        unpinned_vars = {v.split()[0] for v in violations}
        expected_unpinned = set()
        self.assertEqual(
            unpinned_vars,
            expected_unpinned,
            f"Unexpected unpinned targets in Constants.kt: {unpinned_vars}",
        )


if __name__ == "__main__":
    unittest.main()
