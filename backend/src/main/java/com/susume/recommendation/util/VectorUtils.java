package com.susume.recommendation.util;

import java.util.List;

public class VectorUtils {

    /**
     * Computes weighted average of multiple embeddings.
     * Used to construct a user vector from interactions with different weights.
     *
     * @param embeddings list of embedding vectors (each is float[])
     * @param weights    list of weights corresponding to each embedding (e.g.,
     *                   interaction type weights)
     * @return weighted average vector
     */
    public static float[] weightedAverage(List<float[]> embeddings, List<Integer> weights) {
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalArgumentException("Embeddings list cannot be null or empty");
        }
        if (embeddings.size() != weights.size()) {
            throw new IllegalArgumentException("Embeddings and weights lists must have the same size");
        }

        int dim = embeddings.get(0).length;
        float[] result = new float[dim];
        int totalWeight = weights.stream().mapToInt(Integer::intValue).sum();

        if (totalWeight == 0) {
            throw new IllegalArgumentException("Total weight cannot be zero");
        }

        for (int i = 0; i < embeddings.size(); i++) {
            float normalizedWeight = (float) weights.get(i) / totalWeight;
            float[] embedding = embeddings.get(i);

            for (int d = 0; d < dim; d++) {
                result[d] += embedding[d] * normalizedWeight;
            }
        }

        return result;
    }

    /**
     * Computes cosine similarity between two embedding vectors.
     * Used to rank candidate items against the user vector.
     *
     * @param a first embedding vector
     * @param b second embedding vector
     * @return cosine similarity score in range [-1, 1], typically [0, 1] for
     *         embeddings
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null) {
            throw new IllegalArgumentException("Vectors cannot be null");
        }
        if (a.length != b.length) {
            throw new IllegalArgumentException("Vectors must have the same dimension");
        }

        double dot = 0;
        double normA = 0;
        double normB = 0;

        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }

        // Handle zero vectors
        if (normA == 0 || normB == 0) {
            return 0;
        }

        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    public static float[] average(List<float[]> embeddings) {
        if (embeddings == null || embeddings.isEmpty()) {
            throw new IllegalArgumentException("Embeddings list cannot be null or empty");
        }
        List<Integer> equalWeights = java.util.Collections.nCopies(embeddings.size(), 1);
        return weightedAverage(embeddings, equalWeights);
    }
}
