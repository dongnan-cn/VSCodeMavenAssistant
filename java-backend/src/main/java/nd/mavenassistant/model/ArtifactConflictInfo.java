package nd.mavenassistant.model;

public class ArtifactConflictInfo {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final String scope;
    private final boolean droppedByConflict;

    public ArtifactConflictInfo(String groupId, String artifactId, String version, String scope, boolean droppedByConflict) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.scope = scope;
        this.droppedByConflict = droppedByConflict;
    }

    public String getGroupId() { return groupId; }
    public String getArtifactId() { return artifactId; }
    public String getVersion() { return version; }
    public String getScope() { return scope; }
    public boolean isDroppedByConflict() { return droppedByConflict; }
} 