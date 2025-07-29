import * as path from 'path';
import * as vscode from 'vscode';
import * as child_process from 'child_process';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient/node';
import { workspace } from 'vscode';

/**
 * Maven Assistant LSP Client
 * Responsible for communicating with Java backend LSP Server, providing Maven-related functionality
 */
export class LspClient {
	private client: LanguageClient | undefined;
	private context: vscode.ExtensionContext;
	private serverProcess: child_process.ChildProcess | undefined;

	constructor(context: vscode.ExtensionContext) {
		this.context = context;
	}

	/**
	 * Start LSP client
	 */
	async start(): Promise<void> {
		try {
			// Get Java backend jar path
			const serverJarPath = path.join(this.context.extensionPath, 'server', 'java-backend-1.0-SNAPSHOT.jar');
			
			console.log('LSP Server jar path:', serverJarPath);

			// Check if jar file exists
			const fs = require('fs');
			if (!fs.existsSync(serverJarPath)) {
				throw new Error(`LSP Server jar file not found: ${serverJarPath}`);
			}

			// Configure server options
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

			// Configure client options
			const clientOptions: LanguageClientOptions = {
				documentSelector: [
					{ scheme: 'file', language: 'xml' }, // pom.xml files
					{ scheme: 'file', language: 'java' }  // Java files
				],
				synchronize: {
					fileEvents: vscode.workspace.createFileSystemWatcher('**/pom.xml')
				},
				outputChannel: vscode.window.createOutputChannel('Maven Assistant LSP')
			};

			// Create language client
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
	 * Stop LSP client
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
	 * Execute Maven goal
	 */
	async executeMavenGoal(goal: string): Promise<{ success: boolean; error?: string }> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// Call backend to execute Maven command through LSP custom method
			const result = await this.client.sendRequest('maven/executeGoal', { goal });
			return result as { success: boolean; error?: string };
		} catch (error) {
			console.error('Failed to execute Maven goal:', error);
			return { success: false, error: String(error) };
		}
	}

	/**
	 * Get available Maven goals
	 */
	async getAvailableGoals(): Promise<string[]> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// Get available goals through LSP custom method
			const goals = await this.client.sendRequest('maven/getAvailableGoals', {});
			return goals as string[];
		} catch (error) {
			console.error('Failed to get available Maven goals:', error);
			// Return default common goals
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
	 * Edit Maven goal
	 */
	async editGoal(goal: string): Promise<void> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// Edit goal through LSP custom method
			await this.client.sendRequest('maven/editGoal', { goal });
		} catch (error) {
			console.error('Failed to edit Maven goal:', error);
			throw error;
		}
	}

	/**
	 * Get dependency tree
	 */
	async getDependencyTree(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// Get dependency tree through LSP custom method
			const result = await this.client.sendRequest('maven/getDependencyTree', {});
			return result as string;
		} catch (error) {
			console.error('Failed to get dependency tree:', error);
			// Throw error instead of returning mock data
			throw new Error(`Failed to get dependency tree: ${error}`);
		}
	}

	/**
	 * Analyze dependencies
	 */
	async analyzeDependencies(): Promise<string> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// Analyze dependencies through LSP custom method
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
	 * Get dependency conflicts
	 */
	async getDependencyConflicts(): Promise<string[]> {
		try {
			if (!this.client) {
				throw new Error('LSP client not started');
			}

			// Get dependency conflicts through LSP custom method
			const result = await this.client.sendRequest('maven/getDependencyConflicts', {});
			return result as string[];
		} catch (error) {
			console.error('Failed to get dependency conflicts:', error);
			// Throw error instead of returning mock data
			throw new Error(`Failed to get dependency conflicts: ${error}`);
		}
	}



	/**
	 * Insert dependency exclusion
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
	 * Check if LSP client is connected
	 */
	isConnected(): boolean {
		return this.client !== undefined && this.client.isRunning();
	}
}