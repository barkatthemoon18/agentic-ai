package com.fuad.interaction;

import java.util.UUID;

public interface InteractionResponder<T> {
    void visible(UUID sessionId);

    void submit(UUID sessionId, T value, InputModality modality);

    void cancel(UUID sessionId, InputModality modality);

    void unavailable(UUID sessionId, String reason);
}
