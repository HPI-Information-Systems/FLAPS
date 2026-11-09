package diversity.util;

import java.util.*;

/**
 * BitmapIndex stores, for each feature, a compact bitmap
 * of the dataset rows that have this feature set.
 */
public class BitmapIndex {

    private final long[][] bitmapIndex;
    private final int nFeatures;
    private final int nRows;
    private final int nIndexWords;
    private final List<BitSet> patterns;
    private final int[] featureSupports;

    public BitmapIndex(List<BitSet> dataset, int nBits) {
        this.patterns = dataset;
        this.nRows = dataset.size();
        this.nFeatures = nBits;
        this.nIndexWords = (nRows + 63) / 64;
        this.bitmapIndex = new long[nBits][nIndexWords];
        this.featureSupports = new int[nBits];

        buildIndex(dataset);
    }

    private void buildIndex(List<BitSet> dataset) {
        for (int row = 0; row < nRows; row++) {
            BitSet pattern = dataset.get(row);
            for (int bit = pattern.nextSetBit(0); bit >= 0; bit = pattern.nextSetBit(bit + 1)) {
                int widx = row / 64;
                int bpos = row % 64;
                bitmapIndex[bit][widx] |= (1L << bpos);
                featureSupports[bit]++;
            }
        }
    }

    private boolean[] findMoreSpecialMask(List<BitSet> A) {
        boolean[] mask = new boolean[nRows];

        long[] tmp = new long[nIndexWords];
        for (BitSet a : A) {
            int firstBit = a.nextSetBit(0);
            if (firstBit < 0) continue;

            System.arraycopy(getFeatureBitmap(firstBit), 0, tmp, 0, nIndexWords);
            for (int f = a.nextSetBit(0); f >= 0; f = a.nextSetBit(f + 1)) {
                if (f == firstBit) continue;
                long[] featureBitmap = getFeatureBitmap(f);
                boolean isNonZero = false;
                for (int w = 0; w < nIndexWords; w++) {
                    tmp[w] &= featureBitmap[w];
                    isNonZero |= tmp[w] != 0;
                }
                if (!isNonZero) break;
            }

            for (int w = 0; w < nIndexWords; w++) {
                long word = tmp[w];
                while (word != 0) {
                    int b = Long.numberOfTrailingZeros(word);
                    int idx = w * 64 + b;
                    if (idx < nRows) mask[idx] = true;
                    word &= (word - 1); // clear that bit
                }
            }
        }
        return mask;
    }

    /**
     * Test support against a threshold without allocating a bitmap per pattern.
     * Intersects feature bitmaps in ascending bit-index order and stops counting
     * as soon as the support threshold is reached.
     */
    public boolean hasMinCoverage(BitSet pattern, int minCoverage, long[] scratch) {
        if (minCoverage <= 0) return true;
        if (scratch.length < nIndexWords) {
            throw new IllegalArgumentException("Scratch bitmap is too small");
        }

        int firstBit = pattern.nextSetBit(0);
        if (firstBit < 0) return nRows >= minCoverage;
        if (featureSupports[firstBit] < minCoverage) return false;
        if (pattern.nextSetBit(firstBit + 1) < 0) return true;

        System.arraycopy(bitmapIndex[firstBit], 0, scratch, 0, nIndexWords);
        for (int bit = pattern.nextSetBit(0); bit >= 0; bit = pattern.nextSetBit(bit + 1)) {
            if (bit == firstBit) continue;
            long[] featureBitmap = bitmapIndex[bit];
            for (int word = 0; word < nIndexWords; word++) {
                scratch[word] &= featureBitmap[word];
            }
        }

        int support = 0;
        for (int word = 0; word < nIndexWords; word++) {
            support += Long.bitCount(scratch[word]);
            if (support >= minCoverage) return true;
        }
        return false;
    }

    /** Compute the exact support of a pattern using a caller-owned scratch bitmap. */
    public int countSupport(BitSet pattern, long[] scratch) {
        if (scratch.length < nIndexWords) {
            throw new IllegalArgumentException("Scratch bitmap is too small");
        }

        int firstBit = pattern.nextSetBit(0);
        if (firstBit < 0) return nRows;

        System.arraycopy(bitmapIndex[firstBit], 0, scratch, 0, nIndexWords);
        for (int bit = pattern.nextSetBit(firstBit + 1);
             bit >= 0;
             bit = pattern.nextSetBit(bit + 1)) {
            long[] featureBitmap = bitmapIndex[bit];
            for (int word = 0; word < nIndexWords; word++) {
                scratch[word] &= featureBitmap[word];
            }
        }

        int support = 0;
        for (int word = 0; word < nIndexWords; word++) {
            support += Long.bitCount(scratch[word]);
        }
        return support;
    }

    /** Remove all patterns that are more special than something in A */
    public List<BitSet> removeSpecializations(List<BitSet> A) {
        if (patterns.isEmpty() || A.isEmpty()) return patterns;
        boolean[] mask = findMoreSpecialMask(A);

        List<BitSet> filtered = new ArrayList<>();
        for (int i = 0; i < patterns.size(); i++) {
            if (!mask[i]) filtered.add(patterns.get(i));
        }
        return filtered;
    }

    /**
     * Get the bitmap (long array) for a given feature.
     */
    public long[] getFeatureBitmap(int feature) {
        return bitmapIndex[feature];
    }

    public int getNumFeatures() {
        return nFeatures;
    }

    public int getNumRows() {
        return nRows;
    }

    public int getNumIndexWords() {
        return nIndexWords;
    }

    public int getFeatureSupport(int feature) {
        return featureSupports[feature];
    }
}
