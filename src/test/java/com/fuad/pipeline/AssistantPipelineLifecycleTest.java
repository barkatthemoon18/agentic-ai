package com.fuad.pipeline;

import com.fuad.activation.ActivationResult;
import com.fuad.assistant.*;
import com.fuad.assistant.session.ConversationSnapshot;
import com.fuad.assistant.skills.*;
import com.fuad.enums.ActivationType;
import com.fuad.enums.Capability;
import com.fuad.enums.ConversationPolicy;
import com.fuad.interaction.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AssistantPipelineLifecycleTest {
    private final SkillRouter router = mock(SkillRouter.class);
    private final List<Event> events = new ArrayList<>();
    private final AssistantPipeline pipeline = new AssistantPipeline(router, new AssistantExecutionLifecycleListener() {
        @Override public void onExecutionStarted(UUID id, Capability capability) { events.add(new Event(id, capability, true)); }
        @Override public void onExecutionCompleted(UUID id, Capability capability) { events.add(new Event(id, capability, false)); }
    });

    @Test
    void synchronousTurnShouldPublishMatchingLifecycleAndPropagateSkillPolicy() {
        Skill skill = skill(() -> SkillExecution.completed(new AssistantResult("done")));
        when(router.route("command")).thenReturn(new SkillRoute(Capability.GENERAL, skill));
        AssistantTurn.Completed turn = assertInstanceOf(AssistantTurn.Completed.class,
                pipeline.processTurn(activation()));
        assertEquals("done", turn.result().getResponse().getText());
        assertEquals(ConversationPolicy.PRESERVE, turn.result().getConversationPolicy());
        assertEquals(Capability.GENERAL, turn.result().getCapability());
        assertLifecyclePair(0, Capability.GENERAL);
    }

    @Test
    void failureShouldCompleteLifecycleAndPropagateOriginalError() {
        IllegalStateException failure = new IllegalStateException("skill failed");
        Skill skill = skill(() -> { throw failure; });
        when(router.route("command")).thenReturn(new SkillRoute(Capability.GENERAL, skill));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> pipeline.processTurn(activation())));
        assertLifecyclePair(0, Capability.GENERAL);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void asynchronousTurnShouldCompleteLifecycleOnlyWhenFutureTerminates(boolean failed) throws Exception {
        CompletableFuture<AssistantResult> future = new CompletableFuture<>();
        Skill skill = skill(() -> new SkillExecution.Async(future));
        when(router.route("command")).thenReturn(new SkillRoute(Capability.CURRENT_RESEARCH, skill));
        AssistantTurn.Async turn = assertInstanceOf(AssistantTurn.Async.class, pipeline.processTurn(activation()));
        assertEquals(1, events.size());
        assertFalse(turn.stage().toCompletableFuture().isDone());
        if (failed) {
            IllegalStateException failure = new IllegalStateException("backend failed");
            future.completeExceptionally(failure);
            assertSame(failure, assertThrows(CompletionException.class,
                    () -> turn.stage().toCompletableFuture().join()).getCause());
        }
        else {
            future.complete(new AssistantResult("researched"));
            assertEquals("researched", turn.stage().toCompletableFuture().get(2, TimeUnit.SECONDS).getResponse().getText());
        }
        assertLifecyclePair(0, Capability.CURRENT_RESEARCH);
    }

    @Test
    void humanInteractionShouldFinishCurrentExecutionAndStartNewExecutionOnContinuation() {
        ChoiceRequest request = choice();
        Skill skill = skill(() -> new SkillExecution.AwaitingInteraction<>(request,
                result -> SkillExecution.completed(new AssistantResult("selected " + result.value().orElseThrow()))));
        when(router.route("command")).thenReturn(new SkillRoute(Capability.OS_COMMAND, skill));
        @SuppressWarnings("unchecked")
        AssistantTurn.AwaitingInteraction<String> turn = (AssistantTurn.AwaitingInteraction<String>) pipeline.processTurn(activation());
        assertSame(request, turn.request());
        assertLifecyclePair(0, Capability.OS_COMMAND);

        AssistantTurn.Completed resumed = assertInstanceOf(AssistantTurn.Completed.class,
                turn.continuation().apply(InteractionResult.submitted(request, "one", InputModality.TOUCH)));
        assertEquals("selected one", resumed.result().getResponse().getText());
        assertLifecyclePair(2, Capability.OS_COMMAND);
        assertNotEquals(events.getFirst().id(), events.get(2).id());
    }

    @Test
    void directTurnShouldRouteToCapabilityAndExecuteSuppliedActionWithoutSemanticParsing() {
        Skill skill = skill(() -> { throw new AssertionError("Skill's ordinary executeTurn must not run"); });
        when(router.routeTo(Capability.OS_COMMAND)).thenReturn(new SkillRoute(Capability.OS_COMMAND, skill));
        AssistantTurn.Completed turn = assertInstanceOf(AssistantTurn.Completed.class,
                pipeline.processDirectTurn(Capability.OS_COMMAND, () -> SkillExecution.completed(new AssistantResult("opened"))));
        assertEquals("opened", turn.result().getResponse().getText());
        verify(router).routeTo(Capability.OS_COMMAND);
        verifyNoMoreInteractions(router);
        assertLifecyclePair(0, Capability.OS_COMMAND);
    }

    @Test
    void followUpShouldUseOwnerRouteAndContextAndPublishLifecycle() {
        ConversationSnapshot snapshot = new ConversationSnapshot(Capability.CURRENT_RESEARCH, "previous", "answer", "token");
        Skill skill = mock(Skill.class);
        when(skill.executeFollowUpTurn("command", snapshot)).thenReturn(SkillExecution.completed(new AssistantResult("follow up")));
        when(skill.getConversationPolicy()).thenReturn(ConversationPolicy.KEEP_OPEN);
        when(router.routeFollowUp("command", snapshot)).thenReturn(new SkillRoute(Capability.CURRENT_RESEARCH, skill));
        AssistantTurn.Completed turn = assertInstanceOf(AssistantTurn.Completed.class,
                pipeline.processFollowUpTurn(activation(), snapshot));
        assertEquals(ConversationPolicy.KEEP_OPEN, turn.result().getConversationPolicy());
        verify(skill).executeFollowUpTurn("command", snapshot);
        verify(router).routeFollowUp("command", snapshot);
        verifyNoMoreInteractions(router);
        assertLifecyclePair(0, Capability.CURRENT_RESEARCH);
    }

    @Test
    void invalidActivationShouldNotRouteOrPublishExecutionLifecycle() {
        assertThrows(IllegalArgumentException.class, () -> pipeline.processTurn(ActivationResult.none()));
        assertTrue(events.isEmpty());
        verifyNoInteractions(router);
    }

    private void assertLifecyclePair(int offset, Capability capability) {
        assertEquals(offset + 2, events.size());
        Event started = events.get(offset);
        assertTrue(started.started());
        assertEquals(capability, started.capability());
        assertEquals(new Event(started.id(), capability, false), events.get(offset + 1));
    }

    private static ActivationResult activation() {
        return new ActivationResult(true, ActivationType.WAKE_WORD, "command");
    }

    private static Skill skill(Supplier<SkillExecution> execution) {
        return new Skill() {
            @Override public AssistantResult execute(String command) { throw new AssertionError("use executeTurn"); }
            @Override public SkillExecution executeTurn(String command) { return execution.get(); }
        };
    }

    private static ChoiceRequest choice() {
        return new ChoiceRequest(Optional.empty(), "Choose", Set.of(InputModality.TOUCH), Optional.empty(),
                FocusRequirement.PASSIVE, List.of(new ChoiceOption("one", "One", List.of()), new ChoiceOption("two", "Two", List.of())));
    }

    private record Event(UUID id, Capability capability, boolean started) { }
}
