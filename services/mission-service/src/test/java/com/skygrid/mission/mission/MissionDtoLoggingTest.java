package com.skygrid.mission.mission;

import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;

class MissionDtoLoggingTest {
    @Test
    void requestAndResponseStringFormsDoNotIncludeUserTextOrGeometry() {
        var request = new MissionRequest("private mission name", "private description",
                TextNode.valueOf("private coordinate"), TextNode.valueOf("private route"));
        var response = new MissionResponse(UUID.randomUUID(), "private mission name", "private description",
                Instant.now(), TextNode.valueOf("private coordinate"), TextNode.valueOf("private route"));

        assertFalse(request.toString().contains("private"));
        assertFalse(response.toString().contains("private"));
    }
}
