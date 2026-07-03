package devstudioai.ui

import devstudioai.ui.backend.ActionService
import devstudioai.ui.backend.BlockService
import devstudioai.ui.backend.BuildService
import devstudioai.ui.backend.BuildState
import devstudioai.ui.backend.DependencyService
import devstudioai.ui.backend.DiagnosticsService
import devstudioai.ui.backend.EditorService
import devstudioai.ui.backend.FileService
import devstudioai.ui.backend.IdeBackend
import devstudioai.ui.backend.IndexUiStatus
import devstudioai.ui.backend.SigningService
import devstudioai.ui.backend.ModuleService
import devstudioai.ui.backend.NodeKind
import devstudioai.ui.backend.PreviewService
import devstudioai.ui.backend.ProjectInfo
import devstudioai.ui.backend.ProjectService
import devstudioai.ui.backend.SdkService
import devstudioai.ui.backend.SearchService
import devstudioai.ui.backend.SettingsService
import devstudioai.ui.backend.SymbolHit
import devstudioai.ui.backend.TreeNode
import devstudioai.ui.backend.TreeViewMode
import devstudioai.ui.backend.UiCompletionResult
import devstudioai.ui.backend.UiDiagnostic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A no-op [IdeBackend] for tests: it implements every concern service (via `get() = this`) and stubs the
 * abstract members, so a test fake only overrides what it exercises. All members are `open`.
 */
internal open class StubBackend : IdeBackend,
    FileService, EditorService, BlockService, PreviewService, SearchService, BuildService,
    DependencyService, ModuleService, SigningService, ProjectService, SdkService, SettingsService, ActionService,
    DiagnosticsService {

    override val files: FileService get() = this
    override val editor: EditorService get() = this
    override val blocks: BlockService get() = this
    override val preview: PreviewService get() = this
    override val search: SearchService get() = this
    override val build: BuildService get() = this
    override val deps: DependencyService get() = this
    override val modules: ModuleService get() = this
    override val signing: SigningService get() = this
    override val projects: ProjectService get() = this
    override val sdk: SdkService get() = this
    override val settings: SettingsService get() = this
    override val actions: ActionService get() = this
    override val diagnostics: DiagnosticsService get() = this

    override val project: ProjectInfo = ProjectInfo("stub", "/stub", 0)

    // FileService (abstract)
    override fun fileTree(mode: TreeViewMode): TreeNode = TreeNode("root", "stub", NodeKind.Workspace, null)
    override fun readFile(path: String): String = ""
    override fun moduleNameForFile(path: String): String? = null

    // EditorService (abstract)
    override fun updateDocument(path: String, text: String) {}
    override fun saveFile(path: String, text: String) {}
    override suspend fun complete(path: String, text: String, offset: Int): UiCompletionResult =
        UiCompletionResult(emptyList(), offset, offset)
    override suspend fun analyze(path: String, text: String): List<UiDiagnostic> = emptyList()

    // SearchService (abstract)
    override val indexStatus: StateFlow<IndexUiStatus> = MutableStateFlow(IndexUiStatus())
    override suspend fun searchSymbols(query: String, limit: Int): List<SymbolHit> = emptyList()
    override suspend fun searchMembers(query: String, limit: Int): List<SymbolHit> = emptyList()

    // BuildService (abstract)
    override val buildState: StateFlow<BuildState> = MutableStateFlow(BuildState())
    override fun runBuild() {}
    override fun stopBuild() {}
}
