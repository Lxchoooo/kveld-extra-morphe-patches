package app.morphe.patches.instagram

import app.morphe.patcher.patch.rawResourcePatch
import app.morphe.patches.shared.Constants

private val BREAKPAD_NATIVE_LIBS = listOf(
    NativeLibTarget(
        relativePath = "lib/arm64-v8a/libbreakpad.so",
        expectedSize = 482808L,
        expectedSha256 = "79c09b76e10262b50ec646779308129abe850f79a7d7afcb737c20688787e19b",
    ),
    NativeLibTarget(
        relativePath = "lib/arm64-v8a/libbreakpad_cpp_helper.so",
        expectedSize = 6168L,
        expectedSha256 = "4b9fe38f4d1def6701ac53e26b0da054e9d578424f1dbb493aa9935947b1dfd0",
    ),
)

@Suppress("unused")
val instagramBreakpadSlimmerPatch = rawResourcePatch(
    name = "Breakpad Crash Reporter Slimmer",
    description = "Zeroes Google Breakpad native crash reporter binaries (libbreakpad.so, libbreakpad_cpp_helper.so) in-situ. WARNING: Native crash reporting is silenced. If the host application loads these libraries unconditionally without exception guards, startup crashes could occur.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_INSTAGRAM)

    execute {
        var savedBytes = 0L
        val zeroedLibs = mutableListOf<String>()

        for (target in BREAKPAD_NATIVE_LIBS) {
            val libFile = get(target.relativePath)
            val freed = zeroNativeBinary(libFile, target, "Breakpad Crash Reporter Slimmer")
            if (freed > 0L) {
                zeroedLibs.add(target.relativePath)
                savedBytes += freed
            }
        }

        if (zeroedLibs.isEmpty()) {
            println("[Breakpad Crash Reporter Slimmer] Skipped: No candidate Breakpad native libraries found to trim.")
            return@execute
        }

        val formattedSavings = formatBytes(savedBytes)
        println("[Breakpad Crash Reporter Slimmer] Zeroed ${zeroedLibs.size} Breakpad binaries (${zeroedLibs.joinToString(", ")}) -> Saved $formattedSavings.")
    }
}
