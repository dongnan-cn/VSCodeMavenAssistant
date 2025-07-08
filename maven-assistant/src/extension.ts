// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { LspClient } from './lspClient';
import { MavenPanelProvider } from './mavenPanelProvider';
import { DependencyTreeProvider } from './dependencyTreeProvider';
import { DependencyConflictsProvider } from './dependencyConflictsProvider';
import { DependencyAnalyzerEditorProvider } from './dependencyAnalyzerEditorProvider';

/**
 * Maven Assistant 插件主入口
 * 负责注册命令、视图提供者和LSP客户端管理
 */
export function activate(context: vscode.ExtensionContext) {
	console.log('Maven Assistant 插件已激活');

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

	// 注册 Dependency Analyzer 自定义Webview编辑器
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
		console.log('LSP客户端启动成功');
		// 初始化时刷新依赖信息
		dependencyTreeProvider.refresh();
		dependencyConflictsProvider.refresh();
	}).catch((error: any) => {
		console.error('LSP客户端启动失败:', error);
		// 不显示错误消息，因为LspClient内部已经处理了
		// 插件继续运行，使用模拟数据
		console.log('插件将继续运行，使用模拟数据');
	});
}

/**
 * 注册所有Maven Assistant命令
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
				// 如果没有提供目标，弹出输入框让用户输入
				goal = await vscode.window.showInputBox({
					prompt: '请输入Maven目标（如：clean install）',
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

	// 显示有效POM命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.showEffectivePom', async () => {
			await showEffectivePom(lspClient);
		})
	);

	// 刷新依赖信息命令
	context.subscriptions.push(
		vscode.commands.registerCommand('maven-assistant.refreshDependencies', () => {
			dependencyTreeProvider.refresh();
			dependencyConflictsProvider.refresh();
			vscode.window.showInformationMessage('依赖信息已刷新');
		})
	);
}

/**
 * 运行Maven目标
 */
async function runMavenGoal(goal: string, lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage(`正在运行Maven目标: ${goal}`);
		
		// 通过LSP调用后端执行Maven命令
		const result = await lspClient.executeMavenGoal(goal);
		
		if (result.success) {
			vscode.window.showInformationMessage(`Maven目标执行成功: ${goal}`);
		} else {
			vscode.window.showErrorMessage(`Maven目标执行失败: ${result.error}`);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`执行Maven目标时出错: ${error}`);
	}
}

/**
 * 编辑Maven目标
 */
async function editMavenGoal(lspClient: LspClient) {
	try {
		// 获取当前可用的Maven目标
		const goals = await lspClient.getAvailableGoals();
		
		// 显示目标选择器
		const selectedGoal = await vscode.window.showQuickPick(goals, {
			placeHolder: '选择要编辑的Maven目标'
		});
		
		if (selectedGoal) {
			// 打开目标编辑器
			await lspClient.editGoal(selectedGoal);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`编辑Maven目标时出错: ${error}`);
	}
}

/**
 * 快速运行Maven目标
 */
async function quickRunMavenGoal(lspClient: LspClient) {
	try {
		// 获取常用Maven目标
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
			placeHolder: '选择要运行的Maven目标'
		});
		
		if (selectedGoal) {
			await runMavenGoal(selectedGoal, lspClient);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`快速运行Maven目标时出错: ${error}`);
	}
}

/**
 * 显示依赖树
 */
async function showDependencyTree(lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage('正在分析依赖树...');
		
		// 通过LSP获取依赖树信息
		const dependencyTree = await lspClient.getDependencyTree();
		
		// 创建新的文档显示依赖树
		const document = await vscode.workspace.openTextDocument({
			content: dependencyTree,
			language: 'text'
		});
		
		await vscode.window.showTextDocument(document);
		vscode.window.showInformationMessage('依赖树分析完成');
	} catch (error) {
		vscode.window.showErrorMessage(`显示依赖树时出错: ${error}`);
	}
}

/**
 * 分析依赖
 */
async function analyzeDependencies(lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage('正在分析依赖...');
		
		// 通过LSP分析依赖
		const analysis = await lspClient.analyzeDependencies();
		
		// 创建新的文档显示分析结果
		const document = await vscode.workspace.openTextDocument({
			content: analysis,
			language: 'text'
		});
		
		await vscode.window.showTextDocument(document);
		vscode.window.showInformationMessage('依赖分析完成');
	} catch (error) {
		vscode.window.showErrorMessage(`分析依赖时出错: ${error}`);
	}
}

/**
 * 显示依赖冲突
 */
async function showDependencyConflicts(lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage('正在检测依赖冲突...');
		
		// 通过LSP检测依赖冲突
		const conflicts = await lspClient.getDependencyConflicts();
		
		if (conflicts.length === 0) {
			vscode.window.showInformationMessage('未发现依赖冲突');
		} else {
			// 创建新的文档显示冲突信息
			const conflictsText = conflicts.join('\n');
			const document = await vscode.workspace.openTextDocument({
				content: conflictsText,
				language: 'text'
			});
			
			await vscode.window.showTextDocument(document);
			vscode.window.showInformationMessage(`发现 ${conflicts.length} 个依赖冲突`);
		}
	} catch (error) {
		vscode.window.showErrorMessage(`显示依赖冲突时出错: ${error}`);
	}
}

/**
 * 显示有效POM
 */
async function showEffectivePom(lspClient: LspClient) {
	try {
		vscode.window.showInformationMessage('正在生成有效POM...');
		
		// 通过LSP获取有效POM
		const effectivePom = await lspClient.getEffectivePom();
		
		// 创建新的XML文档显示有效POM
		const document = await vscode.workspace.openTextDocument({
			content: effectivePom,
			language: 'xml'
		});
		
		await vscode.window.showTextDocument(document);
		vscode.window.showInformationMessage('有效POM生成完成');
	} catch (error) {
		vscode.window.showErrorMessage(`显示有效POM时出错: ${error}`);
	}
}

export function deactivate() {
	console.log('Maven Assistant 插件已停用');
}
