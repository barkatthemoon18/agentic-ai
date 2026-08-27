package com.fuad.stt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TranscriptionResult {
    String text;
    String language;
    double durationSeconds;
}
