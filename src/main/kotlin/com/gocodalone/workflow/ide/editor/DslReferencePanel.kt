package com.gocodalone.workflow.ide.editor

import com.intellij.openapi.diagnostic.logger

private val LOG = logger<DslReferencePanel>()

data class FieldDoc(val name: String, val type: String, val description: String)

data class DslSection(
    val id: String,
    val title: String,
    val description: String,
    val requiredFields: List<FieldDoc>,
    val optionalFields: List<FieldDoc>,
    val example: String,
    val relationships: List<String>,
    val parent: String? = null,
)

object DslReferencePanel {

    fun buildHtml(sections: List<DslSection>): String {
        val sectionsHtml = if (sections.isEmpty()) {
            "<p class=\"empty\">No reference available.</p>"
        } else {
            sections.joinToString("\n") { renderSection(it) }
        }

        return """<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <style>
    body { font-family: system-ui, sans-serif; font-size: 13px; padding: 8px; margin: 0; background: #1e1e1e; color: #d4d4d4; }
    h1 { font-size: 1.1em; margin: 0 0 12px; padding-bottom: 6px; border-bottom: 1px solid #3e3e3e; }
    details { margin-bottom: 8px; }
    summary { cursor: pointer; font-weight: 600; padding: 4px 6px; border-radius: 3px; list-style: none; display: flex; align-items: center; gap: 6px; }
    summary:hover { background: #2a2d2e; }
    summary::before { content: '▶'; font-size: 0.7em; }
    details[open] > summary::before { content: '▼'; }
    .section-body { padding: 6px 6px 6px 16px; }
    .description { margin-bottom: 8px; line-height: 1.5; }
    table { width: 100%; border-collapse: collapse; font-size: 0.9em; margin-bottom: 8px; }
    th { text-align: left; padding: 3px 6px; background: #2d2d2d; font-size: 0.85em; }
    td { padding: 3px 6px; border-top: 1px solid #3e3e3e; vertical-align: top; }
    .field-name { font-family: monospace; color: #9cdcfe; }
    .field-type { font-family: monospace; color: #4ec9b0; font-size: 0.85em; }
    pre { background: #2d2d2d; padding: 8px; border-radius: 3px; overflow-x: auto; font-size: 0.85em; margin: 0 0 8px; }
    ul { margin: 4px 0; padding-left: 18px; }
    li { margin-bottom: 3px; }
    .empty { color: #888; font-style: italic; }
  </style>
</head>
<body>
  <h1>Workflow DSL Reference</h1>
  $sectionsHtml
</body>
</html>"""
    }

    private fun renderSection(s: DslSection): String {
        val requiredTable = if (s.requiredFields.isNotEmpty()) {
            """<table><tr><th>Field</th><th>Type</th><th>Description</th></tr>${
                s.requiredFields.joinToString("") { f ->
                    "<tr><td class=\"field-name\">${esc(f.name)}</td><td class=\"field-type\">${esc(f.type)}</td><td>${esc(f.description)}</td></tr>"
                }
            }</table>"""
        } else ""

        val optionalTable = if (s.optionalFields.isNotEmpty()) {
            """<table><tr><th>Field (optional)</th><th>Type</th><th>Description</th></tr>${
                s.optionalFields.joinToString("") { f ->
                    "<tr><td class=\"field-name\">${esc(f.name)}</td><td class=\"field-type\">${esc(f.type)}</td><td>${esc(f.description)}</td></tr>"
                }
            }</table>"""
        } else ""

        val exampleBlock = if (s.example.isNotBlank()) "<pre>${esc(s.example)}</pre>" else ""

        val relsList = if (s.relationships.isNotEmpty()) {
            "<ul>${s.relationships.joinToString("") { "<li>${esc(it)}</li>" }}</ul>"
        } else ""

        return """<details>
  <summary>${esc(s.title)}</summary>
  <div class="section-body">
    <p class="description">${esc(s.description)}</p>
    $requiredTable
    $optionalTable
    $exampleBlock
    $relsList
  </div>
</details>"""
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    fun loadSections(): List<DslSection> {
        return try {
            val stream = DslReferencePanel::class.java.getResourceAsStream("/dsl-reference.json")
                ?: return emptyList()
            val text = stream.bufferedReader().readText()
            parseSections(text)
        } catch (e: Exception) {
            LOG.warn("Could not load dsl-reference.json", e)
            emptyList()
        }
    }

    private fun parseSections(json: String): List<DslSection> {
        // Minimal JSON parsing without external dependencies
        // Uses org.json or manual parsing — use the built-in Gson from IntelliJ platform
        return try {
            val gson = com.google.gson.Gson()
            val root = gson.fromJson(json, com.google.gson.JsonObject::class.java)
            val sectionsArray = root.getAsJsonArray("sections") ?: return emptyList()
            sectionsArray.map { elem ->
                val obj = elem.asJsonObject
                DslSection(
                    id = obj.get("id")?.asString ?: "",
                    title = obj.get("title")?.asString ?: "",
                    description = obj.get("description")?.asString ?: "",
                    requiredFields = parseFields(obj, "requiredFields"),
                    optionalFields = parseFields(obj, "optionalFields"),
                    example = obj.get("example")?.asString ?: "",
                    relationships = obj.getAsJsonArray("relationships")?.map { it.asString } ?: emptyList(),
                    parent = obj.get("parent")?.asString,
                )
            }
        } catch (e: Exception) {
            LOG.warn("Failed to parse dsl-reference.json", e)
            emptyList()
        }
    }

    private fun parseFields(obj: com.google.gson.JsonObject, key: String): List<FieldDoc> {
        return obj.getAsJsonArray(key)?.map { elem ->
            val f = elem.asJsonObject
            FieldDoc(
                name = f.get("name")?.asString ?: "",
                type = f.get("type")?.asString ?: "",
                description = f.get("description")?.asString ?: "",
            )
        } ?: emptyList()
    }
}
