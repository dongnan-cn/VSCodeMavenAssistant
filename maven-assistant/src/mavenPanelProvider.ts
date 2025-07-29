import * as vscode from 'vscode';
import { LspClient } from './lspClient';

/**
 * Maven面板提供者
 * 提供WebView界面用于管理Maven目标和配置
 */
export class MavenPanelProvider implements vscode.WebviewViewProvider {
	public static readonly viewType = 'maven-assistant.goals';

	private _view?: vscode.WebviewView;
	private _extensionUri: vscode.Uri;
	private _lspClient: LspClient;

	constructor(extensionUri: vscode.Uri, lspClient: LspClient) {
		this._extensionUri = extensionUri;
		this._lspClient = lspClient;
	}

	/**
	 * 解析WebView视图
	 */
	public resolveWebviewView(
		webviewView: vscode.WebviewView,
		context: vscode.WebviewViewResolveContext,
		_token: vscode.CancellationToken,
	) {
		this._view = webviewView;

		// 设置WebView选项
		webviewView.webview.options = {
			enableScripts: true,
			localResourceRoots: [
				this._extensionUri
			]
		};

		// 设置HTML内容
		webviewView.webview.html = this._getHtmlForWebview(webviewView.webview);

		// 处理来自WebView的消息
		webviewView.webview.onDidReceiveMessage(async (data) => {
			switch (data.type) {
				case 'refreshGoals':
					await this._refreshGoals();
					break;
				default:
					// 其他功能暂未实现
					this._view?.webview.postMessage({
						type: 'showStatus',
						message: 'This feature is not implemented yet',
						status: 'info'
					});
					break;
			}
		});

		// 初始化时加载目标列表
		this._refreshGoals();
	}

	/**
	 * 显示Maven面板
	 */
	public showPanel() {
		if (this._view) {
			this._view.show(true);
		}
	}

	/**
	 * 获取WebView的HTML内容
	 */
	private _getHtmlForWebview(webview: vscode.Webview): string {
		return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Maven Assistant</title>
    <style>
        body {
            font-family: var(--vscode-font-family);
            font-size: var(--vscode-font-size);
            color: var(--vscode-foreground);
            background-color: var(--vscode-editor-background);
            margin: 0;
            padding: 10px;
        }
        
        .header {
            display: flex;
            justify-content: space-between;
            align-items: center;
            margin-bottom: 15px;
            padding-bottom: 10px;
            border-bottom: 1px solid var(--vscode-panel-border);
        }
        
        .title {
            font-size: 16px;
            font-weight: bold;
        }
        
        .toolbar {
            display: flex;
            gap: 5px;
        }
        
        button {
            background-color: var(--vscode-button-background);
            color: var(--vscode-button-foreground);
            border: 1px solid var(--vscode-button-border);
            padding: 5px 10px;
            border-radius: 3px;
            cursor: pointer;
            font-size: 12px;
        }
        
        button:hover {
            background-color: var(--vscode-button-hoverBackground);
        }
        
        button.secondary {
            background-color: var(--vscode-button-secondaryBackground);
            color: var(--vscode-button-secondaryForeground);
        }
        
        .goals-section {
            margin-bottom: 20px;
        }
        
        .section-title {
            font-weight: bold;
            margin-bottom: 10px;
            color: var(--vscode-editor-foreground);
        }
        
        .goal-item {
            display: flex;
            justify-content: space-between;
            align-items: center;
            padding: 8px;
            margin: 5px 0;
            background-color: var(--vscode-list-hoverBackground);
            border-radius: 3px;
            border: 1px solid var(--vscode-list-focusOutline);
        }
        
        .goal-item:hover {
            background-color: var(--vscode-list-activeSelectionBackground);
        }
        
        .goal-name {
            flex: 1;
            margin-right: 10px;
        }
        
        .goal-actions {
            display: flex;
            gap: 5px;
        }
        
        .goal-actions button {
            padding: 3px 6px;
            font-size: 11px;
        }
        
        .status {
            padding: 10px;
            margin: 10px 0;
            border-radius: 3px;
            font-size: 12px;
        }
        
        .status.info {
            background-color: var(--vscode-notificationsInfoBackground);
            color: var(--vscode-notificationsInfoForeground);
        }
        
        .status.error {
            background-color: var(--vscode-notificationsErrorBackground);
            color: var(--vscode-notificationsErrorForeground);
        }
        
        .loading {
            text-align: center;
            padding: 20px;
            color: var(--vscode-descriptionForeground);
        }
        
        .empty-state {
            text-align: center;
            padding: 30px 20px;
            color: var(--vscode-descriptionForeground);
        }
        
        .empty-state button {
            margin-top: 10px;
        }
    </style>
</head>
<body>
    <div class="header">
        <div class="title">Maven 目标管理</div>
        <div class="toolbar">
            <button onclick="addGoal()">添加目标</button>
            <button onclick="refreshGoals()" class="secondary">刷新</button>
        </div>
    </div>
    
    <div id="status"></div>
    
    <div class="goals-section">
        <div class="section-title">常用目标</div>
        <div id="common-goals" class="loading">加载中...</div>
    </div>
    
    <div class="goals-section">
        <div class="section-title">自定义目标</div>
        <div id="custom-goals" class="loading">加载中...</div>
    </div>
    
    <script>
        const vscode = acquireVsCodeApi();
        
        // 页面加载完成后初始化
        document.addEventListener('DOMContentLoaded', function() {
            refreshGoals();
        });
        
        // 刷新目标列表
        function refreshGoals() {
            showStatus('Loading goal list...', 'info');
            vscode.postMessage({ type: 'refreshGoals' });
        }
        
        // 添加目标
        function addGoal() {
            vscode.postMessage({ type: 'addGoal' });
        }
        
        // 运行目标
        function runGoal(goal) {
            vscode.postMessage({ type: 'runGoal', goal: goal });
        }
        
        // 编辑目标
        function editGoal(goal) {
            vscode.postMessage({ type: 'editGoal', goal: goal });
        }
        
        // 删除目标
        function deleteGoal(goal) {
            if (confirm('Are you sure you want to delete goal "' + goal + '"?')) {
                vscode.postMessage({ type: 'deleteGoal', goal: goal });
            }
        }
        
        // 显示状态信息
        function showStatus(message, type = 'info') {
            const statusDiv = document.getElementById('status');
            statusDiv.innerHTML = '<div class="status ' + type + '">' + message + '</div>';
            
            if (type !== 'error') {
                setTimeout(() => {
                    statusDiv.innerHTML = '';
                }, 3000);
            }
        }
        
        // 渲染目标列表
        function renderGoals(goals, containerId) {
            const container = document.getElementById(containerId);
            
            if (!goals || goals.length === 0) {
                container.innerHTML = '<div class="empty-state">No goals available<br><button onclick="addGoal()">Add first goal</button></div>';
                return;
            }
            
            container.innerHTML = goals.map(goal => \`
                <div class="goal-item">
                    <div class="goal-name">\${goal}</div>
                    <div class="goal-actions">
                        <button onclick="runGoal('\${goal}')">运行</button>
                        <button onclick="editGoal('\${goal}')" class="secondary">编辑</button>
                        <button onclick="deleteGoal('\${goal}')" class="secondary">删除</button>
                    </div>
                </div>
            \`).join('');
        }
        
        // 处理来自扩展的消息
        window.addEventListener('message', event => {
            const message = event.data;
            
            switch (message.type) {
                case 'updateGoals':
                    renderGoals(message.commonGoals, 'common-goals');
                    renderGoals(message.customGoals, 'custom-goals');
                    showStatus('Goal list updated', 'info');
                    break;
                case 'showStatus':
                    showStatus(message.message, message.status);
                    break;
                case 'runResult':
                    showStatus(message.success ? 'Goal executed successfully' : 'Goal execution failed: ' + message.error, message.success ? 'info' : 'error');
                    break;
            }
        });
    </script>
</body>
</html>`;
	}

	/**
	 * 刷新目标列表
	 */
	private async _refreshGoals() {
		try {
			// 显示常用Maven目标（静态列表）
			const commonGoals = [
				'clean',
				'compile',
				'test',
				'package',
				'install',
				'clean install'
			];
			
			// 暂无自定义目标功能
			const customGoals: string[] = [];
			
			this._view?.webview.postMessage({
				type: 'updateGoals',
				commonGoals: commonGoals,
				customGoals: customGoals
			});
		} catch (error) {
			this._view?.webview.postMessage({
				type: 'showStatus',
				message: 'Failed to load goal list: ' + error,
				status: 'error'
			});
		}
	}
}