import * as vscode from 'vscode';

/**
 * Dependency Analyzer 自定义Webview编辑器Provider
 * 只读，内容为Hello Dependency Analyzer
 */
export class DependencyAnalyzerEditorProvider implements vscode.CustomReadonlyEditorProvider {
    public static readonly viewType = 'maven-assistant.dependencyAnalyzer';

    constructor(private readonly context: vscode.ExtensionContext) {}

    async openCustomDocument(
        uri: vscode.Uri,
        openContext: vscode.CustomDocumentOpenContext,
        token: vscode.CancellationToken
    ): Promise<vscode.CustomDocument> {
        return { uri, dispose: () => {} };
    }

    async resolveCustomEditor(
        document: vscode.CustomDocument,
        webviewPanel: vscode.WebviewPanel,
        token: vscode.CancellationToken
    ): Promise<void> {
        webviewPanel.webview.options = {
            enableScripts: true,
        };
        webviewPanel.webview.html = this.getHtml();
    }

    private getHtml(): string {
        return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>Dependency Analyzer</title>
    <style>
        body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); margin: 0; padding: 40px; }
        h1 { color: var(--vscode-editor-foreground); }
        .center { text-align: center; margin-top: 100px; }
    </style>
</head>
<body>
    <div class="center">
        <h1>Hello Dependency Analyzer 👋</h1>
        <p>这是自定义Webview编辑器，下一步将集成完整插件功能。</p>
    </div>
</body>
</html>`;
    }
} 