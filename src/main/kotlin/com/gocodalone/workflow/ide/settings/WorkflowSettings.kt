package com.gocodalone.workflow.ide.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.APP)
@State(
    name = "WorkflowEngineSettings",
    storages = [Storage("workflow-engine.xml")]
)
class WorkflowSettings : PersistentStateComponent<WorkflowSettings> {

    /** Path to the wfctl binary. Empty = resolve from PATH. */
    var wfctlPath: String = ""

    /** Path to the workflow-lsp-server binary. Empty = resolve from PATH. */
    var lspServerPath: String = ""

    /** Whether the LSP server integration is enabled. */
    var enableLsp: Boolean = true

    /** Whether to automatically register wfctl as the MCP server on project open. */
    var autoRegisterMcp: Boolean = true

    override fun getState(): WorkflowSettings = this

    override fun loadState(state: WorkflowSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(): WorkflowSettings =
            ApplicationManager.getApplication().getService(WorkflowSettings::class.java)
    }
}
