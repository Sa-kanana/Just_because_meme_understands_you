package com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemeAgentMemeIdPageDTOTest {

    @Test
    void mapsMemeIdsAndNullableNextCursorFromPythonResponse() throws Exception {
        Class<?> dtoType = Class.forName(
                "com.sakana.just_because_meme_understands_you.modules.ai.infrastructure.client.dto."
                        + "MemeAgentMemeIdPageDTO");
        ObjectMapper objectMapper = new ObjectMapper();

        Object nonTerminalPage = objectMapper.readValue(
                "{\"meme_ids\":[\"101\",\"102\"],\"next_cursor\":\"102\"}", dtoType);
        assertEquals(List.of("101", "102"), dtoType.getMethod("getMemeIds").invoke(nonTerminalPage));
        assertEquals("102", dtoType.getMethod("getNextCursor").invoke(nonTerminalPage));

        Object terminalPage = objectMapper.readValue(
                "{\"meme_ids\":[\"103\"],\"next_cursor\":null}", dtoType);
        assertEquals(List.of("103"), dtoType.getMethod("getMemeIds").invoke(terminalPage));
        assertEquals(null, dtoType.getMethod("getNextCursor").invoke(terminalPage));
    }
}
