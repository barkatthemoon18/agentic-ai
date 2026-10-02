package com.fuad.assistant.skills.os;

import com.fuad.enums.OsAction;

public record OsCommandIntent(
        OsAction action,
        String target) {

    public static OsCommandIntent unsupported() {
        return new OsCommandIntent(OsAction.UNSUPPORTED, "");
    }
}
