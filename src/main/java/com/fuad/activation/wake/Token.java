package com.fuad.activation.wake;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class Token {
    String value;
    int start;
    int end;
}
