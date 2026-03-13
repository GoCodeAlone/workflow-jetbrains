// JetBrains JCEF ↔ editor bridge
// hostBridge is injected by WorkflowBridge.kt via JBCefJSQuery

export interface BridgeCallbacks {
  onYamlChanged: (content: string) => void;
  onCursorMoved: (line: number, col: number) => void;
  onSchemasLoaded: (schemas: unknown) => void;
  onPluginSchemasLoaded?: (plugins: unknown[]) => void;
  onAIResponse?: (yaml: string) => void;
}

let callbacks: BridgeCallbacks | null = null;

export function initBridge(cb: BridgeCallbacks) {
  callbacks = cb;

  // These are called by WorkflowBridge.kt via executeJavaScript
  (window as unknown as Record<string, unknown>)['onYamlChanged'] = (content: string) => callbacks?.onYamlChanged(content);
  (window as unknown as Record<string, unknown>)['onCursorMoved'] = (line: number, col: number) => callbacks?.onCursorMoved(line, col);
  (window as unknown as Record<string, unknown>)['onSchemasLoaded'] = (schemas: unknown) => callbacks?.onSchemasLoaded(schemas);
  (window as unknown as Record<string, unknown>)['onPluginSchemasLoaded'] = (plugins: unknown[]) => callbacks?.onPluginSchemasLoaded?.(plugins);
  (window as unknown as Record<string, unknown>)['onAIResponse'] = (yaml: string) => callbacks?.onAIResponse?.(yaml);

  // Wait for hostBridge to be injected
  if ((window as unknown as Record<string, unknown>)['hostBridge']) {
    sendRequestSchemas();
  } else {
    window.addEventListener('hostBridgeReady', () => sendRequestSchemas());
  }
}

function getHostBridge(): Record<string, (arg: unknown) => void> | undefined {
  return (window as unknown as Record<string, unknown>)['hostBridge'] as Record<string, (arg: unknown) => void> | undefined;
}

export function sendYamlUpdated(content: string) {
  getHostBridge()?.['sendYamlUpdated'](content);
}

export function sendNavigateToLine(line: number, col: number) {
  getHostBridge()?.['sendNavigateToLine'](line, col);
}

export function sendRequestSchemas() {
  getHostBridge()?.['sendRequestSchemas']('');
}

export function sendAIRequest(context: { yaml: string; moduleTypes: string[]; userPrompt: string }) {
  getHostBridge()?.['sendAIRequest'](JSON.stringify(context));
}
