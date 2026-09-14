package com.fuad.model.runtime;

import java.time.Duration;
import java.util.List;

public interface LmsCommandRunner extends AutoCloseable {
    CommandResult run(RuntimeComponent owner, List<String> command, Duration timeout)
            throws InterruptedException;

    default CommandResult run(RuntimeComponent owner, List<String> command, Duration timeout,
                              CommandGeneration generation) throws InterruptedException {
        if (!generation.isActive()) {
            return new CommandResult(-1, "Recovery generation was cancelled", false);
        }
        return run(owner, command, timeout);
    }

    void cancel(RuntimeComponent owner);

    @Override
    default void close() {
        for (RuntimeComponent component : RuntimeComponent.values()) {
            cancel(component);
        }
    }
}
