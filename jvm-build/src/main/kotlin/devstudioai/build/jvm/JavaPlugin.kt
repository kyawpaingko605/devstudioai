package devstudioai.build.jvm

import devstudioai.build.BuildConfiguration
import devstudioai.build.BuildGoal
import devstudioai.build.Plugin
import devstudioai.build.SourceGenerator
import devstudioai.build.TaskContainer
import devstudioai.build.TaskName
import devstudioai.build.engine.GenerateSourcesTask
import devstudioai.build.engine.JarTask
import devstudioai.build.engine.LifecycleTask
import devstudioai.build.engine.ProcessResourcesTask
import devstudioai.build.engine.classOutputs
import devstudioai.build.engine.depOutputDirs
import devstudioai.build.engine.directModuleDeps
import devstudioai.build.engine.hasKotlinSources
import devstudioai.build.engine.jarPath
import devstudioai.build.engine.kotlinSiblings
import devstudioai.build.engine.libJars
import devstudioai.build.engine.moduleClosure
import devstudioai.build.engine.resourceRoots
import devstudioai.build.engine.resourcesDir
import devstudioai.lang.jdt.build.JdtCompileTask
import devstudioai.lang.kotlin.build.KotlinCompileTask
import devstudioai.lang.kotlin.compile.BUILTIN_KOTLIN_COMPILER_PLUGINS
import devstudioai.lang.kotlin.compile.IncrementalKotlinCompiler
import devstudioai.lang.kotlin.compile.KotlinCompilerPlugin
import devstudioai.model.ContentRole
import devstudioai.model.Module
import devstudioai.model.ModuleId
import java.nio.file.Path
import java.nio.file.Paths

/**
 * The Java plugin (Gradle's `java`/`java-library`): for each module it registers the standard chain
 * `compileJava → processResources → classes (lifecycle) → jar`, plus a `compileKotlin` step ahead of
 * `compileJava` for any module that carries `.kt` sources (when a [kotlin] compiler is available). The
 * compile tasks are the language modules' own: lang-jdt's [JdtCompileTask] and lang-kotlin's
 * [KotlinCompileTask], constructed here with the host [bootClasspath]. Other plugins (e.g. Android) reuse
 * [registerModule] for plain library modules and depend on the resulting tasks by name (`:lib:jar`,
 * `:lib:classes`).
 */
class JavaPlugin(
    private val bootClasspath: List<Path> = emptyList(),
    private val kotlin: IncrementalKotlinCompiler? = null,
    /** Kotlin compiler plugins applied by the `compileKotlin` tasks (the `platform.kotlinCompilerPlugin`
     *  EP contents; defaults to the built-ins for direct/test wiring). */
    private val plugins: List<KotlinCompilerPlugin> = BUILTIN_KOTLIN_COMPILER_PLUGINS,
    /** Build-time source generators (the `platform.sourceGenerator` EP contents). When a module declares a
     *  `ContentRole.GENERATED` root, a `generateSources` task runs them into it ahead of compilation. */
    private val generators: List<SourceGenerator> = emptyList(),
) : Plugin {

    /** The module's `ContentRole.GENERATED` source roots (where a [GenerateSourcesTask] emits). */
    private fun generatedRoots(module: Module): List<Path> = module.sourceSets
        .flatMap { it.contentRoots }
        .filter { ContentRole.GENERATED in it.roles }
        .map { Paths.get(it.dir.path) }

    override fun apply(config: BuildConfiguration) {
        val byId = config.project.modules.associateBy { it.id }
        val packaging = config.request.goal in setOf(BuildGoal.ASSEMBLE, BuildGoal.PACKAGE, BuildGoal.INSTALL)
        for (m in moduleClosure(config.request.targets, byId)) registerModule(config.tasks, m, byId, withJar = packaging)
    }

    /**
     * Register [module]'s task chain into [tasks]. Call once per module (the caller iterates a deduped
     * closure). [withJar] adds the packaging `jar` task (the library's artifact); compile/classes/resources
     * are always registered. A `compileKotlin` step is added (and `compileJava` made to depend on it + see
     * its output) when [module] has Kotlin sources and a [kotlin] compiler is available.
     */
    fun registerModule(tasks: TaskContainer, module: Module, byId: Map<ModuleId, Module>, withJar: Boolean) {
        val hasKt = kotlin != null && hasKotlinSources(module)
        // generateSources: run the source generators into the module's generated root ahead of compilation.
        // The compile tasks read that root as source (it's ContentRole.GENERATED), so they need only an
        // explicit edge to it — the generated dir is empty at graph-build time, so output/input inference alone
        // wouldn't catch it (the same reason Android wires aapt2Link -> compileJava explicitly).
        val generatedRoots = generatedRoots(module)
        val generateSources: TaskName? = if (generators.isNotEmpty() && generatedRoots.isNotEmpty()) {
            val gen = TaskName(":${module.name}:generateSources")
            val cp = { depOutputDirs(module) + kotlinSiblings(depOutputDirs(module)) + libJars(module) }
            tasks.register(gen) { GenerateSourcesTask(module, gen, generators, generatedRoots.first(), cp) }
            gen
        } else null

        if (hasKt) {
            val kc = TaskName(":${module.name}:compileKotlin")
            tasks.register(kc) { KotlinCompileTask(module, kc, bootClasspath, kotlin, plugins) }.configure {
                generateSources?.let { dependsOn(it) }
                directModuleDeps(module, byId).forEach {
                    dependsOn(TaskName(":${it.name}:compileJava"))
                    if (hasKotlinSources(it)) dependsOn(TaskName(":${it.name}:compileKotlin"))
                }
            }
        }
        val compile = TaskName(":${module.name}:compileJava")
        tasks.register(compile) { JdtCompileTask(module, compile, bootClasspath, ownKotlinOut = hasKt) }.configure {
            generateSources?.let { dependsOn(it) }
            directModuleDeps(module, byId).forEach { dependsOn(TaskName(":${it.name}:compileJava")) }
            if (hasKt) dependsOn(TaskName(":${module.name}:compileKotlin"))
        }
        val procRes = TaskName(":${module.name}:processResources")
        tasks.register(procRes) { ProcessResourcesTask(procRes, resourceRoots(module), resourcesDir(module)) }

        val classes = TaskName(":${module.name}:classes")
        tasks.register(classes) { LifecycleTask(classes, trackedDirs = classOutputs(module) + resourcesDir(module)) }
            .configure { dependsOn(compile, procRes) }

        if (withJar) {
            val jar = TaskName(":${module.name}:jar")
            tasks.register(jar) { JarTask(jar, classOutputs(module), jarPath(module)) }.configure { dependsOn(classes) }
        }
    }
}
