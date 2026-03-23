package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project

data class TestCaseResult(
    val name: String,
    val status: TestStatus,
    val error: String? = null,
)

enum class TestStatus { PASS, FAIL, SKIP }

/**
 * Project-level cache of the most recent wfctl test results, keyed by absolute file path.
 * Updated by [com.gocodalone.workflow.ide.actions.RunTestsAction] after each test run.
 */
@Service(Service.Level.PROJECT)
class TestResultService {

    /** Results per _test.yaml absolute path. A null value means "file was tested, no cases parsed." */
    private val resultsByFile = mutableMapOf<String, List<TestCaseResult>>()

    /** The currently active visual editor bridge, if any. Set by WorkflowBridge. */
    var activeBridge: WorkflowBridge? = null

    fun store(filePath: String, results: List<TestCaseResult>) {
        resultsByFile[filePath] = results
    }

    fun get(filePath: String): List<TestCaseResult>? = resultsByFile[filePath]

    fun sendToWebview(results: List<TestCaseResult>) {
        activeBridge?.sendTestResults(results)
    }

    companion object {
        fun getInstance(project: Project): TestResultService =
            project.getService(TestResultService::class.java)

        private val PASS_RE = Regex("""^(?:---\s+)?PASS[:\s]\s*(.+?)(?:\s+\([\d.]+s\))?\s*$""")
        private val FAIL_RE = Regex("""^(?:---\s+)?FAIL[:\s]\s*(.+?)(?:\s+\([\d.]+s\))?(?:[:\s\u2014-]+(.+))?\s*$""")
        private val SKIP_RE = Regex("""^(?:---\s+)?SKIP[:\s]\s*(.+?)(?:\s+\([\d.]+s\))?\s*$""")

        fun parseOutput(output: String): List<TestCaseResult> {
            val results = mutableListOf<TestCaseResult>()
            for (raw in output.lines()) {
                val line = raw.trim()
                PASS_RE.matchEntire(line)?.let {
                    results += TestCaseResult(it.groupValues[1].trim(), TestStatus.PASS)
                    return@let
                } ?: FAIL_RE.matchEntire(line)?.let {
                    results += TestCaseResult(
                        it.groupValues[1].trim(),
                        TestStatus.FAIL,
                        it.groupValues[2].trim().ifEmpty { null }
                    )
                    return@let
                } ?: SKIP_RE.matchEntire(line)?.let {
                    results += TestCaseResult(it.groupValues[1].trim(), TestStatus.SKIP)
                }
            }
            return results
        }
    }
}
