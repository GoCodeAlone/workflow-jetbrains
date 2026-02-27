# Workflow Engine — JetBrains Plugin

IDE support for [Workflow Engine](https://github.com/GoCodeAlone/workflow) configuration files.

[![JetBrains Marketplace](https://img.shields.io/jetbrains/plugin/v/com.gocodalone.workflow?label=Marketplace)](https://plugins.jetbrains.com/plugin/com.gocodalone.workflow)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/com.gocodalone.workflow)](https://plugins.jetbrains.com/plugin/com.gocodalone.workflow)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## Compatibility

Works with all JetBrains IDEs: IntelliJ IDEA, GoLand, WebStorm, PyCharm, PhpStorm, Rider, CLion, RubyMine, and all other IntelliJ Platform-based products. Requires IDE version **2024.2 or later**.

This plugin targets **workflow engine v0.3.1+**. The bundled JSON schema and live templates are generated from the v0.3.1 registry.

---

## Features

### Real-time Validation (LSP)

The plugin connects to `workflow-lsp-server` over stdio using the LSP4IJ bridge. When the LSP server is running, the editor highlights:

- Unknown module types (`type: http.unknown`)
- Unknown step types and trigger types
- Missing required dependencies between modules
- Unknown configuration keys in `config:` blocks

### Autocomplete (LSP)

Context-aware completions provided by the LSP server:

- Module types (e.g., `http.server`, `http.router`, `datastore.sqlite`, `auth.jwt`)
- Step types (e.g., `step.set`, `step.http_call`, `step.http_response`)
- Trigger types (`http`, `schedule`, `event`)
- Config keys for each module and step type
- Template functions (`uuidv4`, `now`, `lower`, `default`, `json`)

### Hover Documentation (LSP)

Hovering over module names, step types, or config keys shows inline documentation including field descriptions and config tables pulled from the Workflow Engine registry.

### Live Templates

Type an abbreviation and press Tab to expand a template. Templates are available in all workflow YAML files (`workflow.yaml`, `app.yaml`, `*-workflow.yaml`, etc.).

See [Live Templates Reference](#live-templates-reference) for the full list.

### wfctl Tool Actions

The **Tools > Workflow Engine** menu provides direct access to wfctl commands without leaving the IDE. Results appear as IDE notifications. See [Tool Actions Reference](#tool-actions-reference).

### JSON Schema Validation

The plugin bundles a JSON Schema for workflow config files and automatically registers it with the IDE's YAML plugin. Schema validation activates for:

- `workflow.yaml` / `workflow.yml`
- `app.yaml` / `app.yml`
- Any `.yaml`/`.yml` file whose name contains `workflow` or `app` and whose content includes a `modules:` key

This provides structural validation (required fields, allowed values, type checks) without requiring the LSP server to be running.

### Settings UI

Configure all plugin options at **Settings > Tools > Workflow Engine**. See [Configuration](#configuration).

---

## Installation

### From JetBrains Marketplace

> Note: This plugin is not yet published to the Marketplace. Use one of the methods below until it is available.

Once published: **Settings > Plugins > Marketplace**, search for "Workflow Engine", click Install.

### From GitHub Releases

1. Download the `.zip` file from the [GitHub Releases](https://github.com/GoCodeAlone/workflow-jetbrains/releases) page.
2. In your IDE: **Settings > Plugins > gear icon (top-right) > Install Plugin from Disk...**
3. Select the downloaded `.zip` and restart the IDE.

### From Source

```sh
git clone git@github.com:GoCodeAlone/workflow-jetbrains.git
cd workflow-jetbrains
./gradlew buildPlugin
```

The plugin zip is produced at `build/distributions/`. Install it via **Settings > Plugins > Install Plugin from Disk...**.

---

## Prerequisites

| Dependency | Purpose | Install |
|---|---|---|
| JDK 17+ | Building from source only | [Adoptium](https://adoptium.net/) |
| `wfctl` | Tool actions (validate, run, inspect, etc.) | `go install github.com/GoCodeAlone/workflow/cmd/wfctl@v0.3.1` |
| `workflow-lsp-server` | LSP features (autocomplete, hover, diagnostics) | `go install github.com/GoCodeAlone/workflow/cmd/workflow-lsp-server@v0.3.1` |
| YAML plugin | YAML editing support | Bundled with most JetBrains IDEs |
| LSP4IJ plugin | Enhanced LSP client bridge | Optional — install from Marketplace if LSP features are not working |

After installing `wfctl` and `workflow-lsp-server`, confirm they are on your `PATH`:

```sh
wfctl version
workflow-lsp-server --version
```

If the binaries are not on `PATH`, configure their absolute paths in **Settings > Tools > Workflow Engine**.

---

## Configuration

Navigate to **Settings > Tools > Workflow Engine**.

| Setting | Default | Description |
|---|---|---|
| wfctl path | _(from PATH)_ | Absolute path to the `wfctl` binary. Leave blank to resolve from system PATH. |
| LSP server path | _(from PATH)_ | Absolute path to `workflow-lsp-server`. Leave blank to resolve from system PATH. |
| Enable LSP server integration | Enabled | Turns on hover documentation, diagnostics, and autocomplete via the LSP server. |
| MCP server path | _(from PATH)_ | Absolute path to `workflow-mcp-server`. |
| Auto-register MCP server with AI Assistant | Disabled | Registers the Workflow MCP server with the IDE's built-in AI assistant for context-aware help. |

---

## Live Templates Reference

All templates are available in workflow YAML files. Type the abbreviation and press Tab.

| Abbreviation | Description |
|---|---|
| `wf:app` | Complete app scaffold — modules, pipeline, and workflow in one block |
| `wf:module` | Generic module declaration with `type` and `config` |
| `wf:module:http-server` | HTTP server module (`http.server`) with `port` and `host` |
| `wf:module:router` | HTTP router module (`http.router`) linked to a server |
| `wf:module:sqlite` | SQLite datastore module (`datastore.sqlite`) |
| `wf:module:jwt` | JWT auth module (`auth.jwt`) with `secret` and `expiry` |
| `wf:pipeline` | Pipeline declaration with a single step |
| `wf:step:set` | Set-variable step (`step.set`) with a `values` map |
| `wf:step:http-call` | Outbound HTTP call step (`step.http_call`) |
| `wf:step:json-response` | JSON HTTP response step (`step.http_response`) |
| `wf:trigger:http` | Inline HTTP trigger with `path` and `method` |
| `wf:trigger:schedule` | Cron schedule trigger |
| `wf:trigger:event` | Event/messaging trigger with `topic` |
| `wf:workflow:http` | HTTP-triggered workflow referencing a pipeline |
| `wf:workflow:messaging` | Event-triggered workflow referencing a pipeline |
| `wf:workflow:statemachine` | State machine workflow with states, transitions, and trigger |
| `wf:requires` | Plugin/module dependency declaration (`requires:` block) |

---

## Tool Actions Reference

All actions are accessible from **Tools > Workflow Engine** in the menu bar. Most actions operate on the currently open file.

| Action | Menu Item | Description |
|---|---|---|
| Validate Config | Tools > Workflow Engine > Validate Config | Runs `wfctl template validate --config <file>` and shows pass/fail in an IDE notification |
| Inspect Dependencies | Tools > Workflow Engine > Inspect Dependencies | Runs `wfctl inspect -deps <file>` and displays the module dependency graph |
| Init from Template... | Tools > Workflow Engine > Init from Template... | Opens a template picker and runs `wfctl template init --template <name> --output <dir>` to scaffold a new config |
| Run Workflow | Tools > Workflow Engine > Run Workflow | Runs `wfctl run -config <file>` in the integrated terminal |
| Show Schema | Tools > Workflow Engine > Show Schema | Runs `wfctl schema` to print the full workflow config JSON schema |
| Template Validate | Tools > Workflow Engine > Template Validate | Alias for Validate Config; can be assigned a separate keyboard shortcut |

Available templates for "Init from Template..." are: `http-api`, `messaging`, `statemachine`, `microservice`, `ecommerce`.

---

## Development

### Prerequisites

- JDK 17 or later
- Gradle (the Gradle wrapper `./gradlew` is included — no separate install required)

### Build

```sh
./gradlew buildPlugin
```

The plugin zip is produced at `build/distributions/`.

### Run in Sandbox IDE

Launches a sandboxed instance of IntelliJ IDEA Community with the plugin installed:

```sh
./gradlew runIde
```

### Run Tests

```sh
./gradlew test
```

### Verify Plugin Compatibility

Runs the JetBrains Plugin Verifier against a set of recommended IDE versions:

```sh
./gradlew verifyPlugin
```

### Sign Plugin

Signing requires environment variables set (see [CI/CD secrets](#cicd-secrets)):

```sh
CERTIFICATE_CHAIN=... PRIVATE_KEY=... PRIVATE_KEY_PASSWORD=... ./gradlew signPlugin
```

---

## Publishing to JetBrains Marketplace

### First-Time Setup

1. Create a vendor account at https://plugins.jetbrains.com/author/me
2. Go to https://plugins.jetbrains.com/plugin/add and upload the zip from `build/distributions/`.
3. The plugin ID must match the `id` in `plugin.xml`: **`com.gocodalone.workflow`**.
4. JetBrains reviews new plugins (typically 1-3 business days). Updates are usually auto-approved.

After the first upload, the plugin is accessible at:
`https://plugins.jetbrains.com/plugin/com.gocodalone.workflow`

### Generate a Permanent Token

1. Go to https://plugins.jetbrains.com/author/me/tokens
2. Create a new permanent token.
3. Save it — this is the `JETBRAINS_MARKETPLACE_TOKEN` used for publishing.

### Publish via CLI

```sh
./gradlew publishPlugin -Dorg.gradle.project.intellijPublishToken=<TOKEN>
```

### CI/CD via GitHub Actions

The `release.yml` workflow handles publishing automatically when a `v*` tag is pushed:

```sh
git tag v0.2.0
git push origin v0.2.0
```

The workflow:
1. Builds and tests the plugin
2. Verifies compatibility
3. Signs the plugin
4. Publishes to JetBrains Marketplace
5. Creates a GitHub Release with the zip attached

#### CI/CD Secrets

Add these secrets to the GitHub repository under **Settings > Secrets and variables > Actions**:

| Secret | Description |
|---|---|
| `JETBRAINS_MARKETPLACE_TOKEN` | Permanent token from https://plugins.jetbrains.com/author/me/tokens |
| `CERTIFICATE_CHAIN` | Plugin signing certificate chain (PEM format) |
| `PRIVATE_KEY` | Plugin signing private key (PEM format) |
| `PRIVATE_KEY_PASSWORD` | Password for the signing private key |

Signing is optional but recommended — signed plugins display a verified badge on the Marketplace. Without signing secrets, remove the `signPlugin` step from `release.yml`.

---

## License

MIT — see [LICENSE](LICENSE).
