package devstudioai.build.jvm

import devstudioai.build.BuildGoal
import devstudioai.build.BuildRequest
import devstudioai.build.VariantSelector
import devstudioai.build.engine.classOutputs
import devstudioai.build.engine.kotlinOutputDir
import devstudioai.lang.kotlin.compile.IncrementalKotlinCompiler
import devstudioai.model.BuildSystemId
import devstudioai.model.ContentRole
import devstudioai.model.DependencyScope
import devstudioai.model.FacetTemplate
import devstudioai.model.ModuleId
import devstudioai.model.ModuleType
import devstudioai.model.Project
import devstudioai.model.SourceSetTemplate
import devstudioai.model.impl.FacetCodecRegistry
import devstudioai.model.impl.ModuleTypeRegistry
import devstudioai.model.impl.ProjectModel
import devstudioai.model.impl.ProjectModelStore
import devstudioai.platform.PluginId
import devstudioai.platform.impl.PlatformCore
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Locks the **graph shape** of Kotlin compilation in the Java build system, without running the real
 * compiler (fast, no K2). For a module that carries `.kt`, [JavaPlugin] must register a `compileKotlin`
 * step, make `compileJava` depend on it (so Kotlin compiles first and the Java compile sees its output),
 * and the module's `kotlin-classes` output must be part of its class outputs — the invariant that puts the
 * Kotlin output into both the module jar and the run/dex runtime classpath. Real Kotlin/Java interop
 * codegen is proven end-to-end by `KotlinJavaInteropBuildTest` in `:ide-core`, which drives the actual K2
 * compiler; this test pins the wiring deterministically and cheaply.
 */
class KotlinBuildWiringTest {

    private class JavaLib : ModuleType {
        override val id = "java-lib"
        override val displayName = "Java Library"
        override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
        override fun defaultFacets(): List<FacetTemplate> = emptyList()
        override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
    }

    private fun buildWorkspace(dir: Path, platform: PlatformCore): Pair<ProjectModelStore, Project> {
        ModuleTypeRegistry(platform.extensions).register(JavaLib(), PluginId("java-support"))
        val store = ProjectModel.open(dir, platform, FacetCodecRegistry())
        val javaLib = ModuleTypeRegistry(platform.extensions).resolve("java-lib")
        store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
        val mixed = SourceSetTemplate(
            "main", DependencyScope.IMPLEMENTATION,
            mapOf("src/main/java" to setOf(ContentRole.SOURCE), "src/main/kotlin" to setOf(ContentRole.SOURCE)),
        )
        store.workspace.projects.single().beginModification().apply {
            addModule("app", javaLib).addSourceSet(mixed); commit()
        }
        write(dir, "app/src/main/java/com/example/Main.java", MAIN)
        write(dir, "app/src/main/kotlin/com/example/Helper.kt", "package com.example.k\nclass Helper")
        return store to store.workspace.projects.single()
    }

    @Test
    fun compileKotlinIsWiredBeforeCompileJavaAndItsOutputIsPackaged() {
        val dir = Files.createTempDirectory("kt-wiring")
        val platform = PlatformCore()
        try {
            val (_, project) = buildWorkspace(dir, platform)
            // A non-null Kotlin compiler makes the plugin register `compileKotlin`. We inspect the realized
            // graph's structure only (never execute it), so no real K2 ever runs — this stays fast.
            val build = JavaBuildSystem(kotlin = IncrementalKotlinCompiler())
            val graph = build.createBuildGraph(
                project, BuildRequest(listOf(ModuleId("app")), VariantSelector("main"), BuildGoal.PACKAGE),
            )
            val byName = graph.tasks.associateBy { it.name.value }
            val compileJava = byName[":app:compileJava"] ?: error("compileJava must be registered")
            assertTrue(":app:compileKotlin" in byName, "compileKotlin must be registered for a module with .kt")
            assertTrue(
                graph.dependencies(compileJava).any { it.name.value == ":app:compileKotlin" },
                "compileJava must depend on compileKotlin (Kotlin compiles first, Java sees its output)",
            )

            val app = project.modules.single()
            assertTrue(
                kotlinOutputDir(app) in classOutputs(app),
                "the module's kotlin-classes output must be part of its class outputs (so it is packaged into " +
                    "the jar and joins the run/dex runtime classpath)",
            )
        } finally {
            platform.dispose(); dir.toFile().deleteRecursively()
        }
    }

    private fun write(root: Path, rel: String, content: String) {
        val f = root.resolve(rel); Files.createDirectories(f.parent); Files.writeString(f, content)
    }

    private companion object {
        val MAIN = "package com.example;\npublic class Main { public static void main(String[] a) {} }"
    }
}
