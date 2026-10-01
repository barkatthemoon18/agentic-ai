package com.fuad.view.workspace.files;

import java.util.List;

public record FilesWorkspaceSnapshot(
        String currentPath,
        List<FileEntry> entries) {
    /* Empty intentionally */
}
