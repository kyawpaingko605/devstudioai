package devstudioai.core

import devstudioai.model.LanguageLevel
import devstudioai.model.ModuleType
import devstudioai.model.Workspace
import devstudioai.model.impl.ProjectModelStore
import devstudioai.model.template.ProjectScaffold
import devstudioai.vfs.VirtualFile
import java.nio.file.Files
import kotlin.io.path.writeText

/**
 * The [ProjectScaffold] a [devstudioai.model.template.ProjectTemplate] builds against, backed by a
 * [ProjectModelStore]. Exposes the store's workspace transaction surface and a `java.nio` file writer
 * rooted at the workspace dir — the same write path `SampleProject`/`SampleAndroidProject` use.
 */
internal class ScaffoldImpl(
    private val store: ProjectModelStore,
    override val languageLevel: LanguageLevel,
) : ProjectScaffold {
    override val workspace: Workspace get() = store.workspace
    override val rootDir: VirtualFile get() = store.vfs.root()

    override fun moduleType(id: String): ModuleType = store.moduleTypes.resolve(id)

    override fun writeText(relPath: String, content: String) {
        val file = store.rootPath.resolve(relPath)
        Files.createDirectories(file.parent)
        // trimIndent() drops the leading/trailing blank lines of a triple-quoted literal + common indent.
        file.writeText(content.trimIndent() + "\n")
    }
}
