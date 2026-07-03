package devstudioai.android.support

import devstudioai.android.support.templates.AndroidAppTemplate
import devstudioai.android.support.templates.AndroidLibraryTemplate
import devstudioai.android.support.templates.JetpackComposeAppTemplate
import devstudioai.android.support.templates.MaterialYouAppTemplate
import devstudioai.model.impl.FacetCodecRegistry
import devstudioai.model.impl.FileIconRegistry
import devstudioai.model.impl.ModuleTypeRegistry
import devstudioai.model.impl.ProjectTemplateRegistry
import devstudioai.platform.PluginId

/**
 * The android-support plugin entry point: contributes the Android module types and the [AndroidFacet]
 * codec to a host's registries. A host (`:ide-core`, `:ide-android`) calls this once at startup so
 * `module.toml` files of type `android-app`/`android-lib` load with a resolvable type and a decodable
 * facet, and the project-structure UI can create new Android modules.
 */
object AndroidSupport {
    val PLUGIN = PluginId("android-support")

    fun register(moduleTypes: ModuleTypeRegistry, codecs: FacetCodecRegistry) {
        moduleTypes.register(AndroidAppModuleType, PLUGIN)
        moduleTypes.register(AndroidLibModuleType, PLUGIN)
        codecs.register(AndroidFacetCodec)
    }

    /** Contribute the Android tree icons (res/assets/manifest/android-module) to a host's icon registry. */
    fun registerIcons(icons: FileIconRegistry) {
        icons.register(AndroidFileIconProvider, PLUGIN)
    }

    /** Contribute the Android project templates (app, Material You app, library) to a host's Create-Project gallery. */
    fun registerTemplates(templates: ProjectTemplateRegistry) {
        templates.register(AndroidAppTemplate, PLUGIN)
        templates.register(MaterialYouAppTemplate, PLUGIN)
        templates.register(JetpackComposeAppTemplate, PLUGIN)
        templates.register(AndroidLibraryTemplate, PLUGIN)
    }
}
