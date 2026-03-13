import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowEditor } from '@gocodealone/workflow-editor';
import { useModuleSchemaStore, useWorkflowStore } from '@gocodealone/workflow-editor/stores';
import { buildYamlLineMap, parseYamlSafe } from '@gocodealone/workflow-editor/utils';
import { initBridge, sendYamlUpdated, sendNavigateToLine, sendAIRequest } from './bridge';
import '@xyflow/react/dist/style.css';

function App() {
  const [yaml, setYaml] = useState<string>('');
  const initializedRef = useRef(false);
  const yamlRef = useRef<string>('');

  const loadSchemas = useModuleSchemaStore((s) => s.loadSchemas);
  const loadPluginSchemas = useModuleSchemaStore((s) => s.loadPluginSchemas);
  const setHighlightedNode = useWorkflowStore((s) => s.setHighlightedNode);
  const importFromConfig = useWorkflowStore((s) => s.importFromConfig);
  const addToast = useWorkflowStore((s) => s.addToast);

  useEffect(() => {
    if (initializedRef.current) return;
    initializedRef.current = true;

    initBridge({
      onYamlChanged: (content) => {
        yamlRef.current = content;
        setYaml(content);
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
    });
  }, [loadSchemas, loadPluginSchemas, setHighlightedNode, importFromConfig, addToast]);

  return (
    <WorkflowEditor
      initialYaml={yaml}
      embedded
      onChange={(newYaml) => sendYamlUpdated(newYaml)}
      onSave={async (newYaml) => sendYamlUpdated(newYaml)}
      onNavigateToSource={(line, col) => sendNavigateToLine(line, col)}
      onAIRequest={(context) => sendAIRequest(context)}
    />
  );
}

createRoot(document.getElementById('root')!).render(<App />);
