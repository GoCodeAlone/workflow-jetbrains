package com.gocodalone.workflow.ide.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(
    name = "WorkflowEngineProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)]
)
class WorkflowProjectSettings : PersistentStateComponent<WorkflowProjectSettings> {

    /** Glob patterns identifying workflow config files relative to project root. */
    var configPaths: List<String> = emptyList()

    /** Whether to suppress the content-detection notification prompt in this project. */
    var suppressDetectionPrompt: Boolean = false

    /** Whether MCP server has been registered for this project. */
    var mcpRegistered: Boolean = false

    /** Whether the user declined MCP registration for this project. */
    var mcpRegistrationDeclined: Boolean = false

    override fun getState(): WorkflowProjectSettings = this

    override fun loadState(state: WorkflowProjectSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): WorkflowProjectSettings =
            project.getService(WorkflowProjectSettings::class.java)
    }
}
