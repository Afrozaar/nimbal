package com.afrozaar.nimbal.core;

import static java.lang.String.format;

public class MavenCoords {
    private String groupId;
    private String artifactId;
    private String version;

    public MavenCoords(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getAsString() {
        //com.afrozaar.ashes.module:ashes-sbp-scheduled:jar:module:0.4.0-SNAPSHOT
        return format("%s:%s:%s", groupId, artifactId, version);
    }

    @Override
    public String toString() {
        return "MavenCoords [groupId=" + groupId + ", artifactId=" + artifactId + ", version=" + version + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((artifactId == null) ? 0 : artifactId.hashCode());
        result = prime * result + ((groupId == null) ? 0 : groupId.hashCode());
        result = prime * result + ((version == null) ? 0 : version.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        MavenCoords other = (MavenCoords) obj;
        if (artifactId == null) {
            if (other.artifactId != null)
                return false;
        } else if (!artifactId.equals(other.artifactId))
            return false;
        if (groupId == null) {
            if (other.groupId != null)
                return false;
        } else if (!groupId.equals(other.groupId))
            return false;
        if (version == null) {
            if (other.version != null)
                return false;
        } else if (!version.equals(other.version))
            return false;
        return true;
    }

    public boolean isStable() {
        return !isSnapshot();
    }

    public boolean isSnapshot() {
        return version != null && version.endsWith("SNAPSHOT");
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
}