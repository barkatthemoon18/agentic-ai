package com.fuad.interaction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class InteractionVoiceRouterTest {
    private final TrackingPresenter presenter = new TrackingPresenter();
    private final DefaultInteractionService service = new DefaultInteractionService(presenter, InteractionLifecycleListener.noop());
    private final InteractionVoiceRouter router = new InteractionVoiceRouter(service);

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void ambiguousNormalizedAliasShouldRemainUnresolved() {
        var stage = service.request(new ChoiceRequest(Optional.empty(), "Elige",
                Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                FocusRequirement.PASSIVE, List.of(
                new ChoiceOption("a", "Studio A", List.of("studio")),
                new ChoiceOption("b", "Studio B", List.of("stúdio")))));
        presenter.visible();

        assertEquals(VoiceRouteOutcome.UNRESOLVABLE, router.route("Studio"));
        assertFalse(stage.toCompletableFuture().isDone());
    }

    @Test
    void universalCancelShouldWorkBeforeVisibleAndWithoutVoiceModality() throws Exception {
        var stage = service.request(new ConfirmationRequest(Optional.empty(), "Confirma",
                Set.of(InputModality.TOUCH), Optional.empty(), FocusRequirement.PASSIVE,
                "Sí", "No"));

        assertEquals(VoiceRouteOutcome.CANCELLED, router.route("Olvídalo"));
        assertEquals(InteractionOutcome.CANCELLED,
                stage.toCompletableFuture().get(1, TimeUnit.SECONDS).outcome());
    }

    @Test
    void uniqueAliasAndOrdinalShouldResolveExactlyOneOption() throws Exception {
        ChoiceRequest request = new ChoiceRequest(Optional.empty(), "Elige",
                Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                FocusRequirement.PASSIVE, List.of(
                new ChoiceOption("idea", "IntelliJ IDEA", List.of("idea")),
                new ChoiceOption("code", "Visual Studio Code", List.of("code"))));
        var stage = service.request(request);
        presenter.visible();

        assertEquals(VoiceRouteOutcome.RESOLVED, router.route("la segunda"));
        assertEquals("code", stage.toCompletableFuture().get().value().orElseThrow());
    }

    @Test
    void domainResolverShouldKeepInteractionPendingUntilItResolvesAnActiveOption() throws Exception {
        ChoiceRequest request = new ChoiceRequest(Optional.empty(), "Elige",
                Set.of(InputModality.TOUCH, InputModality.VOICE), Optional.empty(),
                FocusRequirement.PASSIVE, List.of(
                new ChoiceOption("photo", "Topaz Photo AI", List.of()),
                new ChoiceOption("video", "Topaz Video AI", List.of())),
                Optional.of(transcription -> switch (transcription.toLowerCase()) {
                    case "topas" -> ChoiceVoiceResolution.ambiguous();
                    case "topas photo" -> ChoiceVoiceResolution.resolved("photo");
                    case "fuera" -> ChoiceVoiceResolution.resolved("outside");
                    default -> ChoiceVoiceResolution.unknown();
                }));
        var stage = service.request(request);
        presenter.visible();

        assertEquals(VoiceRouteOutcome.UNRESOLVABLE, router.route("Topas"));
        assertEquals(VoiceRouteOutcome.UNRESOLVABLE, router.route("desconocida"));
        assertEquals(VoiceRouteOutcome.UNRESOLVABLE, router.route("fuera"));
        assertFalse(stage.toCompletableFuture().isDone());

        assertEquals(VoiceRouteOutcome.RESOLVED, router.route("Topas Photo"));
        assertEquals("photo", stage.toCompletableFuture().get(1, TimeUnit.SECONDS)
                .value().orElseThrow());
    }

    private static final class TrackingPresenter implements InteractionPresenter {
        private UUID id;
        private InteractionResponder<?> responder;

        @Override
        public <T> void present(UUID sessionId, InteractionRequest<T> request,
                                InteractionResponder<T> responder) {
            id = sessionId;
            this.responder = responder;
        }

        @Override
        public void dismiss(UUID sessionId) {
        }

        void visible() {
            responder.visible(id);
        }
    }
}
