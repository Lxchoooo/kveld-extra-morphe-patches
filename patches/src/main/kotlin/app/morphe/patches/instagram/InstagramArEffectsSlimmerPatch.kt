package app.morphe.patches.instagram

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

internal data class NativeLibTarget(
    val relativePath: String,
    val expectedSize: Long,
    val expectedSha256: String,
)

private val AR_ASSET_PATHS = listOf(
    "assets/app_modules/contents/arservicesforbodytracking.json",
    "assets/app_modules/contents/arservicesforexpressionfitting.json",
    "assets/app_modules/contents/arservicesforfacewave.json",
    "assets/app_modules/contents/arservicesforgenericml.json",
    "assets/app_modules/contents/arservicesforhairsegmentation.json",
    "assets/app_modules/contents/arservicesforhandtracking.json",
    "assets/app_modules/contents/arservicesforpersonsegmentation.json",
    "assets/app_modules/contents/arservicesforruntimerigmapping.json",
    "assets/app_modules/contents/arservicesforunifiedtargettracking.json",
    "assets/app_modules/contents/arservicesforwolf.json",
    "assets/app_modules/contents/arclassBenchmarks.json",
    "assets/ReferenceFaceShapeConstants/v01_high_end_face_compressed.bin",
)

private val AR_NATIVE_LIBS = listOf(
    NativeLibTarget(
        relativePath = "lib/arm64-v8a/libarcore_sdk_c.so",
        expectedSize = 68120L,
        expectedSha256 = "bd61b8bd62ed3029ecc965c85fc57d06aed8239a8f7f7897773cab22b5f162ea",
    ),
    NativeLibTarget(
        relativePath = "lib/arm64-v8a/libarcore_sdk_jni.so",
        expectedSize = 117272L,
        expectedSha256 = "bdc27b78a5360c80c3ae1a4da07f9792f8414b96a438b608e19b01fd083589ce",
    ),
)

// Standard 4-byte ELF magic header: 0x7F, 'E', 'L', 'F'
internal val ELF_MAGIC = byteArrayOf(0x7F.toByte(), 0x45.toByte(), 0x4C.toByte(), 0x46.toByte())

internal fun validateFilePreconditions(
    file: File,
    expectedSize: Long,
    logPrefix: String,
    relativePath: String,
): Boolean {
    if (!file.exists() || !file.isFile) {
        println("[$logPrefix] Skipped $relativePath: file not found.")
        return false
    }

    val actualSize = file.length()
    if (actualSize != expectedSize) {
        println("[$logPrefix] Skipped $relativePath: size mismatch (expected $expectedSize, found $actualSize).")
        return false
    }

    if (expectedSize < ELF_MAGIC.size.toLong()) {
        println("[$logPrefix] Skipped $relativePath: file size smaller than ELF header.")
        return false
    }

    return true
}

internal fun verifyElfMagic(raf: RandomAccessFile): Boolean {
    if (raf.length() < ELF_MAGIC.size.toLong()) return false

    val header = ByteArray(ELF_MAGIC.size)
    raf.seek(0L)
    raf.readFully(header)
    return header.contentEquals(ELF_MAGIC)
}

internal fun computeSha256(raf: RandomAccessFile): String {
    val digest = MessageDigest.getInstance("SHA-256")
    raf.seek(0L)
    val buffer = ByteArray(8192)
    var bytesRead = raf.read(buffer)
    while (bytesRead != -1) {
        digest.update(buffer, 0, bytesRead)
        bytesRead = raf.read(buffer)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

internal fun zeroNativeBinary(
    file: File,
    target: NativeLibTarget,
    logPrefix: String,
): Long {
    if (!validateFilePreconditions(file, target.expectedSize, logPrefix, target.relativePath)) {
        return 0L
    }

    return try {
        RandomAccessFile(file, "rw").use { raf ->
            if (!verifyElfMagic(raf)) {
                println("[$logPrefix] Skipped ${target.relativePath}: invalid ELF magic header.")
                return@use 0L
            }

            val actualSha256 = computeSha256(raf)
            if (!actualSha256.equals(target.expectedSha256, ignoreCase = true)) {
                println("[$logPrefix] Skipped ${target.relativePath}: SHA-256 mismatch (expected ${target.expectedSha256}, found $actualSha256).")
                return@use 0L
            }

            raf.setLength(0L)
            target.expectedSize
        }
    } catch (e: Exception) {
        println("[$logPrefix] Skipped ${target.relativePath}: I/O error (${e.message}).")
        0L
    }
}

private fun pruneAssetFile(file: File): Long {
    if (!file.exists() || !file.isFile) return 0L
    val size = file.length()
    return if (file.delete()) size else 0L
}

private fun pruneArAssets(getFn: (String) -> File): Pair<Int, Long> {
    var count = 0
    var savedBytes = 0L

    for (assetPath in AR_ASSET_PATHS) {
        val assetFile = getFn(assetPath)
        val freed = pruneAssetFile(assetFile)
        if (freed > 0L) {
            count++
            savedBytes += freed
        }
    }

    return Pair(count, savedBytes)
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes bytes"
    return if (bytes >= 1024 * 1024) {
        String.format(java.util.Locale.US, "%.2f MB", bytes.toDouble() / (1024 * 1024))
    } else {
        "${bytes / 1024} KB"
    }
}

@Suppress("unused")
val instagramArEffectsSlimmerPatch = rawResourcePatch(
    name = "AR Effects Slimmer",
    description = "Strips ARCore native binaries (libarcore_sdk_c.so, libarcore_sdk_jni.so) and AR effect/face tracking asset models. WARNING: Disables camera AR filters, 3D face effects, and body/face tracking.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_INSTAGRAM)

    execute {
        val (prunedAssets, savedAssetBytes) = pruneArAssets { get(it) }

        var savedNativeBytes = 0L
        val zeroedLibs = mutableListOf<String>()

        for (target in AR_NATIVE_LIBS) {
            val libFile = get(target.relativePath)
            val freed = zeroNativeBinary(libFile, target, "AR Effects Slimmer")
            if (freed > 0L) {
                zeroedLibs.add(target.relativePath)
                savedNativeBytes += freed
            }
        }

        val totalSavedBytes = savedAssetBytes + savedNativeBytes
        if (prunedAssets == 0 && zeroedLibs.isEmpty()) {
            println("[AR Effects Slimmer] Skipped: No candidate AR assets or native libraries found to trim.")
            return@execute
        }

        val formattedSavings = formatBytes(totalSavedBytes)
        println("[AR Effects Slimmer] Pruned $prunedAssets assets, zeroed ${zeroedLibs.size} native libs (${zeroedLibs.joinToString(", ")}) -> Saved $formattedSavings.")
    }
}
