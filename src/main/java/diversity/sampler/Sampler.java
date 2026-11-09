package diversity.sampler;

import java.util.List;
import java.util.BitSet;
import java.util.Set;

public interface Sampler {
    /**
     * Feed a dataset into the sampler.
     */
    void fit(List<BitSet> dataset);

    void fit(List<BitSet> dataset, int size);

    /**
     * Get patterns that are guaranteed to be covered.
     */
    Set<BitSet> getCoveredPatterns();
}
