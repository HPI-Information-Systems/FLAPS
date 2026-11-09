package diversity.util;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

public class PatternHandler {

    private final List<BitSet> dataset;

    public PatternHandler(int[][] data) {
        this.dataset = new ArrayList<>();
        for (int[] row : data) {
            dataset.add(toBitSet(row));
        }
    }

    private static BitSet toBitSet(int[] row) {
        BitSet bs = new BitSet();
        for (int i = 0; i < row.length; i++) {
            if (row[i] == 1) {
                bs.set(i);
            }
        }
        return bs;
    }

    public List<BitSet> getDataset() {
        return dataset;
    }

    public static int countBits(BitSet bs) {
        return bs.cardinality();
    }

    public static boolean isSuperset(BitSet A, BitSet B) {
        BitSet copy = (BitSet) B.clone();
        copy.andNot(A);
        return copy.isEmpty();
    }

    public static List<BitSet> generateParents(BitSet pattern) {
        List<BitSet> parents = new ArrayList<>();
        for (int i = pattern.nextSetBit(0); i >= 0; i = pattern.nextSetBit(i+1)) {
            BitSet parent = (BitSet) pattern.clone();
            parent.clear(i);
            parents.add(parent);
        }
        return parents;
    }
}
