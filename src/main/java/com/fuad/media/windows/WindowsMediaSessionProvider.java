package com.fuad.media.windows;

import com.fuad.media.MediaSessionProvider;
import com.fuad.media.MediaSessionSnapshot;

public class WindowsMediaSessionProvider implements MediaSessionProvider {
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
