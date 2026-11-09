package diversity.validator;

import diversity.util.BitMap;
import diversity.util.BitmapIndex;
import diversity.util.MUPHandler;
import diversity.util.PatternGenerator;

import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopDownValidator extends BaseValidator {

    private static final Logger log = LoggerFactory.getLogger(TopDownValidator.class);

    public TopDownValidator(List<BitSet> dataset, BitMap bitMap, int mincov) {
        super(dataset, bitMap, mincov);
        this.patternGenerator = new PatternGenerator(bitMap);
    }

    @Override
    public List<BitSet> validate(List<BitSet> A, Set<BitSet> coveredSet) {
        resetCoverageThresholdEvaluations();
        MUPHandler mupHandler = new MUPHandler();
        List<BitSet> specializedPatternsCovered = new ArrayList<>();
        List<BitSet> specializedPatternsUnknown = new ArrayList<>();
        List<BitSet> AforvalidationCovered;
        List<BitSet> AforvalidationUnknown;


        for (int level = 1; level <= bitMap.getNumFeatures(); level++) {
            log.info("Level {}" , level);

            if (level == 1) {
                Partition split = splitByHashSetCoverage(A, coveredSet);
                AforvalidationCovered = split.covered();
                AforvalidationUnknown = split.unknown();
            }else{
                AforvalidationCovered = specializedPatternsCovered;
                AforvalidationUnknown = specializedPatternsUnknown;
            }

            log.info("Covered: {}, Unknown: {}", AforvalidationCovered.size(), AforvalidationUnknown.size());

            Partition filtered = splitByMinCoverage(AforvalidationUnknown);
            List<BitSet> newlyCovered = filtered.covered();
            AforvalidationCovered.addAll(newlyCovered);
            List<BitSet> uncovered = filtered.unknown();

            mupHandler.addMUPs(uncovered);
            log.info("Mups: {}", uncovered.size());

            if (level == bitMap.getNumFeatures()) break;

            List<BitSet> specializedPatterns = patternGenerator.specializePatternsRightward(AforvalidationCovered, 1);
            Partition next = splitByHashSetCoverage(specializedPatterns, coveredSet);
            specializedPatternsCovered = next.covered();
            specializedPatternsUnknown = retainCandidatesWithCoveredParents(
                    next.unknown(), coveredSet, newlyCovered);

        }

        return mupHandler.getMUPs();
    }

    /**
     * A candidate can lie on the uncovered boundary only when every immediate
     * parent is covered. Breadth-first traversal has already classified those
     * parents, so hash lookups replace the temporary candidate bitmap index and
     * repeated scans over every MUP found at earlier levels.
     */
    private List<BitSet> retainCandidatesWithCoveredParents(
            List<BitSet> candidates,
            Set<BitSet> coveredCertificates,
            List<BitSet> newlyCovered
    ) {
        if (candidates.isEmpty()) return candidates;

        Set<BitSet> exactlyCovered = newlyCovered.isEmpty()
                ? Set.of()
                : new HashSet<>(newlyCovered);
        List<BitSet> retained = new ArrayList<>(candidates.size());

        for (BitSet candidate : candidates) {
            if (allImmediateParentsCovered(candidate, coveredCertificates, exactlyCovered)) {
                retained.add(candidate);
            }
        }
        return retained;
    }

    private boolean allImmediateParentsCovered(
            BitSet candidate,
            Set<BitSet> coveredCertificates,
            Set<BitSet> exactlyCovered
    ) {
        for (int bit = candidate.nextSetBit(0);
             bit >= 0;
             bit = candidate.nextSetBit(bit + 1)) {
            candidate.clear(bit);
            boolean parentCovered = coveredCertificates.contains(candidate)
                    || exactlyCovered.contains(candidate);
            candidate.set(bit);
            if (!parentCovered) return false;
        }
        return true;
    }

    private Partition splitByHashSetCoverage(
            List<BitSet> patterns,
            Set<BitSet> coveredPatterns
    ) {
        List<BitSet> covered = new ArrayList<>();
        List<BitSet> unknown = new ArrayList<>();

        for (BitSet p : patterns) {
            if (coveredPatterns.contains(p)) {
                covered.add(p);
            } else {
                unknown.add(p);
            }
        }

        return new Partition(covered, unknown);
    }

    private Partition splitByMinCoverage(List<BitSet> patterns) {
        List<BitSet> covered = new ArrayList<>();
        List<BitSet> uncovered = new ArrayList<>();
        long[] scratch = new long[bitmapIndex.getNumIndexWords()];
        recordCoverageThresholdEvaluations(patterns.size());
        for (BitSet pattern : patterns) {
            if (bitmapIndex.hasMinCoverage(pattern, minCov, scratch)) {
                covered.add(pattern);
            } else {
                uncovered.add(pattern);
            }
        }
        return new Partition(covered, uncovered);
    }

    public int[] countSupersets(List<BitSet> patterns, BitmapIndex index) {
        int numPatterns = patterns.size();
        int[] counts = new int[numPatterns];
        int numWords = index.getNumIndexWords();

        for (int i = 0; i < numPatterns; i++) {
            BitSet p = patterns.get(i);

            long[] candidate = new long[numWords];
            Arrays.fill(candidate, -1L);

            for (int bit = p.nextSetBit(0); bit >= 0; bit = p.nextSetBit(bit + 1)) {
                long[] featureBitmap = index.getFeatureBitmap(bit);
                for (int w = 0; w < numWords; w++) {
                    candidate[w] &= featureBitmap[w];
                }
            }

            int count = 0;
            for (int w = 0; w < numWords; w++) {
                count += Long.bitCount(candidate[w]);
            }
            counts[i] = count;
        }
        return counts;
    }

    private record Partition(List<BitSet> covered, List<BitSet> unknown) {}


}
