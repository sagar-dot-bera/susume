package com.susume.recommendation.framework;

import com.susume.recommendation.util.VectorUtils;
import java.util.*;

public class SimilarityService {

    public double computeEmbeddingSimilarity(float[] vectorA, float[] vectorB) {
        if (vectorA == null || vectorB == null) return 0.0;
        return VectorUtils.cosineSimilarity(vectorA, vectorB);
    }

    public double computeJaccardSimilarity(Set<String> setA, Set<String> setB) {
        if (setA == null || setB == null || setA.isEmpty() || setB.isEmpty()) return 0.0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }

    public double computeMetadataFieldSimilarity(Map<String, Object> metaA, Map<String, Object> metaB, List<String> fields) {
        if (metaA == null || metaB == null || fields == null || fields.isEmpty()) return 0.0;

        int matches = 0;
        for (String field : fields) {
            Object valA = metaA.get(field);
            Object valB = metaB.get(field);
            if (valA != null && valB != null && valA.toString().equalsIgnoreCase(valB.toString())) {
                matches++;
            }
        }
        return (double) matches / fields.size();
    }
}
