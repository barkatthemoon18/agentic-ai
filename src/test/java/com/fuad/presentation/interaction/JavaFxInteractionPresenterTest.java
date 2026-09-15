package com.fuad.presentation.interaction;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaFxInteractionPresenterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Visual Studio Code|VC",
            "Spotify|SP",
            "Árbol Ñandú|ÁÑ",
            "123 Player|1P",
            "***|--",
            "中文 应用|中应",
            "ß|SS"
    })
    void shouldBuildDeterministicUnicodeSafeMonogram(String label, String expected) {
        assertEquals(expected, JavaFxInteractionPresenter.monogram(label));
    }
}
