package devstudioai.build.engine

import devstudioai.build.SourceGenRequest
import devstudioai.build.SourceGenerator
import devstudioai.build.Task
import devstudioai.build.TaskContext
import devstudioai.build.TaskInputs
import devstudioai.build.TaskName
import devstudioai.build.TaskOutputs
import devstudioai.build.TaskResult
import devstudioai.model.ContentRole
import devstudioai.model.Module
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.stream.Collectors

/**
 * `generateSources`: runs the applicable [SourceGenerator]s for [module] into [outputDir] (the module's
 * `ContentRole.GENERATED` root), BEFORE `compileKotlin`/`compileJava` — which read that root as source, so
 * the generated files compile and index like hand-written ones (no compile-task change needed).
 *
 * Inputs are the module's **hand-written** sources only (the SOURCE roots, never [outputDir] itself, so the
 * generator never sees its own output and can't ping-pong the up-to-date check) plus the compile classpath;
 * the output is [outputDir]. `JavaPlugin` adds an explicit `compile -> generateSources` edge (the generated
 * dir is empty at graph-build time, so the engine's output/input inference alone wouldn't catch it).
 */
class GenerateSourcesTask(
    private val module: Module,
    override val name: TaskName,
    private val generators: List<SourceGenerator>,
    private val outputDir: Path,
    private val classpath: () -> List<Path>,
) : Task {

    /** The module's hand-written source files of [ext] (SOURCE roots only; excludes the generated root). */
    private fun handWritten(ext: String): List<Path> = module.sourceSets
        .flatMap { it.contentRoots }
        .filter { ContentRole.SOURCE in it.roles }
        .map { Paths.get(it.dir.path) }
        .filter { Files.isDirectory(it) }
        .flatMap { root -> Files.walk(root).use { s -> s.filter { it.toString().endsWith(ext) }.collect(Collectors.toList()) } }

    private fun request(): SourceGenRequest = SourceGenRequest(
        moduleName = module.name,
        kotlinSources = handWritten(".kt"),
        javaSources = handWritten(".java"),
        classpath = classpath(),
        outputDir = outputDir,
    )

    override val inputs: TaskInputs
        get() = TaskInputsImpl().apply {
            filePaths("kotlinSources", handWritten(".kt"))
            filePaths("javaSources", handWritten(".java"))
            dirPaths("deps", depOutputDirs(module))
            filePaths("libs", libJars(module))
            property("generators", generators.joinToString(",") { it.id })
        }
    override val outputs: TaskOutputs
        get() = TaskOutputsImpl().apply { dirPath("generated", outputDir) }

    override suspend fun execute(ctx: TaskContext): TaskResult {
        ctx.checkCanceled()
        Files.createDirectories(outputDir)
        val req = request()
        val applicable = generators.filter { it.appliesTo(req) }
        if (applicable.isEmpty()) return TaskResult.Success
        for (g in applicable) {
            ctx.checkCanceled()
            val r = g.generate(req)
            r.messages.forEach(ctx.logger())
            if (!r.success) {
                return TaskResult.Failed(r.messages.joinToString("\n").ifBlank { "source generation (${g.id}) failed" })
            }
        }
        ctx.logger()(":${module.name}:generateSources OK (${applicable.joinToString(",") { it.id }})")
        return TaskResult.Success
    }
}
