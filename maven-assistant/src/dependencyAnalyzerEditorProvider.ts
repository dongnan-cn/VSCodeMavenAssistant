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
            } else if (msg.type === 'showContextMenu') {
                // 处理右键菜单请求
                await this.handleContextMenu(msg.data);
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

    /**
     * 处理右键菜单请求
     * @param data 菜单数据，包含节点信息和路径信息
     */
    private async handleContextMenu(data: any) {
        try {
            const { node, pathIndex, nodeIndex, pathInfo } = data;

            // 步骤1修正：根据nodeIndex动态获取父依赖GAV
            const parentIndex = nodeIndex + 1;
            let parent = null;
            if (pathInfo && pathInfo.length > parentIndex) {
                parent = pathInfo[parentIndex];
                // 有父依赖，后续实现
                console.log('有父依赖，应该在本地仓库中查找父依赖pom.xml');
            } else {
                // 依赖链顶端，当前项目pom.xml
                console.log('当前为依赖链顶端，应该在当前项目pom.xml中查找依赖声明');
                // Step: 在当前项目pom.xml中查找依赖声明
                const pomFiles = await vscode.workspace.findFiles('pom.xml', undefined, 1);
                if (pomFiles.length === 0) {
                    vscode.window.showErrorMessage('未找到当前项目的 pom.xml 文件');
                    return;
                }
                const pomUri = pomFiles[0];
                const document = await vscode.workspace.openTextDocument(pomUri);
                const editor = await vscode.window.showTextDocument(document);
                const text = document.getText();
                // 精确定位artifactId标签
                const dependencyBlockPattern = /<dependency>[\s\S]*?<\/dependency>/g;
                let match: RegExpExecArray | null;
                let found = false;
                while ((match = dependencyBlockPattern.exec(text))) {
                    const block = match[0];
                    if (
                        block.includes(`<groupId>${node.groupId}</groupId>`) &&
                        block.includes(`<artifactId>${node.artifactId}</artifactId>`)
                    ) {
                        const artifactTag = `<artifactId>${node.artifactId}</artifactId>`;
                        const relIndex = block.indexOf(artifactTag);
                        if (relIndex !== -1) {
                            const absIndex = match.index + relIndex;
                            const position = document.positionAt(absIndex);
                            editor.selection = new vscode.Selection(position, position);
                            editor.revealRange(new vscode.Range(position, position));
                            vscode.window.showInformationMessage(`已跳转到 ${node.groupId}:${node.artifactId} 的声明位置`);
                            found = true;
                            break;
                        }
                    }
                }
                if (!found) {
                    vscode.window.showWarningMessage(`未在当前项目 pom.xml 中找到 ${node.groupId}:${node.artifactId} 的声明`);
                }
                return;
            }
            
            // 右键菜单选项逻辑保持不变
            const selected = await vscode.window.showQuickPick([
                {
                    label: '$(file-code) 跳转到 pom.xml',
                    description: `查找 ${node.groupId}:${node.artifactId} 的声明`,
                    value: 'goto-pom'
                },
                {
                    label: '$(exclude) 排除此依赖',
                    description: `从依赖树中排除 ${node.groupId}:${node.artifactId}`,
                    value: 'exclude'
                }
            ], {
                placeHolder: '选择操作...'
            });
            
            if (!selected) {
                return; // 用户取消了选择
            }
            
            switch (selected.value) {
                case 'goto-pom':
                    await this.gotoPomXml(node);
                    break;
                case 'exclude':
                    await this.excludeDependency(node);
                    break;
            }
            
        } catch (error) {
            vscode.window.showErrorMessage(`处理右键菜单失败: ${error}`);
        }
    }
    
    /**
     * 跳转到 pom.xml 文件
     */
    // 异步方法，用于跳转到 pom.xml 文件中指定依赖声明的位置
    private async gotoPomXml(node: any) {
        try {
            // 查找当前工作区的 pom.xml 文件
            const pomFiles = await vscode.workspace.findFiles('**/pom.xml');
            
            if (pomFiles.length === 0) {
                // 如果未找到 pom.xml 文件，则显示错误信息
                vscode.window.showErrorMessage('未找到 pom.xml 文件');
                return;
            }
            
            // 打开第一个找到的 pom.xml 文件
            const pomUri = pomFiles[0];
            const document = await vscode.workspace.openTextDocument(pomUri);
            const editor = await vscode.window.showTextDocument(document);
            
            // 查找依赖声明的位置
            const text = document.getText();
            const dependencyPattern = new RegExp(
                `<dependency>\\s*<groupId>${node.groupId}</groupId>\\s*<artifactId>${node.artifactId}</artifactId>`,
                'g'
            );
            
            const match = dependencyPattern.exec(text);
            if (match) {
                // 如果找到依赖声明，则跳转到该位置
                const position = document.positionAt(match.index);
                editor.selection = new vscode.Selection(position, position);
                editor.revealRange(new vscode.Range(position, position));
                vscode.window.showInformationMessage(`已跳转到 ${node.groupId}:${node.artifactId} 的声明位置`);
            } else {
                // 如果未找到依赖声明，则显示警告信息
                vscode.window.showWarningMessage(`未在 pom.xml 中找到 ${node.groupId}:${node.artifactId} 的声明`);
            }
            
        } catch (error) {
            // 如果跳转失败，则显示错误信息
            console.error('跳转到 pom.xml 失败:', error);
            vscode.window.showErrorMessage(`跳转到 pom.xml 失败: ${error}`);
        }
    }
    
    /**
     * 排除依赖（暂时只显示消息）
     */
    private async excludeDependency(node: any) {
        vscode.window.showInformationMessage(`排除依赖功能正在开发中: ${node.groupId}:${node.artifactId}`);
    }
} 