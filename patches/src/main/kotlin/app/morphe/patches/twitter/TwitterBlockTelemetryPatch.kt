package app.morphe.patches.twitter

import app.morphe.patcher.Fingerprint
import app.morphe.patcher.extensions.InstructionExtensions.addInstructions
import app.morphe.patcher.extensions.InstructionExtensions.replaceInstruction
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.patch.resourcePatch
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patches.shared.Constants
import app.morphe.patches.shared.disableComponentsByName
import app.morphe.patches.shared.removeComponentDiscoveryRegistrarsWhere
import app.morphe.patches.shared.setApplicationMetaData
import app.morphe.patches.shared.stripPermissionsWhere
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OffsetInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private val twitterTelemetryResourcePatch = resourcePatch(
    name = "X Telemetry Manifest Purge",
    description = "Strips tracking/advertising permissions, disables analytics services and receivers, and injects opt-out metadata in AndroidManifest.xml.",
    default = false,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)

    execute {
        val manifestFile = get("AndroidManifest.xml")
        if (!manifestFile.exists()) {
            println("[X Telemetry] AndroidManifest.xml not found - skipping manifest purge.")
            return@execute
        }

        val blockedPermissions = setOf(
            "com.google.android.gms.permission.AD_ID",
            "android.permission.ACCESS_ADSERVICES_AD_ID",
            "android.permission.ACCESS_ADSERVICES_ATTRIBUTION",
            "android.permission.ACCESS_ADSERVICES_TOPICS",
            "com.google.android.finsky.permission.BIND_GET_INSTALL_REFERRER_SERVICE",
        )

        val blockedComponents = setOf(
            // Services
            "com.google.android.gms.measurement.AppMeasurementService",
            "com.google.android.gms.measurement.AppMeasurementJobService",
            "com.google.android.datatransport.runtime.backends.TransportBackendDiscovery",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",

            // Receivers
            "com.google.android.gms.measurement.AppMeasurementReceiver",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerSchedulerBroadcastReceiver",
            "com.twitter.analytics.tracking.InstallationReferrer\$OemIntentReceiver",

            // Braze marketing components (BrazePushReceiver is preserved)
            "com.braze.BrazeFlushPushDeliveryReceiver",
            "com.braze.push.NotificationTrampolineActivity",
            "com.braze.ui.activities.ContentCardsActivity",
            "com.braze.ui.BrazeWebViewActivity",
        )

        val optOutMetadata = listOf(
            "firebase_analytics_collection_enabled" to "false",
            "firebase_analytics_collection_deactivated" to "true",
            "firebase_crashlytics_collection_enabled" to "false",
            "firebase_performance_collection_enabled" to "false",
            "firebase_performance_collection_deactivated" to "true",
            "google_analytics_adid_collection_enabled" to "false",
            "google_analytics_default_allow_ad_personalization_signals" to "false",
        )

        var removedPermissions = 0
        var disabledComponents = 0
        var removedRegistrars = 0
        var injectedMetadata = 0

        document(manifestFile.absolutePath).use { doc ->
            val application = doc.documentElement.getElementsByTagName("application").item(0) as? org.w3c.dom.Element

            // 1. Remove advertising and tracking permissions
            removedPermissions = doc.documentElement.stripPermissionsWhere { it in blockedPermissions }.size

            if (application != null) {
                // 2. Disable verified measurement and tracking components
                disabledComponents = application.disableComponentsByName(*blockedComponents.toTypedArray())

                // 3. Remove discovery meta-data registrars (excluding MLKit, push messaging, and Crashlytics)
                removedRegistrars = application.removeComponentDiscoveryRegistrarsWhere { name ->
                    val matchesKeyword = listOf(
                        "analytics",
                        "measurement",
                        "crashlytics",
                        "perf",
                        "remoteconfig",
                        "sessions",
                        "abt",
                    ).any { name.contains(it, ignoreCase = true) }
                    val isExcluded = name.contains("mlkit", ignoreCase = true) ||
                        name.contains("messaging", ignoreCase = true) ||
                        name.contains("crashlytics", ignoreCase = true)
                    matchesKeyword && !isExcluded
                }

                // 4. Inject declarative SDK opt-out metadata flags
                optOutMetadata.forEach { (name, value) ->
                    application.setApplicationMetaData(name, value)
                    injectedMetadata++
                }
            }
        }

        println("[X Telemetry] Stripped $removedPermissions permissions, disabled $disabledComponents tracking components, removed $removedRegistrars discovery registrars, and injected $injectedMetadata opt-out flags in AndroidManifest.xml.")
    }
}

context(_: BytecodePatchContext)
private fun hookVoidMethod(
    definingClass: String,
    name: String,
    parameters: List<String>,
    hookedMethods: MutableList<String>,
    label: String,
) {
    Fingerprint(
        definingClass = definingClass,
        name = name,
        parameters = parameters,
        returnType = "V",
    ).method.apply {
        addInstructions(0, "return-void")
        hookedMethods.add(label)
    }
}

private const val MAX_NULL_CHECK_WINDOW = 8

private fun isFirebasePerfGetInstance(ins: Instruction): Boolean {
    if (ins.opcode != Opcode.INVOKE_STATIC) return false
    val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    return ref.parameterTypes.isEmpty() &&
        ref.returnType == ref.definingClass &&
        ref.definingClass.startsWith("Lcom/google/firebase/perf/")
}

private fun isGetClassOnRegister(ins: Instruction, reg: Int): Boolean {
    if (ins.opcode != Opcode.INVOKE_VIRTUAL && ins.opcode != Opcode.INVOKE_VIRTUAL_RANGE) return false
    val ref = (ins as? ReferenceInstruction)?.reference as? MethodReference ?: return false
    if (ref.definingClass != "Ljava/lang/Object;" ||
        ref.name != "getClass" ||
        ref.returnType != "Ljava/lang/Class;" ||
        ref.parameterTypes.isNotEmpty()
    ) {
        return false
    }
    return when (ins) {
        is FiveRegisterInstruction -> ins.registerCount == 1 && ins.registerC == reg
        is RegisterRangeInstruction -> ins.registerCount == 1 && ins.startRegister == reg
        else -> false
    }
}

private fun isMoveResult(opcode: Opcode): Boolean =
    opcode == Opcode.MOVE_RESULT ||
        opcode == Opcode.MOVE_RESULT_WIDE ||
        opcode == Opcode.MOVE_RESULT_OBJECT

private fun writesRegister(ins: Instruction, reg: Int): Boolean {
    val setsSingle = ins.opcode.setsRegister()
    val setsWide = ins.opcode.setsWideRegister()
    if (!setsSingle && !setsWide) return false
    val dest = when (ins) {
        is OneRegisterInstruction -> ins.registerA
        is TwoRegisterInstruction -> ins.registerA
        else -> null
    } ?: return false
    return dest == reg || (setsWide && dest + 1 == reg)
}

private fun isBranchOrReturn(ins: Instruction): Boolean =
    !ins.opcode.canContinue() || ins is OffsetInstruction

private fun findNullCheckIndexInWindow(
    instructions: List<Instruction>,
    startIndex: Int,
    reg: Int,
): Int? {
    val windowEnd = minOf(instructions.size - 1, startIndex + MAX_NULL_CHECK_WINDOW)
    for (idx in (startIndex + 1)..windowEnd) {
        val ins = instructions[idx]
        if (isGetClassOnRegister(ins, reg)) {
            val nextIns = instructions.getOrNull(idx + 1)
            if (nextIns != null && isMoveResult(nextIns.opcode)) {
                return null
            }
            return idx
        }
        if (writesRegister(ins, reg) || isBranchOrReturn(ins)) {
            return null
        }
    }
    return null
}

private fun findPerfNullCheckIndices(method: Method): List<Int> {
    val instructions = method.implementation?.instructions?.toList() ?: return emptyList()
    val matches = mutableListOf<Int>()
    for (i in instructions.indices) {
        val ins = instructions[i]
        if (!isFirebasePerfGetInstance(ins)) continue
        val nextIns = instructions.getOrNull(i + 1) ?: continue
        if (nextIns.opcode != Opcode.MOVE_RESULT_OBJECT) continue
        val reg = (nextIns as? OneRegisterInstruction)?.registerA ?: continue
        val nullCheckIdx = findNullCheckIndexInWindow(instructions, i + 1, reg) ?: continue
        matches.add(nullCheckIdx)
    }
    return matches
}

private fun patchNullChecksInMethod(method: MutableMethod, indices: List<Int>): Int {
    val distinctIndices = indices.distinct().sortedDescending()
    for (index in distinctIndices) {
        method.replaceInstruction(index, "nop")
    }
    return distinctIndices.size
}

/**
 * Removes bare Firebase Performance getInstance() null checks in application code.
 *
 * Purging FirebasePerfRegistrar in twitterTelemetryResourcePatch causes FirebasePerformance.getInstance()
 * to return null. R8 inlines Kotlin's
 * newTrace() calls, leaving an orphaned getClass() call on the null instance whose result is unused.
 * This throws NullPointerException when entering post detail or media playback.
 *
 * This function scans for calls to Firebase Performance getInstance() whose move-result register
 * is solely checked via Ljava/lang/Object;->getClass()Ljava/lang/Class;, replacing the getClass instruction
 * with nop to neutralize the crash.
 */
private fun BytecodePatchContext.removePerfGetInstanceNullChecks(): Int {
    var removedCount = 0

    classDefForEach { classDef ->
        if (classDef.type.startsWith("Lcom/google/firebase/")) return@classDefForEach

        val hasMatch = classDef.methods.any { findPerfNullCheckIndices(it).isNotEmpty() }
        if (!hasMatch) return@classDefForEach

        val mutableClass = mutableClassDefBy(classDef)
        for (method in mutableClass.methods) {
            val matches = findPerfNullCheckIndices(method)
            if (matches.isNotEmpty()) {
                removedCount += patchNullChecksInMethod(method, matches)
            }
        }
    }

    if (removedCount == 0) {
        error("[X Telemetry] No Firebase Performance getInstance() null-check call sites found.")
    }

    return removedCount
}

private val brazeInitFingerprint = Fingerprint(
    strings = listOf("Braze SDK Initializing"),
)

private val brazeConfigFingerprint = Fingerprint(
    strings = listOf("Configuring Braze SDK"),
)

private val brazeApiKeyFingerprint = Fingerprint(
    strings = listOf("subscriptions_braze_api_key"),
)

private val brazeEndpointFingerprint = Fingerprint(
    strings = listOf("subscriptions_braze_endpoint"),
)

private val sentryDsnFingerprint = Fingerprint(
    strings = listOf("io.sentry.dsn"),
)

private val sentryEndpointFingerprint = Fingerprint(
    strings = listOf("o794212.ingest.us.sentry.io"),
)

private fun evaluateSafeVoidDispatcher(method: Method): Pair<Boolean, String> {
    if (method.name == "<init>" || method.name == "<clinit>") {
        return false to "constructor (${method.name})"
    }
    if (method.returnType != "V") {
        return false to "non-void return type '${method.returnType}'"
    }
    val callbackParams = method.parameterTypes.map { it.toString() }.filter { param ->
        param.contains("Callback", ignoreCase = true) ||
            param.contains("Listener", ignoreCase = true) ||
            param.contains("Continuation", ignoreCase = true) ||
            param.contains("Observer", ignoreCase = true) ||
            param.contains("Handler", ignoreCase = true)
    }
    if (callbackParams.isNotEmpty()) {
        return false to "has callback/listener/continuation parameters (${callbackParams.joinToString()})"
    }
    val lifecycleNames = setOf(
        "onCreate", "onStart", "onResume", "onPause", "onStop", "onDestroy",
        "onReceive", "onBind", "onStartCommand", "attachBaseContext",
    )
    if (method.name in lifecycleNames) {
        return false to "lifecycle method '${method.name}'"
    }
    val definingClass = method.definingClass
    if (definingClass.startsWith("Lcom/sardine/") ||
        definingClass.startsWith("Lcom/plaid/") ||
        definingClass.startsWith("Lcom/persona/")
    ) {
        return false to "fraud/risk or auth SDK class '$definingClass'"
    }
    return true to "safe"
}

@Suppress("unused")
val twitterBlockTelemetryPatch = bytecodePatch(
    name = "Block Telemetry & Trackers",
    description = "Neutralizes Google AppMeasurement, Firebase Performance, Braze marketing SDK, and strips advertising identifiers.",
    default = true,
) {
    compatibleWith(Constants.COMPATIBILITY_TWITTER)
    dependsOn(twitterTelemetryResourcePatch)

    execute {
        val hookedMethods = mutableListOf<String>()
        val appliedHooks = mutableListOf<String>()
        val skippedHooks = mutableListOf<String>()

        // Google AppMeasurement
        hookVoidMethod(
            definingClass = "Lcom/google/android/gms/measurement/AppMeasurement;",
            name = "logEventInternal",
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;", "Landroid/os/Bundle;"),
            hookedMethods = hookedMethods,
            label = "AppMeasurement.logEventInternal",
        )
        appliedHooks.add("AppMeasurement.logEventInternal")

        // Firebase Performance
        val traceClass = "Lcom/google/firebase/perf/metrics/Trace;"
        hookVoidMethod(
            definingClass = traceClass,
            name = "start",
            parameters = emptyList(),
            hookedMethods = hookedMethods,
            label = "Trace.start",
        )
        appliedHooks.add("Trace.start")
        hookVoidMethod(
            definingClass = traceClass,
            name = "stop",
            parameters = emptyList(),
            hookedMethods = hookedMethods,
            label = "Trace.stop",
        )
        appliedHooks.add("Trace.stop")
        hookVoidMethod(
            definingClass = traceClass,
            name = "putAttribute",
            parameters = listOf("Ljava/lang/String;", "Ljava/lang/String;"),
            hookedMethods = hookedMethods,
            label = "Trace.putAttribute",
        )
        appliedHooks.add("Trace.putAttribute")
        hookVoidMethod(
            definingClass = traceClass,
            name = "removeAttribute",
            parameters = listOf("Ljava/lang/String;"),
            hookedMethods = hookedMethods,
            label = "Trace.removeAttribute",
        )
        appliedHooks.add("Trace.removeAttribute")
        hookVoidMethod(
            definingClass = traceClass,
            name = "putMetric",
            parameters = listOf("Ljava/lang/String;", "J"),
            hookedMethods = hookedMethods,
            label = "Trace.putMetric",
        )
        appliedHooks.add("Trace.putMetric")
        hookVoidMethod(
            definingClass = traceClass,
            name = "incrementMetric",
            parameters = listOf("Ljava/lang/String;", "J"),
            hookedMethods = hookedMethods,
            label = "Trace.incrementMetric",
        )
        appliedHooks.add("Trace.incrementMetric")

        // Braze SDK String Anchors (best-effort: applied only when a safe void dispatcher resolves)
        val brazeRequiredAnchors = listOf(
            "Braze SDK Initializing" to brazeInitFingerprint,
            "Configuring Braze SDK" to brazeConfigFingerprint,
        )
        val brazeBestEffortAnchors = listOf(
            "subscriptions_braze_api_key" to brazeApiKeyFingerprint,
            "subscriptions_braze_endpoint" to brazeEndpointFingerprint,
        )

        for ((label, fp) in brazeRequiredAnchors) {
            val sdkMatches = (fp.matchAllOrNull() ?: emptyList()).filter { it.method.definingClass.startsWith("Lcom/braze/") }
            val method = sdkMatches.map { it.method }.firstOrNull { evaluateSafeVoidDispatcher(it).first }
            if (method == null) {
                skippedHooks.add("Braze ($label: no safe void dispatcher - best-effort)")
                println("[X Telemetry] Skipped Braze anchor '$label': no safe void dispatcher (best-effort).")
                continue
            }
            method.addInstructions(0, "return-void")
            val hookLabel = "Braze ($label -> ${method.definingClass}->${method.name})"
            hookedMethods.add(hookLabel)
            appliedHooks.add(hookLabel)
        }

        for ((label, fp) in brazeBestEffortAnchors) {
            val method = (fp.matchAllOrNull() ?: emptyList()).map { it.method }.firstOrNull { evaluateSafeVoidDispatcher(it).first }
            if (method == null) {
                skippedHooks.add("Braze ($label: no safe void dispatcher - best-effort)")
                println("[X Telemetry] Skipped Braze anchor '$label': no safe void dispatcher (best-effort).")
                continue
            }
            method.addInstructions(0, "return-void")
            val hookLabel = "Braze ($label -> ${method.definingClass}->${method.name})"
            hookedMethods.add(hookLabel)
            appliedHooks.add(hookLabel)
        }

        // Sentry Telemetry Reconnaissance & Consumers (report-only: no first-party SDK, DSN may be manifest-only)
        val sentryAnchors = listOf(
            "io.sentry.dsn" to sentryDsnFingerprint,
            "o794212.ingest.us.sentry.io" to sentryEndpointFingerprint,
        )

        for ((label, fp) in sentryAnchors) {
            val method = (fp.matchAllOrNull() ?: emptyList()).map { it.method }.firstOrNull { evaluateSafeVoidDispatcher(it).first }
            if (method == null) {
                skippedHooks.add("Sentry ($label: no safe void consumer - report-only)")
                println("[X Telemetry] Sentry anchor '$label': no safe void consumer found (report-only).")
                continue
            }
            method.addInstructions(0, "return-void")
            val hookLabel = "Sentry ($label -> ${method.definingClass}->${method.name})"
            hookedMethods.add(hookLabel)
            appliedHooks.add(hookLabel)
        }

        val removedNullChecks = removePerfGetInstanceNullChecks()

        println("[X Telemetry] Neutralized ${hookedMethods.size} telemetry dispatch methods: ${hookedMethods.joinToString(", ")}.")
        println("[X Telemetry] Telemetry hooks summary -> applied: ${appliedHooks.size} (${appliedHooks.joinToString(", ")}), skipped: ${skippedHooks.size} (${skippedHooks.joinToString("; ")}).")
        println("[X Telemetry] Removed $removedNullChecks Firebase Performance getInstance() null checks (registrar purged).")
    }
}
