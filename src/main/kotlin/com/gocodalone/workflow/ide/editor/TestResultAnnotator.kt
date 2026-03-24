package com.gocodalone.workflow.ide.editor

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.icons.AllIcons
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.psi.PsiElement
import javax.swing.Icon

/**
 * Displays pass/fail gutter icons on test case name lines in *_test.yaml files.
 * Results are provided by [TestResultService] after a wfctl test run.
 */
class TestResultAnnotator : LineMarkerProvider {

    // Match "name: <value>" lines (with optional leading dash for list items)
    private val NAME_RE = Regex("""^\s*-?\s*name:\s*["']?(.+?)["']?\s*$""")

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        val file = element.containingFile ?: return null
        val vFile = file.virtualFile ?: return null

        val name = vFile.name
        if (!name.endsWith("_test.yaml") && !name.endsWith("_test.yml")) return null

        val project = element.project
        val service = TestResultService.getInstance(project)
        val results = service.get(vFile.path) ?: return null
        if (results.isEmpty()) return null

        // Only process leaf text elements that look like "name: ..." lines
        val lineText = element.text ?: return null
        val match = NAME_RE.matchEntire(lineText.trimEnd()) ?: return null
        val testName = match.groupValues[1]

        val result = results.find { it.name == testName } ?: return null

        return when (result.status) {
            TestStatus.PASS -> makeMarker(element, AllIcons.General.InspectionsOK, "Test passed: $testName")
            TestStatus.FAIL -> makeMarker(
                element,
                AllIcons.General.Error,
                buildString {
                    append("Test failed: $testName")
                    if (!result.error.isNullOrBlank()) append("\n${result.error}")
                }
            )
            TestStatus.SKIP -> makeMarker(element, AllIcons.General.Warning, "Test skipped: $testName")
        }
    }

    private fun makeMarker(element: PsiElement, icon: Icon, tooltip: String): LineMarkerInfo<PsiElement> {
        return LineMarkerInfo(
            element,
            element.textRange,
            icon,
            { tooltip },
            null,
            GutterIconRenderer.Alignment.LEFT,
            { tooltip },
        )
    }
}
