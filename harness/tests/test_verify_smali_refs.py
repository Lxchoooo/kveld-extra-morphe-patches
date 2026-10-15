"""
Unit and integration tests for smali member reference verifier.
"""

from __future__ import annotations

import os
import unittest
from pathlib import Path

from harness.verify_smali_refs import (
    ClassInfo,
    DexClassIndex,
    ResolutionResult,
    SmaliRef,
    extract_smali_refs_from_directory,
    extract_smali_refs_from_text,
    parse_smali_ref,
    resolve_reference,
)

REFERENCE_APK_PATH = Path(
    "/home/kveld/Descargas/com.twitter.android_12.34.0-prod.01-312340001_2arch_2dpi_24lang_53abc0944bf2aa378b9e166178c666b1_apkmirror.com.apkm"
)


class TestSmaliExtraction(unittest.TestCase):
    """Unit tests for smali reference extraction from patch source text."""

    def test_extract_field_reference_iget(self):
        smali = """
            iget-object v1, v0, Lcom/x/models/timelines/items/z1;->a:Lcom/x/models/sf;
        """
        refs = extract_smali_refs_from_text(smali, filename="SamplePatch.kt")
        self.assertEqual(len(refs), 1)
        ref = refs[0]
        self.assertEqual(ref.kind, "field")
        self.assertEqual(ref.defining_class, "Lcom/x/models/timelines/items/z1;")
        self.assertEqual(ref.member_name, "a")
        self.assertEqual(ref.type_descriptor, "Lcom/x/models/sf;")
        self.assertEqual(ref.descriptor, "Lcom/x/models/timelines/items/z1;->a:Lcom/x/models/sf;")

    def test_extract_field_reference_sget_array(self):
        smali = """
            sget-object v0, Lcom/x/urt/b0;->a:[I
        """
        refs = extract_smali_refs_from_text(smali)
        self.assertEqual(len(refs), 1)
        self.assertEqual(refs[0].kind, "field")
        self.assertEqual(refs[0].defining_class, "Lcom/x/urt/b0;")
        self.assertEqual(refs[0].member_name, "a")
        self.assertEqual(refs[0].type_descriptor, "[I")

    def test_extract_field_reference_primitive(self):
        smali = """
            iget v1, v0, Lcom/x/icons/b;->a:I
        """
        refs = extract_smali_refs_from_text(smali)
        self.assertEqual(len(refs), 1)
        self.assertEqual(refs[0].type_descriptor, "I")

    def test_extract_method_reference_invoke_virtual(self):
        smali = """
            invoke-virtual {v1}, Lcom/x/models/timelines/items/k1;->y()Lcom/x/models/ef;
        """
        refs = extract_smali_refs_from_text(smali)
        self.assertEqual(len(refs), 1)
        ref = refs[0]
        self.assertEqual(ref.kind, "method")
        self.assertEqual(ref.defining_class, "Lcom/x/models/timelines/items/k1;")
        self.assertEqual(ref.member_name, "y")
        self.assertEqual(ref.type_descriptor, "()Lcom/x/models/ef;")

    def test_extract_method_reference_with_params(self):
        smali = """
            invoke-static/range {v0 .. v3}, Lcom/x/custom/Helper;->execute(ILjava/lang/String;[Z)V
        """
        refs = extract_smali_refs_from_text(smali)
        self.assertEqual(len(refs), 1)
        ref = refs[0]
        self.assertEqual(ref.defining_class, "Lcom/x/custom/Helper;")
        self.assertEqual(ref.member_name, "execute")
        self.assertEqual(ref.type_descriptor, "(ILjava/lang/String;[Z)V")

    def test_ignore_framework_and_extension_types(self):
        smali = """
            invoke-static {}, Ljava/util/Collections;->emptyList()Ljava/util/List;
            sget-object v0, Ljava/lang/Boolean;->FALSE:Ljava/lang/Boolean;
            invoke-virtual {v0}, Landroid/content/Context;->getPackageName()Ljava/lang/String;
            sget-object v1, Lkotlin/Unit;->INSTANCE:Lkotlin/Unit;
            invoke-static {}, Lcom/kveld9/morphe/extra/extension/TwitterFontHelper;->process()V
        """
        refs = extract_smali_refs_from_text(smali)
        self.assertEqual(len(refs), 0)

    def test_ignore_const_string_and_comments(self):
        smali = """
            # iget-object v0, v1, Lcom/x/fake/Commented;->field:I
            // invoke-virtual {v0}, Lcom/x/fake/Commented;->method()V
            const-string v0, "sget-object v0, Lcom/x/fake/ConstString;->value:I"
            const-string v1, "Lcom/x/fake/ConstString;->method()V"
        """
        refs = extract_smali_refs_from_text(smali)
        self.assertEqual(len(refs), 0)

    def test_handle_kotlin_register_interpolation(self):
        smali = """
            sget-object v$targetReg, Lcom/x/inlineactionbar/k1;->Countless:Lcom/x/inlineactionbar/k1;
        """
        refs = extract_smali_refs_from_text(smali)
        self.assertEqual(len(refs), 1)
        self.assertEqual(refs[0].defining_class, "Lcom/x/inlineactionbar/k1;")
        self.assertEqual(refs[0].member_name, "Countless")


class TestSmaliResolutionUnit(unittest.TestCase):
    """Unit tests for resolution against synthetic DEX class index."""

    def setUp(self):
        self.index = DexClassIndex({
            "Lcom/x/models/TargetClass;": ClassInfo(
                descriptor="Lcom/x/models/TargetClass;",
                fields={
                    "presentField": "Lcom/x/models/FieldType;",
                    "retypedField": "[I",
                    "renamedField": "Lcom/x/models/UniqueType;",
                    "unrelatedObfField": "Lcom/x/models/NewType;",
                },
                methods={
                    "presentMethod": ["()V", "(I)Ljava/lang/String;"],
                    "renamedMethod": ["(Lcom/x/models/Arg;)Lcom/x/models/Ret;"],
                    "retypedMethod": ["(I)I"],
                },
            ),
        })

    def test_resolve_field_present(self):
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->presentField:Lcom/x/models/FieldType;", "field")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "PRESENT")

    def test_resolve_field_renamed(self):
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->oldName:Lcom/x/models/UniqueType;", "field")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "RENAMED")
        self.assertIn("renamedField", res.details)

    def test_resolve_field_retyped(self):
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->retypedField:Lcom/x/models/OldType;", "field")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "RETYPED")
        self.assertIn("now [I", res.details)

    def test_resolve_field_gone_unrelated_class(self):
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->unrelatedObfField:Lcom/x/models/OldType;", "field")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "GONE")

    def test_resolve_field_gone_missing(self):
        # Uses VanishedType which has 0 same-type candidates in TargetClass, asserting clean GONE
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->missingField:Lcom/x/models/VanishedType;", "field")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "GONE")

    def test_resolve_class_missing(self):
        ref = parse_smali_ref("Lcom/x/models/MissingClass;->field:I", "field")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "GONE")
        self.assertIn("class not found", res.details)

    def test_resolve_method_present(self):
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->presentMethod(I)Ljava/lang/String;", "method")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "PRESENT")

    def test_resolve_method_renamed(self):
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->oldMethod(Lcom/x/models/Arg;)Lcom/x/models/Ret;", "method")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "RENAMED")
        self.assertIn("renamedMethod", res.details)

    def test_resolve_method_retyped(self):
        ref = parse_smali_ref("Lcom/x/models/TargetClass;->retypedMethod()V", "method")
        res = resolve_reference(ref, self.index)
        self.assertEqual(res.status, "RETYPED")


class TestSmaliResolutionIntegration(unittest.TestCase):
    """Integration test asserting known outcomes on Twitter 12.34.0 reference APK."""

    @classmethod
    def setUpClass(cls):
        if not REFERENCE_APK_PATH.is_file():
            cls.dex_index = None
        else:
            cls.dex_index = DexClassIndex.from_apk(REFERENCE_APK_PATH)

    def test_reference_apk_known_member_outcomes(self):
        """Assert e0.a is PRESENT and b1.g is GONE against Twitter 12.34.0 reference APK."""
        if self.dex_index is None:
            self.skipTest(f"Reference APK not found at {REFERENCE_APK_PATH}")

        # Known case 1: Lcom/x/urt/e0;->a:Lcom/x/urt/e0; must be PRESENT
        ref_e0 = parse_smali_ref("Lcom/x/urt/e0;->a:Lcom/x/urt/e0;", "field", location="TwitterRemoveAdsPatch.kt:147")
        res_e0 = resolve_reference(ref_e0, self.dex_index)
        self.assertEqual(res_e0.status, "PRESENT")

        # Known case 2: Lcom/x/models/timelines/items/b1;->g:Lcom/x/models/u0; must be GONE
        ref_b1 = parse_smali_ref("Lcom/x/models/timelines/items/b1;->g:Lcom/x/models/u0;", "field", location="TwitterTimelineUiPatch.kt:461")
        res_b1 = resolve_reference(ref_b1, self.dex_index)
        self.assertEqual(res_b1.status, "GONE")

    def test_current_twitter_patch_sources_verification(self):
        """Verify all Twitter patch sources in current checkout against reference APK."""
        if self.dex_index is None:
            self.skipTest(f"Reference APK not found at {REFERENCE_APK_PATH}")

        patches_dir = Path("patches/src/main/kotlin/app/morphe/patches")
        extracted_refs = extract_smali_refs_from_directory(patches_dir, app="twitter")
        self.assertGreater(len(extracted_refs), 0)

        results = [resolve_reference(r, self.dex_index) for r in extracted_refs]

        # Confirm e0.a is present in results
        e0_matches = [r for r in results if r.ref.descriptor == "Lcom/x/urt/e0;->a:Lcom/x/urt/e0;"]
        self.assertTrue(len(e0_matches) > 0)
        self.assertTrue(all(r.status == "PRESENT" for r in e0_matches))

        # In current checkout (post-12.34.0 fixes), all 28 Twitter smali references must be PRESENT
        all_present = all(r.status == "PRESENT" for r in results)
        self.assertTrue(all_present)
        self.assertEqual(len(results), 28)


if __name__ == "__main__":
    unittest.main()
