package nd.mavenassistant.lsp;

import org.eclipse.lsp4j.launch.LSPLauncher;
import org.eclipse.lsp4j.services.LanguageServer;

import java.io.IOException;

/**
 * LSP Server 主入口
 * 这是 VSCode 插件后端 Java 进程的启动类。
 * 目前仅实现最基础的启动逻辑，后续可扩展具体服务能力。
 */
public class LspServerMain {
    public static void main(String[] args) throws IOException {
        // 创建自定义的 LanguageServer 实现（此处用最简单的空实现）
        LanguageServer server = new SimpleLanguageServer();
        // 启动 LSP4J 的 Launcher，绑定标准输入输出，实现与前端的通信
        LSPLauncher.createServerLauncher(server, System.in, System.out).startListening();
        // 进程会一直阻塞，直到被前端关闭
    }
} 