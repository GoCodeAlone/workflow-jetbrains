package com.gocodalone.workflow.ide

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

class WorkflowStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        McpRegistration.checkAndRegister(project)
    }
}
