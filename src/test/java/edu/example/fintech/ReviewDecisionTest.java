package edu.example.fintech;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ReviewDecisionTest {
    @Test void riskySubmissionIsHeld() {
        assertEquals("QUARANTINED", ReviewDecision.state(true));
        assertEquals("READY_FOR_PUBLISH", ReviewDecision.state(false));
    }
}
