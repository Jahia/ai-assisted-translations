package org.jahia.community.translation.assisted.service.glossary;

public class GlossaryFileDescriptor {

    private final String path;
    private final long lastModified;

    public GlossaryFileDescriptor(String path, long lastModified) {
        this.path = path;
        this.lastModified = lastModified;
    }

    public String getPath() {
        return path;
    }

    public long getLastModified() {
        return lastModified;
    }
}

