import * as vscode from 'vscode';
import { LspClient } from './lspClient';

/**
 * Dependency Analyzer 自定义Webview编辑器Provider
 * 只读，内容为依赖分析视图，支持手动刷新
 */
export class DependencyAnalyzerEditorProvider implements vscode.CustomReadonlyEditorProvider {
    public static readonly viewType = 'maven-assistant.dependencyAnalyzer';

    constructor(
        private readonly context: vscode.ExtensionContext,
        private readonly lspClient: LspClient
    ) {}

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

        // 定义渲染分析内容的函数
        const renderAnalysis = (analysis: string) => {
            webviewPanel.webview.postMessage({ type: 'updateAnalysis', html: this.renderAnalysisHtml(analysis) });
        };

        // 初次加载时获取依赖分析内容
        const analysis = await this.lspClient.analyzeDependencies();
        webviewPanel.webview.html = this.getHtml();
        // Webview 加载后主动推送内容
        setTimeout(() => renderAnalysis(analysis), 100);

        // 监听Webview消息，实现刷新功能
        webviewPanel.webview.onDidReceiveMessage(async (msg) => {
            if (msg.type === 'refresh') {
                // 调用LSP后端获取最新依赖分析数据
                const newAnalysis = await this.lspClient.analyzeDependencies();
                renderAnalysis(newAnalysis);
            }
        });
    }

    /**
     * 生成Webview HTML，包含刷新按钮和内容区域
     */
    private getHtml(): string {
        return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <title>Dependency Analyzer</title>
    <style>
        body { font-family: var(--vscode-font-family); color: var(--vscode-foreground); background: var(--vscode-editor-background); margin: 0; padding: 40px; }
        h2 { color: var(--vscode-editor-foreground); }
        .toolbar { display: flex; align-items: center; margin-bottom: 16px; }
        .toolbar button { margin-left: 8px; }
        #analyzer-content { margin-top: 16px; }
    </style>
</head>
<body>
    <div class="toolbar">
        <h2 style="flex:1;">依赖分析结果</h2>
        <button id="refresh-btn">刷新</button>
    </div>
    <div id="analyzer-content">
        <p>正在加载依赖分析...</p>
    </div>
    <script>
        // 获取VSCode API
        const vscode = acquireVsCodeApi();
        // 刷新按钮点击事件
        document.getElementById('refresh-btn').onclick = function() {
            vscode.postMessage({ type: 'refresh' });
        };
        // 监听扩展端发来的消息，更新分析内容
        window.addEventListener('message', event => {
            const msg = event.data;
            if (msg.type === 'updateAnalysis') {
                document.getElementById('analyzer-content').innerHTML = msg.html;
            }
        });
    </script>
</body>
</html>`;
    }

    /**
     * 渲染依赖分析内容为HTML
     * @param analysis 依赖分析字符串
     */
    private renderAnalysisHtml(analysis: string): string {
        // 这里可以根据实际格式美化内容
        return `<pre style="white-space:pre-wrap;">${analysis ? analysis : '暂无依赖分析结果'}</pre>`;
    }
} 