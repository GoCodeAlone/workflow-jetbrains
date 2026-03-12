import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowEditor } from '@gocodealone/workflow-editor';
import { useModuleSchemaStore, useWorkflowStore } from '@gocodealone/workflow-editor/stores';
import { buildYamlLineMap } from '@gocodealone/workflow-editor/utils';
import { initBridge, sendYamlUpdated, sendNavigateToLine } from './bridge';
import '@xyflow/react/dist/style.css';

function App() {
  const [yaml, setYaml] = useState<string>('');
  const initializedRef = useRef(false);
  const yamlRef = useRef<string>('');

  const loadSchemas = useModuleSchemaStore((s) => s.loadSchemas);
  const setHighlightedNode = useWorkflowStore((s) => s.setHighlightedNode);

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
    });
  }, [loadSchemas, setHighlightedNode]);

  return (
    <WorkflowEditor
      initialYaml={yaml}
      onChange={(newYaml) => sendYamlUpdated(newYaml)}
      onSave={async (newYaml) => sendYamlUpdated(newYaml)}
      onNavigateToSource={(line, col) => sendNavigateToLine(line, col)}
    />
  );
}

createRoot(document.getElementById('root')!).render(<App />);
