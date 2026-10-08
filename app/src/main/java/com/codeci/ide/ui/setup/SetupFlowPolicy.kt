package com.codeci.ide.ui.setup

import com.codeci.ide.ui.models.Template
import com.codeci.ide.ui.models.TemplateProvider
import com.codeci.ide.ui.projects.GameArenaSample
import com.codeci.ide.ui.projects.ProjectNameCheck
import com.codeci.ide.ui.projects.ProjectNameVerdict
import com.codeci.ide.ui.projects.ProjectPathUtils

/**
 * Phase 97 — the post-agreement setup flow's whole decision surface, as pure
 * code (rule.md §4.4; Android-free, host-tested by `SetupFlowPolicyTest`).
 *
 * The owner asked for the Pydroid-shaped flow (2026-10-08): seven beats after
 * the privacy agreement, and **the user picks and names their first project**
 * instead of receiving the CodeC Arcade sample. The research this spends is
 * `docs/research/ONBOARDING_PERSONALISATION_DESIGN_20261008.md`; the laws it
 * must not break are listed there and pinned by tests here:
 *
 * 1. **Skip ≡ today's behaviour.** [skipChoice] is the legacy launch: the
 *    `codec-arcade` sample, exactly as every build before Phase 97 seeded it.
 *    A user who skips gets the app they would have had.
 * 2. **Every answer changes something visible.** [planFor] is the promise the
 *    flow renders and the seeder executes — there is no question here whose
 *    answer is not a project, a file, or a stored setting.
 * 3. **Only the truth is shown.** [planFor] lists the files the seeder really
 *    writes, in the order it writes them, and [SetupPlan.needsDownload] is true
 *    for exactly one start.
 * 4. **No profile is kept.** The choice is a project id and a project name;
 *    nothing here reads or writes anything about the person.
 *
 * Everything Android-touching lives in [SetupSeeding]; nothing in this file
 * imports `android.*`.
 */

/** The seven beats, in order. The progress strip counts the first six. */
enum class SetupStep {
    WELCOME,
    PICK,
    NAME,
    LOOKS,
    HELPS,
    BUILDING,
    READY,
}

/**
 * What the user wants to make. [downloadMb] is 0 for everything that works
 * offline — the one honest cost line the flow shows per option.
 */
enum class SetupStart(
    val id: String,
    val defaultProjectName: String,
    val projectType: String,
    val entryFile: String,
    val downloadMb: Int,
    /** Only C has shipped variants today (the five templates); never faked. */
    val hasVariants: Boolean,
) {
    ARCADE("arcade", "MyFirstGame", "web", GameArenaSample.ENTRY_FILE, 0, false),
    C("c", "MyCProgram", "c", "main.c", 0, true),
    PYTHON("python", "MyPythonScript", "python", "main.py", 40, false),
    WEB("web", "MyWebPage", "web", "index.html", 0, false),
}

/** One shipped variant of a start — a C template, today. */
data class SetupVariant(
    val id: String,
    val label: String,
    /** The template's own `concepts`, joined — the plain words, never invented. */
    val detail: String,
    val difficulty: Int,
)

/** The three stored answers: what, which variant, and what it will be called. */
data class SetupChoice(
    val start: SetupStart = SetupStart.ARCADE,
    val variantId: String? = null,
    val projectName: String = SetupStart.ARCADE.defaultProjectName,
)

/** Text size, as three steps (S/M/L) that write the real font-size key. */
enum class SetupTextSize(val label: String, val fontSp: Float) {
    S("S", 14f),
    M("M", 16f),
    L("L", 20f),
}

/**
 * The theme answer. `AUTO` is the app's `SYSTEM`; the labels are the flow's own
 * words, the ids are the stored values ([com.codeci.ide.ui.theme.AppThemeMode]).
 */
enum class SetupTheme(val id: String, val label: String) {
    DARK("dark", "Dark"),
    LIGHT("light", "Light"),
    AUTO("auto", "Auto"),
}

/**
 * Everything the flow wrote by the time the user taps *Start coding*. The
 * settings halves are the app's own keys — this data class is the flow's
 * answer, not a second store.
 */
data class SetupPicks(
    val textSize: SetupTextSize = SetupTextSize.M,
    val theme: SetupTheme = SetupTheme.DARK,
    val plainWords: Boolean = true,
    val hints: Boolean = false,
    val lineNumbers: Boolean = true,
    val wordWrap: Boolean = false,
)

/**
 * What the seeder will really do, rendered on [SetupStep.BUILDING] and then
 * executed by [SetupSeeding]. [files] is the write order.
 */
data class SetupPlan(
    val projectName: String,
    val projectType: String,
    val entryFile: String,
    val files: List<String>,
    val needsDownload: Boolean,
    val downloadMb: Int,
    val templateId: String?,
)

object SetupFlowPolicy {

    /** The seven beats, in the order the flow shows them. */
    val steps: List<SetupStep> = SetupStep.values().toList()

    /** S1-S6 are counted by the hairline strip; [SetupStep.READY] is the payoff. */
    const val PROGRESS_SEGMENTS = 6

    /**
     * The legacy sample's project name — the string [GameArenaSample.NAME] has
     * always been. Duplicated here so the skip law is assertable from pure code;
     * `SetupFlowPolicyTest` pins the two together through [GameArenaSample].
     */
    val LEGACY_SAMPLE_PROJECT_NAME: String = GameArenaSample.NAME

    /** S1's Continue with nothing touched: Arcade, ready to run offline. */
    fun defaultChoice(): SetupChoice = SetupChoice(
        start = SetupStart.ARCADE,
        variantId = null,
        projectName = SetupStart.ARCADE.defaultProjectName,
    )

    /**
     * Law 1 — *Skip setup* is today's app. The sample's own name and entry
     * file, no variant, no settings written, no project renamed.
     */
    fun skipChoice(): SetupChoice = SetupChoice(
        start = SetupStart.ARCADE,
        variantId = null,
        projectName = LEGACY_SAMPLE_PROJECT_NAME,
    )

    /** True when [choice] is the untouched skip: the legacy seeding path. */
    fun isLegacySkip(choice: SetupChoice): Boolean =
        choice.start == SetupStart.ARCADE &&
            choice.variantId == null &&
            choice.projectName == LEGACY_SAMPLE_PROJECT_NAME

    /** The variants a start really has (C: the five shipped templates). */
    fun variantsFor(start: SetupStart): List<SetupVariant> =
        if (!start.hasVariants) {
            emptyList()
        } else {
            TemplateProvider.templates.map { template ->
                SetupVariant(
                    id = template.id,
                    label = template.name,
                    detail = template.concepts.joinToString(" · "),
                    difficulty = template.difficulty,
                )
            }
        }

    /**
     * The variant the choice will really use: the picked one, else the start's
     * first (C defaults to Hello World, the level-1 template).
     */
    fun effectiveVariantId(choice: SetupChoice): String? {
        if (!choice.start.hasVariants) return null
        val variants = variantsFor(choice.start)
        if (variants.isEmpty()) return null
        return choice.variantId?.takeIf { id -> variants.any { it.id == id } } ?: variants.first().id
    }

    /** The template behind [choice], or null when the start has no templates. */
    fun templateFor(choice: SetupChoice): Template? =
        effectiveVariantId(choice)?.let { id -> TemplateProvider.templates.firstOrNull { it.id == id } }

    /**
     * The exact bytes written into `main.c` for a C template: the template's own
     * code, preceded by one comment line built from its own `concepts`. No
     * rewriting of the code — the library's source stays the source.
     */
    fun templateSource(template: Template): String = buildString {
        append("/* ").append(template.name)
        if (template.concepts.isNotEmpty()) {
            append(" — ").append(template.concepts.joinToString(" · "))
        }
        append(" */\n\n")
        append(template.code.trimEnd()).append('\n')
    }

    /** The name the seeder will use, or null when [choice]'s name is unusable. */
    fun safeProjectName(choice: SetupChoice): String? =
        if (isLegacySkip(choice)) LEGACY_SAMPLE_PROJECT_NAME
        else ProjectPathUtils.sanitizeProjectName(choice.projectName)

    /** The live field verdict — the app's own rules, never a second copy. */
    fun nameVerdict(choice: SetupChoice, existingNames: Collection<String>): ProjectNameVerdict =
        if (isLegacySkip(choice)) {
            ProjectNameCheck.check(LEGACY_SAMPLE_PROJECT_NAME, emptyList())
        } else {
            ProjectNameCheck.check(choice.projectName, existingNames)
        }

    /** True when *Continue* may be pressed on the name beat. */
    fun mayContinue(choice: SetupChoice, existingNames: Collection<String>): Boolean =
        isLegacySkip(choice) || nameVerdict(choice, existingNames).ok

    /**
     * Law 3 — the plan the flow shows and the seeder executes. [files] is the
     * real write order: the project config first, then the scaffold/asset files.
     */
    fun planFor(choice: SetupChoice): SetupPlan {
        val name = safeProjectName(choice) ?: choice.start.defaultProjectName
        if (isLegacySkip(choice)) {
            return SetupPlan(
                projectName = LEGACY_SAMPLE_PROJECT_NAME,
                projectType = GameArenaSample.TYPE,
                entryFile = GameArenaSample.ENTRY_FILE,
                files = listOf(".codec/project.json") + GameArenaSample.PROJECT_FILES,
                needsDownload = false,
                downloadMb = 0,
                templateId = null,
            )
        }
        val template = templateFor(choice)
        val files = when {
            choice.start == SetupStart.ARCADE ->
                listOf(".codec/project.json") + GameArenaSample.PROJECT_FILES
            template != null -> listOf(".codec/project.json", choice.start.entryFile)
            else -> listOf(".codec/project.json", choice.start.entryFile)
        }
        return SetupPlan(
            projectName = name,
            projectType = choice.start.projectType,
            entryFile = choice.start.entryFile,
            files = files,
            needsDownload = choice.start.downloadMb > 0,
            downloadMb = choice.start.downloadMb,
            templateId = template?.id,
        )
    }

    /** The strip state for [step]: 0..5 for S1-S6, [PROGRESS_SEGMENTS] for S7. */
    fun progressIndex(step: SetupStep): Int = when (step) {
        SetupStep.WELCOME -> 0
        SetupStep.PICK -> 1
        SetupStep.NAME -> 2
        SetupStep.LOOKS -> 3
        SetupStep.HELPS -> 4
        SetupStep.BUILDING -> 5
        SetupStep.READY -> PROGRESS_SEGMENTS
    }

    /** The step after [step], or null on the last one. */
    fun next(step: SetupStep): SetupStep? =
        steps.getOrNull(steps.indexOf(step) + 1)

    /** The step before [step], or null on the first one (S1 shows no back). */
    fun previous(step: SetupStep): SetupStep? =
        steps.getOrNull(steps.indexOf(step) - 1)

    /** The builder-completion line the receipt shows, in the user's own name. */
    fun receiptProjectLine(plan: SetupPlan): String = "${plan.entryFile} · opens in the editor"
}
