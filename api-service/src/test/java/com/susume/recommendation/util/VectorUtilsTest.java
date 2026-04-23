package com.susume.recommendation.util;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VectorUtilsTest {

    @Test
    void weightedAverageWithSingleEmbedding() {
        float[] embedding = { 1.0f, 2.0f, 3.0f };
        List<float[]> embeddings = Arrays.asList(embedding);
        List<Integer> weights = Arrays.asList(1);

        float[] result = VectorUtils.weightedAverage(embeddings, weights);

        assertArrayEquals(embedding, result, 0.001f);
    }

    @Test
    void weightedAverageWeightsHigherInteractionsMore() {
        float[] embedding1 = { 1.0f, 0.0f, 0.0f };
        float[] embedding2 = { 0.0f, 1.0f, 0.0f };
        List<float[]> embeddings = Arrays.asList(embedding1, embedding2);
        List<Integer> weights = Arrays.asList(3, 1); // Weight first embedding more

        float[] result = VectorUtils.weightedAverage(embeddings, weights);

        // Expected: (1*3 + 0*1)/4 = 0.75, (0*3 + 1*1)/4 = 0.25, (0*3 + 0*1)/4 = 0.0
        assertEquals(0.75f, result[0], 0.001f);
        assertEquals(0.25f, result[1], 0.001f);
        assertEquals(0.0f, result[2], 0.001f);
    }

    @Test
    void cosineSimilarityOfIdenticalVectorsIsOne() {
        float[] a = { 1.0f, 0.0f, 0.0f };
        float[] b = { 1.0f, 0.0f, 0.0f };

        double similarity = VectorUtils.cosineSimilarity(a, b);

        assertEquals(1.0, similarity, 0.001);
    }

    @Test
    void cosineSimilarityOfOrthogonalVectorsIsZero() {
        float[] a = { 1.0f, 0.0f, 0.0f };
        float[] b = { 0.0f, 1.0f, 0.0f };

        double similarity = VectorUtils.cosineSimilarity(a, b);

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void cosineSimilarityHandlesZeroVector() {
        float[] a = { 0.0f, 0.0f, 0.0f };
        float[] b = { 1.0f, 0.0f, 0.0f };

        double similarity = VectorUtils.cosineSimilarity(a, b);

        assertEquals(0.0, similarity, 0.001);
    }

    @Test
    void weightedAverageThrowsOnEmptyEmbeddings() {
        assertThrows(IllegalArgumentException.class,
                () -> VectorUtils.weightedAverage(Arrays.asList(), Arrays.asList(1)));
    }

    @Test
    void weightedAverageThrowsOnMismatchedSizes() {
        float[] embedding = { 1.0f, 2.0f };
        List<float[]> embeddings = Arrays.asList(embedding, embedding);
        List<Integer> weights = Arrays.asList(1); // Mismatch

        assertThrows(IllegalArgumentException.class, () -> VectorUtils.weightedAverage(embeddings, weights));
    }

    @Test
    void cosineSimilarityThrowsOnNullVectors() {
        float[] a = { 1.0f, 2.0f };

        assertThrows(IllegalArgumentException.class, () -> VectorUtils.cosineSimilarity(null, a));

        assertThrows(IllegalArgumentException.class, () -> VectorUtils.cosineSimilarity(a, null));
    }

    @Test
    void cosineSimilarityThrowsOnMismatchedDimensions() {
        float[] a = { 1.0f, 2.0f };
        float[] b = { 1.0f, 2.0f, 3.0f };

        assertThrows(IllegalArgumentException.class, () -> VectorUtils.cosineSimilarity(a, b));
    }
}
