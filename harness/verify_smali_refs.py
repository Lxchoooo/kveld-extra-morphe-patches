#!/usr/bin/env python3
"""
Smali Member Reference Verifier for Target DEX.

Extracts obfuscated field and method descriptors hardcoded in smali injection
within Morphe patch sources and verifies each against the target APK's DEX.
Fails loudly if any member is RENAMED, RETYPED, or GONE.
"""

from __future__ import annotations

import argparse
import glob
import hashlib
import io
import logging
import os
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Dict, List, Optional, Set, Tuple

# Quiet third-party loggers
try:
    from loguru import logger as loguru_logger
    loguru_logger.disable("androguard")
except Exception:
    pass
logging.getLogger("androguard").setLevel(logging.ERROR)

IGNORED_CLASS_PREFIXES: Tuple[str, ...] = (
    "Ljava/",
    "Ljavax/",
    "Landroid/",
    "Landroidx/",
    "Lkotlin/",
    "Lkotlinx/",
    "Ldalvik/",
    "Lcom/kveld9/",
    "Lapp/morphe/",
)

FIELD_INSN_RE = re.compile(
    r"\b(?:[is]get|[is]put)(?:-\w+)?\s+[^,]+(?:,\s*[^,]+)?,\s*"
    r"(L[a-zA-Z0-9_$/]+;->[a-zA-Z0-9_$<>-]+:\[*(?:[ZBSCIJFD]|L[a-zA-Z0-9_$/]+;))"
)

METHOD_INSN_RE = re.compile(
    r"\binvoke-(?:virtual|super|direct|static|interface)(?:/range)?\s+\{[^\}]*\},\s*"
    r"(L[a-zA-Z0-9_$/]+;->[a-zA-Z0-9_$<>-]+\((?:\[*(?:[ZBSCIJFD]|L[a-zA-Z0-9_$/]+;))*\)(?:V|\[*(?:[ZBSCIJFD]|L[a-zA-Z0-9_$/]+;)))"
)


@dataclass(frozen=True)
class SmaliRef:
    location: str  # file:line
    kind: str      # "field" or "method"
    descriptor: str  # Full descriptor: Lcls;->name:type or Lcls;->name(params)ret
    defining_class: str  # Lcls;
    member_name: str     # name
    type_descriptor: str # type or (params)ret


@dataclass
class ResolutionResult:
    ref: SmaliRef
    status: str  # "PRESENT", "RENAMED", "RETYPED", "GONE"
    details: str = ""


@dataclass
class ClassInfo:
    descriptor: str
    fields: Dict[str, str]          # name -> type_descriptor
    methods: Dict[str, List[str]]   # name -> list of signature descriptors
    superclass: Optional[str] = None
    interfaces: List[str] = None

    def __post_init__(self):
        if self.interfaces is None:
            self.interfaces = []


def parse_smali_ref(raw_desc: str, kind: str, location: str = "") -> Optional[SmaliRef]:
    """Parse a member descriptor into a SmaliRef object."""
    if ";->" not in raw_desc:
        return None
    cls_part, member_part = raw_desc.split(";->", 1)
    cls_name = cls_part + ";"

    if kind == "field":
        if ":" not in member_part:
            return None
        m_name, m_type = member_part.split(":", 1)
        return SmaliRef(
            location=location,
            kind="field",
            descriptor=raw_desc,
            defining_class=cls_name,
            member_name=m_name,
            type_descriptor=m_type,
        )
    elif kind == "method":
        if "(" not in member_part or ")" not in member_part:
            return None
        m_name, sig_part = member_part.split("(", 1)
        m_sig = "(" + sig_part
        return SmaliRef(
            location=location,
            kind="method",
            descriptor=raw_desc,
            defining_class=cls_name,
            member_name=m_name,
            type_descriptor=m_sig,
        )
    return None


def extract_smali_refs_from_text(text: str, filename: str = "") -> List[SmaliRef]:
    """Extract smali member references from text content line by line."""
    refs: List[SmaliRef] = []
    lines = text.splitlines()

    for idx, line in enumerate(lines):
        line_num = idx + 1
        stripped = line.strip()

        # Skip comments
        if stripped.startswith(("#", "//", "/*", "*")):
            continue

        # Skip const-string lines to avoid string literal false positives
        if re.search(r"\bconst-string\b", stripped):
            continue

        # Normalize Kotlin escaped string templates
        norm_line = line.replace("${'$'}", "$")

        # Field instructions: iget/sget/iput/sput
        for match in FIELD_INSN_RE.finditer(norm_line):
            desc = match.group(1)
            # Skip framework and extension types
            cls_name = desc.split(";->")[0] + ";"
            if any(cls_name.startswith(prefix) for prefix in IGNORED_CLASS_PREFIXES):
                continue
            # Skip dynamic Kotlin string interpolations in member names
            member_part = desc.split(";->")[1]
            if "$" in member_part.split(":", 1)[0]:
                continue
            loc = f"{filename}:{line_num}" if filename else str(line_num)
            ref = parse_smali_ref(desc, "field", loc)
            if ref:
                refs.append(ref)

        # Method instructions: invoke-*
        for match in METHOD_INSN_RE.finditer(norm_line):
            desc = match.group(1)
            cls_name = desc.split(";->")[0] + ";"
            if any(cls_name.startswith(prefix) for prefix in IGNORED_CLASS_PREFIXES):
                continue
            member_part = desc.split(";->")[1]
            if "$" in member_part.split("(", 1)[0]:
                continue
            loc = f"{filename}:{line_num}" if filename else str(line_num)
            ref = parse_smali_ref(desc, "method", loc)
            if ref:
                refs.append(ref)

    return refs


def extract_smali_refs_from_file(file_path: Path | str) -> List[SmaliRef]:
    """Extract smali member references from a single Kotlin patch file."""
    path = Path(file_path)
    if not path.is_file():
        return []
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        content = f.read()
    return extract_smali_refs_from_text(content, filename=str(path))


def extract_smali_refs_from_directory(patches_dir: Path | str, app: Optional[str] = None) -> List[SmaliRef]:
    """Scan patch source directory for all .kt files and extract smali references."""
    root = Path(patches_dir)
    if not root.exists():
        raise FileNotFoundError(f"Patches directory not found: {patches_dir}")

    target_dir = root
    if app:
        sub = root / app
        if sub.is_dir():
            target_dir = sub

    kt_files = sorted(target_dir.rglob("*.kt"))
    if app and target_dir == root:
        kt_files = [f for f in kt_files if f"/{app}/" in str(f) or f.name.lower().startswith(app.lower())]

    all_refs: List[SmaliRef] = []
    for kt_file in kt_files:
        all_refs.extend(extract_smali_refs_from_file(kt_file))
    return all_refs


def _find_dexdump_bin() -> Optional[str]:
    """Locate dexdump executable in Android SDK or system PATH."""
    which_dexdump = shutil.which("dexdump")
    if which_dexdump:
        return which_dexdump

    sdk_dirs = [
        Path.home() / "Android" / "Sdk",
        Path("/usr/lib/android-sdk"),
    ]
    env_sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
    if env_sdk:
        sdk_dirs.insert(0, Path(env_sdk))

    for sdk_dir in sdk_dirs:
        build_tools = sdk_dir / "build-tools"
        if build_tools.is_dir():
            for ver_dir in sorted(build_tools.iterdir(), reverse=True):
                cand = ver_dir / "dexdump"
                if cand.is_file() and os.access(cand, os.X_OK):
                    return str(cand)
    return None


class DexClassIndex:
    """Class and member index constructed from DEX files."""

    def __init__(self, classes: Dict[str, ClassInfo]):
        self.classes = classes

    @classmethod
    def from_apk(cls, apk_path: Path | str, cache_dir: Optional[Path | str] = None) -> DexClassIndex:
        """Parse APK/APKM and construct DexClassIndex using fast dexdump or androguard fallback."""
        apk_p = Path(apk_path).resolve()
        if not apk_p.is_file():
            raise FileNotFoundError(f"APK not found: {apk_path}")

        # Compute file sha256 for caching
        hasher = hashlib.sha256()
        with open(apk_p, "rb") as f:
            while chunk := f.read(65536):
                hasher.update(chunk)
        apk_hash = hasher.hexdigest()

        if cache_dir is None:
            cache_dir = Path("/tmp/agy/verifydex")
        cache_p = Path(cache_dir)
        cache_p.mkdir(parents=True, exist_ok=True)
        dump_path = cache_p / f"{apk_hash}.dump"

        dexdump_bin = _find_dexdump_bin()
        if dexdump_bin:
            if not dump_path.exists() or dump_path.stat().st_size == 0:
                cls._dump_with_dexdump(apk_p, dump_path, dexdump_bin, cache_p)
            if dump_path.exists() and dump_path.stat().st_size > 0:
                classes = cls._parse_dexdump_output(dump_path)
                return DexClassIndex(classes)

        # Fallback to androguard
        return cls._from_apk_androguard(apk_p)

    @classmethod
    def _dump_with_dexdump(cls, apk_p: Path, dump_path: Path, dexdump_bin: str, cache_p: Path) -> None:
        """Run dexdump on extracted APK or split APK files."""
        # Handle APKM or standalone APK
        with zipfile.ZipFile(apk_p, "r") as zf:
            namelist = zf.namelist()
            if "base.apk" in namelist:
                base_apk_path = cache_p / f"{apk_p.stem}_base.apk"
                with open(base_apk_path, "wb") as f_out:
                    f_out.write(zf.read("base.apk"))
                target_to_dump = base_apk_path
            else:
                target_to_dump = apk_p

        try:
            with open(dump_path, "w", encoding="utf-8") as out_f:
                subprocess.run(
                    [dexdump_bin, str(target_to_dump)],
                    stdout=out_f,
                    stderr=subprocess.DEVNULL,
                    check=True,
                )
        finally:
            if "base.apk" in namelist and base_apk_path.exists():
                try:
                    base_apk_path.unlink()
                except OSError:
                    pass

    @classmethod
    def _parse_dexdump_output(cls, dump_path: Path) -> Dict[str, ClassInfo]:
        """Stream dexdump text output and parse classes, fields, and methods."""
        classes: Dict[str, ClassInfo] = {}
        current_class: Optional[ClassInfo] = None
        current_member_name: Optional[str] = None

        with open(dump_path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                line_s = line.strip()
                if line_s.startswith("Class descriptor"):
                    desc = line_s.split(":", 1)[1].strip().strip("'")
                    current_class = ClassInfo(descriptor=desc, fields={}, methods={})
                    classes[desc] = current_class
                    current_member_name = None
                elif current_class is not None:
                    if line_s.startswith("Superclass"):
                        current_class.superclass = line_s.split(":", 1)[1].strip().strip("'")
                    elif line_s.startswith("name"):
                        current_member_name = line_s.split(":", 1)[1].strip().strip("'")
                    elif line_s.startswith("type") and current_member_name is not None:
                        member_type = line_s.split(":", 1)[1].strip().strip("'")
                        if "(" in member_type:
                            current_class.methods.setdefault(current_member_name, []).append(member_type)
                        else:
                            current_class.fields[current_member_name] = member_type
                        current_member_name = None
        return classes

    @classmethod
    def _from_apk_androguard(cls, apk_p: Path) -> DexClassIndex:
        """Fallback DEX parser using androguard."""
        from androguard.core.dex import DEX
        from harness.core.apk import ApkContext

        classes: Dict[str, ClassInfo] = {}
        with ApkContext(apk_p) as ctx:
            dex_entries = ctx.extract_dex_bytes()

        for dex_name, raw_bytes in dex_entries:
            d = DEX(raw_bytes)
            for c in d.get_classes():
                cls_name = c.get_name()
                cls_info = classes.get(cls_name)
                if not cls_info:
                    cls_info = ClassInfo(
                        descriptor=cls_name,
                        fields={},
                        methods={},
                        superclass=c.get_superclassname(),
                        interfaces=list(c.get_interfaces()),
                    )
                    classes[cls_name] = cls_info

                for f in c.get_fields():
                    cls_info.fields[f.get_name()] = f.get_descriptor()

                for m in c.get_methods():
                    cls_info.methods.setdefault(m.get_name(), []).append(m.get_descriptor())

        return DexClassIndex(classes)


def resolve_reference(ref: SmaliRef, dex_index: DexClassIndex) -> ResolutionResult:
    """Resolve a single SmaliRef against the DEX class index."""
    cls_info = dex_index.classes.get(ref.defining_class)
    if cls_info is None:
        return ResolutionResult(
            ref=ref,
            status="GONE",
            details="class not found in DEX",
        )

    if ref.kind == "field":
        fields = cls_info.fields
        # 1. Same member name exists: exact match => PRESENT, different descriptor => RETYPED or GONE
        if ref.member_name in fields:
            actual_type = fields[ref.member_name]
            if actual_type == ref.type_descriptor:
                return ResolutionResult(ref=ref, status="PRESENT")
            # In obfuscated classes, single letter names are reassigned by R8.
            # An unrelated obfuscated class indicates the old field was deleted (GONE).
            # Primitive, array, or standard library changes indicate retyping (RETYPED).
            is_primitive_or_array = actual_type.startswith("[") or actual_type in "ZBSCIJFD"
            is_non_obfuscated = not actual_type.startswith("Lcom/x/")
            if is_primitive_or_array or is_non_obfuscated:
                return ResolutionResult(
                    ref=ref,
                    status="RETYPED",
                    details=f"now {actual_type}",
                )
            return ResolutionResult(
                ref=ref,
                status="GONE",
                details=f"field {ref.member_name} has unrelated type {actual_type}",
            )

        # 2. Name is absent: check for rename candidate (same descriptor type under different name)
        candidates = [
            name for name, ftype in fields.items()
            if ftype == ref.type_descriptor
        ]
        if candidates:
            return ResolutionResult(
                ref=ref,
                status="RENAMED",
                details=f"candidates: {sorted(candidates)}",
            )

        return ResolutionResult(ref=ref, status="GONE", details="field not found in class")

    elif ref.kind == "method":
        methods = cls_info.methods
        # 1. Same member name exists: exact match => PRESENT, different signature => RETYPED
        if ref.member_name in methods:
            if ref.type_descriptor in methods[ref.member_name]:
                return ResolutionResult(ref=ref, status="PRESENT")
            actual_sigs = methods[ref.member_name]
            return ResolutionResult(
                ref=ref,
                status="RETYPED",
                details=f"signatures: {actual_sigs}",
            )

        # 2. Name is absent: check for rename candidate (same signature under different name)
        candidates = [
            name for name, sigs in methods.items()
            if ref.type_descriptor in sigs
        ]
        if candidates:
            return ResolutionResult(
                ref=ref,
                status="RENAMED",
                details=f"candidates: {sorted(candidates)}",
            )

        return ResolutionResult(ref=ref, status="GONE", details="method not found in class")

    return ResolutionResult(ref=ref, status="GONE", details="unknown member kind")


def format_results_table(results: List[ResolutionResult], base_dir: Optional[Path] = None) -> str:
    """Format resolution results as a human-readable ASCII table."""
    if not results:
        return "No smali references found to verify."

    rows = []
    for r in results:
        loc = r.ref.location
        if base_dir and loc.startswith(str(base_dir)):
            loc = os.path.relpath(loc, base_dir)
        rows.append((r.status, r.ref.kind, loc, r.ref.descriptor, r.details))

    col_names = ("Status", "Kind", "Location", "Descriptor", "Details")
    widths = [len(name) for name in col_names]
    for row in rows:
        for i, val in enumerate(row):
            widths[i] = max(widths[i], len(val))

    def make_divider(char: str = "-") -> str:
        parts = [char * (w + 2) for w in widths]
        return "+" + "+".join(parts) + "+"

    def format_row(cols: Tuple[str, ...]) -> str:
        parts = [f" {cols[i].ljust(widths[i])} " for i in range(len(cols))]
        return "|" + "|".join(parts) + "|"

    output = []
    sep = make_divider("-")
    output.append(sep)
    output.append(format_row(col_names))
    output.append(sep)

    present_count = 0
    renamed_count = 0
    retyped_count = 0
    gone_count = 0

    for row in rows:
        output.append(format_row(row))
        st = row[0]
        if st == "PRESENT":
            present_count += 1
        elif st == "RENAMED":
            renamed_count += 1
        elif st == "RETYPED":
            retyped_count += 1
        elif st == "GONE":
            gone_count += 1

    output.append(sep)
    total = len(rows)
    output.append(
        f"Summary: {total} references checked "
        f"({present_count} PRESENT, {gone_count} GONE, {renamed_count} RENAMED, {retyped_count} RETYPED)"
    )
    return "\n".join(output)


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify injected smali member references against target APK DEX."
    )
    parser.add_argument("--apk", required=True, help="Path to target APK or APKM bundle.")
    parser.add_argument("--patches", required=False, help="Path to patch source directory.")
    parser.add_argument("--app", required=False, help="Filter patches by application (e.g. twitter).")
    parser.add_argument(
        "--member",
        required=False,
        help="Verify a single member descriptor directly (e.g. Lcom/x/urt/e0;->a:Lcom/x/urt/e0;).",
    )
    args = parser.parse_args()

    if not args.patches and not args.member:
        parser.error("Either --patches or --member must be specified.")

    refs: List[SmaliRef] = []
    if args.member:
        kind = "method" if "(" in args.member else "field"
        parsed = parse_smali_ref(args.member, kind, location="CLI")
        if not parsed:
            print(f"[ERROR] Invalid member descriptor format: {args.member}", file=sys.stderr)
            return 1
        refs.append(parsed)

    if args.patches:
        extracted = extract_smali_refs_from_directory(args.patches, app=args.app)
        refs.extend(extracted)

    if not refs:
        print("No smali member references extracted.")
        return 0

    apk_path = Path(args.apk)
    dex_index = DexClassIndex.from_apk(apk_path)

    results = [resolve_reference(r, dex_index) for r in refs]

    base_dir = Path(args.patches).resolve() if args.patches else Path.cwd()
    table = format_results_table(results, base_dir=base_dir)
    print(table)

    all_present = all(r.status == "PRESENT" for r in results)
    if all_present:
        print("SUCCESS: All smali member references are PRESENT in the target DEX.")
        return 0
    else:
        invalid = [r for r in results if r.status != "PRESENT"]
        print(f"FAILURE: {len(invalid)} member reference(s) failed verification.")
        return 1


if __name__ == "__main__":
    sys.exit(main())
