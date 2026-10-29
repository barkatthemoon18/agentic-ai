package com.fuad.media.context;

import com.fuad.media.control.MediaAction;
import com.fuad.media.control.MediaActionExecutor;
import com.fuad.media.control.MediaActionHandler;
import com.fuad.media.control.MediaActionResult;

import java.util.concurrent.CompletionStage;

public final class MediaContextService implements MediaContextProvider, MediaActionExecutor, MediaActionHandler, AutoCloseable {

    @Override
    public MediaContextSnapshot current() {
        return null;
    }

    @Override
    public CompletionStage<MediaActionResult> executeAsync(MediaAction action) {
        return null;
    }

    @Override
    public boolean submit(MediaAction action) {
        return false;
    }

    @Override
    public void close() throws Exception {

    }
}
