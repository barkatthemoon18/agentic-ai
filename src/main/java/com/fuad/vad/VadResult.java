package com.fuad.vad;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class VadResult {
    float probability;
    boolean speech;
}
