package devstudioai.android.support

import devstudioai.android.support.templates.AndroidTemplateSupport
import devstudioai.android.support.templates.MaterialYouAppTemplate
import devstudioai.model.LanguageLevel
import devstudioai.model.ModuleType
import devstudioai.model.Workspace
import devstudioai.model.impl.FacetCodecRegistry
import devstudioai.model.impl.ModuleTypeRegistry
import devstudioai.model.impl.ProjectModel
import devstudioai.model.impl.ProjectModelStore
import devstudioai.model.template.ProjectScaffold
import devstudioai.model.template.TemplateArgs
import devstudioai.platform.impl.PlatformCore
import devstudioai.vfs.VirtualFile
import java.nio.file.Files
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaterialYouTemplateTest {

    @Test
    fun declaresGoogleMaterialDependencyOnTheAppModule() {
        val deps = MaterialYouAppTemplate.dependencies(args())
        assertEquals(1, deps.size)
        assertEquals("app", deps.single().module)
        assertEquals(AndroidTemplateSupport.MATERIAL_COORDINATE, deps.single().coordinate)
        assertEquals("implementation", deps.single().scope)
    }

    @Test
    fun minSdkChoicesAreMaterialCompatible() {
        val minSdk = MaterialYouAppTemplate.parameters()
            .filterIsInstance<devstudioai.model.template.TemplateParameter.Choice>()
            .first { it.key == "minSdk" }
        assertTrue(minSdk.options.all { it.value.toInt() >= 21 }, "Material requires minSdk >= 21")
    }

    @Test
    fun generatesFabLayoutMaterial3ThemeAndWiredActivity() {
        withScaffold { scaffold, root ->
            MaterialYouAppTemplate.generate(scaffold, args())

            val layout = root.resolve("app/src/main/res/layout/activity_main.xml").readText()
            assertTrue("com.google.android.material.floatingactionbutton.FloatingActionButton" in layout, "layout has the FAB")
            assertTrue("androidx.coordinatorlayout.widget.CoordinatorLayout" in layout, "FAB sits in a CoordinatorLayout")
            assertTrue("@+id/fab" in layout)

            val theme = root.resolve("app/src/main/res/values/themes.xml").readText()
            assertTrue("Theme.Material3.DynamicColors.DayNight" in theme, "Material You dynamic-colour theme")

            val activity = root.resolve("app/src/main/java/com/example/app/MainActivity.java").readText()
            assertTrue("extends AppCompatActivity" in activity)
            assertTrue("Snackbar.make" in activity, "FAB tap shows a Snackbar")
            assertTrue("R.id.fab" in activity)
        }
    }

    @Test
    fun generatesProguardRulesFileForTheReleaseDefault() {
        withScaffold { scaffold, root ->
            MaterialYouAppTemplate.generate(scaffold, args())
            // The default `release` build type references `proguard-rules.pro`; the template writes it so the
            // entry resolves (instead of R8 silently skipping a missing module file) when minify is enabled.
            val rules = root.resolve("app/proguard-rules.pro")
            assertTrue(Files.isRegularFile(rules), "proguard-rules.pro is generated at the module root")
            assertEquals(
                "proguard-rules.pro",
                AndroidFacet.DEFAULT_BUILD_TYPES.first { it.name == "release" }.proguardFiles
                    .first { !DefaultProguardFiles.isDefault(it) },
                "the file matches the release build type's module-relative proguardFiles entry",
            )
        }
    }

    @Test
    fun kotlinVariantEmitsKotlinActivity() {
        withScaffold { scaffold, root ->
            MaterialYouAppTemplate.generate(scaffold, args(mapOf("language" to "kotlin")))
            val activity = root.resolve("app/src/main/kotlin/com/example/app/MainActivity.kt").readText()
            assertTrue("class MainActivity : AppCompatActivity()" in activity)
            assertTrue("findViewById<FloatingActionButton>(R.id.fab)" in activity)
        }
    }

    // --- support ---

    private fun args(extra: Map<String, String> = emptyMap()) = TemplateArgs(
        mapOf(TemplateArgs.NAME to "MaterialApp", TemplateArgs.PACKAGE to "com.example.app") + extra,
    )

    private fun withScaffold(body: (ProjectScaffold, root: java.nio.file.Path) -> Unit) {
        val dir = Files.createTempDirectory("material-you-template")
        val platform = PlatformCore()
        try {
            val store: ProjectModelStore = ProjectModel.open(dir, platform, FacetCodecRegistry().register(AndroidFacetCodec))
            ModuleTypeRegistry(platform.extensions).register(AndroidAppModuleType, AndroidSupport.PLUGIN)
            val scaffold = object : ProjectScaffold {
                override val workspace: Workspace get() = store.workspace
                override val rootDir: VirtualFile get() = store.vfs.root()
                override val languageLevel: LanguageLevel = LanguageLevel.JAVA_17
                override fun moduleType(id: String): ModuleType = ModuleTypeRegistry(platform.extensions).resolve(id)
                override fun writeText(relPath: String, content: String) {
                    val file = store.rootPath.resolve(relPath)
                    Files.createDirectories(file.parent)
                    file.writeText(content.trimIndent() + "\n")
                }
            }
            body(scaffold, store.rootPath)
        } finally {
            platform.dispose(); dir.toFile().deleteRecursively()
        }
    }
}
