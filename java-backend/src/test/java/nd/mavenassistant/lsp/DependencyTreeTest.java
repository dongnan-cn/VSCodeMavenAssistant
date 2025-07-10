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

    @Test
    public void testAnalyzeDependenciesTreeStructure() throws Exception {
        SimpleLanguageServer server = new SimpleLanguageServer();
        String pomPath = Paths.get("pom.xml").toAbsolutePath().toString();
        String result = server.analyzeDependencies(pomPath).get();
        assertFalse(result.contains("\"error\""), "依赖树解析失败: " + result);

        // 递归打印树形结构
        printDependencyTree(result, 0);
    }

    /**
     * 递归打印树形依赖结构（增强版，显示 children 数量）
     */
    private void printDependencyTree(String json, int indent) {
        com.google.gson.JsonObject obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        printNode(obj, indent);
    }

    private void printNode(com.google.gson.JsonObject node, int indent) {
        String prefix = "  ".repeat(indent);
        String label = node.get("groupId").getAsString() + ":" + node.get("artifactId").getAsString() + ":" + node.get("version").getAsString();
        int childrenCount = node.has("children") ? node.getAsJsonArray("children").size() : 0;
        System.out.println(prefix + label + "  [children: " + childrenCount + "]");
        if (childrenCount > 0) {
            for (var child : node.getAsJsonArray("children")) {
                printNode(child.getAsJsonObject(), indent + 1);
            }
        }
    }
} 