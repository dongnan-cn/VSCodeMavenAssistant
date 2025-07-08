package nd.mavenassistant.lsp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Paths;

public class DependencyTreeTest {
    @Test
    public void testAnalyzeDependencies() throws Exception {
        SimpleLanguageServer server = new SimpleLanguageServer();
        // 获取当前模块下的 pom.xml 绝对路径
        String pomPath = Paths.get("pom.xml").toAbsolutePath().toString();
        String result = server.analyzeDependencies(pomPath).get();
        System.out.println("依赖树 JSON 输出：\n" + result);
        // 断言结果不包含 error 字段
        assertFalse(result.contains("\"error\""), "依赖树解析失败: " + result);
    }
} 