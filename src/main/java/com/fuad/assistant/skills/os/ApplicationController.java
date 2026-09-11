package com.fuad.assistant.skills.os;

import java.io.IOException;

public interface ApplicationController {
    boolean open(ApplicationDefinition applicationDefinition) throws IOException;
    boolean close(ApplicationDefinition applicationDefinition);

    default ApplicationActionResult openDetailed(ApplicationDefinition applicationDefinition) throws IOException {
        return open(applicationDefinition) ? ApplicationActionResult.success()
                : ApplicationActionResult.failed("Launch was rejected");
    }

    default ApplicationActionResult closeDetailed(ApplicationDefinition applicationDefinition) {
        return close(applicationDefinition) ? ApplicationActionResult.success()
                : ApplicationActionResult.of(ApplicationActionResult.Status.NOT_RUNNING);
    }

    default ApplicationActionResult focus(ApplicationDefinition applicationDefinition) {
        return ApplicationActionResult.of(ApplicationActionResult.Status.NO_VISIBLE_WINDOW);
    }

    default ApplicationActionResult runtimeState(ApplicationDefinition applicationDefinition) {
        return ApplicationActionResult.of(ApplicationActionResult.Status.PROCESS_IDENTITY_UNAVAILABLE);
    }
}
