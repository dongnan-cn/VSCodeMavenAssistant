// The module 'vscode' contains the VS Code extensibility API
// Import the module and reference it with the alias vscode in your code below
import * as vscode from 'vscode';
import { startLspClient, stopLspClient } from './lspClient';

// This method is called when your extension is activated
// Your extension is activated the very first time the command is executed
export async function activate(context: vscode.ExtensionContext) {
	console.log('Maven Assistant extension is now active!');
	vscode.window.showInformationMessage('Maven Assistant 插件已激活');
	console.log('workspaceFolder:', vscode.workspace.workspaceFolders?.[0].uri.fsPath);

	// 启动 LSP 客户端
	await startLspClient(context);

	// The command has been defined in the package.json file
	// Now provide the implementation of the command with registerCommand
	// The commandId parameter must match the command field in package.json
	const disposable = vscode.commands.registerCommand('maven-assistant.helloWorld', () => {
		// The code you place here will be executed every time your command is executed
		// Display a message box to the user
		vscode.window.showInformationMessage('Hello World from Maven Assistant!');
	});

	context.subscriptions.push(disposable);
}

// This method is called when your extension is deactivated
export async function deactivate() {
	await stopLspClient();
}
