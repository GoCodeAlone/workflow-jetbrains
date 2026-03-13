package com.gocodalone.workflow.ide.actions

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class WfctlActionTest : BasePlatformTestCase() {

    fun testValidateActionBuildArgsWithFile() {
        val action = ValidateAction()
        val file = myFixture.configureByText("app.yaml", "modules: []")
        val args = action.buildArgs(file.virtualFile)
        assertEquals(listOf("template", "validate", "--config", file.virtualFile.path), args)
    }

    fun testValidateActionBuildArgsWithoutFile() {
        val action = ValidateAction()
        val args = action.buildArgs(null)
        assertEquals(listOf("template", "validate"), args)
    }

    fun testInspectActionBuildArgsWithFile() {
        val action = InspectAction()
        val file = myFixture.configureByText("workflow.yaml", "modules: []")
        val args = action.buildArgs(file.virtualFile)
        assertEquals(listOf("inspect", "-deps", file.virtualFile.path), args)
    }

    fun testInspectActionBuildArgsWithoutFile() {
        val action = InspectAction()
        val args = action.buildArgs(null)
        assertEquals(listOf("inspect", "-deps"), args)
    }

    fun testRunActionBuildArgsWithFile() {
        val action = RunAction()
        val file = myFixture.configureByText("app.yaml", "modules: []")
        val args = action.buildArgs(file.virtualFile)
        assertEquals(listOf("run", "-config", file.virtualFile.path), args)
    }

    fun testRunActionBuildArgsWithoutFile() {
        val action = RunAction()
        val args = action.buildArgs(null)
        assertEquals(listOf("run"), args)
    }

    fun testSchemaActionBuildArgs() {
        val action = WorkflowSchemaAction()
        val file = myFixture.configureByText("app.yaml", "modules: []")
        val args = action.buildArgs(file.virtualFile)
        assertEquals(listOf("schema"), args)
    }

    fun testSchemaActionBuildArgsIgnoresFile() {
        val action = WorkflowSchemaAction()
        val args = action.buildArgs(null)
        assertEquals("Schema action should return same args regardless of file", listOf("schema"), args)
    }

    fun testSchemaActionIsAlwaysApplicable() {
        val action = WorkflowSchemaAction()
        val yamlFile = myFixture.configureByText("app.yaml", "modules: []")
        val goFile = myFixture.configureByText("main.go", "package main")
        assertTrue("Schema action should be applicable to yaml", action.isApplicable(yamlFile.virtualFile))
        assertTrue("Schema action should be applicable to any file", action.isApplicable(goFile.virtualFile))
    }

    fun testValidateActionIsApplicableOnlyToYaml() {
        val action = ValidateAction()
        val yamlFile = myFixture.configureByText("app.yaml", "modules: []")
        val ymlFile = myFixture.configureByText("config.yml", "modules: []")
        val goFile = myFixture.configureByText("main.go", "package main")
        assertTrue("Should be applicable to .yaml", action.isApplicable(yamlFile.virtualFile))
        assertTrue("Should be applicable to .yml", action.isApplicable(ymlFile.virtualFile))
        assertFalse("Should NOT be applicable to .go", action.isApplicable(goFile.virtualFile))
    }

    fun testInspectActionIsApplicableOnlyToYaml() {
        val action = InspectAction()
        val yamlFile = myFixture.configureByText("workflow.yaml", "modules: []")
        val jsonFile = myFixture.configureByText("config.json", "{}")
        assertTrue("Should be applicable to .yaml", action.isApplicable(yamlFile.virtualFile))
        assertFalse("Should NOT be applicable to .json", action.isApplicable(jsonFile.virtualFile))
    }

    fun testRunActionIsApplicableOnlyToYaml() {
        val action = RunAction()
        val ymlFile = myFixture.configureByText("app.yml", "modules: []")
        val txtFile = myFixture.configureByText("readme.txt", "hello")
        assertTrue("Should be applicable to .yml", action.isApplicable(ymlFile.virtualFile))
        assertFalse("Should NOT be applicable to .txt", action.isApplicable(txtFile.virtualFile))
    }

    fun testTemplateValidateActionMatchesValidateAction() {
        val validate = ValidateAction()
        val templateValidate = WorkflowTemplateValidateAction()
        val file = myFixture.configureByText("app.yaml", "modules: []")
        assertEquals(
            "TemplateValidateAction should produce same args as ValidateAction",
            validate.buildArgs(file.virtualFile),
            templateValidate.buildArgs(file.virtualFile)
        )
    }

    fun testTemplateValidateActionWithoutFile() {
        val validate = ValidateAction()
        val templateValidate = WorkflowTemplateValidateAction()
        assertEquals(
            "TemplateValidateAction with null should match ValidateAction with null",
            validate.buildArgs(null),
            templateValidate.buildArgs(null)
        )
    }

    fun testWfctlResultSuccess() {
        val result = WfctlResult(exitCode = 0, stdout = "OK", stderr = "")
        assertTrue("Exit code 0 should be success", result.isSuccess)
    }

    fun testWfctlResultFailure() {
        val result = WfctlResult(exitCode = 1, stdout = "", stderr = "Error")
        assertFalse("Non-zero exit code should be failure", result.isSuccess)
    }

    fun testWfctlResultNegativeExitCode() {
        val result = WfctlResult(exitCode = -1, stdout = "", stderr = "not found")
        assertFalse("Negative exit code should be failure", result.isSuccess)
    }

    fun testValidateActionBuildArgsContainsFilePath() {
        val action = ValidateAction()
        val file = myFixture.configureByText("routes.yaml", "modules: []")
        val args = action.buildArgs(file.virtualFile)
        assertTrue("Args should contain the file path", args.contains(file.virtualFile.path))
        assertEquals("File path should be the last argument", file.virtualFile.path, args.last())
    }
}
