package com.fuad.assistant.skills.os;

import java.io.IOException;
import java.util.List;

public interface ApplicationDiscovery {
    List<ApplicationDefinition> discover() throws IOException;
}
