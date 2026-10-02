package com.fuad.audio.output;

import com.fuad.presentation.media.MediaWorkspaceSnapshot;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public final class MediaExclusiveAudioDetector {
    Supplier<MediaWorkspaceSnapshot>  mediaWorkspaceSnapshotSupplier;

    public MediaExclusiveAudioDetector() {
        this(MediaWorkspaceSnapshot::unavailable);
    }

    public MediaExclusiveAudioDetector(Supplier<MediaWorkspaceSnapshot> supplier) {
        this.mediaWorkspaceSnapshotSupplier = Objects.requireNonNull(supplier);
    }

    public boolean isOutputReserved() {
        if (!isTidalRunning()) {
            return false;
        }
        return mediaWorkspaceSnapshotSupplier.get().mediaPlaybackState().map(mediaPlaybackState -> switch (mediaPlaybackState) {
            case PLAYING, UNKNOWN -> true;
            case PAUSED, STOPPED -> false;
        }).orElse(true);
    }

    private boolean isTidalRunning() {
        return ProcessHandle.allProcesses()
                .map(ProcessHandle::info)
                .map(ProcessHandle.Info::command)
                .flatMap(Optional::stream)
                .map(Path::of)
                .map(Path::getFileName)
                .map(Path::toString)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .anyMatch(name -> name.equals("tidal.exe"));
    }
}
