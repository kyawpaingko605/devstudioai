package devstudioai.model.impl

import devstudioai.model.BuildSystemId
import devstudioai.model.FacetTemplate
import devstudioai.model.ModuleType
import devstudioai.model.ModuleTypeExtensionPoint
import devstudioai.model.SourceSetTemplate
import devstudioai.platform.ExtensionRegistry
import devstudioai.platform.PluginId

/**
 * Resolves persisted module-type ids back to the [ModuleType]s plugins contributed to
 * [ModuleTypeExtensionPoint]. A persisted id that no plugin provides resolves to [UnknownModuleType]
 * (with no source-set/facet templates) rather than failing the load — the model can still be inspected
 * and the missing plugin reported.
 */
class ModuleTypeRegistry(private val extensions: ExtensionRegistry) {
    fun register(type: ModuleType, plugin: PluginId) = extensions.register(ModuleTypeExtensionPoint, type, plugin)

    fun byId(id: String): ModuleType? =
        extensions.extensions(ModuleTypeExtensionPoint).firstOrNull { it.id == id }

    /** Every module type contributed to the extension point, in registration order (for the New-Module picker). */
    fun all(): List<ModuleType> = extensions.extensions(ModuleTypeExtensionPoint)

    fun resolve(id: String): ModuleType = byId(id) ?: UnknownModuleType(id)
}

class UnknownModuleType(override val id: String) : ModuleType {
    override val displayName: String get() = "Unknown module type ($id)"
    override fun defaultSourceSets(): List<SourceSetTemplate> = emptyList()
    override fun defaultFacets(): List<FacetTemplate> = emptyList()
    override fun supportedBuildSystems(): Set<BuildSystemId> = emptySet()
}
