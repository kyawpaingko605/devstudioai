package devstudioai.android.support

import devstudioai.android.support.tools.AndroidSdk
import devstudioai.android.support.tools.SigningConfig
import devstudioai.build.BuildGoal
import devstudioai.build.BuildRequest
import devstudioai.build.CyclicTaskDependencyException
import devstudioai.build.VariantSelector
import devstudioai.model.BuildSystemId
import devstudioai.model.ContentRole
import devstudioai.model.DependencyScope
import devstudioai.model.FacetTemplate
import devstudioai.model.LanguageLevel
import devstudioai.model.ModuleDependency
import devstudioai.model.ModuleId
import devstudioai.model.ModuleType
import devstudioai.model.SourceSetTemplate
import devstudioai.model.impl.FacetCodecRegistry
import devstudioai.model.impl.ModuleTypeRegistry
import devstudioai.model.impl.ProjectModel
import devstudioai.platform.PluginId
import devstudioai.platform.impl.PlatformCore
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.fail

/**
 * Regression for issue #993: the default demo (`app android-app → feature android-lib → core java-lib`)
 * must produce an **acyclic** build graph. A reported cycle
 * `:feature:classes → :core:jar → :core:classes → :feature:jar → :feature:classes`
 * meant a clean v3.0.0 install could not build the demo at all. This builds the graph (no SDK needed —
 * construction only stores paths) and runs the topological sort, which throws on a cycle.
 */
class AndroidBuildGraphCycleTest {

    private object JavaLib : ModuleType {
        override val id = "java-lib"
        override val displayName = "Java Library"
        override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
        override fun defaultFacets(): List<FacetTemplate> = emptyList()
        override fun supportedBuildSystems(): Set<BuildSystemId> = setOf(BuildSystemId.NATIVE)
    }

    @Test
    fun demoGraphIsAcyclic() {
        val dir = Files.createTempDirectory("android-cycle")
        val platform = PlatformCore()
        try {
            val store = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
            val types = ModuleTypeRegistry(platform.extensions)
            types.register(JavaLib, PluginId("java-support"))
            AndroidSupport.register(types, FacetCodecRegistry())

            SampleAndroidProject.generate(
                store,
                androidApp = types.resolve("android-app"),
                androidLib = types.resolve("android-lib"),
                javaLib = types.resolve("java-lib"),
            )
            // Mirror the device flow: the demo is generated, saved, then RELOADED from `module.toml` on the
            // next launch (ProjectManager.open). Path resolution on reload is what the IDE actually builds.
            store.save()
            val reopened = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))

            // Fake SDK / signing — graph construction only records paths, it never reads them.
            val sdk = AndroidSdk(
                androidJar = dir.resolve("fake/android.jar"),
                buildToolsDir = dir.resolve("fake/build-tools"),
            )
            val signing = SigningConfig(dir.resolve("fake/debug.ks"), "android", "android", "android")
            val buildSystem = AndroidBuildSystem.subprocess(sdk, signing)

            val project = reopened.workspace.projects.single { it.name == SampleAndroidProject.PROJECT }
            for (goal in listOf(BuildGoal.COMPILE_ONLY, BuildGoal.PACKAGE)) {
                val graph = buildSystem.createBuildGraph(
                    project,
                    BuildRequest(listOf(ModuleId("app")), VariantSelector("debug"), goal),
                )
                try {
                    graph.topologicalLevels()
                } catch (e: CyclicTaskDependencyException) {
                    fail("demo build graph has a cycle (goal=$goal): ${e.cycle.joinToString(" -> ") { it.value }}")
                }
            }
        } finally {
            platform.dispose(); dir.toFile().deleteRecursively()
        }
    }
}
