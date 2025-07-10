package nd.mavenassistant.model;

public class ArtifactGav {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String moduleName; // 可选，JPMS模块名

    public ArtifactGav(String groupId, String artifactId, String version, String moduleName) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.moduleName = moduleName;
    }

    public ArtifactGav(String groupId, String artifactId, String version) {
        this(groupId, artifactId, version, null);
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

    @Override
    public String toString() {
        return groupId + ":" + artifactId + ":" + version +
                (moduleName != null ? " -- module " + moduleName : "");
    }
} 