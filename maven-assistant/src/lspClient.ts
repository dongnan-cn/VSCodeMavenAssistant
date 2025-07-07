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
			const serverJarPath = path.join(this.context.extensionPath, 'server', 'java-backend-1.0-SNAPSHOT-shaded.jar');
			
			console.log('LSP Server jar路径:', serverJarPath);

			// 检查jar包是否存在
			const fs = require('fs');
			if (!fs.existsSync(serverJarPath)) {
				throw new Error(`LSP Server jar包不存在: ${serverJarPath}`);
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

			// 启动客户端
			console.log('正在启动LSP客户端...');
			await this.client.start();
			console.log('LSP客户端启动成功');

		} catch (error) {
			console.error('LSP客户端启动失败:', error);
			// 不抛出错误，让插件继续运行，使用模拟数据
			vscode.window.showWarningMessage('Maven Assistant: LSP后端连接失败，将使用模拟数据。请检查Java环境和jar包。');
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
				throw new Error('LSP客户端未启动');
			}

			// 通过LSP自定义方法调用后端执行Maven命令
			const result = await this.client.sendRequest('maven/executeGoal', { goal });
			return result as { success: boolean; error?: string };
		} catch (error) {
			console.error('执行Maven目标失败:', error);
			return { success: false, error: String(error) };
		}
	}

	/**
	 * 获取可用的Maven目标
	 */
	async getAvailableGoals(): Promise<string[]> {
		try {
			if (!this.client) {
				throw new Error('LSP客户端未启动');
			}

			// 通过LSP自定义方法获取可用目标
			const goals = await this.client.sendRequest('maven/getAvailableGoals', {});
			return goals as string[];
		} catch (error) {
			console.error('获取可用Maven目标失败:', error);
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
				throw new Error('LSP客户端未启动');
			}

			// 通过LSP自定义方法编辑目标
			await this.client.sendRequest('maven/editGoal', { goal });
		} catch (error) {
			console.error('编辑Maven目标失败:', error);
			throw error;
		}
	}

	/**
	 * 获取依赖树
	 */
	async getDependencyTree(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP客户端未启动');
			}

			// 通过LSP自定义方法获取依赖树
			const result = await this.client.sendRequest('maven/getDependencyTree', {});
			return result as string;
		} catch (error) {
			console.error('获取依赖树失败:', error);
			// 返回模拟的依赖树数据
			return `[INFO] 依赖树分析失败: ${error}\n\n模拟依赖树:\n└── com.example:my-project:1.0.0\n    ├── org.springframework:spring-core:5.3.0\n    └── org.junit:junit:4.13.2`;
		}
	}

	/**
	 * 分析依赖
	 */
	async analyzeDependencies(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP客户端未启动');
			}

			// 通过LSP自定义方法分析依赖
			const result = await this.client.sendRequest('maven/analyzeDependencies', {});
			return result as string;
		} catch (error) {
			console.error('分析依赖失败:', error);
			// 返回模拟的分析结果
			return `[INFO] 依赖分析失败: ${error}\n\n模拟分析结果:\n- 直接依赖: 5个\n- 传递依赖: 23个\n- 可选依赖: 2个\n- 排除依赖: 1个`;
		}
	}

	/**
	 * 获取依赖冲突
	 */
	async getDependencyConflicts(): Promise<string[]> {
		try {
			if (!this.client) {
				throw new Error('LSP客户端未启动');
			}

			// 通过LSP自定义方法获取依赖冲突
			const result = await this.client.sendRequest('maven/getDependencyConflicts', {});
			return result as string[];
		} catch (error) {
			console.error('获取依赖冲突失败:', error);
			// 返回模拟的冲突信息
			return [
				'[WARNING] 发现依赖冲突:',
				'  org.slf4j:slf4j-api:1.7.30 (选择) vs 1.7.25 (排除)',
				'  org.apache.commons:commons-lang3:3.12.0 (选择) vs 3.11.0 (排除)'
			];
		}
	}

	/**
	 * 获取有效POM
	 */
	async getEffectivePom(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP客户端未启动');
			}

			// 通过LSP自定义方法获取有效POM
			const result = await this.client.sendRequest('maven/getEffectivePom', {});
			return result as string;
		} catch (error) {
			console.error('获取有效POM失败:', error);
			// 返回模拟的有效POM
			return `<?xml version="1.0" encoding="UTF-8"?>\n<project>\n  <!-- 有效POM生成失败: ${error} -->\n  <modelVersion>4.0.0</modelVersion>\n  <groupId>com.example</groupId>\n  <artifactId>my-project</artifactId>\n  <version>1.0.0</version>\n</project>`;
		}
	}

	/**
	 * 检查LSP客户端是否已连接
	 */
	isConnected(): boolean {
		return this.client !== undefined && this.client.isRunning();
	}
} 