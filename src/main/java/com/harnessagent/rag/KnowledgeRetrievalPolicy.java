package com.harnessagent.rag;

import org.springframework.stereotype.Component;

@Component
public class KnowledgeRetrievalPolicy {

    private final double keywordWeight;
    private final double vectorWeight;
    private final int defaultLimit;
    private final double minimumScore;

    public KnowledgeRetrievalPolicy() {
        this(1.0d, 1.0d, 5, 0.01d);
    }

    public KnowledgeRetrievalPolicy(
            double keywordWeight, double vectorWeight, int defaultLimit, double minimumScore) {
        this.keywordWeight = keywordWeight;
        this.vectorWeight = vectorWeight;
        this.defaultLimit = defaultLimit;
        this.minimumScore = minimumScore;
    }

    public double keywordWeight() {
        return keywordWeight;
    }

    public double vectorWeight() {
        return vectorWeight;
    }

    public int defaultLimit() {
        return defaultLimit;
    }

    public double minimumScore() {
        return minimumScore;
    }
}
