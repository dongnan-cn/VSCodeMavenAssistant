package nd.mavenassistant.model;

public class ArtifactGav {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String moduleName; // 可选，JPMS模块名
    private final String scope; // 依赖范围，如compile、test等

    /**
     * 全参数构造方法
     * @param groupId 组ID
     * @param artifactId 构件ID
     * @param version 版本号
     * @param moduleName JPMS模块名，可为null
     * @param scope 依赖范围
     */
    public ArtifactGav(String groupId, String artifactId, String version, String moduleName, String scope) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.moduleName = moduleName;
        this.scope = scope;
    }

    /**
     * 无moduleName和scope的构造方法
     */
    public ArtifactGav(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null, null);
    }

    /**
     * 无scope的构造方法
     */
    public ArtifactGav(String groupId, String artifactId, String version, String moduleName) {
        this(groupId, artifactId, version, moduleName, null);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public String getModuleName() {
        return moduleName;
    }

    /**
     * 获取scope
     * @return 依赖范围
     */
    public String getScope() {
        return scope;
    }

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version +
                (moduleName != null ? " -- module " + moduleName : "") +
                (scope != null ? " -- scope " + scope : "");
    }
} 