package com.fuad.activation;

import com.fuad.enums.ActivationType;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ActivationResult {
    boolean activated;
    ActivationType type;
    String command;

    public static ActivationResult none() {
        return new ActivationResult(false, ActivationType.NONE, "");
    }
}
