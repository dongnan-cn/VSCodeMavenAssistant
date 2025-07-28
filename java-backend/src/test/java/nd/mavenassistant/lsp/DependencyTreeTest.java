package nd.mavenassistant.lsp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Paths;

public class DependencyTreeTest {
    @Test
    public void testAnalyzeDependencies() throws Exception {
        SimpleLanguageServer server = new SimpleLanguageServer();
        // 获取测试专用的 pom.xml 绝对路径
        String pomPath = Paths.get("src/test/resources/test-pom.xml").toAbsolutePath().toString();
        String result = server.analyzeDependencies(pomPath).get();
        System.out.println("依赖树 JSON 输出：\n" + result);
        // 断言结果不包含 error 字段
        assertFalse(result.contains("\"error\""), "依赖树解析失败: " + result);
    }

    @Test
    public void testAnalyzeDependenciesTreeStructure() throws Exception {
        SimpleLanguageServer server = new SimpleLanguageServer();
        String pomPath = Paths.get("src/test/resources/test-pom.xml").toAbsolutePath().toString();
        String result = server.analyzeDependencies(pomPath).get();
        assertFalse(result.contains("\"error\""), "依赖树解析失败: " + result);

        // 递归打印依赖数组结构
        printDependencyArray(result, 0);
    }

    /**
     * 递归打印依赖数组结构（适配返回为数组的情况）
     */
    private void printDependencyArray(String json, int indent) {
        com.google.gson.JsonElement elem = com.google.gson.JsonParser.parseString(json);
        if (elem.isJsonArray()) {
            for (var item : elem.getAsJsonArray()) {
                printNode(item.getAsJsonObject(), indent);
            }
        } else if (elem.isJsonObject()) {
            printNode(elem.getAsJsonObject(), indent);
        }
    }

    private void printNode(com.google.gson.JsonObject node, int indent) {
        String prefix = "  ".repeat(indent);
        String groupId = node.has("groupId") && !node.get("groupId").isJsonNull() ? node.get("groupId").getAsString() : "";
        String artifactId = node.has("artifactId") && !node.get("artifactId").isJsonNull() ? node.get("artifactId").getAsString() : "";
        String version = node.has("version") && !node.get("version").isJsonNull() ? node.get("version").getAsString() : "";
        String label = groupId + ":" + artifactId + ":" + version;
        // 增加scope信息（如有）
        String scope = node.has("scope") && !node.get("scope").isJsonNull() ? node.get("scope").getAsString() : "";
        // 增加droppedByConflict信息（如有）
        String dropped = node.has("droppedByConflict") && !node.get("droppedByConflict").isJsonNull() ? (node.get("droppedByConflict").getAsBoolean() ? "[DROPPED]" : "[USED]") : "";
        int childrenCount = node.has("children") ? node.getAsJsonArray("children").size() : 0;
        System.out.println(prefix + label + (scope.isEmpty() ? "" : (" [scope: " + scope + "]")) + " " + dropped + "  [children: " + childrenCount + "]");
        if (childrenCount > 0) {
            for (var child : node.getAsJsonArray("children")) {
                printNode(child.getAsJsonObject(), indent + 1);
            }
        }
    }
}