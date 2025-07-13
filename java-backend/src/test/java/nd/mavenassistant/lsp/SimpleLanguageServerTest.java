package nd.mavenassistant.lsp;

import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.concurrent.CompletableFuture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.apache.maven.model.Model;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试SimpleLanguageServer的基础功能
 */
public class SimpleLanguageServerTest {
    
    private Path tempDir;
    
    @BeforeEach
    public void setUp() throws Exception {
        // 创建临时目录用于测试
        tempDir = Files.createTempDirectory("maven-assistant-test");
    }
    
    @AfterEach
    public void tearDown() throws Exception {
        // 清理临时文件
        if (tempDir != null && Files.exists(tempDir)) {
            Files.walk(tempDir)
                .sorted((a, b) -> b.compareTo(a)) // 先删除子文件，再删除目录
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (Exception e) {
                        // 忽略删除失败的情况
                    }
                });
        }
    }

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


    

    
    @Test
    public void testInsertExclusionWithComments() throws Exception {
        // 构造一个包含注释的 pom.xml 内容
        String pom =
                "<?xml version=\"1.0\"?>\n" +
                "<project>\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>demo</artifactId>\n" +
                "  <version>1.0</version>\n" +
                "  <dependencies>\n" +
                "    <!-- 这是一个注释 -->\n" +
                "    <dependency>\n" +
                "      <groupId>org.example</groupId>\n" +
                "      <artifactId>root</artifactId>\n" +
                "      <version>1.0</version>\n" +
                "    </dependency>\n" +
                "    <!-- 另一个注释 -->\n" +
                "  </dependencies>\n" +
                "</project>\n";
        // 写入临时文件
        Path tempPom = tempDir.resolve("test-pom-with-comments.xml");
        Files.writeString(tempPom, pom);

        // 构造请求参数
        Map<String, Object> req = new HashMap<>();
        req.put("pomPath", tempPom.toString());
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.example");
        rootDep.put("artifactId", "root");
        rootDep.put("version", "1.0");
        req.put("rootDependency", rootDep);
        Map<String, String> targetDep = new HashMap<>();
        targetDep.put("groupId", "org.foo");
        targetDep.put("artifactId", "bar");
        req.put("targetDependency", targetDep);

        SimpleLanguageServer server = new SimpleLanguageServer();
        String result = server.insertExclusion(new com.google.gson.Gson().toJson(req)).get();
        System.out.println("测试结果: " + result);
        assertTrue(result.contains("success"));
        
        // 检查文件内容
        String newContent = Files.readString(tempPom);
        System.out.println("写回后的pom内容：\n" + newContent);
        assertTrue(newContent.contains("<exclusions>"));
        assertTrue(newContent.contains("<groupId>org.foo</groupId>"));
        assertTrue(newContent.contains("<artifactId>bar</artifactId>"));
        // 注释应该被保留
        assertTrue(newContent.contains("<!-- 这是一个注释 -->"));
        assertTrue(newContent.contains("<!-- 另一个注释 -->"));
    }


    
    @Test
    public void testInsertExclusionDependencyNotFound() throws Exception {
        // 构造一个简单的 pom.xml 内容
        String pom =
                "<project>\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>demo</artifactId>\n" +
                "  <version>1.0</version>\n" +
                "  <dependencies>\n" +
                "    <dependency>\n" +
                "      <groupId>org.example</groupId>\n" +
                "      <artifactId>root</artifactId>\n" +
                "      <version>1.0</version>\n" +
                "    </dependency>\n" +
                "  </dependencies>\n" +
                "</project>\n";
        // 写入临时文件
        Path tempPom = tempDir.resolve("test-pom-not-found.xml");
        Files.writeString(tempPom, pom);

        // 构造请求参数，使用不存在的依赖
        Map<String, Object> req = new HashMap<>();
        req.put("pomPath", tempPom.toString());
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.nonexistent");
        rootDep.put("artifactId", "nonexistent");
        rootDep.put("version", "1.0");
        req.put("rootDependency", rootDep);
        Map<String, String> targetDep = new HashMap<>();
        targetDep.put("groupId", "org.foo");
        targetDep.put("artifactId", "bar");
        req.put("targetDependency", targetDep);

        SimpleLanguageServer server = new SimpleLanguageServer();
        String result = server.insertExclusion(new com.google.gson.Gson().toJson(req)).get();
        System.out.println("DependencyNotFound 测试结果: " + result);
        assertTrue(result.contains("error"));
        assertTrue(result.contains("not found"));
        
        // 检查文件内容应该没有变化
        String newContent = Files.readString(tempPom);
        assertEquals(pom, newContent);
    }
    
    @Test
    public void testInsertExclusionWithVersionMatching() throws Exception {
        // 构造一个包含多个版本依赖的 pom.xml 内容
        String pom =
                "<project>\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>demo</artifactId>\n" +
                "  <version>1.0</version>\n" +
                "  <dependencies>\n" +
                "    <dependency>\n" +
                "      <groupId>org.example</groupId>\n" +
                "      <artifactId>root</artifactId>\n" +
                "      <version>1.0</version>\n" +
                "    </dependency>\n" +
                "    <dependency>\n" +
                "      <groupId>org.example</groupId>\n" +
                "      <artifactId>root</artifactId>\n" +
                "      <version>2.0</version>\n" +
                "    </dependency>\n" +
                "  </dependencies>\n" +
                "</project>\n";
        // 写入临时文件
        Path tempPom = tempDir.resolve("test-pom-multiple-versions.xml");
        Files.writeString(tempPom, pom);

        // 构造请求参数，指定版本1.0
        Map<String, Object> req = new HashMap<>();
        req.put("pomPath", tempPom.toString());
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.example");
        rootDep.put("artifactId", "root");
        rootDep.put("version", "1.0");
        req.put("rootDependency", rootDep);
        Map<String, String> targetDep = new HashMap<>();
        targetDep.put("groupId", "org.foo");
        targetDep.put("artifactId", "bar");
        req.put("targetDependency", targetDep);

        SimpleLanguageServer server = new SimpleLanguageServer();
        String result = server.insertExclusion(new com.google.gson.Gson().toJson(req)).get();
        assertTrue(result.contains("success"));

        // 检查写回后的pom内容
        String newContent = Files.readString(tempPom);
        System.out.println("写回后的pom内容：\n" + newContent);

        // 用正则分割每个dependency块
        java.util.regex.Pattern depPattern = java.util.regex.Pattern.compile("<dependency>([\\s\\S]*?)</dependency>");
        java.util.regex.Matcher matcher = depPattern.matcher(newContent);

        boolean found1 = false, found2 = false;
        while (matcher.find()) {
            String depBlock = matcher.group(1);
            if (depBlock.contains("<version>1.0</version>")) {
                found1 = true;
                assertTrue(depBlock.contains("<exclusions>"), "1.0依赖块应包含<exclusions>");
                assertTrue(depBlock.contains("<groupId>org.foo</groupId>"));
                assertTrue(depBlock.contains("<artifactId>bar</artifactId>"));
            }
            if (depBlock.contains("<version>2.0</version>")) {
                found2 = true;
                assertFalse(depBlock.contains("<exclusions>"), "2.0依赖块不应包含<exclusions>");
            }
        }
        assertTrue(found1, "应找到1.0依赖块");
        assertTrue(found2, "应找到2.0依赖块");
    }

    @Test
    public void testInsertExclusion() throws Exception {
        // 构造一个简单的 pom.xml 内容
        String pom =
                "<project>\n" +
                "  <modelVersion>4.0.0</modelVersion>\n" +
                "  <groupId>demo</groupId>\n" +
                "  <artifactId>demo</artifactId>\n" +
                "  <version>1.0</version>\n" +
                "  <dependencies>\n" +
                "    <dependency>\n" +
                "      <groupId>org.example</groupId>\n" +
                "      <artifactId>root</artifactId>\n" +
                "      <version>1.0</version>\n" +
                "    </dependency>\n" +
                "  </dependencies>\n" +
                "</project>\n";
        // 写入临时文件
        Path tempPom = tempDir.resolve("test-pom-old-method.xml");
        Files.writeString(tempPom, pom);

        // 构造请求参数
        Map<String, Object> req = new HashMap<>();
        req.put("pomPath", tempPom.toString());
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.example");
        rootDep.put("artifactId", "root");
        rootDep.put("version", "1.0");
        req.put("rootDependency", rootDep);
        Map<String, String> targetDep = new HashMap<>();
        targetDep.put("groupId", "org.foo");
        targetDep.put("artifactId", "bar");
        req.put("targetDependency", targetDep);

        SimpleLanguageServer server = new SimpleLanguageServer();
        String result = server.insertExclusion(new com.google.gson.Gson().toJson(req)).get();
        System.out.println("testInsertExclusion 测试结果: " + result);
        assertTrue(result.contains("success"));
        assertTrue(result.contains("highlightLine"));
        // 检查文件内容
        String newContent = Files.readString(tempPom);
        assertTrue(newContent.contains("<exclusions>"));
        assertTrue(newContent.contains("<groupId>org.foo</groupId>"));
        assertTrue(newContent.contains("<artifactId>bar</artifactId>"));
    }
} 