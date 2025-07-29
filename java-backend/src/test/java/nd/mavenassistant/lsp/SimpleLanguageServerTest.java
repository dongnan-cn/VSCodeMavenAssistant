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
import static nd.mavenassistant.lsp.TestConstants.*;

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

    // ==================== 辅助方法 ====================
    
    /**
     * 复制test-pom.xml到临时文件
     * @param fileName 临时文件名
     * @return 临时文件路径
     */
    private Path copyTestPomToTemp(String fileName) throws Exception {
        Path sourcePom = Path.of(TEST_POM_PATH).toAbsolutePath();
        Path tempPom = tempDir.resolve(fileName);
        Files.copy(sourcePom, tempPom);
        return tempPom;
    }
    
    /**
     * 创建依赖对象（不包含版本）
     * @param groupId 组ID
     * @param artifactId 构件ID
     * @return 依赖Map
     */
    private Map<String, String> createDependency(String groupId, String artifactId) {
        Map<String, String> dependency = new HashMap<>();
        dependency.put("groupId", groupId);
        dependency.put("artifactId", artifactId);
        return dependency;
    }
    
    /**
     * 创建依赖对象（包含版本）
     * @param groupId 组ID
     * @param artifactId 构件ID
     * @param version 版本
     * @return 依赖Map
     */
    private Map<String, String> createDependency(String groupId, String artifactId, String version) {
        Map<String, String> dependency = createDependency(groupId, artifactId);
        dependency.put("version", version);
        return dependency;
    }
    
    /**
     * 创建insertExclusion请求对象
     * @param pomPath POM文件路径
     * @param rootDep 根依赖
     * @param targetDep 目标依赖
     * @return 请求Map
     */
    private Map<String, Object> createInsertExclusionRequest(Path pomPath, Map<String, String> rootDep, Map<String, String> targetDep) {
        Map<String, Object> request = new HashMap<>();
        request.put("pomPath", pomPath.toString());
        request.put("rootDependency", rootDep);
        request.put("targetDependency", targetDep);
        return request;
    }
    
    /**
     * 解析Maven Model
     * @param pomPath POM文件路径
     * @return Maven Model对象
     */
    private Model parseModel(Path pomPath) throws Exception {
        org.apache.maven.model.io.xpp3.MavenXpp3Reader reader = new org.apache.maven.model.io.xpp3.MavenXpp3Reader();
        return reader.read(Files.newInputStream(pomPath));
    }
    
    /**
     * 构建排除映射
     * @param pomPath POM文件路径
     * @return 排除映射
     */
    private Map<String, Set<String>> buildExclusionMap(Path pomPath) throws Exception {
        Model model = parseModel(pomPath);
        return MavenModelUtils.buildExclusionMap(model);
    }
    
    /**
     * 构建排除映射
     * @param model Maven模型
     * @return 排除映射
     */
    private Map<String, Set<String>> buildExclusionMap(Model model) {
        return MavenModelUtils.buildExclusionMap(model);
    }
    
    /**
     * 调用insertExclusion方法
     * @param request 请求对象
     * @return 结果字符串
     */
    private String callInsertExclusion(Map<String, Object> request) throws Exception {
        SimpleLanguageServer server = createServer();
        return server.insertExclusion(new com.google.gson.Gson().toJson(request)).get();
    }
    
    /**
     * 创建服务器实例
     * @return SimpleLanguageServer实例
     */
    private SimpleLanguageServer createServer() {
        return new SimpleLanguageServer();
    }
    
    /**
     * 创建默认的Maven Resolver根依赖
     * @return Maven Resolver依赖Map
     */
    private Map<String, String> createDefaultRootDependency() {
        return createDependency(MAVEN_RESOLVER_GROUP_ID, MAVEN_RESOLVER_ARTIFACT_ID);
    }
    
    /**
     * 创建默认的测试目标依赖
     * @return 测试目标依赖Map
     */
    private Map<String, String> createDefaultTargetDependency() {
        return createDependency(TEST_FOO_GROUP_ID, TEST_BAR_ARTIFACT_ID);
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
        Path tempPom = copyTestPomToTemp("test-pom-with-comments.xml");

        // 构造请求参数，使用 test-pom.xml 中的实际依赖
        Map<String, String> rootDep = createDefaultRootDependency();
        Map<String, String> targetDep = createDefaultTargetDependency();
        Map<String, Object> req = createInsertExclusionRequest(tempPom, rootDep, targetDep);

        // 调用服务并验证结果
        String result = callInsertExclusion(req);
        //System.out.println("Test result: " + result);
        assertTrue(result.contains("success"));
        
        // 检查文件内容
        String newContent = Files.readString(tempPom);
        //System.out.println("POM content after writing back:\n" + newContent);
        assertTrue(newContent.contains("<exclusions>"));
        assertTrue(newContent.contains("<groupId>" + TEST_FOO_GROUP_ID + "</groupId>"));
        assertTrue(newContent.contains("<artifactId>" + TEST_BAR_ARTIFACT_ID + "</artifactId>"));
        // 原始依赖应该被保留
        assertTrue(newContent.contains(MAVEN_RESOLVER_GROUP_ID));
    }


    
    @Test
    public void testInsertExclusionDependencyNotFound() throws Exception {
        // 使用test-pom.xml文件，测试不存在的依赖
        Path tempPom = copyTestPomToTemp("test-pom-not-found.xml");
        
        // 保存原始内容用于后续比较
        String originalContent = Files.readString(tempPom);

        // 构造请求参数，使用不存在的依赖
        Map<String, String> rootDep = createDependency(NONEXISTENT_GROUP_ID, NONEXISTENT_ARTIFACT_ID, NONEXISTENT_VERSION);
        Map<String, String> targetDep = createDefaultTargetDependency();
        Map<String, Object> req = createInsertExclusionRequest(tempPom, rootDep, targetDep);

        // 调用服务并验证错误结果
        String result = callInsertExclusion(req);
        //System.out.println("DependencyNotFound test result: " + result);
        assertTrue(result.contains("error"));
        assertTrue(result.contains("not found"));
        
        // 检查文件内容应该没有变化
        String newContent = Files.readString(tempPom);
        assertEquals(originalContent, newContent);
    }
    
    @Test
    public void testInsertExclusionWithVersionMatching() throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path tempPom = copyTestPomToTemp("test-pom-multiple-versions.xml");

        // 构造请求参数，使用 test-pom.xml 中的实际依赖
        Map<String, String> rootDep = createDefaultRootDependency();

        // 插入第一个exclusion
        Map<String, String> targetDep1 = createDefaultTargetDependency();
        Map<String, Object> req1 = createInsertExclusionRequest(tempPom, rootDep, targetDep1);

        String result1 = callInsertExclusion(req1);
        assertTrue(result1.contains("success"));

        // 再插入第二个exclusion
        Map<String, String> targetDep2 = createDependency(TEST_HELLO_GROUP_ID, TEST_WORLD_ARTIFACT_ID);
        Map<String, Object> req2 = createInsertExclusionRequest(tempPom, rootDep, targetDep2);
        String result2 = callInsertExclusion(req2);
        assertTrue(result2.contains("success"));

        // 检查写回后的pom内容
        String newContent = Files.readString(tempPom);
        //System.out.println("POM content after writing back:\n" + newContent);

        // 检查 maven-resolver-connector-basic 依赖块中包含了两个exclusion
        assertTrue(newContent.contains("<exclusions>"), "Should contain <exclusions> tag");
        assertTrue(newContent.contains("<groupId>" + TEST_FOO_GROUP_ID + "</groupId>"), "Should contain first exclusion");
        assertTrue(newContent.contains("<artifactId>" + TEST_BAR_ARTIFACT_ID + "</artifactId>"), "Should contain first exclusion");
        assertTrue(newContent.contains("<groupId>" + TEST_HELLO_GROUP_ID + "</groupId>"), "Should contain second exclusion");
        assertTrue(newContent.contains("<artifactId>" + TEST_WORLD_ARTIFACT_ID + "</artifactId>"), "Should contain second exclusion");
    }

    @Test
    public void testInsertExclusion() throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path tempPom = copyTestPomToTemp("test-pom-old-method.xml");

        // 构造请求参数，使用 test-pom.xml 中的实际依赖
        Map<String, String> rootDep = createDefaultRootDependency();
        Map<String, String> targetDep = createDefaultTargetDependency();
        Map<String, Object> req = createInsertExclusionRequest(tempPom, rootDep, targetDep);

        // 调用服务并验证结果
        String result = callInsertExclusion(req);
        //System.out.println("testInsertExclusion test result: " + result);
        assertTrue(result.contains("success"));
        assertTrue(result.contains("highlightLine"));
        
        // 检查文件内容
        String newContent = Files.readString(tempPom);
        assertTrue(newContent.contains("<exclusions>"));
        assertTrue(newContent.contains("<groupId>" + TEST_FOO_GROUP_ID + "</groupId>"));
        assertTrue(newContent.contains("<artifactId>" + TEST_BAR_ARTIFACT_ID + "</artifactId>"));
    }

    @Test
    public void testInsertMultipleExclusions() throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path tempPom = copyTestPomToTemp("test-pom-multiple.xml");

        // 第一次插入排除项
        Map<String, String> rootDep = createDefaultRootDependency();
        Map<String, String> targetDep1 = createDefaultTargetDependency();
        Map<String, Object> req1 = createInsertExclusionRequest(tempPom, rootDep, targetDep1);

        String result1 = callInsertExclusion(req1);
        assertTrue(result1.contains("success"));

        // 第二次插入排除项
        Map<String, String> targetDep2 = createDependency(TEST_BAZ_GROUP_ID, TEST_QUX_ARTIFACT_ID);
        Map<String, Object> req2 = createInsertExclusionRequest(tempPom, rootDep, targetDep2);

        String result2 = callInsertExclusion(req2);
        assertTrue(result2.contains("success"));

        // 检查文件内容
        String newContent = Files.readString(tempPom);
        assertTrue(newContent.contains("<exclusions>"));
        assertTrue(newContent.contains("<groupId>" + TEST_FOO_GROUP_ID + "</groupId>"));
        assertTrue(newContent.contains("<artifactId>" + TEST_BAR_ARTIFACT_ID + "</artifactId>"));
        assertTrue(newContent.contains("<groupId>" + TEST_BAZ_GROUP_ID + "</groupId>"));
        assertTrue(newContent.contains("<artifactId>" + TEST_QUX_ARTIFACT_ID + "</artifactId>"));
    }

    /**
     * Test inserting n exclusions
     * @param n Number of exclusions to insert
     */
    public void testInsertMultipleExclusions(int n) throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path tempPom = copyTestPomToTemp("test-pom-multi-exclusions.xml");

        // 使用默认的根依赖
        Map<String, String> rootDep = createDefaultRootDependency();

        for (int i = 1; i <= n; i++) {
            Map<String, String> targetDep = createDependency("org.foo" + i, "bar" + i);
            Map<String, Object> req = createInsertExclusionRequest(tempPom, rootDep, targetDep);
            
            //String requestJson = new com.google.gson.Gson().toJson(req);
            //System.out.println("Request " + i + ": " + requestJson);
            String result = callInsertExclusion(req);
            //System.out.println("Result " + i + ": " + result);
            assertTrue(result.contains("success"), "Insertion " + i + " should succeed");
        }

        // 检查写回后的pom内容
        String newContent = Files.readString(tempPom);
        //System.out.println("POM content after writing back:\n" + newContent);
        // 检查所有exclusion都存在
        for (int i = 1; i <= n; i++) {
            assertTrue(newContent.contains("<groupId>org.foo" + i + "</groupId>"), "Should contain exclusion " + i);
            assertTrue(newContent.contains("<artifactId>bar" + i + "</artifactId>"), "Should contain exclusion " + i);
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
        // 复制 test-pom.xml 到临时文件
        Path tempPom = copyTestPomToTemp("test-pom-exclusions.xml");
        
        // 解析 Maven 模型并构建排除映射
        Model model = parseModel(tempPom);
        Map<String, Set<String>> exclusionMap = buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap should not be null");
        assertEquals(2, exclusionMap.size(), "Should have 2 dependencies containing exclusions");
        
        // 验证spring-core的exclusions
        String springCoreKey = "org.springframework:spring-core";
        assertTrue(exclusionMap.containsKey(springCoreKey), "Should contain spring-core exclusions");
        Set<String> springCoreExclusions = exclusionMap.get(springCoreKey);
        assertEquals(2, springCoreExclusions.size(), "spring-core should have 2 exclusions");
        assertTrue(springCoreExclusions.contains("commons-logging:commons-logging"), "Should exclude commons-logging");
        assertTrue(springCoreExclusions.contains("org.springframework:spring-jcl"), "Should exclude spring-jcl");
        
        // 验证commons-lang3的exclusions
        String commonsLang3Key = "org.apache.commons:commons-lang3";
        assertTrue(exclusionMap.containsKey(commonsLang3Key), "Should contain commons-lang3 exclusions");
        Set<String> commonsLang3Exclusions = exclusionMap.get(commonsLang3Key);
        assertEquals(1, commonsLang3Exclusions.size(), "commons-lang3 should have 1 exclusion");
        assertTrue(commonsLang3Exclusions.contains("junit:junit"), "Should exclude junit");
        
        //System.out.println("Parsed exclusionMap: " + exclusionMap);
    }

    /**
     * Test buildExclusionMap method - parsing exclusions in dependencyManagement
     */
    @Test
    public void testBuildExclusionMapWithDependencyManagement() throws Exception {
        // 复制 test-pom.xml 到临时文件
        Path tempPom = copyTestPomToTemp("test-pom.xml");
        
        // 解析 Maven 模型并构建排除映射
        Model model = parseModel(tempPom);
        Map<String, Set<String>> exclusionMap = buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap should not be null");
        assertEquals(2, exclusionMap.size(), "Should have 2 dependencies containing exclusions");
        
        // 验证spring-core的exclusions
        String springCoreKey = "org.springframework:spring-core";
        assertTrue(exclusionMap.containsKey(springCoreKey), "Should contain spring-core exclusions");
        Set<String> springCoreExclusions = exclusionMap.get(springCoreKey);
        assertEquals(2, springCoreExclusions.size(), "spring-core should have 2 exclusions");
        assertTrue(springCoreExclusions.contains("commons-logging:commons-logging"), "Should exclude commons-logging");
        assertTrue(springCoreExclusions.contains("org.springframework:spring-jcl"), "Should exclude spring-jcl");
        
        // 验证commons-lang3的exclusions
        String commonsLang3Key = "org.apache.commons:commons-lang3";
        assertTrue(exclusionMap.containsKey(commonsLang3Key), "Should contain commons-lang3 exclusions");
        Set<String> commonsLang3Exclusions = exclusionMap.get(commonsLang3Key);
        assertEquals(1, commonsLang3Exclusions.size(), "commons-lang3 should have 1 exclusion");
        assertTrue(commonsLang3Exclusions.contains("junit:junit"), "Should exclude junit");
        
        //System.out.println("Parsed exclusionMap: " + exclusionMap);
    }

    /**
     * Test buildExclusionMap method - handling cases with no exclusions
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
        
        // 解析 Maven 模型并构建排除映射
        Model model = parseModel(tempPom);
        Map<String, Set<String>> exclusionMap = buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap should not be null");
        assertTrue(exclusionMap.isEmpty(), "exclusionMap should be empty when there are no exclusions");
        
        //System.out.println("Parsed exclusionMap: " + exclusionMap);
    }

    /**
     * Test buildExclusionMap method - handling cases where the same dependency has exclusions in both dependencies and dependencyManagement
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
        
        // 解析 Maven 模型并构建排除映射
        Model model = parseModel(tempPom);
        Map<String, Set<String>> exclusionMap = buildExclusionMap(model);
        
        // 验证解析结果
        assertNotNull(exclusionMap, "exclusionMap should not be null");
        assertEquals(1, exclusionMap.size(), "Should have 1 dependency containing exclusions");
        
        // 验证spring-core的exclusions被合并了
        String springCoreKey = "org.springframework:spring-core";
        assertTrue(exclusionMap.containsKey(springCoreKey), "Should contain spring-core exclusions");
        Set<String> springCoreExclusions = exclusionMap.get(springCoreKey);
        assertEquals(2, springCoreExclusions.size(), "spring-core should have 2 exclusions (after merging)");
        assertTrue(springCoreExclusions.contains("commons-logging:commons-logging"), "Should exclude commons-logging (from dependencyManagement)");
        assertTrue(springCoreExclusions.contains("org.springframework:spring-jcl"), "Should exclude spring-jcl (from dependencies)");
        
        //System.out.println("Parsed exclusionMap: " + exclusionMap);
    }
}