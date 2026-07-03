package devstudioai.lang.kotlin.analysis

import devstudioai.analysis.ACTION_PROVIDER_EP
import devstudioai.analysis.ActionProvider
import devstudioai.analysis.AnalysisTarget
import devstudioai.analysis.AnalyzerId
import devstudioai.analysis.CodeActionKind
import devstudioai.analysis.DIAGNOSTIC_PROVIDER_EP
import devstudioai.analysis.Diagnostic
import devstudioai.analysis.DiagnosticProvider
import devstudioai.analysis.DiagnosticSource
import devstudioai.analysis.FixContext
import devstudioai.analysis.QUICK_FIX_PROVIDER_EP
import devstudioai.analysis.QuickFix
import devstudioai.analysis.QuickFixProvider
import devstudioai.analysis.WorkspaceEdit
import devstudioai.lang.dom.TextRange
import devstudioai.lang.kotlin.KotlinDiagnosticCodes
import devstudioai.lang.kotlin.KotlinLanguageBackend
import devstudioai.lang.kotlin.KotlinSourceAnalyzer
import devstudioai.platform.ExtensionRegistry
import devstudioai.platform.PluginId

/**
 * Contributes the Kotlin editor analysis surface onto the analysis-api extension points: a diagnostic
 * provider (the tolerant PSI parse + the resolver's semantic findings) and a code-action provider (the
 * "import unresolved reference" fixes). Both delegate to the per-module [KotlinSourceAnalyzer] reached via
 * `target.resolver`, so the analyzer's tuned incremental work is preserved — the engine's `targetFor`
 * already parsed the live buffer through that same cached instance. Declares `languages = {kotlin}` so it
 * never runs on Java/XML targets now that one pipeline serves every language.
 */
private val KOTLIN = KotlinLanguageBackend.LANGUAGE_ID

/** The Kotlin diagnostics, adapted from the resolver into the unified [Diagnostic] stream. */
class KotlinDiagnosticProvider(override val id: String = "kotlin") : DiagnosticProvider {
    override val languages = setOf(KOTLIN)

    override suspend fun diagnose(target: AnalysisTarget): List<Diagnostic> {
        val analyzer = target.resolver as? KotlinSourceAnalyzer ?: return emptyList()
        return analyzer.analyze(target.file).diagnostics.map { d ->
            // The tolerant parser tags syntax errors `kt.syntax`; everything else is a semantic finding.
            val sid = if (d.code == KotlinDiagnosticCodes.SYNTAX) "kotlin.syntax" else "kotlin.semantic"
            Diagnostic(d.range, d.severity, d.message, DiagnosticSource.Analyzer(AnalyzerId(sid)), d.code)
        }
    }
}

/** Caret intentions for Kotlin: offer an `import` for each unresolved reference at the caret. */
class KotlinImportActionProvider : ActionProvider {
    override val languages = setOf(KOTLIN)

    override fun actions(target: AnalysisTarget, range: TextRange): List<QuickFix> {
        val analyzer = target.resolver as? KotlinSourceAnalyzer ?: return emptyList()
        return analyzer.importFixesAt(target.file, range.start).map { fix ->
            object : QuickFix {
                override val title = fix.title
                override val kind = CodeActionKind.QUICK_FIX
                override suspend fun computeEdits(ctx: FixContext): WorkspaceEdit =
                    WorkspaceEdit.of(ctx.target.file, *fix.edits.toTypedArray())
            }
        }
    }
}

/** Code-keyed quick-fix: on `kt.abstractNotImplemented`, generate `override` stubs for the unimplemented
 *  inherited abstract members into the class body (the "Implement members" lightbulb). */
class KotlinImplementMembersFixProvider : QuickFixProvider {
    override val forCodes = setOf(KotlinDiagnosticCodes.ABSTRACT_NOT_IMPLEMENTED)
    override val languages = setOf(KOTLIN)

    override fun fixes(diagnostic: Diagnostic, target: AnalysisTarget): List<QuickFix> {
        val analyzer = target.resolver as? KotlinSourceAnalyzer ?: return emptyList()
        val fix = analyzer.implementMembersFix(target.file, diagnostic.range.start) ?: return emptyList()
        return listOf(object : QuickFix {
            override val title = fix.title
            override val kind = CodeActionKind.QUICK_FIX
            override suspend fun computeEdits(ctx: FixContext): WorkspaceEdit =
                WorkspaceEdit.of(ctx.target.file, *fix.edits.toTypedArray())
        })
    }
}

object KotlinAnalysisSupport {
    val PLUGIN = PluginId("kotlin-analysis")

    fun register(extensions: ExtensionRegistry, plugin: PluginId = PLUGIN) {
        extensions.register(DIAGNOSTIC_PROVIDER_EP, KotlinDiagnosticProvider(), plugin)
        extensions.register(ACTION_PROVIDER_EP, KotlinImportActionProvider(), plugin)
        extensions.register(QUICK_FIX_PROVIDER_EP, KotlinImplementMembersFixProvider(), plugin)
    }
}
