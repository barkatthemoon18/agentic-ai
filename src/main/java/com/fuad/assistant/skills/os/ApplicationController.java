package com.fuad.assistant.skills.os;

import java.io.IOException;

public interface ApplicationController {
    boolean open(ApplicationDefinition applicationDefinition) throws IOException;
    boolean close(ApplicationDefinition applicationDefinition);
}
