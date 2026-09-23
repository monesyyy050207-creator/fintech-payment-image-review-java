package edu.example.fintech;

public final class ReviewDecision {
    private ReviewDecision() {}

    public static String state(boolean flagged) {
        return flagged ? "QUARANTINED" : "READY_FOR_PUBLISH";
    }
}
