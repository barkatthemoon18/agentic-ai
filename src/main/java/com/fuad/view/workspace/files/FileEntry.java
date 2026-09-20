package com.fuad.view.workspace.files;

public record FileEntry(
        String name,
        FileEntryType entryType,
        String size,
        String modified) {

    public boolean isDirectory() {
        return entryType == FileEntryType.DIRECTORY;
    }
}
