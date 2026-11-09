package diversity.sampler;

import diversity.util.CompactBitSetSet;

import java.util.*;

public class ChunkSamplerFull implements Sampler {

    private final Set<BitSet> coveredPatterns;
    private final Map<BitSet, Integer> chunkCounts = new HashMap<>();

    private final int minCov;
    private final int nFeatures;
    private final int chunkSizeK;
    private final int chunksPerRow;

    private final Random rng = new Random();

    public ChunkSamplerFull(int minCov, int nFeatures, int chunkSizeK, int chunksPerRow) {
        this.minCov = minCov;
        this.nFeatures = nFeatures;
        this.chunkSizeK = Math.max(1, chunkSizeK);
        this.chunksPerRow = Math.max(1, chunksPerRow);
        this.coveredPatterns = new CompactBitSetSet(nFeatures);
    }

    @Override
    public void fit(List<BitSet> dataset) {
        coveredPatterns.clear();
        chunkCounts.clear();
        Map<BitSet, Integer> exhaustiveRowFrequencies = new LinkedHashMap<>();

        for (BitSet row : dataset) {
            int rowSize = row.cardinality();
            if (rowSize < chunkSizeK) continue;

            long totalComb = nCr(rowSize, chunkSizeK);
            if (totalComb <= chunksPerRow) {
                // Identical exhaustive rows generate identical chunks. Store
                // one row with its multiplicity and add that weight to every
                // chunk count; this is exact for every coverage threshold.
                exhaustiveRowFrequencies.merge(row, 1, Integer::sum);
            } else {
                int[] idx = indicesOf(row);
                sampleUniqueKSubsets(idx, chunkSizeK, chunksPerRow, totalComb);
            }
        }

        for (Map.Entry<BitSet, Integer> entry : exhaustiveRowFrequencies.entrySet()) {
            int[] idx = indicesOf(entry.getKey());
            int occurrences = entry.getValue();
            if (chunkSizeK == idx.length - 1) {
                generateAllButOneSubsets(idx, occurrences);
            } else {
                generateAllKSubsets(idx, chunkSizeK, occurrences);
            }
        }
    }

    @Override
    public void fit(List<BitSet> dataset, int size) {
        fit(dataset);
    }

    @Override
    public Set<BitSet> getCoveredPatterns() {
        return coveredPatterns;
    }

    private void processChunk(BitSet chunk) {
        processChunk(chunk, 1);
    }

    private void processChunk(BitSet chunk, int occurrences) {
        if (minCov <= occurrences) {
            if (minCov > 1) chunkCounts.remove(chunk);
            markWithParents(chunk);
            return;
        }
        if (coveredPatterns.contains(chunk)) return;

        Integer previous = chunkCounts.get(chunk);
        long count = (previous == null ? 0L : previous.longValue()) + occurrences;
        if (count >= minCov) {
            chunkCounts.remove(chunk);
            markWithParents(chunk);
        } else {
            chunkCounts.put(chunk, (int) count);
        }
    }

    private int[] indicesOf(BitSet bs) {
        int n = bs.cardinality();
        int[] out = new int[n];
        int i = 0;
        for (int bit = bs.nextSetBit(0); bit >= 0; bit = bs.nextSetBit(bit + 1)) {
            out[i++] = bit;
        }
        return out;
    }

    private void generateAllKSubsets(int[] idx, int k, int occurrences) {
        int[] comb = new int[k];
        generateCombinations(idx, 0, comb, 0, occurrences);
    }

    /** Generate every (d-1)-chunk by omitting one value from the row. */
    private void generateAllButOneSubsets(int[] idx, int occurrences) {
        BitSet working = new BitSet(nFeatures);
        for (int bit : idx) working.set(bit);

        for (int bit : idx) {
            working.clear(bit);
            processChunk(minCov <= occurrences
                    ? working
                    : (BitSet) working.clone(), occurrences);
            working.set(bit);
        }
    }

    private void generateCombinations(
            int[] idx,
            int start,
            int[] comb,
            int depth,
            int occurrences
    ) {
        if (depth == comb.length) {
            BitSet bs = new BitSet(nFeatures);
            for (int position : comb) {
                bs.set(idx[position]);
            }
            processChunk(bs, occurrences);
            return;
        }
        for (int i = start; i <= idx.length - (comb.length - depth); i++) {
            comb[depth] = i;
            generateCombinations(idx, i + 1, comb, depth + 1, occurrences);
        }
    }

    private void sampleUniqueKSubsets(int[] idx, int k, int howMany, long totalComb) {
        if (totalComb != Long.MAX_VALUE && totalComb <= (long) howMany * 4L) {
            sampleCombinationRanks(idx, k, howMany, totalComb);
            return;
        }

        Set<BitSet> hasSeen = new HashSet<>(hashCapacity(howMany));
        int[] pool = Arrays.copyOf(idx, idx.length);
        int[] swaps = new int[k];
        while (hasSeen.size() < howMany) {
            BitSet chunk = sampleKSubsetAsBitSet(pool, k, swaps);
            if (hasSeen.add(chunk)) {
                processChunk(chunk);
            }
        }
    }

    private BitSet sampleKSubsetAsBitSet(int[] pool, int k, int[] swaps) {
        int m = pool.length;
        for (int i = 0; i < k; i++) {
            int j = i + rng.nextInt(m - i);
            swaps[i] = j;
            int tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp;
        }
        BitSet chunk = new BitSet(nFeatures);
        for (int i = 0; i < k; i++) {
            chunk.set(pool[i]);
        }
        for (int i = k - 1; i >= 0; i--) {
            int j = swaps[i];
            int tmp = pool[i]; pool[i] = pool[j]; pool[j] = tmp;
        }
        return chunk;
    }

    private void sampleCombinationRanks(int[] idx, int k, int howMany, long totalComb) {
        Set<Long> ranks = new HashSet<>(hashCapacity(howMany));
        for (long upper = totalComb - howMany; upper < totalComb; upper++) {
            long rank = rng.nextLong(upper + 1);
            if (!ranks.add(rank)) ranks.add(upper);
        }
        for (long rank : ranks) {
            processChunk(combinationAtRank(idx, k, rank));
        }
    }

    private BitSet combinationAtRank(int[] idx, int k, long rank) {
        BitSet result = new BitSet(nFeatures);
        int start = 0;
        for (int position = 0; position < k; position++) {
            int remaining = k - position - 1;
            for (int candidate = start; candidate <= idx.length - remaining - 1; candidate++) {
                long blockSize = nCr(idx.length - candidate - 1, remaining);
                if (rank < blockSize) {
                    result.set(idx[candidate]);
                    start = candidate + 1;
                    break;
                }
                rank -= blockSize;
            }
        }
        return result;
    }

    /** Mark pattern and all its parents as covered. */
    private void markWithParents(BitSet pattern) {
        if (!addCovered(pattern)) return;
        BitSet workingCopy = (BitSet) pattern.clone();
        int[] bits = indicesOf(workingCopy);
        enumerateParents(workingCopy, bits, 0);
    }

    private void enumerateParents(BitSet current, int[] bits, int start) {
        for (int i = start; i < bits.length; i++) {
            int bit = bits[i];
            current.clear(bit);
            if (!current.isEmpty()) {
                if (addCovered(current)) enumerateParents(current, bits, i + 1);
            }
            current.set(bit);
        }
    }

    private boolean addCovered(BitSet pattern) {
        return coveredPatterns.add(pattern);
    }

    private long nCr(int n, int k) {
        if (k > n) return 0;
        if (k == 0 || k == n) return 1;
        k = Math.min(k, n - k);
        long res = 1L;
        for (int i = 1; i <= k; i++) {
            long numerator = n - k + (long) i;
            long denominator = i;
            long gcd = gcd(numerator, denominator);
            numerator /= gcd;
            denominator /= gcd;
            gcd = gcd(res, denominator);
            res /= gcd;
            denominator /= gcd;
            if (denominator != 1 || res > Long.MAX_VALUE / numerator) {
                return Long.MAX_VALUE;
            }
            res *= numerator;
        }
        return res;
    }

    private long gcd(long left, long right) {
        while (right != 0) {
            long remainder = left % right;
            left = right;
            right = remainder;
        }
        return left;
    }

    private int hashCapacity(int expectedSize) {
        return Math.max(16, (int) Math.min(Integer.MAX_VALUE - 8L,
                (long) Math.ceil(expectedSize / 0.75d)));
    }
}
