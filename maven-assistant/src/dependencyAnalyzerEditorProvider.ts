import * as vscode from 'vscode';
import { LspClient } from './lspClient';

/**
 * Dependency Assistant 自定义Webview编辑器Provider
 * 只读，内容为依赖分析视图，支持手动刷新
 */
export class DependencyAnalyzerEditorProvider implements vscode.CustomReadonlyEditorProvider {
    public static readonly viewType = 'maven-assistant.dependencyAnalyzer';
    private currentWebviewPanel?: vscode.WebviewPanel; // 保存当前webview引用

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
        // 保存webviewPanel引用
        this.currentWebviewPanel = webviewPanel;
        
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
            } else if (msg.type === 'getConflictDependencies') {
                // 处理获取冲突依赖请求 - 复用现有依赖树数据
                try {
                    console.log('[DependencyAnalyzer] Handling getConflictDependencies request');
                    // 获取依赖树数据
                    const analysis = await this.lspClient.analyzeDependencies();
                    const dependencyTree = JSON.parse(analysis);
                    
                    // 发送依赖树数据给前端进行冲突分析
                    webviewPanel.webview.postMessage({ 
                        type: 'dependencyTreeForConflicts', 
                        data: dependencyTree 
                    });
                } catch (error) {
                    console.error('[DependencyAnalyzer] Failed to get dependency data:', error);
                    webviewPanel.webview.postMessage({ 
                        type: 'error', 
                        message: `Failed to get dependency data: ${error}` 
                    });
                }
            }
            // 可扩展：处理节点点击、详情等
        });
        
        // 监听webviewPanel关闭事件，清理引用
        webviewPanel.onDidDispose(() => {
            this.currentWebviewPanel = undefined;
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
     * 在指定 pom.xml 文件中查找依赖声明并跳转到 <artifactId> 标签
     */
    private async jumpToDependencyInPom(pomUri: vscode.Uri, groupId: string, artifactId: string): Promise<boolean> {
        const document = await vscode.workspace.openTextDocument(pomUri);
        const editor = await vscode.window.showTextDocument(document);
        const text = document.getText();
        const dependencyBlockPattern = /<dependency>[\s\S]*?<\/dependency>/g;
        let match: RegExpExecArray | null;
        let found = false;
        while ((match = dependencyBlockPattern.exec(text))) {
            const block = match[0];
            // 检查是否包含目标的groupId和artifactId
            if (
                block.includes(`<groupId>${groupId}</groupId>`) &&
                block.includes(`<artifactId>${artifactId}</artifactId>`)
            ) {
                // 确保这是一个正常的dependency块，而不是exclusion块内的内容
                // 通过检查artifactId标签前面是否有exclusion标签来判断
                const artifactTag = `<artifactId>${artifactId}</artifactId>`;
                const relIndex = block.indexOf(artifactTag);
                if (relIndex !== -1) {
                    // 检查artifactId标签之前的内容，确保不在exclusion块内
                    const beforeArtifact = block.substring(0, relIndex);
                    const exclusionStartCount = (beforeArtifact.match(/<exclusion>/g) || []).length;
                    const exclusionEndCount = (beforeArtifact.match(/<\/exclusion>/g) || []).length;
                    
                    // 如果exclusion开始标签数量大于结束标签数量，说明当前artifactId在exclusion块内
                    if (exclusionStartCount > exclusionEndCount) {
                        continue; // 跳过exclusion块内的artifactId，继续查找下一个
                    }
                    
                    const absIndex = match.index + relIndex;
                    const position = document.positionAt(absIndex);
                    const lineNumber = position.line;
                    const lineRange = document.lineAt(lineNumber).range;
                    editor.selection = new vscode.Selection(lineRange.start, lineRange.end);
                    editor.revealRange(lineRange, vscode.TextEditorRevealType.InCenter);
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    /**
     * 处理右键菜单请求
     * @param data 菜单数据，包含节点信息和路径信息
     */
    private async handleContextMenu(data: any) {
        try {
            const { node, nodeIndex, pathInfo, action } = data;
            console.log('Context menu data node:', node);
            console.log('Context menu data nodeIndex:', nodeIndex);
            console.log('Context menu data pathInfo:', pathInfo);
            if (action === 'exclude') {
                // 1. 获取根依赖（pathInfo 最后一个节点）和目标依赖（node）
                // 注意：pathInfo数组中，最后一个元素是根依赖，第一个元素是当前选中的节点
                const root = pathInfo[pathInfo.length - 1];
                const target = node;
                // 2. 构造参数，调用后端插入exclusion
                const pomFiles = await vscode.workspace.findFiles('pom.xml', undefined, 1);
                if (pomFiles.length === 0) {
                    vscode.window.showErrorMessage('Could not find pom.xml file in current project');
                    return;
                }
                const params = {
                    pomPath: pomFiles[0].fsPath,
                    rootDependency: {
                        groupId: root.groupId,
                        artifactId: root.artifactId,
                        version: root.version,
                        scope: root.scope
                    },
                    targetDependency: {
                        groupId: target.groupId,
                        artifactId: target.artifactId
                    }
                };
                const result = JSON.parse(await this.lspClient.insertExclusion(params));
                if (result && result.success) {
                    // 高亮新加的 exclusion 行
                    const document = await vscode.workspace.openTextDocument(pomFiles[0]);
                    const editor = await vscode.window.showTextDocument(document);
                    const line = (result.highlightLine ? result.highlightLine : 1) - 1;
                    const pos = new vscode.Position(line, 0);
                    editor.selection = new vscode.Selection(pos, pos);
                    editor.revealRange(new vscode.Range(pos, pos));
                    vscode.window.showInformationMessage(result.message || 'Successfully inserted exclusion');
                    
                    // 通知前端exclude成功，需要更新依赖树
                    // 这里需要获取当前的webviewPanel引用
                    if (this.currentWebviewPanel) {
                        this.currentWebviewPanel.webview.postMessage({
                            type: 'excludeSuccess',
                            excludedDependency: {
                                groupId: target.groupId,
                                artifactId: target.artifactId
                            }
                        });
                    }
                } else {
                    console.log('result error:', result.error);
                    vscode.window.showErrorMessage(result && result.error ? result.error : 'Failed to insert exclusion');
                }
                return;
            } else if (action === 'goto-pom') {
                // 跳转到 pom.xml 逻辑（原有逻辑）
                const parentIndex = nodeIndex + 1;
                let parent = null;
                if (pathInfo && pathInfo.length > parentIndex) {
                    parent = pathInfo[parentIndex];
                    // 有父依赖，查找.m2仓库父依赖pom文件
                    const os = require('os');
                    const path = require('path');
                    const m2 = process.env.M2_REPO || path.join(os.homedir(), '.m2', 'repository');
                    const groupPath = parent.groupId.replace(/\./g, path.sep);
                    const pomPath = path.join(
                        m2,
                        groupPath,
                        parent.artifactId,
                        parent.version,
                        `${parent.artifactId}-${parent.version}.pom`
                    );
                    const fs = require('fs');
                    if (!fs.existsSync(pomPath)) {
                        vscode.window.showWarningMessage(`Could not find parent dependency pom.xml in local repository: ${pomPath}`);
                        return;
                    }
                    const pomUri = vscode.Uri.file(pomPath);
                    const found = await this.jumpToDependencyInPom(pomUri, node.groupId, node.artifactId);
                    if (found) {
                        vscode.window.showInformationMessage(`Jumped to declaration of ${node.groupId}:${node.artifactId} (parent dependency pom)`);
                    } else {
                        vscode.window.showWarningMessage(`Could not find declaration of ${node.groupId}:${node.artifactId} in parent dependency pom.xml`);
                    }
                    return;
                } else {
                    // 依赖链顶端，当前项目pom.xml
                    const pomFiles = await vscode.workspace.findFiles('pom.xml', undefined, 1);
                    if (pomFiles.length === 0) {
                        vscode.window.showErrorMessage('Could not find pom.xml file in current project');
                        return;
                    }
                    const pomUri = pomFiles[0];
                    const found = await this.jumpToDependencyInPom(pomUri, node.groupId, node.artifactId);
                    if (found) {
                        vscode.window.showInformationMessage(`Jumped to declaration of ${node.groupId}:${node.artifactId}`);
                    } else {
                        vscode.window.showWarningMessage(`Could not find declaration of ${node.groupId}:${node.artifactId} in current project pom.xml`);
                    }
                    return;
                }
            }
            // 其他 action 或默认逻辑可在此扩展
        } catch (error) {
            vscode.window.showErrorMessage(`Failed to handle context menu: ${error}`);
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
                vscode.window.showErrorMessage('Could not find pom.xml file');
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
                vscode.window.showInformationMessage(`Jumped to declaration of ${node.groupId}:${node.artifactId}`);
            } else {
                // 如果未找到依赖声明，则显示警告信息
                vscode.window.showWarningMessage(`Could not find declaration of ${node.groupId}:${node.artifactId} in pom.xml`);
            }
            
        } catch (error) {
            // 如果跳转失败，则显示错误信息
            console.error('Failed to jump to pom.xml:', error);
            vscode.window.showErrorMessage(`Failed to jump to pom.xml: ${error}`);
        }
    }
    
    /**
     * 排除依赖（暂时只显示消息）
     */
    private async excludeDependency(node: any) {
        vscode.window.showInformationMessage(`Exclude dependency feature is under development: ${node.groupId}:${node.artifactId}`);
    }
}