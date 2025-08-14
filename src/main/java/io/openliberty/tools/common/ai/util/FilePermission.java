package io.openliberty.tools.common.ai.util;

public enum FilePermission {
    READ("Read permissions"),
    WRITE("Write permissions");

    private String description;

    FilePermission(String description) {
        this.description = description;
    }

    public String getDescription() {
        return this.description;
    }

}
