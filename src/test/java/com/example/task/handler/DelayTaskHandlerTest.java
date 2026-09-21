package com.example.task.handler;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DelayTaskHandlerTest {

    @Test
    void usesDefaultWhenPayloadIsMissing() {
        assertEquals(DelayTaskHandler.DEFAULT_DELAY_MS, DelayTaskHandler.parse(null));
        assertEquals(DelayTaskHandler.DEFAULT_DELAY_MS, DelayTaskHandler.parse("   "));
    }

    @Test
    void parsesAndCapsTheDelay() {
        assertEquals(250L, DelayTaskHandler.parse(" 250 "));
        assertEquals(DelayTaskHandler.MAX_DELAY_MS, DelayTaskHandler.parse("999999"));
    }

    @Test
    void rejectsInvalidPayloads() {
        assertThrows(IllegalArgumentException.class, () -> DelayTaskHandler.parse("abc"));
        assertThrows(IllegalArgumentException.class, () -> DelayTaskHandler.parse("-5"));
    }
}
