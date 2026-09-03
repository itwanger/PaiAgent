package com.paiagent.service.rag.benchmark;

import java.util.Random;

final class DeterministicVectorDataset {
    static final long SEED = 20260827L;

    private DeterministicVectorDataset() {
    }

    static float[] vector(int row, int dimension) {
        Random random = new Random(SEED + 104729L * row);
        float[] vector = new float[dimension];
        double norm = 0;
        for (int i = 0; i < dimension; i++) {
            float value = random.nextFloat() - 0.5f;
            vector[i] = value;
            norm += value * value;
        }
        float scale = (float) (1.0d / Math.sqrt(norm));
        for (int i = 0; i < dimension; i++) vector[i] *= scale;
        return vector;
    }

    static int queryRow(int queryIndex, int vectorCount) {
        return Math.floorMod(queryIndex * 7919 + 17, vectorCount);
    }

    static String content(int row) {
        return "chunk-" + row + " " + "PaiAgent RAG benchmark content ".repeat(8);
    }

    static String metadata(int row) {
        return "{\"knowledgeBaseId\":\"1\",\"documentId\":\"1\",\"chunkIndex\":" + row
                + ",\"indexVersion\":\"benchmark-v1\",\"active\":true}";
    }

    static String vectorLiteral(float[] vector) {
        StringBuilder value = new StringBuilder(vector.length * 12).append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) value.append(',');
            value.append(Float.toString(vector[i]));
        }
        return value.append(']').toString();
    }
}
