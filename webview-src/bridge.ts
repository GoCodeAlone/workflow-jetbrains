// JetBrains JCEF ↔ editor bridge
// hostBridge is injected by WorkflowBridge.kt via JBCefJSQuery

export interface BridgeCallbacks {
  onYamlChanged: (content: string) => void;
  onCursorMoved: (line: number, col: number) => void;
  onSchemasLoaded: (schemas: unknown) => void;
  onPluginSchemasLoaded?: (plugins: unknown[]) => void;
  onAIResponse?: (yaml: string) => void;
  /** Host → webview: navigate to node at (filePath, line). Uses line map to select node. */
  onNavigateToNode?: (filePath: string | null, line: number) => void;
  /** Host → webview: an imported file's content changed. */
  onFileChanged?: (filePath: string, content: string) => void;
}

let callbacks: BridgeCallbacks | null = null;

// Pending resolveFile requests keyed by request ID
const pendingResolveFile = new Map<string, { resolve: (content: string | null) => void }>();
let resolveFileCounter = 0;

export function initBridge(cb: BridgeCallbacks) {
  callbacks = cb;

  // These are called by WorkflowBridge.kt via executeJavaScript
  (window as unknown as Record<string, unknown>)['onYamlChanged'] = (content: string) => callbacks?.onYamlChanged(content);
  (window as unknown as Record<string, unknown>)['onCursorMoved'] = (line: number, col: number) => callbacks?.onCursorMoved(line, col);
  (window as unknown as Record<string, unknown>)['onSchemasLoaded'] = (schemas: unknown) => callbacks?.onSchemasLoaded(schemas);
  (window as unknown as Record<string, unknown>)['onPluginSchemasLoaded'] = (plugins: unknown[]) => callbacks?.onPluginSchemasLoaded?.(plugins);
  (window as unknown as Record<string, unknown>)['onAIResponse'] = (yaml: string) => callbacks?.onAIResponse?.(yaml);
  (window as unknown as Record<string, unknown>)['onResolveFileResponse'] = (requestId: string, content: string | null) => {
    const pending = pendingResolveFile.get(requestId);
    if (pending) {
      pendingResolveFile.delete(requestId);
      pending.resolve(content);
    }
  };
  (window as unknown as Record<string, unknown>)['onNavigateToNode'] = (filePath: string | null, line: number) =>
    callbacks?.onNavigateToNode?.(filePath, line);
  (window as unknown as Record<string, unknown>)['onFileChanged'] = (filePath: string, content: string) =>
    callbacks?.onFileChanged?.(filePath, content);

  // Wait for hostBridge to be injected, then signal ready
  // sendReady triggers the Kotlin readyQuery handler which sends YAML + schemas
  if ((window as unknown as Record<string, unknown>)['hostBridge']) {
    getHostBridge()?.['sendReady']('');
  } else {
    window.addEventListener('hostBridgeReady', () => {
      getHostBridge()?.['sendReady']('');
    });
  }
}

function getHostBridge(): Record<string, (arg: unknown) => void> | undefined {
  return (window as unknown as Record<string, unknown>)['hostBridge'] as Record<string, (arg: unknown) => void> | undefined;
}

export function sendYamlUpdated(content: string) {
  getHostBridge()?.['sendYamlUpdated'](content);
}

/** Navigate to a specific line in the host editor.
 *  When filePath is provided, the host will open that file before navigating.
 *  Data format: "filePath,line,col" (3 parts) or "line,col" (2 parts, backward compat). */
export function sendNavigateToLine(line: number, col: number, filePath?: string | null) {
  const data = (filePath !== undefined && filePath !== null)
    ? `${filePath},${line},${col}`
    : `${line},${col}`;
  getHostBridge()?.['sendNavigateToLine'](data);
}

export function sendRequestSchemas() {
  getHostBridge()?.['sendRequestSchemas']('');
}

export function sendAIRequest(context: { yaml: string; moduleTypes: string[]; userPrompt: string }) {
  getHostBridge()?.['sendAIRequest'](JSON.stringify(context));
}

/** Request the host to read a file relative to the open document. Returns file content or null. */
export function sendResolveFile(relativePath: string): Promise<string | null> {
  const requestId = `rf-${++resolveFileCounter}`;
  return new Promise((resolve) => {
    pendingResolveFile.set(requestId, { resolve });
    getHostBridge()?.['sendResolveFile'](JSON.stringify({ requestId, relativePath }));
    // Timeout after 5 seconds
    setTimeout(() => {
      if (pendingResolveFile.has(requestId)) {
        pendingResolveFile.delete(requestId);
        resolve(null);
      }
    }, 5000);
  });
}

/** Send multi-file save to host. fileMap keys are relative paths (null = main file). */
export function sendSaveFiles(fileMap: Map<string | null, string>) {
  const entries: Array<{ path: string | null; content: string }> = [];
  for (const [path, content] of fileMap.entries()) {
    entries.push({ path, content });
  }
  getHostBridge()?.['sendSaveFiles'](JSON.stringify(entries));
}
