package com.fuad.assistant.skills;

import com.fuad.assistant.AssistantResult;
import com.fuad.interaction.InteractionRequest;
import com.fuad.interaction.InteractionResult;

import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

public sealed interface SkillExecution
        permits SkillExecution.Completed, SkillExecution.Async,
        SkillExecution.AwaitingInteraction {

    static Completed completed(AssistantResult result) {
        return new Completed(result);
    }

    record Completed(AssistantResult result) implements SkillExecution {
        public Completed {
            Objects.requireNonNull(result, "result must not be null");
        }
    }

    record Async(CompletionStage<AssistantResult> stage) implements SkillExecution {
        public Async {
            Objects.requireNonNull(stage, "stage must not be null");
        }
    }

    record AwaitingInteraction<T>(InteractionRequest<T> request,
                                  Function<InteractionResult<T>, SkillExecution> continuation)
            implements SkillExecution {
        public AwaitingInteraction {
            Objects.requireNonNull(request, "request must not be null");
            Objects.requireNonNull(continuation, "continuation must not be null");
        }
    }
}
