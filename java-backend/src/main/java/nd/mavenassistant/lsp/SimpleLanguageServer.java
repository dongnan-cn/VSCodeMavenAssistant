package nd.mavenassistant.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.SetTraceParams;

/**
 * 最基础的 LanguageServer 实现
 * 目前所有方法均为空实现，后续可逐步扩展具体功能。
 */
public class SimpleLanguageServer implements LanguageServer {
    // LanguageClient 用于与 VSCode 前端通信，推送日志等
    private LanguageClient client;

    // 提供一个方法让主入口注入 LanguageClient
    public void connect(LanguageClient client) {
        this.client = client;
    }

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        // 通过 LSP 协议向 VSCode 输出面板推送初始化日志
        if (client != null) {
            client.logMessage(new MessageParams(MessageType.Info, "LSP Server 已初始化"));
        }
        // 返回空的初始化结果
        return CompletableFuture.completedFuture(new InitializeResult(new ServerCapabilities()));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // 进程退出时的处理
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return null;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return null;
    }

    @JsonRequest("maven/analyzeDependencies")
    public CompletableFuture<String> analyzeDependencies() {
        // 返回模拟依赖分析数据
        return CompletableFuture.completedFuture("模拟依赖分析结果：\n- org.example:demo:1.0.0\n- org.springframework:spring-core:5.3.0");
    }

    // 实现 setTrace 方法，防止 VSCode 发送 $/setTrace 时抛出异常
    @Override
    public void setTrace(SetTraceParams params) {
        // 这里可以根据 params.getValue() 设置日志级别，目前为空实现
    }
}