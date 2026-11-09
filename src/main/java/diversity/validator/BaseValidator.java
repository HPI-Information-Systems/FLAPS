package diversity.validator;

import diversity.util.BitMap;
import diversity.util.BitmapIndex;
import diversity.util.PatternGenerator;

import java.util.*;

public abstract class BaseValidator implements Validator {
    protected final List<BitSet> dataset;
    protected final BitMap bitMap;
    protected final int minCov;

    protected final BitmapIndex bitmapIndex;
    protected PatternGenerator patternGenerator;
    private long coverageThresholdEvaluations;

    public BaseValidator(List<BitSet> dataset, BitMap bitMap, int minCov) {
        this.dataset = dataset;
        this.bitMap = bitMap;
        this.minCov = minCov;
        this.bitmapIndex = new BitmapIndex(dataset, bitMap.getTotalBits());
    }

    protected final void resetCoverageThresholdEvaluations() {
        coverageThresholdEvaluations = 0L;
    }

    protected final void recordCoverageThresholdEvaluations(long count) {
        coverageThresholdEvaluations += count;
    }

    @Override
    public final long getCoverageThresholdEvaluations() {
        return coverageThresholdEvaluations;
    }

    protected Map<String, List<BitSet>> filterByMinCov(
            List<BitSet> unknownPatterns,
            int[] counts
    ) {
        List<BitSet> newlyCovered = new ArrayList<>();
        List<BitSet> uncovered = new ArrayList<>();

        for (int i = 0; i < unknownPatterns.size(); i++) {
            BitSet p = unknownPatterns.get(i);
            if (counts[i] >= this.minCov) {
                newlyCovered.add(p);
            } else {
                uncovered.add(p);
            }
        }

        Map<String, List<BitSet>> result = new HashMap<>();
        result.put("covered", newlyCovered);
        result.put("uncovered", uncovered);
        return result;
    }

}
