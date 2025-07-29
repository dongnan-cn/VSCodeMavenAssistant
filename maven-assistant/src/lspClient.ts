import * as path from 'path';
import * as vscode from 'vscode';
import * as child_process from 'child_process';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient/node';
import { workspace } from 'vscode';

/**
 * Maven Assistant LSP客户端
 * 负责与Java后端LSP Server通信，提供Maven相关功能
 */
export class LspClient {
	private client: LanguageClient | undefined;
	private context: vscode.ExtensionContext;
	private serverProcess: child_process.ChildProcess | undefined;

	constructor(context: vscode.ExtensionContext) {
		this.context = context;
	}

	/**
	 * 启动LSP客户端
	 */
	async start(): Promise<void> {
		try {
			// 获取Java后端jar包路径
			const serverJarPath = path.join(this.context.extensionPath, 'server', 'java-backend-1.0-SNAPSHOT.jar');
			
			console.log('LSP Server jar path:', serverJarPath);

			// 检查jar包是否存在
			const fs = require('fs');
			if (!fs.existsSync(serverJarPath)) {
				throw new Error(`LSP Server jar file not found: ${serverJarPath}`);
			}

			// 配置服务器选项
			const serverOptions: ServerOptions = {
				run: {
					command: 'java',
					args: ['-jar', serverJarPath],
					transport: TransportKind.stdio,
					options: {
						cwd: vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || process.cwd()
					}
				},
				debug: {
					command: 'java',
					args: ['-jar', serverJarPath],
					transport: TransportKind.stdio,
					options: {
						cwd: vscode.workspace.workspaceFolders?.[0]?.uri.fsPath || process.cwd()
					}
				}
			};

			// 配置客户端选项
			const clientOptions: LanguageClientOptions = {
				documentSelector: [
					{ scheme: 'file', language: 'xml' }, // pom.xml文件
					{ scheme: 'file', language: 'java' }  // Java文件
				],
				synchronize: {
					fileEvents: vscode.workspace.createFileSystemWatcher('**/pom.xml')
				},
				outputChannel: vscode.window.createOutputChannel('Maven Assistant LSP')
			};

			// 创建语言客户端
			this.client = new LanguageClient(
				'maven-assistant',
				'Maven Assistant Language Server',
				serverOptions,
				clientOptions
			);

			// Start the client
			console.log('Starting LSP client...');
			await this.client.start();
			console.log('LSP client started successfully');

		} catch (error) {
			console.error('Failed to start LSP client:', error);
			// Don't throw error, let the plugin continue running with mock data
			vscode.window.showWarningMessage('Maven Assistant: LSP backend connection failed, will use mock data. Please check Java environment and jar file.');
		}
	}

	/**
	 * 停止LSP客户端
	 */
	async stop(): Promise<void> {
		if (this.client) {
			await this.client.stop();
			this.client = undefined;
		}
		
		if (this.serverProcess) {
			this.serverProcess.kill();
			this.serverProcess = undefined;
		}
	}

	/**
	 * 执行Maven目标
	 */
	async executeMavenGoal(goal: string): Promise<{ success: boolean; error?: string }> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// 通过LSP自定义方法调用后端执行Maven命令
			const result = await this.client.sendRequest('maven/executeGoal', { goal });
			return result as { success: boolean; error?: string };
		} catch (error) {
			console.error('Failed to execute Maven goal:', error);
			return { success: false, error: String(error) };
		}
	}

	/**
	 * 获取可用的Maven目标
	 */
	async getAvailableGoals(): Promise<string[]> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// 通过LSP自定义方法获取可用目标
			const goals = await this.client.sendRequest('maven/getAvailableGoals', {});
			return goals as string[];
		} catch (error) {
			console.error('Failed to get available Maven goals:', error);
			// 返回默认的常用目标
			return [
				'clean',
				'compile',
				'test',
				'package',
				'install',
				'clean install',
				'clean package',
				'clean test',
				'dependency:tree',
				'dependency:analyze'
			];
		}
	}

	/**
	 * 编辑Maven目标
	 */
	async editGoal(goal: string): Promise<void> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// 通过LSP自定义方法编辑目标
			await this.client.sendRequest('maven/editGoal', { goal });
		} catch (error) {
			console.error('Failed to edit Maven goal:', error);
			throw error;
		}
	}

	/**
	 * 获取依赖树
	 */
	async getDependencyTree(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// 通过LSP自定义方法获取依赖树
			const result = await this.client.sendRequest('maven/getDependencyTree', {});
			return result as string;
		} catch (error) {
			console.error('Failed to get dependency tree:', error);
			// Throw error instead of returning mock data
			throw new Error(`Failed to get dependency tree: ${error}`);
		}
	}

	/**
	 * 分析依赖
	 */
	async analyzeDependencies(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// 通过LSP自定义方法分析依赖
			const result = await this.client.sendRequest('maven/analyzeDependencies', null);
			// console.log(result);
			return result as string;
		} catch (error) {
			console.error('Failed to analyze dependencies:', error);
			// Throw error instead of returning mock data
			throw new Error(`Failed to analyze dependencies: ${error}`);
		}
	}

	/**
	 * 获取依赖冲突
	 */
	async getDependencyConflicts(): Promise<string[]> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// 通过LSP自定义方法获取依赖冲突
			const result = await this.client.sendRequest('maven/getDependencyConflicts', {});
			return result as string[];
		} catch (error) {
			console.error('Failed to get dependency conflicts:', error);
			// Throw error instead of returning mock data
			throw new Error(`Failed to get dependency conflicts: ${error}`);
		}
	}

	/**
	 * 获取有效POM
	 */
	async getEffectivePom(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// 通过LSP自定义方法获取有效POM
			const result = await this.client.sendRequest('maven/getEffectivePom', {});
			return result as string;
		} catch (error) {
			console.error('Failed to get effective POM:', error);
			// Return mock effective POM
			return `<?xml version="1.0" encoding="UTF-8"?>\n<project>\n  <!-- Failed to generate effective POM: ${error} -->\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>com.example</groupId>\n  <artifactId>my-project</artifactId>\n  <version>1.0.0</version>\n</project>`;
		}
	}

	/**
	 * 插入依赖排除（exclusion）
	 */
	async insertExclusion(params: any): Promise<any> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}
			const result = await this.client.sendRequest('maven/insertExclusion', JSON.stringify(params));
			return result;
		} catch (error) {
			console.error('Failed to insert exclusion:', error);
			return { success: false, error: String(error) };
		}
	}

	/**
	 * 检查LSP客户端是否已连接
	 */
	isConnected(): boolean {
		return this.client !== undefined && this.client.isRunning();
	}
}