package com.fuad.view.workspace.media.windows;

import com.fuad.view.workspace.media.MediaSessionProvider;
import com.fuad.view.workspace.media.MediaSessionSnapshot;

public class WindowsMediaSessionprovider implements MediaSessionProvider {
    @Override
    public MediaSessionSnapshot current() {
        return MediaSessionSnapshot.unavailable();
    }

    @Override
    public boolean playPause() {
        return false;
    }

    @Override
    public boolean next() {
        return false;
    }

    @Override
    public boolean previous() {
        return false;
    }
}
