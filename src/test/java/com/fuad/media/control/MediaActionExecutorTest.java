package com.fuad.media.control;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class MediaActionExecutorTest {
    @ParameterizedTest
    @EnumSource(MediaAction.class)
    void unavailableExecutorShouldCompleteEveryActionAsClosed(MediaAction action) throws Exception {
        MediaActionResult result = MediaActionExecutor.unavailable().executeAsync(action)
                .toCompletableFuture().get();

        assertEquals(new MediaActionResult(action, MediaActionStatus.CLOSED), result);
        assertFalse(result.successful());
    }

    @Test
    void unavailableExecutorShouldRejectNullActions() {
        assertThrows(NullPointerException.class, () -> MediaActionExecutor.unavailable().executeAsync(null));
    }

    @ParameterizedTest
    @EnumSource(MediaActionStatus.class)
    void onlyAcknowledgedResultsShouldBeSuccessful(MediaActionStatus status) {
        assertEquals(status == MediaActionStatus.ACKNOWLEDGED,
                new MediaActionResult(MediaAction.NEXT, status).successful());
    }
}
