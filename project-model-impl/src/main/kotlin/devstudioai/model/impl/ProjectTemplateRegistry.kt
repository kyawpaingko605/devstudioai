package devstudioai.model.impl

import devstudioai.model.template.ProjectTemplate
import devstudioai.model.template.ProjectTemplateExtensionPoint
import devstudioai.model.template.TemplateId
import devstudioai.platform.ExtensionRegistry
import devstudioai.platform.PluginId

/**
 * Resolves the [ProjectTemplate]s plugins contributed to [ProjectTemplateExtensionPoint] — the
 * Create-Project gallery's source of truth. Mirrors [ModuleTypeRegistry] over its own extension point.
 */
class ProjectTemplateRegistry(private val extensions: ExtensionRegistry) {
    fun register(template: ProjectTemplate, plugin: PluginId) =
        extensions.register(ProjectTemplateExtensionPoint, template, plugin)

    fun all(): List<ProjectTemplate> = extensions.extensions(ProjectTemplateExtensionPoint)

    fun byId(id: TemplateId): ProjectTemplate? = all().firstOrNull { it.id == id }
}
