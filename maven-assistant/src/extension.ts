// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { LspClient } from './lspClient';
import { MavenPanelProvider } from './mavenPanelProvider';
import { DependencyTreeProvider } from './dependencyTreeProvider';
import { DependencyConflictsProvider } from './dependencyConflictsProvider';
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

	// 注册依赖冲突提供者
	const dependencyConflictsProvider = new DependencyConflictsProvider(lspClient);
	context.subscriptions.push(
		vscode.window.registerTreeDataProvider('maven-assistant.conflicts', dependencyConflictsProvider)
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
	registerCommands(context, lspClient, mavenPanelProvider, dependencyTreeProvider, dependencyConflictsProvider);

	// 启动LSP客户端
	lspClient.start().then(() => {
		console.log('LSP client started successfully');
		// Refresh dependency information on initialization
		dependencyTreeProvider.refresh();
		dependencyConflictsProvider.refresh();
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
	dependencyTreeProvider: DependencyTreeProvider,
	dependencyConflictsProvider: DependencyConflictsProvider
) {
	// 打开Maven面板命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.openMavenPanel', () => {
			mavenPanelProvider.showPanel();
		})
	);

	// 运行Maven目标命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.runMavenGoal', async (goal?: string) => {
			if (!goal) {
				// If no goal provided, show input box for user input
				goal = await vscode.window.showInputBox({
					prompt: 'Please enter Maven goal (e.g.: clean install)',
					placeHolder: 'clean install'
				});
			}
			
			if (goal) {
				await runMavenGoal(goal, lspClient);
			}
		})
	);

	// 编辑Maven目标命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.editMavenGoal', async () => {
			await editMavenGoal(lspClient);
		})
	);

	// 快速运行命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.quickRun', async () => {
			await quickRunMavenGoal(lspClient);
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

	// 显示依赖冲突命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.showDependencyConflicts', async () => {
			await showDependencyConflicts(lspClient);
		})
	);



	// 刷新依赖信息命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.refreshDependencies', () => {
			dependencyTreeProvider.refresh();
			dependencyConflictsProvider.refresh();
			vscode.window.showInformationMessage('Dependency information refreshed');
		})
	);
}

/**
 * Run Maven goal
 */
async function runMavenGoal(goal: string, lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage(`Running Maven goal: ${goal}`);
		
		// Call backend to execute Maven command via LSP
		const result = await lspClient.executeMavenGoal(goal);
		
		if (result.success) {
			vscode.window.showInformationMessage(`Maven goal executed successfully: ${goal}`);
		} else {
			vscode.window.showErrorMessage(`Maven goal execution failed: ${result.error}`);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`Error executing Maven goal: ${error}`);
	}
}

/**
 * Edit Maven goal
 */
async function editMavenGoal(lspClient: LspClient) {
	try {
		// Get currently available Maven goals
		const goals = await lspClient.getAvailableGoals();
		
		// Show goal selector
		const selectedGoal = await vscode.window.showQuickPick(goals, {
			placeHolder: 'Select Maven goal to edit'
		});
		
		if (selectedGoal) {
			// Open goal editor
			await lspClient.editGoal(selectedGoal);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`Error editing Maven goal: ${error}`);
	}
}

/**
 * Quick run Maven goal
 */
async function quickRunMavenGoal(lspClient: LspClient) {
	try {
		// Get common Maven goals
		const commonGoals = [
			'clean',
			'compile',
			'test',
			'package',
			'install',
			'clean install',
			'clean package',
			'clean test'
		];
		
		const selectedGoal = await vscode.window.showQuickPick(commonGoals, {
			placeHolder: 'Select Maven goal to run'
		});
		
		if (selectedGoal) {
			await runMavenGoal(selectedGoal, lspClient);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`Error quick running Maven goal: ${error}`);
	}
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

/**
 * Show dependency conflicts
 */
async function showDependencyConflicts(lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage('Detecting dependency conflicts...');
		
		// Detect dependency conflicts via LSP
		const conflicts = await lspClient.getDependencyConflicts();
		
		if (conflicts.length === 0) {
			vscode.window.showInformationMessage('No dependency conflicts found');
		} else {
			// Create new document to display conflict information
			const conflictsText = conflicts.join('\n');
			const document = await vscode.workspace.openTextDocument({
				content: conflictsText,
				language: 'text'
			});
			
			await vscode.window.showTextDocument(document);
			vscode.window.showInformationMessage(`Found ${conflicts.length} dependency conflicts`);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`Error showing dependency conflicts: ${error}`);
	}
}



export function deactivate() {
	console.log('Maven Assistant plugin deactivated');
}
