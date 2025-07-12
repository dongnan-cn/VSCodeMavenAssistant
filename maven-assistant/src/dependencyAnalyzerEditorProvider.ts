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
        // 允许Webview加载本地资源和脚本
        webviewPanel.webview.options = {
            enableScripts: true,
            localResourceRoots: [
                vscode.Uri.joinPath(this.context.extensionUri, 'webview-ui', 'dist')
            ]
        };

        // 获取Vue构建产物index.html的内容
        const html = await this.getVueWebviewHtml(webviewPanel.webview);
        webviewPanel.webview.html = html;

        // 定义推送依赖分析数据的函数
        const postDependencyData = async () => {
            try {
                const analysis = await this.lspClient.analyzeDependencies();
                webviewPanel.webview.postMessage({ type: 'updateAnalysis', data: analysis });
            } catch (e) {
                webviewPanel.webview.postMessage({ type: 'error', message: String(e) });
            }
        };

        // Webview加载后，主动推送依赖数据
        setTimeout(() => postDependencyData(), 100);

        // 监听Webview消息，实现刷新等功能
        webviewPanel.webview.onDidReceiveMessage(async (msg) => {
            if (msg.type === 'refresh') {
                await postDependencyData();
            }
            // 可扩展：处理节点点击、详情等
        });
    }

    /**
     * 读取Vue构建产物index.html，并修正静态资源路径，适配VSCode Webview
     */
    private async getVueWebviewHtml(webview: vscode.Webview): Promise<string> {
        // 获取dist目录下的index.html
        const distFolder = vscode.Uri.joinPath(this.context.extensionUri, 'webview-ui', 'dist');
        const indexHtmlUri = vscode.Uri.joinPath(distFolder, 'index.html');
        let html = (await vscode.workspace.fs.readFile(indexHtmlUri)).toString();

        // 更宽松的正则，兼容crossorigin等属性
        html = html.replace(/<script[^>]*type="module"[^>]*src="([^"]+)"[^>]*><\/script>/g, (match, src) => {
            let realSrc = src.replace(/^\//, '').replace(/^\.\//, '');
            const scriptUri = webview.asWebviewUri(vscode.Uri.joinPath(distFolder, realSrc));
            return match.replace(src, scriptUri.toString());
        });
        html = html.replace(/<link[^>]*rel="stylesheet"[^>]*href="([^"]+)"[^>]*>/g, (match, href) => {
            let realHref = href.replace(/^\//, '').replace(/^\.\//, '');
            const styleUri = webview.asWebviewUri(vscode.Uri.joinPath(distFolder, realHref));
            return match.replace(href, styleUri.toString());
        });
        return html;
    }
} 