package nd.mavenassistant.lsp;

import nd.mavenassistant.utils.MavenModelUtils;
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
import java.util.Set;

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
        // 复制 test-pom.xml 到临时文件
        Path sourcePom = Path.of("src/test/resources/test-pom.xml").toAbsolutePath();
        Path tempPom = tempDir.resolve("test-pom-with-comments.xml");
        Files.copy(sourcePom, tempPom);

        // 构造请求参数，使用 test-pom.xml 中的实际依赖
        Map<String, Object> req = new HashMap<>();
        req.put("pomPath", tempPom.toString());
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.apache.maven.resolver");
        rootDep.put("artifactId", "maven-resolver-connector-basic");
        // 不指定版本，让系统自动匹配
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
        // 原始依赖应该被保留
        assertTrue(newContent.contains("org.apache.maven.resolver"));
    }


    
    @Test
    public void testInsertExclusionDependencyNotFound() throws Exception {
        // 使用test-pom.xml文件，测试不存在的依赖
        Path sourcePom = Path.of("src/test/resources/test-pom.xml").toAbsolutePath();
        Path tempPom = tempDir.resolve("test-pom-not-found.xml");
        Files.copy(sourcePom, tempPom);
        
        // 保存原始内容用于后续比较
        String originalContent = Files.readString(tempPom);

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
        assertEquals(originalContent, newContent);
    }
    
    @Test
    public void testInsertExclusionWithVersionMatching() throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path sourcePom = Path.of("src/test/resources/test-pom.xml").toAbsolutePath();
        Path tempPom = tempDir.resolve("test-pom-multiple-versions.xml");
        Files.copy(sourcePom, tempPom);

        // 构造请求参数，使用 test-pom.xml 中的实际依赖
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.apache.maven.resolver");
        rootDep.put("artifactId", "maven-resolver-connector-basic");
        // 不指定版本，让系统自动匹配

        // 插入第一个exclusion
        Map<String, Object> req1 = new HashMap<>();
        req1.put("pomPath", tempPom.toString());
        req1.put("rootDependency", rootDep);
        Map<String, String> targetDep1 = new HashMap<>();
        targetDep1.put("groupId", "org.foo");
        targetDep1.put("artifactId", "bar");
        req1.put("targetDependency", targetDep1);

        SimpleLanguageServer server = new SimpleLanguageServer();
        String result1 = server.insertExclusion(new com.google.gson.Gson().toJson(req1)).get();
        assertTrue(result1.contains("success"));

        // 再插入第二个exclusion
        Map<String, Object> req2 = new HashMap<>();
        req2.put("pomPath", tempPom.toString());
        req2.put("rootDependency", rootDep);
        Map<String, String> targetDep2 = new HashMap<>();
        targetDep2.put("groupId", "org.hello");
        targetDep2.put("artifactId", "world");
        req2.put("targetDependency", targetDep2);
        String result2 = server.insertExclusion(new com.google.gson.Gson().toJson(req2)).get();
        assertTrue(result2.contains("success"));

        // 检查写回后的pom内容
        String newContent = Files.readString(tempPom);
        System.out.println("写回后的pom内容：\n" + newContent);

        // 检查 maven-resolver-connector-basic 依赖块中包含了两个exclusion
        assertTrue(newContent.contains("<exclusions>"), "应包含<exclusions>标签");
        assertTrue(newContent.contains("<groupId>org.foo</groupId>"), "应包含第一个exclusion");
        assertTrue(newContent.contains("<artifactId>bar</artifactId>"), "应包含第一个exclusion");
        assertTrue(newContent.contains("<groupId>org.hello</groupId>"), "应包含第二个exclusion");
        assertTrue(newContent.contains("<artifactId>world</artifactId>"), "应包含第二个exclusion");
    }

    @Test
    public void testInsertExclusion() throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path sourcePom = Path.of("src/test/resources/test-pom.xml").toAbsolutePath();
        Path tempPom = tempDir.resolve("test-pom-old-method.xml");
        Files.copy(sourcePom, tempPom);

        // 构造请求参数，使用 test-pom.xml 中的实际依赖
        Map<String, Object> req = new HashMap<>();
        req.put("pomPath", tempPom.toString());
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.apache.maven.resolver");
        rootDep.put("artifactId", "maven-resolver-connector-basic");
        // 不指定版本，让系统自动匹配
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

    /**
     * 测试插入n个exclusion
     * @param n 要插入的exclusion数量
     */
    public void testInsertMultipleExclusions(int n) throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path sourcePom = Path.of("src/test/resources/test-pom.xml").toAbsolutePath();
        Path tempPom = tempDir.resolve("test-pom-multi-exclusions.xml");
        Files.copy(sourcePom, tempPom);

        // 使用 test-pom.xml 中的实际依赖
        Map<String, String> rootDep = new HashMap<>();
        rootDep.put("groupId", "org.apache.maven.resolver");
        rootDep.put("artifactId", "maven-resolver-connector-basic");
        // 不指定版本，让系统自动匹配

        SimpleLanguageServer server = new SimpleLanguageServer();
        for (int i = 1; i <= n; i++) {
            Map<String, Object> req = new HashMap<>();
            req.put("pomPath", tempPom.toString());
            req.put("rootDependency", rootDep);
            Map<String, String> targetDep = new HashMap<>();
            targetDep.put("groupId", "org.foo" + i);
            targetDep.put("artifactId", "bar" + i);
            req.put("targetDependency", targetDep);
            
            String requestJson = new com.google.gson.Gson().toJson(req);
            System.out.println("第" + i + "次请求: " + requestJson);
            String result = server.insertExclusion(requestJson).get();
            System.out.println("第" + i + "次结果: " + result);
            assertTrue(result.contains("success"), "第" + i + "次插入应该成功");
        }

        // 检查写回后的pom内容
        String newContent = Files.readString(tempPom);
        System.out.println("写回后的pom内容：\n" + newContent);
        // 检查所有exclusion都存在
        for (int i = 1; i <= n; i++) {
            assertTrue(newContent.contains("<groupId>org.foo" + i + "</groupId>"), "应包含第" + i + "个exclusion");
            assertTrue(newContent.contains("<artifactId>bar" + i + "</artifactId>"), "应包含第" + i + "个exclusion");
        }
    }

    @Test
    public void testInsertMultipleExclusions5() throws Exception {
        testInsertMultipleExclusions(5);
    }

    /**
     * 测试buildExclusionMap方法 - 解析直接依赖中的exclusions
     */
    @Test
    public void testBuildExclusionMapWithDirectDependencies() throws Exception {
        // 使用test-pom.xml文件，该文件包含exclusions的依赖
        Path sourcePom = Path.of("src/test/resources/test-pom.xml");
        Path tempPom = tempDir.resolve("test-pom-exclusions.xml");
        Files.copy(sourcePom, tempPom);
        
        // 使用Maven Model API解析pom.xml
        org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
        Model model = reader.read(Files.newInputStream(tempPom));
        
        // 直接调用MavenModelUtils.buildExclusionMap方法
        Map<String, Set<String>> exclusionMap = MavenModelUtils.buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap不应该为null");
        assertEquals(2, exclusionMap.size(), "应该有2个依赖包含exclusions");
        
        // 验证spring-core的exclusions
        String springCoreKey = "org.springframework:spring-core";
        assertTrue(exclusionMap.containsKey(springCoreKey), "应该包含spring-core的exclusions");
        Set<String> springCoreExclusions = exclusionMap.get(springCoreKey);
        assertEquals(2, springCoreExclusions.size(), "spring-core应该有2个exclusions");
        assertTrue(springCoreExclusions.contains("commons-logging:commons-logging"), "应该排除commons-logging");
        assertTrue(springCoreExclusions.contains("org.springframework:spring-jcl"), "应该排除spring-jcl");
        
        // 验证commons-lang3的exclusions
        String commonsLang3Key = "org.apache.commons:commons-lang3";
        assertTrue(exclusionMap.containsKey(commonsLang3Key), "应该包含commons-lang3的exclusions");
        Set<String> commonsLang3Exclusions = exclusionMap.get(commonsLang3Key);
        assertEquals(1, commonsLang3Exclusions.size(), "commons-lang3应该有1个exclusion");
        assertTrue(commonsLang3Exclusions.contains("junit:junit"), "应该排除junit");
        
        System.out.println("解析到的exclusionMap: " + exclusionMap);
    }

    /**
     * 测试buildExclusionMap方法 - 解析dependencyManagement中的exclusions
     */
    @Test
    public void testBuildExclusionMapWithDependencyManagement() throws Exception {
        // 使用test-pom.xml文件进行测试
        Path sourcePom = Path.of("src/test/resources/test-pom.xml");
        Path tempPom = tempDir.resolve("test-pom.xml");
        Files.copy(sourcePom, tempPom);
        
        // 使用Maven Model API解析pom.xml
        org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
        Model model = reader.read(Files.newInputStream(tempPom));
        
        // 直接调用MavenModelUtils.buildExclusionMap方法
        Map<String, Set<String>> exclusionMap = MavenModelUtils.buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap不应该为null");
        assertEquals(2, exclusionMap.size(), "应该有2个依赖包含exclusions");
        
        // 验证spring-core的exclusions
        String springCoreKey = "org.springframework:spring-core";
        assertTrue(exclusionMap.containsKey(springCoreKey), "应该包含spring-core的exclusions");
        Set<String> springCoreExclusions = exclusionMap.get(springCoreKey);
        assertEquals(2, springCoreExclusions.size(), "spring-core应该有2个exclusions");
        assertTrue(springCoreExclusions.contains("commons-logging:commons-logging"), "应该排除commons-logging");
        assertTrue(springCoreExclusions.contains("org.springframework:spring-jcl"), "应该排除spring-jcl");
        
        // 验证commons-lang3的exclusions
        String commonsLang3Key = "org.apache.commons:commons-lang3";
        assertTrue(exclusionMap.containsKey(commonsLang3Key), "应该包含commons-lang3的exclusions");
        Set<String> commonsLang3Exclusions = exclusionMap.get(commonsLang3Key);
        assertEquals(1, commonsLang3Exclusions.size(), "commons-lang3应该有1个exclusion");
        assertTrue(commonsLang3Exclusions.contains("junit:junit"), "应该排除junit");
        
        System.out.println("解析到的exclusionMap: " + exclusionMap);
    }

    /**
     * 测试buildExclusionMap方法 - 处理没有exclusions的情况
     */
    @Test
    public void testBuildExclusionMapWithNoExclusions() throws Exception {
        // 创建一个不包含exclusions的简单POM内容
        String simplePom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>demo</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencies>
                        <dependency>
                            <groupId>org.apache.commons</groupId>
                            <artifactId>commons-lang3</artifactId>
                            <version>3.12.0</version>
                        </dependency>
                    </dependencies>
                </project>
                """;
        Path tempPom = tempDir.resolve("test-pom-no-exclusions.xml");
        Files.writeString(tempPom, simplePom);
        
        // 使用Maven Model API解析pom.xml
        org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
        Model model = reader.read(Files.newInputStream(tempPom));
        
        // 直接调用MavenModelUtils.buildExclusionMap方法
        Map<String, Set<String>> exclusionMap = MavenModelUtils.buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap不应该为null");
        assertTrue(exclusionMap.isEmpty(), "没有exclusions时，exclusionMap应该为空");
        
        System.out.println("解析到的exclusionMap: " + exclusionMap);
    }

    /**
     * 测试buildExclusionMap方法 - 处理同一依赖在dependencies和dependencyManagement中都有exclusions的情况
     */
    @Test
    public void testBuildExclusionMapWithMergedExclusions() throws Exception {
        // 创建一个包含dependencyManagement和dependencies中都有exclusions的POM内容
        String mergedPom = """
                <?xml version="1.0" encoding="UTF-8"?>
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                    <modelVersion>4.0.0</modelVersion>
                    <groupId>demo</groupId>
                    <artifactId>demo</artifactId>
                    <version>1.0</version>
                    <dependencyManagement>
                        <dependencies>
                            <dependency>
                                <groupId>org.springframework</groupId>
                                <artifactId>spring-core</artifactId>
                                <version>5.3.21</version>
                                <exclusions>
                                    <exclusion>
                                        <groupId>commons-logging</groupId>
                                        <artifactId>commons-logging</artifactId>
                                    </exclusion>
                                </exclusions>
                            </dependency>
                        </dependencies>
                    </dependencyManagement>
                    <dependencies>
                        <dependency>
                            <groupId>org.springframework</groupId>
                            <artifactId>spring-core</artifactId>
                            <exclusions>
                                <exclusion>
                                    <groupId>org.springframework</groupId>
                                    <artifactId>spring-jcl</artifactId>
                                </exclusion>
                            </exclusions>
                        </dependency>
                    </dependencies>
                </project>
                """;
        Path tempPom = tempDir.resolve("test-pom-merged-exclusions.xml");
        Files.writeString(tempPom, mergedPom);
        
        // 使用Maven Model API解析pom.xml
        org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
        Model model = reader.read(Files.newInputStream(tempPom));
        
        // 直接调用MavenModelUtils.buildExclusionMap方法
        Map<String, Set<String>> exclusionMap = MavenModelUtils.buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap不应该为null");
        assertEquals(1, exclusionMap.size(), "应该有1个依赖包含exclusions");
        
        // 验证spring-core的exclusions被合并了
        String springCoreKey = "org.springframework:spring-core";
        assertTrue(exclusionMap.containsKey(springCoreKey), "应该包含spring-core的exclusions");
        Set<String> springCoreExclusions = exclusionMap.get(springCoreKey);
        assertEquals(2, springCoreExclusions.size(), "spring-core应该有2个exclusions（合并后）");
        assertTrue(springCoreExclusions.contains("commons-logging:commons-logging"), "应该排除commons-logging（来自dependencyManagement）");
        assertTrue(springCoreExclusions.contains("org.springframework:spring-jcl"), "应该排除spring-jcl（来自dependencies）");
        
        System.out.println("解析到的exclusionMap: " + exclusionMap);
    }
}