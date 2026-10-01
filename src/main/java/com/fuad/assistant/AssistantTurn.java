package com.fuad.assistant;

import com.fuad.interaction.InteractionRequest;
import com.fuad.interaction.InteractionResult;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public sealed interface AssistantTurn
        permits AssistantTurn.Completed, AssistantTurn.Async,
        AssistantTurn.AwaitingInteraction {

    record Completed(AssistantExecutionResult result) implements AssistantTurn {
        public Completed {
            Objects.requireNonNull(result, "result must not be null");
        }
    }

    record Async(CompletionStage<AssistantExecutionResult> stage) implements AssistantTurn {
        public Async {
            Objects.requireNonNull(stage, "stage must not be null");
        }
    }

    record AwaitingInteraction<T>(InteractionRequest<T> request,
                                  Function<InteractionResult<T>, AssistantTurn> continuation)
            implements AssistantTurn {
        public AwaitingInteraction {
            Objects.requireNonNull(request, "request must not be null");
            Objects.requireNonNull(continuation, "continuation must not be null");
        }
    }
}
