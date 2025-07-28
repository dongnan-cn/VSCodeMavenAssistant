package nd.mavenassistant.lsp;

/**
 * 测试常量类 - 存储测试中常用的依赖信息和路径
 */
public class TestConstants {
    
    // Maven Resolver 相关常量
    public static final String MAVEN_RESOLVER_GROUP_ID = "org.apache.maven.resolver";
    public static final String MAVEN_RESOLVER_ARTIFACT_ID = "maven-resolver-connector-basic";
    
    // Spring 相关常量
    public static final String SPRING_CORE_GROUP_ID = "org.springframework";
    public static final String SPRING_CORE_ARTIFACT_ID = "spring-core";
    public static final String SPRING_CORE_KEY = "org.springframework:spring-core";
    
    // Commons 相关常量
    public static final String COMMONS_LANG3_GROUP_ID = "org.apache.commons";
    public static final String COMMONS_LANG3_ARTIFACT_ID = "commons-lang3";
    public static final String COMMONS_LANG3_KEY = "org.apache.commons:commons-lang3";
    
    // 测试用依赖常量
    public static final String TEST_FOO_GROUP_ID = "org.foo";
    public static final String TEST_BAR_ARTIFACT_ID = "bar";
    public static final String TEST_BAZ_GROUP_ID = "org.baz";
    public static final String TEST_QUX_ARTIFACT_ID = "qux";
    public static final String TEST_HELLO_GROUP_ID = "org.hello";
    public static final String TEST_WORLD_ARTIFACT_ID = "world";
    
    // 不存在的依赖常量
    public static final String NONEXISTENT_GROUP_ID = "org.nonexistent";
    public static final String NONEXISTENT_ARTIFACT_ID = "nonexistent";
    public static final String NONEXISTENT_VERSION = "1.0";
    
    // 文件路径常量
    public static final String TEST_POM_PATH = "src/test/resources/test-pom.xml";
    
    // 排除项常量
    public static final String COMMONS_LOGGING_EXCLUSION = "commons-logging:commons-logging";
    public static final String SPRING_JCL_EXCLUSION = "org.springframework:spring-jcl";
    public static final String JUNIT_EXCLUSION = "junit:junit";
    
    // 私有构造函数，防止实例化
    private TestConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}