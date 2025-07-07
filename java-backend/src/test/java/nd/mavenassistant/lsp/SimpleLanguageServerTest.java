package nd.mavenassistant.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试SimpleLanguageServer的基础功能
 */
public class SimpleLanguageServerTest {
    @Test
    public void testInitialize() throws Exception {
        // 创建服务端实例
        SimpleLanguageServer server = new SimpleLanguageServer();
        // 调用initialize方法
        CompletableFuture<InitializeResult> result = server.initialize(new InitializeParams());
        // 断言返回结果不为null
        assertNotNull(result);
        // 断言最终结果不为null
        assertNotNull(result.get());
    }
} 