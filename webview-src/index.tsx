import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowEditor } from '@gocodealone/workflow-editor';
import { useModuleSchemaStore, useWorkflowStore } from '@gocodealone/workflow-editor/stores';
import { buildYamlLineMap, parseYamlSafe } from '@gocodealone/workflow-editor/utils';
import { initBridge, sendYamlUpdated, sendNavigateToLine, sendAIRequest, sendResolveFile, sendSaveFiles } from './bridge';
import '@xyflow/react/dist/style.css';

function App() {
  const [yaml, setYaml] = useState<string>('');
  const initializedRef = useRef(false);
  const yamlRef = useRef<string>('');
  const fromHostRef = useRef(false);

  const loadSchemas = useModuleSchemaStore((s) => s.loadSchemas);
  const loadPluginSchemas = useModuleSchemaStore((s) => s.loadPluginSchemas);
  const setHighlightedNode = useWorkflowStore((s) => s.setHighlightedNode);
  const setSelectedNode = useWorkflowStore((s) => s.setSelectedNode);
  const importFromConfig = useWorkflowStore((s) => s.importFromConfig);
  const addToast = useWorkflowStore((s) => s.addToast);

  // Bidirectional sync: store changes → YAML → host is handled by onChange prop on WorkflowEditor.
  // fromHostRef prevents echo loops when host sends YAML that triggers store updates.

  useEffect(() => {
    if (initializedRef.current) return;
    initializedRef.current = true;

    initBridge({
      onYamlChanged: (content) => {
        yamlRef.current = content;
        setYaml(content);
        // Import directly into the store so nodes update
        fromHostRef.current = true;
        const { config, error } = parseYamlSafe(content);
        if (error) {
          addToast(`YAML parse error: ${error}`, 'error');
        } else {
          importFromConfig(config);
        }
        fromHostRef.current = false;
      },
      onCursorMoved: (line, _col) => {
        const lineMap = buildYamlLineMap(yamlRef.current);
        let found: string | null = null;
        for (const [nodeId, range] of Object.entries(lineMap)) {
          if (line >= range.startLine && line <= range.endLine) {
            found = nodeId;
            break;
          }
        }
        setHighlightedNode(found);
      },
      onSchemasLoaded: (schemas) => {
        if (schemas && typeof schemas === 'object') {
          loadSchemas(schemas as Parameters<typeof loadSchemas>[0]);
        }
      },
      onPluginSchemasLoaded: (plugins) => {
        if (Array.isArray(plugins)) {
          loadPluginSchemas(plugins as Parameters<typeof loadPluginSchemas>[0]);
        }
      },
      onAIResponse: (content) => {
        const { config, error } = parseYamlSafe(content);
        if (error) {
          addToast(`AI response parse error: ${error}`, 'error');
        } else {
          importFromConfig(config);
          sendYamlUpdated(content);
          addToast('AI design applied', 'success');
        }
      },
      onNavigateToNode: (_filePath, line) => {
        // Use the current merged YAML line map to find and select the node at this line
        const lineMap = buildYamlLineMap(yamlRef.current);
        for (const [nodeName, range] of Object.entries(lineMap)) {
          if (line >= range.startLine && line <= range.endLine) {
            const nodes = useWorkflowStore.getState().nodes;
            const node = nodes.find((n) => (n.data?.label as string) === nodeName);
            if (node) setSelectedNode(node.id);
            break;
          }
        }
      },
      onFileChanged: (_filePath, _content) => {
        // Host has re-sent the merged YAML via yamlChanged when it detected this file change.
        // No additional action needed in the webview.
      },
    });
  }, [loadSchemas, loadPluginSchemas, setHighlightedNode, setSelectedNode, importFromConfig, addToast]);

  return (
    <WorkflowEditor
      initialYaml={yaml}
      embedded
      onChange={(newYaml) => {
        if (fromHostRef.current) return;
        yamlRef.current = newYaml;
        sendYamlUpdated(newYaml);
      }}
      onSave={async (newYaml, fileMap) => {
        if (fileMap && fileMap.size > 0) {
          sendSaveFiles(fileMap);
        } else {
          sendYamlUpdated(newYaml);
        }
      }}
      onNavigateToSource={(...args: [number, number] | [string | null, number, number]) => {
        if (typeof args[0] === 'string' || args[0] === null) {
          sendNavigateToLine(args[1], args[2], args[0]);
        } else {
          sendNavigateToLine(args[0], args[1]);
        }
      }}
      onResolveFile={(relativePath) => sendResolveFile(relativePath)}
      onAIRequest={(context) => sendAIRequest(context)}
    />
  );
}

createRoot(document.getElementById('root')!).render(<App />);
