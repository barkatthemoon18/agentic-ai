package com.fuad.assistant.skills.os;

import com.fuad.enums.OsAction;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OsCommandIntent {
    OsAction action;
    String target;

    public static OsCommandIntent unsupported() {
        return new OsCommandIntent(OsAction.UNSUPPORTED, "");
    }
}
