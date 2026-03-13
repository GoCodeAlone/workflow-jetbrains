package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WorkflowVisualEditorTest : BasePlatformTestCase() {

    fun testActionEnabledForYamlFiles() {
        val file = myFixture.configureByText("app.yaml", "modules: []")
        val action = WorkflowVisualEditorAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file.virtualFile)
            .build()
        val event = AnActionEvent.createEvent(dataContext, null, "test", ActionUiKind.NONE, null)
        action.update(event)
        assertTrue(
            "Expected action to be enabled for YAML file",
            event.presentation.isEnabledAndVisible
        )
    }

    fun testActionDisabledForNonYamlFiles() {
        val file = myFixture.configureByText("main.go", "package main")
        val action = WorkflowVisualEditorAction()
        val dataContext = SimpleDataContext.builder()
            .add(CommonDataKeys.PROJECT, project)
            .add(CommonDataKeys.VIRTUAL_FILE, file.virtualFile)
            .build()
        val event = AnActionEvent.createEvent(dataContext, null, "test", ActionUiKind.NONE, null)
        action.update(event)
        assertFalse(
            "Expected action to be disabled for non-YAML file",
            event.presentation.isEnabledAndVisible
        )
    }
}
