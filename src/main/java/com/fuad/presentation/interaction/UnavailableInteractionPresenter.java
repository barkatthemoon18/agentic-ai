package com.fuad.presentation.interaction;

import com.fuad.interaction.InteractionPresenter;
import com.fuad.interaction.InteractionRequest;
import com.fuad.interaction.InteractionResponder;

import java.util.UUID;

public final class UnavailableInteractionPresenter implements InteractionPresenter {
    private final String reason;

    public UnavailableInteractionPresenter(String reason) {
        this.reason = reason;
    }

    @Override
    public <T> void present(UUID sessionId, InteractionRequest<T> request,
                            InteractionResponder<T> responder) {
        responder.unavailable(sessionId, reason);
    }

    @Override
    public void dismiss(UUID sessionId) {
    }
}
