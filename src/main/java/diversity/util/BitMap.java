package diversity.util;

import java.util.*;

public class BitMap {

    private final Map<String, int[]> featureToRange;
    private final List<String> indexToFeature;
    private int totalBits;

    public BitMap() {
        this.featureToRange = new LinkedHashMap<>();
        this.indexToFeature = new ArrayList<>();
        this.totalBits = 0;
    }

    /** Add a feature with its number of categories (e.g., binary = 2, color=4, etc.) */
    public void addFeature(String featureName, int nCategories) {
        int start = totalBits;
        int end = start + nCategories - 1;
        featureToRange.put(featureName, new int[]{start, end});
        for (int i = start; i <= end; i++) {
            indexToFeature.add(featureName);
        }
        totalBits += nCategories;
    }

    /** Get bit range for a feature */
    public int[] getRange(String featureName) {
        return featureToRange.get(featureName);
    }

    /** Get feature name for a specific index */
    public String getFeatureForIndex(int index) {
        if (index < 0 || index >= indexToFeature.size()) {
            throw new IndexOutOfBoundsException("Invalid bit index: " + index);
        }
        return indexToFeature.get(index);
    }

    /** Total number of bits across all features */
    public int getTotalBits() {
        return totalBits;
    }

    /** All features in order */
    public Set<String> getFeatures() {
        return featureToRange.keySet();
    }

    public int getNumFeatures() {
        return featureToRange.size();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("BitMap:\n");
        featureToRange.forEach((feature, range) ->
                sb.append(feature).append(": (")
                        .append(range[0]).append(", ")
                        .append(range[1]).append(")\n"));
        return sb.toString();
    }
}
