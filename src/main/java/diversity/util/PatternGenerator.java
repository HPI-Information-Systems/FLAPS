package diversity.util;

import java.util.*;

public class PatternGenerator {

    private final BitMap bitMap;
    private final int[][] featureRanges;
    private final int[] bitToFeature;

    public PatternGenerator(BitMap bitMap) {
        this.bitMap = bitMap;
        List<String> features = new ArrayList<>(bitMap.getFeatures());
        this.featureRanges = new int[features.size()][];
        this.bitToFeature = new int[bitMap.getTotalBits()];
        for (int feature = 0; feature < features.size(); feature++) {
            int[] range = bitMap.getRange(features.get(feature));
            featureRanges[feature] = range;
            Arrays.fill(bitToFeature, range[0], range[1] + 1, feature);
        }
    }

    /**
     * Generate level-1 patterns:
     * one BitSet per bit position (single feature value active).
     */
    public List<BitSet> initializeLevelOne() {
        int totalBits = bitMap.getTotalBits();
        List<BitSet> patterns = new ArrayList<>();

        for (int i = 0; i < totalBits; i++) {
            BitSet bs = new BitSet(totalBits);
            bs.set(i);
            patterns.add(bs);
        }

        return patterns;
    }

    public List<BitSet> specializePatternsRightward(List<BitSet> coveredPatterns, int specializeBy) {
        if (specializeBy == 1) {
            return specializeByOneFeature(coveredPatterns);
        }
        List<BitSet> result = new ArrayList<>();

        for (BitSet pattern : coveredPatterns) {
            int rightmostSetBit = pattern.length() - 1;

            String rightmostFeature = bitMap.getFeatureForIndex(rightmostSetBit);
            List<String> features = new ArrayList<>(bitMap.getFeatures());
            int rightmostGroupIdx = features.indexOf(rightmostFeature);

            List<String> rightGroups = features.subList(rightmostGroupIdx + 1, features.size());

            List<List<String>> groupCombos = combinations(rightGroups, specializeBy);

            for (List<String> groupCombo : groupCombos) {
                generateSpecializations(pattern, groupCombo, result);
            }
        }
        return result;
    }

    private List<BitSet> specializeByOneFeature(List<BitSet> coveredPatterns) {
        int estimatedSize = 0;
        for (BitSet pattern : coveredPatterns) {
            int rightmostBit = pattern.length() - 1;
            if (rightmostBit < 0) continue;
            int rightmostFeature = bitToFeature[rightmostBit];
            for (int feature = rightmostFeature + 1; feature < featureRanges.length; feature++) {
                int[] range = featureRanges[feature];
                estimatedSize = saturatedAdd(estimatedSize, range[1] - range[0] + 1);
            }
        }

        List<BitSet> result = new ArrayList<>(estimatedSize);
        for (BitSet pattern : coveredPatterns) {
            int rightmostBit = pattern.length() - 1;
            if (rightmostBit < 0) continue;
            int rightmostFeature = bitToFeature[rightmostBit];
            for (int feature = rightmostFeature + 1; feature < featureRanges.length; feature++) {
                int[] range = featureRanges[feature];
                for (int bit = range[0]; bit <= range[1]; bit++) {
                    BitSet specialized = (BitSet) pattern.clone();
                    specialized.set(bit);
                    result.add(specialized);
                }
            }
        }
        return result;
    }

    private int saturatedAdd(int left, int right) {
        return left > Integer.MAX_VALUE - right ? Integer.MAX_VALUE : left + right;
    }

    private void generateSpecializations(BitSet base, List<String> groups, List<BitSet> result) {
        if (groups.isEmpty()) {
            result.add((BitSet) base.clone());
            return;
        }

        String group = groups.get(0);
        int[] range = bitMap.getRange(group);
        for (int i = range[0]; i <= range[1]; i++) {
            BitSet specialized = (BitSet) base.clone();
            specialized.set(i);
            generateSpecializations(specialized, groups.subList(1, groups.size()), result);
        }
    }

    private List<List<String>> combinations(List<String> elements, int k) {
        List<List<String>> result = new ArrayList<>();
        combineHelper(elements, k, 0, new ArrayList<>(), result);
        return result;
    }

    private void combineHelper(List<String> elements, int k, int start, List<String> current, List<List<String>> result) {
        if (current.size() == k) {
            result.add(new ArrayList<>(current));
            return;
        }
        for (int i = start; i < elements.size(); i++) {
            current.add(elements.get(i));
            combineHelper(elements, k, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
}
