package diversity.validator;

import java.util.BitSet;
import java.util.List;
import java.util.Set;

public interface Validator {
    List<BitSet> validate(List<BitSet> candidates, Set<BitSet> coveredSet);

    long getCoverageThresholdEvaluations();
}
