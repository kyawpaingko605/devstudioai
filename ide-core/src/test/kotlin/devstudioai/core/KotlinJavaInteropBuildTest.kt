package devstudioai.core

import devstudioai.build.BuildGoal
import devstudioai.build.BuildRequest
import devstudioai.build.VariantSelector
import devstudioai.build.engine.BuildCache
import devstudioai.build.engine.SimpleTaskContext
import devstudioai.build.engine.TaskExecutorImpl
import devstudioai.build.engine.jarPath
import devstudioai.build.jvm.JavaBuildSystem
import devstudioai.lang.kotlin.compile.IncrementalKotlinCompiler
import devstudioai.lang.kotlin.compile.KotlinJvmCompiler
import devstudioai.model.BuildSystemId
import devstudioai.model.ContentRole
import devstudioai.model.DependencyScope
import devstudioai.model.FacetTemplate
import devstudioai.model.LibraryDependency
import devstudioai.model.LibraryKind
import devstudioai.model.LibraryRef
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
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * End-to-end Kotlin/Java interop in the native build: one module
 * with mixed `.kt`/`.java` sources where the dependency runs both ways:
 *  - `Greeter.kt` (Kotlin) calls `JavaUtil.shout(...)` (same-module Java): Kotlin to Java
 *  - `Main.java` (Java) constructs `Greeter` and calls `greet(...)` (Kotlin): Java to Kotlin
 * so the build only succeeds if `compileKotlin` resolves the Java sources AND `compileJava` sees the Kotlin
 * output on its classpath. Asserts the jar carries all three classes, then runs it (real `java`) and
 * checks the program output, proving the interop links and executes against the Kotlin stdlib.
 */
class KotlinJavaInteropBuildTest {

    private class JavaLib : ModuleType {
        override val id = "java-lib"
        override val displayName = "Java Library"
        override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
        override fun defaultFacets(): List<FacetTemplate> = emptyList()
        override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
    }

    /** The loaded kotlin-stdlib jar — added as a module library so the program links + runs. */
    private fun stdlibJar(): Path = Path.of(Unit::class.java.protectionDomain.codeSource.location.toURI())

    private fun buildWorkspace(dir: Path, platform: PlatformCore): Pair<ProjectModelStore, Project> {
        ModuleTypeRegistry(platform.extensions).register(JavaLib(), PluginId("java-support"))
        val store = ProjectModel.open(dir, platform, FacetCodecRegistry())
        val javaLib = ModuleTypeRegistry(platform.extensions).resolve("java-lib")
        store.workspace.beginModification().apply { addProject("demo", BuildSystemId.NATIVE, store.vfs.root()); commit() }
        store.workspace.libraryTable.create("kotlin-stdlib")
            .apply { kind = LibraryKind.JAR; addClassesRoot(store.vfs.fileFor(stdlibJar())); commit() }
        val mixed = SourceSetTemplate(
            "main", DependencyScope.IMPLEMENTATION,
            mapOf("src/main/java" to setOf(ContentRole.SOURCE), "src/main/kotlin" to setOf(ContentRole.SOURCE)),
        )
        store.workspace.projects.single().beginModification().apply {
            addModule("app", javaLib).apply {
                addSourceSet(mixed)
                addDependency(LibraryDependency(LibraryRef("kotlin-stdlib"), DependencyScope.IMPLEMENTATION))
            }
            commit()
        }
        write(dir, "app/src/main/java/com/example/JavaUtil.java", JAVA_UTIL)
        write(dir, "app/src/main/kotlin/com/example/Greeter.kt", GREETER)
        write(dir, "app/src/main/java/com/example/Main.java", MAIN)
        return store to store.workspace.projects.single()
    }

    @Test
    fun buildsAndRunsAMixedKotlinJavaModule() {
        val dir = Files.createTempDirectory("ktjava-interop")
        val platform = PlatformCore()
        try {
            val (_, project) = buildWorkspace(dir, platform)
            // Desktop wiring, exactly as IdeServices builds it: empty boot classpath (ecj/K2 use the host
            // JRE) and the real incremental K2 compiler. compileJava/compileKotlin call ecj/K2 directly.
            val build = JavaBuildSystem(kotlin = IncrementalKotlinCompiler(KotlinJvmCompiler()))
            val graph = build.createBuildGraph(
                project, BuildRequest(listOf(ModuleId("app")), VariantSelector("main"), BuildGoal.PACKAGE),
            )
            val exec = TaskExecutorImpl(BuildCache(dir.resolve(".caches/build")))
            val log = StringBuilder()
            val outcome = runBlocking { exec.execute(graph, SimpleTaskContext(log = { log.appendLine(it) }), 2) }
            assertTrue(outcome.succeeded, "mixed build failed:\n$log")

            val ran = outcome.ranTasks.map { it.value }
            assertTrue(":app:compileKotlin" in ran, "compileKotlin must run: $ran")
            assertTrue(ran.indexOf(":app:compileKotlin") < ran.indexOf(":app:compileJava"),
                "compileKotlin must run before compileJava: $ran")

            val jar = jarPath(project.modules.single())
            val names = JarFile(jar.toFile()).use { jf -> jf.entries().toList().map { it.name } }
            assertTrue("com/example/Greeter.class" in names, "Kotlin class missing from jar: $names")
            assertTrue("com/example/JavaUtil.class" in names, "Java class missing from jar: $names")
            assertTrue("com/example/Main.class" in names, "Java entrypoint missing from jar: $names")

            // Run it (real java fork): proves Java to Kotlin to Java links and executes against the stdlib.
            val runtime = listOf(jar, stdlibJar())
            assertTrue("HELLO, WORLD!" in runJava(runtime, "com.example.Main"), "wrong program output")

            // A clean rebuild does nothing: both compiles are up-to-date.
            val again = runBlocking { exec.execute(graph, SimpleTaskContext(), 2) }
            assertTrue(again.ranTasks.isEmpty(), "re-build must be up-to-date, ran=${again.ranTasks.map { it.value }}")
        } finally {
            platform.dispose(); dir.toFile().deleteRecursively()
        }
    }

    private fun write(root: Path, rel: String, content: String) {
        val f = root.resolve(rel); Files.createDirectories(f.parent); Files.writeString(f, content.trimIndent())
    }

    private fun runJava(classpath: List<Path>, mainClass: String): String {
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val proc = ProcessBuilder(javaBin, "-cp", classpath.joinToString(File.pathSeparator), mainClass)
            .redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        return text.trim()
    }

    private companion object {
        val JAVA_UTIL = """
            package com.example;
            public final class JavaUtil {
                public static String shout(String s) { return s.toUpperCase(); }
            }
        """
        // Kotlin to Java: references the same-module Java class JavaUtil (resolved from its source by kotlinc).
        val GREETER = """
            package com.example
            class Greeter {
                fun greet(name: String): String = JavaUtil.shout("hello, ${'$'}name")
            }
        """
        // Java to Kotlin: references the Kotlin class Greeter (resolved from the compileKotlin output).
        val MAIN = """
            package com.example;
            public class Main {
                public static void main(String[] args) {
                    System.out.println(new Greeter().greet("world") + "!");
                }
            }
        """
    }
}
