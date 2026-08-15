/**
 * Static guardrails for the mobile UI layer.
 *
 * Registered on the ROOT project on purpose. `:mobile:composeApp` applies the Android application
 * plugin, which needs an Android SDK just to *configure* — so a check that lived there could not run
 * in an SDK-less CI container, which is exactly where a cheap text check is most valuable.
 *
 * These are ratchets, not absolutes. Every rule below has a real violation count today; failing the
 * build on the first run would just mean the checks get deleted. Instead each rule carries a
 * baseline in `ui-guardrails-baseline.properties`, and the build fails when a count goes UP. As the
 * design-system migration lands, the baselines come down, and a rule that reaches 0 stays at 0.
 *
 * Run: ./gradlew verifyUi
 * Update a baseline after genuinely reducing a count: ./gradlew verifyUi -PupdateUiBaseline
 */

val mobileSrc = rootProject.file("mobile/composeApp/src")
val uiSrc = File(mobileSrc, "commonMain/kotlin/com/muhabbet/app/ui")
val designSystemSrc = rootProject.file("mobile/designsystem/src")
val resourcesDir = File(mobileSrc, "commonMain/composeResources")
val baselineFile = rootProject.file("gradle/ui-guardrails-baseline.properties")

/**
 * Both roots are scanned. Scanning only composeApp would let the design-system module escape every
 * rule the moment code moved into it — the counts would fall, the ratchet would look like it was
 * improving, and the new module would be unpoliced.
 */
val scanRoots = listOf(uiSrc, designSystemSrc)

/** A rule counts lines matching [pattern] across [scanRoots], minus anything [exempt] allows. */
data class UiRule(
    val id: String,
    val why: String,
    val pattern: Regex,
    val exempt: (File) -> Boolean = { false }
)

private fun File.unixPath(): String = path.replace('\\', '/')

/**
 * The token definitions themselves — the one place a raw colour, `sp` or `dp` is the point.
 *
 * Deliberately narrower than "anywhere in the design system": shared components must consume tokens
 * like everyone else. `SectionHeader` hardcoding 16.dp/12.dp/8.dp is exactly the kind of thing this
 * rule exists to surface, and exempting the whole module would have hidden it.
 */
val inTokenDefinitions: (File) -> Boolean = { it.unixPath().contains("/designsystem/") && it.unixPath().contains("/theme/") }

/**
 * The whole design-system module — for rules about wrapping raw Material primitives. Components
 * exist precisely to call `Scaffold`, `TopAppBar` and `CircularProgressIndicator` so screens do not.
 */
val inDesignSystem: (File) -> Boolean = { it.unixPath().contains("/designsystem/") }

/**
 * The library's own text utilities, where the *correct* Turkish casing is implemented. Its
 * `else -> uppercase()` fallback is the one legitimate call in the codebase.
 */
val inTextUtils: (File) -> Boolean = { it.unixPath().contains("/designsystem/") && it.unixPath().contains("/util/") }

val uiRules = listOf(
    UiRule(
        "hardcodedColor",
        "Colour literals belong in the theme, so a palette change is one file.",
        Regex("""Color\(0x"""), inTokenDefinitions
    ),
    UiRule(
        "topAppBarColors",
        "23 copies of the same colour block are why top bars come in three different colours. Use MuhabbetTopBar.",
        Regex("""topAppBarColors\("""), inDesignSystem
    ),
    UiRule(
        "rawTopAppBar",
        "Hand-rolled TopAppBar. Use MuhabbetTopBar; ChatScreen and HomeShellScreen are the two deliberate exceptions.",
        Regex("""[^a-zA-Z]TopAppBar\("""), inDesignSystem
    ),
    UiRule(
        "rawScaffold",
        "Hand-rolled Scaffold. Use MuhabbetScaffold, which also carries the one correct WindowInsets policy.",
        Regex("""[^a-zA-Z]Scaffold\("""), inDesignSystem
    ),
    UiRule(
        "bareProgress",
        "A centred spinner is not a loading state. Use MuhabbetScreenState / MuhabbetSkeleton.",
        Regex("""CircularProgressIndicator\("""), inDesignSystem
    ),
    UiRule(
        "directIcons",
        "Icons are referenced by semantic name via MuhabbetIcons, so the icon set can change in one file.",
        Regex("""(?<!import )\bIcons\."""), inDesignSystem
    ),
    UiRule(
        "spLiteral",
        "Type sizes live in MuhabbetTypography / MuhabbetTextStyles.",
        Regex("""\b\d+(\.\d+)?\.sp\b"""), inTokenDefinitions
    ),
    UiRule(
        "dpLiteral",
        "Dimensions live in MuhabbetSpacing / MuhabbetSizes / MuhabbetCorners.",
        Regex("""\b\d+(\.\d+)?\.dp\b"""), inTokenDefinitions
    ),
    UiRule(
        // Already at 0 — the view-once thumbnail blur was removed because Modifier.blur is a no-op
        // below API 31 while minSdk is 26, so it protected nothing on Android 8.0-11.
        "modifierBlur",
        "Blur may degrade decoratively, never protectively. Modifier.blur does nothing below API 31 and minSdk is 26.",
        Regex("""\.blur\(""")
    ),
    UiRule(
        // Kotlin's no-arg lowercase()/uppercase() are locale-INDEPENDENT (root locale), which is the
        // problem rather than the fix: "ismail".uppercase() is "ISMAIL", not "İSMAİL", and
        // "İsmail".lowercase() is "i̇smail" (i + U+0307), which no user will ever type as a query.
        // Both live consequences are fixed: name search now folds through foldForSearch, and the
        // call screen's avatar initial goes through UserAvatar/firstGrapheme.
        //
        // The 2 remaining hits are correct and are expected to stay:
        //   PhoneInputScreen      — deliberately locale-invariant scan of an English Firebase
        //                           message, documented at the call site.
        //   WallpaperPickerScreen — upper-casing hex digits, which are ASCII by construction.
        // A rise above 2 means a real one crept back in.
        "unlocaledCase",
        "Turkish needs i/İ and ı/I handled explicitly. Use foldForSearch (search) or firstGrapheme (display).",
        Regex("""\.(uppercase|lowercase)\(\)"""), inTextUtils
    )
)

fun File.kotlinFiles(): List<File> =
    if (exists()) walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() else emptyList()

/**
 * The file's lines with comment text blanked out, so the rules measure code and not prose.
 *
 * This is not a nicety. Every rule below is a ratchet, and a ratchet that counts documentation
 * punishes explaining yourself: a docblock saying *"replaces a flat `Surface(80.dp)`"* was scored as
 * a new `dp` literal and failed the build. The incentive that creates — delete the sentence, or
 * describe the old code less precisely — is the exact opposite of what these checks exist for, and
 * the whole convention in this repo is that a rule states its reasoning in a comment.
 *
 * Deliberately crude: it strips line comments to end-of-line, tracks block-comment open and close
 * markers, and does not understand strings that contain those markers. A false *negative* here costs
 * one uncounted violation; getting cute with a real lexer costs more than that is worth.
 */
fun File.analysableLines(): List<String> {
    var inBlock = false
    return readLines().map { line ->
        val out = StringBuilder()
        var i = 0
        while (i < line.length) {
            if (inBlock) {
                if (line.startsWith("*/", i)) { inBlock = false; i += 2 } else i++
            } else if (line.startsWith("/*", i)) {
                inBlock = true; i += 2
            } else if (line.startsWith("//", i)) {
                break
            } else {
                out.append(line[i]); i++
            }
        }
        out.toString()
    }
}

fun loadBaseline(): Map<String, Int> {
    if (!baselineFile.exists()) return emptyMap()
    return baselineFile.readLines()
        .filter { it.isNotBlank() && !it.trimStart().startsWith("#") }
        .mapNotNull { line ->
            val (k, v) = line.split("=", limit = 2).takeIf { it.size == 2 } ?: return@mapNotNull null
            k.trim() to (v.trim().toIntOrNull() ?: return@mapNotNull null)
        }.toMap()
}

val verifyDesignSystem by tasks.registering {
    group = "verification"
    description = "Ratchets design-system violations in the mobile UI layer; fails when a count rises."
    // Declared conditionally so a checkout without the mobile sources still configures.
    scanRoots.filter { it.exists() }.forEach { inputs.dir(it) }
    if (baselineFile.exists()) inputs.file(baselineFile)
    doLast {
        val baseline = loadBaseline()
        val counts = LinkedHashMap<String, Int>()
        val regressions = mutableListOf<String>()

        uiRules.forEach { rule ->
            val hits = scanRoots.flatMap { it.kotlinFiles() }
                .filterNot(rule.exempt)
                .sumOf { file -> file.analysableLines().count { rule.pattern.containsMatchIn(it) } }
            counts[rule.id] = hits
            val allowed = baseline[rule.id]
            if (allowed == null) {
                logger.lifecycle("ui-guardrails: new rule '${rule.id}' at $hits — recording as baseline")
            } else if (hits > allowed) {
                regressions += "  ${rule.id}: $hits (baseline $allowed, +${hits - allowed})\n      ${rule.why}"
            } else if (hits < allowed) {
                logger.lifecycle("ui-guardrails: ${rule.id} improved $allowed -> $hits (lower the baseline)")
            }
        }

        if (project.hasProperty("updateUiBaseline") || !baselineFile.exists()) {
            baselineFile.writeText(
                buildString {
                    appendLine("# Generated by ./gradlew verifyUi -PupdateUiBaseline")
                    appendLine("# Counts may only go DOWN. See gradle/ui-guardrails.gradle.kts.")
                    counts.forEach { (k, v) -> appendLine("$k=$v") }
                }
            )
            logger.lifecycle("ui-guardrails: baseline written to ${baselineFile.relativeTo(rootProject.projectDir)}")
            return@doLast
        }

        check(regressions.isEmpty()) {
            "Design-system guardrails regressed:\n" + regressions.joinToString("\n") +
                "\n\nFix the new violations, or if the increase is genuinely justified, " +
                "run ./gradlew verifyUi -PupdateUiBaseline and explain why in the commit."
        }
    }
}

val verifyStringResourceSync by tasks.registering {
    group = "verification"
    description = "Every string exists in both locales, and every Res.string.* reference exists in both."
    if (resourcesDir.exists()) inputs.dir(resourcesDir)
    if (File(mobileSrc, "commonMain/kotlin").exists()) inputs.dir(File(mobileSrc, "commonMain/kotlin"))
    doLast {
        val namePattern = Regex("""<string\s+name="([^"]+)"""")
        fun namesIn(locale: String): Set<String> {
            val f = File(resourcesDir, "$locale/strings.xml")
            check(f.exists()) { "Missing strings file: $f" }
            return namePattern.findAll(f.readText()).map { it.groupValues[1] }.toSet()
        }

        val tr = namesIn("values")
        val en = namesIn("values-en")
        val problems = mutableListOf<String>()

        (tr - en).sorted().forEach { problems += "  only in values/ (Turkish), missing English: $it" }
        (en - tr).sorted().forEach { problems += "  only in values-en/, missing Turkish: $it" }

        // Referenced-but-undeclared is the failure that actually reaches users: it compiles against
        // the generated Res class only if it exists in the DEFAULT locale, so a key present only in
        // values-en/ builds fine and then renders untranslated.
        val referenced = File(mobileSrc, "commonMain/kotlin").kotlinFiles()
            .flatMap { f -> Regex("""Res\.string\.([A-Za-z0-9_]+)""").findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
        (referenced - tr).sorted().forEach { problems += "  referenced in Kotlin but not declared in values/: $it" }
        (referenced - en).sorted().forEach { problems += "  referenced in Kotlin but not declared in values-en/: $it" }

        check(problems.isEmpty()) {
            "String resources are out of sync (${tr.size} tr / ${en.size} en):\n" + problems.joinToString("\n")
        }
        logger.lifecycle("ui-guardrails: ${tr.size} strings in sync across both locales")
    }
}

tasks.register("verifyUi") {
    group = "verification"
    description = "All static UI guardrails. Runs without an Android SDK."
    dependsOn(verifyDesignSystem, verifyStringResourceSync)
}
