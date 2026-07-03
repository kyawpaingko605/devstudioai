package devstudioai.core.backend

import devstudioai.core.BackendContext
import devstudioai.core.BuildRunner
import devstudioai.core.IdeServices
import devstudioai.ui.backend.BuildService
import devstudioai.ui.backend.BuildState
import devstudioai.ui.backend.RunConsoleUi
import devstudioai.ui.backend.RunTaskOption
import devstudioai.ui.backend.UiPermissionDecision
import devstudioai.ui.backend.UiPermissionRequest
import kotlinx.coroutines.flow.StateFlow

/** [BuildService] over the engine: build/run state, the run-task list, interactive console I/O, and the
 *  run-sandbox permission prompts. The observable flows re-point to the live engine on each project swap. */
internal class BuildBackend(private val ctx: BackendContext) : BuildService {
    /** The single point where the build/run engine is chosen — in-process or the `:build` daemon, decided by
     *  whether the host injected a remote-runner factory. See docs/build-process-isolation.md. */
    private fun runner(s: IdeServices): BuildRunner = ctx.buildRunnerFor(s)

    override val buildState: StateFlow<BuildState> = ctx.engineFlow(BuildState()) { runner(it).buildState }
    override fun runTasks(): List<RunTaskOption> = runner(ctx.services).runTasks()
    override fun runTask(id: String) = runner(ctx.services).runTask(id)
    override fun runBuild() = runner(ctx.services).runBuild()
    override fun stopBuild() = runner(ctx.services).stopBuild()

    override val runConsole: StateFlow<RunConsoleUi?> = ctx.engineFlow<RunConsoleUi?>(null) { runner(it).runConsole }
    override fun sendRunInput(text: String) = runner(ctx.services).sendRunInput(text)
    override fun closeRunInput() = runner(ctx.services).closeRunInput()

    override val permissionRequest: StateFlow<UiPermissionRequest?> =
        ctx.engineFlow<UiPermissionRequest?>(null) { runner(it).permissionRequest }
    override fun answerPermission(id: Int, decision: UiPermissionDecision) = runner(ctx.services).answerPermission(id, decision)

    override fun listVariants(moduleName: String): List<String> = ctx.services.listVariants(moduleName)
    override fun activeVariant(moduleName: String): String? = ctx.services.activeVariant(moduleName)
    override fun setActiveVariant(moduleName: String, variant: String) = ctx.services.setActiveVariant(moduleName, variant)
}
