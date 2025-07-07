import * as path from 'path';
import * as vscode from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions } from 'vscode-languageclient/node';
import { workspace } from 'vscode';

let client: LanguageClient | undefined;

/**
 * 启动 Java 后端 LSP Server，并建立通信
 */
export async function startLspClient(context: vscode.ExtensionContext) {
    // 获取后端 jar 的绝对路径
    const jarPath = context.asAbsolutePath(
        path.join('server', 'java-backend-1.0-SNAPSHOT-shaded.jar')
    );
    vscode.window.showInformationMessage('准备启动 LSP 客户端: ' + jarPath);

    // 配置 LSP Server 启动命令
    const serverOptions: ServerOptions = {
        command: 'java',
        args: ['-jar', jarPath],
        options: { cwd: workspace.rootPath }
    };

    // 配置 LSP 客户端选项
    const clientOptions: LanguageClientOptions = {
        documentSelector: [{ scheme: 'file', language: 'xml' }, { scheme: 'file', language: 'java' }],
        outputChannel: vscode.window.createOutputChannel('Maven Assistant LSP')
    };

    // 创建并启动 LSP 客户端
    client = new LanguageClient(
        'mavenAssistantLsp',
        'Maven Assistant LSP',
        serverOptions,
        clientOptions
    );

    await client.start();
}

/**
 * 停止 LSP 客户端
 */
export async function stopLspClient() {
    if (client) {
        await client.stop();
        client = undefined;
    }
} 