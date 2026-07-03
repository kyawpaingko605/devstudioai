package devstudioai.lang.jdt

import devstudioai.lang.BackendCapability
import devstudioai.lang.CompilationContext
import devstudioai.lang.LanguageBackend
import devstudioai.lang.LanguageId
import devstudioai.lang.SourceAnalyzer

/**
 * The default Java [LanguageBackend], engine = Eclipse JDT. Error-tolerant (statement + binding
 * recovery), so editor features work on broken code; bindings provide the access/modifier info that
 * powers smart completion. Implements the backend-neutral language-api SPI.
 */
class JdtLanguageBackend : LanguageBackend {
    override val id: String = "jdt"
    override val languages: Set<LanguageId> = setOf(LanguageId("java"))
    override val capabilities: Set<BackendCapability> = setOf(
        BackendCapability.ERROR_RECOVERY,
        BackendCapability.BINDINGS,
        BackendCapability.COMPLETION,
        BackendCapability.SNIPPETS,
        BackendCapability.POSTFIX,
        BackendCapability.INLAY_HINTS,
        BackendCapability.SIGNATURE_HELP,
        BackendCapability.SEMANTIC_HIGHLIGHT,
        BackendCapability.CODE_FOLDING,
        BackendCapability.FORMAT,
    )

    override fun createAnalyzer(ctx: CompilationContext): SourceAnalyzer = JdtSourceAnalyzer(ctx)
}
