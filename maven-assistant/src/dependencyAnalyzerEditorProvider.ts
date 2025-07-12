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
        body { 
            font-family: var(--vscode-font-family); 
            color: var(--vscode-foreground); 
            background: var(--vscode-editor-background); 
            margin: 0; 
            padding: 40px; 
        }
        h2 { color: var(--vscode-editor-foreground); }
        .toolbar { display: flex; align-items: center; margin-bottom: 16px; }
        .toolbar button { margin-left: 8px; }
        #analyzer-content { 
            margin-top: 16px; 
            font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
            font-size: 12px;
            line-height: 1.4;
        }
        .dependency-tree {
            white-space: pre-wrap;
            color: var(--vscode-editor-foreground);
        }
        .error {
            color: var(--vscode-errorForeground);
        }
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
        try {
            // 尝试解析 JSON
            const dependencyTree = JSON.parse(analysis);
            return this.formatDependencyTree(dependencyTree);
        } catch (error) {
            // 如果解析失败，显示详细的错误信息
            const preview = analysis.length > 100 ? analysis.substring(0, 100) + '...' : analysis;
            return `<div class="error">
                <h3>解析失败</h3>
                <p><strong>错误信息:</strong> ${error}</p>
                <p><strong>响应预览:</strong></p>
                <pre style="background: var(--vscode-textBlockQuote-background); padding: 8px; border-radius: 4px;">${preview}</pre>
                <p><strong>完整响应:</strong></p>
                <pre style="white-space:pre-wrap; background: var(--vscode-textBlockQuote-background); padding: 8px; border-radius: 4px;">${analysis}</pre>
            </div>`;
        }
    }

    /**
     * 格式化依赖树为可读的文本格式
     * @param node 依赖节点
     * @param indent 缩进级别
     */
    private formatDependencyTree(node: any, indent: number = 0): string {
        if (node && !node.groupId && Array.isArray(node.children)) {
            // 兼容根节点是 { children: [...] } 的情况
            return node.children.map((child: any) => this.formatDependencyTree(child, indent)).join('');
        }
        if (!node || !node.groupId) {
            return '';
        }
        const indentStr = '&nbsp;&nbsp;'.repeat(indent);
        const gav = `${node.groupId}:${node.artifactId}:${node.version}`;
        const scope = node.scope || 'compile';
        const status = node.droppedByConflict ? 'DROPPED' : 'USED';
        const childrenCount = node.children ? node.children.length : 0;
        const statusColor = node.droppedByConflict ? 'var(--vscode-errorForeground)' : 'var(--vscode-textPreformat-foreground)';
        let result = `<div style="margin: 0; padding: 0;">${indentStr}${gav} [scope: ${scope}] <span style="color: ${statusColor};">[${status}]</span>  [children: ${childrenCount}]</div>`;
        if (node.children && node.children.length > 0) {
            for (const child of node.children) {
                result += this.formatDependencyTree(child, indent + 1);
            }
        }
        return result;
    }
} 