package com.gocodalone.workflow.ide.editor

import com.intellij.codeInsight.daemon.RelatedItemLineMarkerInfo
import com.intellij.codeInsight.daemon.RelatedItemLineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.util.regex.Pattern

private val PIPELINE_TAG_PATTERN: Pattern = Pattern.compile("""@pipeline:(\S+)""")
private val PIPELINE_DEF_PATTERN: Pattern = Pattern.compile("""^(\s*)(\S+):""")

/**
 * Provides gutter icons in .feature files for @pipeline:name tags,
 * linking to the pipeline definition in workflow YAML files.
 */
class PipelineLineMarkerProvider : RelatedItemLineMarkerProvider() {

    override fun collectNavigationMarkers(
        element: PsiElement,
        result: MutableCollection<in RelatedItemLineMarkerInfo<*>>
    ) {
        val file = element.containingFile ?: return
        if (!file.name.endsWith(".feature")) return

        // Only process leaf elements that are the first token on a tag line
        val text = element.text ?: return
        val matcher = PIPELINE_TAG_PATTERN.matcher(text)
        if (!matcher.find()) return

        val pipelineName = matcher.group(1)
        val targets = findPipelineDefinitions(element.project, pipelineName)
        if (targets.isEmpty()) return

        val builder = NavigationGutterIconBuilder
            .create(AllIcons.Actions.Forward)
            .setTargets(targets)
            .setTooltipText("Navigate to pipeline '$pipelineName'")

        result.add(builder.createLineMarkerInfo(element))
    }

    private fun findPipelineDefinitions(project: Project, pipelineName: String): List<PsiElement> {
        val results = mutableListOf<PsiElement>()
        val scope = GlobalSearchScope.projectScope(project)

        // Search all YAML files for a top-level key matching pipelineName
        val yamlFiles = FilenameIndex.getAllFilesByExt(project, "yaml", scope) +
            FilenameIndex.getAllFilesByExt(project, "yml", scope)

        for (vFile in yamlFiles) {
            val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vFile) ?: continue
            val match = findPipelineInFile(psiFile, pipelineName)
            if (match != null) results.add(match)
        }
        return results
    }

    private fun findPipelineInFile(psiFile: PsiFile, pipelineName: String): PsiElement? {
        val text = psiFile.text ?: return null
        // Look for lines like "  pipelineName:" at any indent (pipeline definitions are map keys)
        val lines = text.lines()
        var offset = 0
        for (line in lines) {
            val trimmed = line.trimStart()
            if (trimmed.startsWith("$pipelineName:")) {
                return psiFile.findElementAt(offset + (line.length - trimmed.length))
            }
            offset += line.length + 1 // +1 for newline
        }
        return null
    }
}
