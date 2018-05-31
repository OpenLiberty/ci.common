package net.wasdev.wlp.common.arquillian.util;

public class ArtifactCoordinates {

    private final String groupId;
    private final String artifactId;
    
    public ArtifactCoordinates(String groupId, String artifactId) {
        this.groupId = groupId;
        this.artifactId = artifactId;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public String getArtifactId() {
        return artifactId;
    }
    
}
