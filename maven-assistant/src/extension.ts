// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { LspClient } from './lspClient';
import { MavenPanelProvider } from './mavenPanelProvider';
import { DependencyTreeProvider } from './dependencyTreeProvider';
import { DependencyAnalyzerEditorProvider } from './dependencyAnalyzerEditorProvider';

/**
 * Maven Assistant plugin main entry point
 * Responsible for registering commands, view providers and LSP client management
 */
export function activate(context: vscode.ExtensionContext) {
	console.log('Maven Assistant plugin activated');

	// 创建LSP客户端实例
	const lspClient = new LspClient(context);
	
	// 注册Maven面板提供者（WebView）
	const mavenPanelProvider = new MavenPanelProvider(context.extensionUri, lspClient);
	context.subscriptions.push(
		vscode.window.registerWebviewViewProvider('maven-assistant.goals', mavenPanelProvider)
	);

	// 注册依赖树提供者
	const dependencyTreeProvider = new DependencyTreeProvider(lspClient);
	context.subscriptions.push(
		vscode.window.registerTreeDataProvider('maven-assistant.dependencies', dependencyTreeProvider)
	);

	// 注册 Dependency Assistant 自定义Webview编辑器
	context.subscriptions.push(
		vscode.window.registerCustomEditorProvider(
			DependencyAnalyzerEditorProvider.viewType,
			new DependencyAnalyzerEditorProvider(context, lspClient),
			{ webviewOptions: { retainContextWhenHidden: true } }
		)
	);

	// 注册命令处理器
	registerCommands(context, lspClient, mavenPanelProvider, dependencyTreeProvider);

	// 启动LSP客户端
	lspClient.start().then(() => {
		console.log('LSP client started successfully');
		// Refresh dependency information on initialization
		dependencyTreeProvider.refresh();
	}).catch((error: any) => {
		console.error('LSP client startup failed:', error);
		// Don't show error message as LspClient handles it internally
		// Plugin continues to run with mock data
		console.log('Plugin will continue running with mock data');
	});
}

/**
 * Register all Maven Assistant commands
 */
function registerCommands(
	context: vscode.ExtensionContext,
	lspClient: LspClient,
	mavenPanelProvider: MavenPanelProvider,
	dependencyTreeProvider: DependencyTreeProvider
) {
	// 打开Maven面板命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.openMavenPanel', () => {
			mavenPanelProvider.showPanel();
		})
	);

	// 显示依赖树命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.showDependencyTree', async () => {
			await showDependencyTree(lspClient);
		})
	);

	// 分析依赖命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.analyzeDependencies', async () => {
			await analyzeDependencies(lspClient);
		})
	);

	// 刷新依赖信息命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.refreshDependencies', () => {
			dependencyTreeProvider.refresh();
			vscode.window.showInformationMessage('Dependency information refreshed');
		})
	);
}

/**
 * Show dependency tree
 */
async function showDependencyTree(lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage('Analyzing dependency tree...');
		
		// Get dependency tree information via LSP
		const dependencyTree = await lspClient.getDependencyTree();
		
		// Create new document to display dependency tree
		const document = await vscode.workspace.openTextDocument({
			content: dependencyTree,
			language: 'text'
		});
		
		await vscode.window.showTextDocument(document);
		vscode.window.showInformationMessage('Dependency tree analysis completed');
	} catch (error) {
		vscode.window.showErrorMessage(`Error showing dependency tree: ${error}`);
	}
}

/**
 * Analyze dependencies
 */
async function analyzeDependencies(lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage('Analyzing dependencies...');
		
		// Analyze dependencies via LSP
		const analysis = await lspClient.analyzeDependencies();
		
		// Create new document to display analysis results
		const document = await vscode.workspace.openTextDocument({
			content: analysis,
			language: 'text'
		});
		
		await vscode.window.showTextDocument(document);
		vscode.window.showInformationMessage('Dependency analysis completed');
	} catch (error) {
		vscode.window.showErrorMessage(`Error analyzing dependencies: ${error}`);
	}
}

export function deactivate() {
	console.log('Maven Assistant plugin deactivated');
}
