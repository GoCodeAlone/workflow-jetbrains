import React, { useEffect, useRef, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { WorkflowEditor } from '@gocodealone/workflow-editor';
import { initBridge, sendYamlUpdated, sendNavigateToLine } from './bridge';
import '@xyflow/react/dist/style.css';

function App() {
  const [yaml, setYaml] = useState<string>('');
  const initializedRef = useRef(false);

  useEffect(() => {
    if (initializedRef.current) return;
    initializedRef.current = true;

    initBridge({
      onYamlChanged: (content) => setYaml(content),
      onCursorMoved: (_line, _col) => {
        // TODO: highlight corresponding node
      },
      onSchemasLoaded: (_schemas) => {
        // Inject into moduleSchemaStore
      },
    });
  }, []);

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
