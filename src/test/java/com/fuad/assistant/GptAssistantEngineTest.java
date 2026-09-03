package com.fuad.assistant;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GptAssistantEngineTest {

    @Test
    void engineShouldContainOnlyInfrastructureState() {
        List<Field> instanceFields = List.of(GptAssistantEngine.class.getDeclaredFields()).stream()
                .filter(field -> !Modifier.isStatic(field.getModifiers()))
                .toList();

        assertEquals(List.of("client"), instanceFields.stream().map(Field::getName).toList());
    }
}
