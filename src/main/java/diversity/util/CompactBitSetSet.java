package diversity.util;

import java.util.AbstractSet;
import java.util.Arrays;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Append-only set specialized for immutable bit patterns.
 *
 * <p>Patterns of at most 128 bits are stored directly in one or two primitive
 * words with open addressing. Larger patterns use a defensive-copy HashSet
 * fallback. The Seeder never stores the empty pattern; the set nevertheless
 * tracks it separately so the all-zero word pair remains an unambiguous
 * empty-slot marker without weakening normal {@link Set} semantics.</p>
 */
public final class CompactBitSetSet extends AbstractSet<BitSet> {

    private static final int INITIAL_CAPACITY = 16;
    private static final int MAX_PACKED_BITS = 128;

    private final int maxBits;
    private final boolean packed;
    private final boolean singleWord;
    private final Set<BitSet> fallback;

    private long[] lowTable;
    private long[] highTable;
    private int size;
    private int resizeThreshold;
    private boolean containsEmpty;

    public CompactBitSetSet(int maxBits) {
        if (maxBits < 0) throw new IllegalArgumentException("maxBits must be non-negative");
        this.maxBits = maxBits;
        this.packed = maxBits <= MAX_PACKED_BITS;
        this.singleWord = maxBits <= Long.SIZE;

        if (packed) {
            this.fallback = null;
            allocateTables(INITIAL_CAPACITY);
        } else {
            this.fallback = new HashSet<>();
        }
    }

    @Override
    public boolean add(BitSet value) {
        if (value == null) throw new NullPointerException("value");
        if (value.length() > maxBits) {
            throw new IllegalArgumentException("Pattern exceeds configured bit width");
        }

        if (!packed) {
            return fallback.add((BitSet) value.clone());
        }
        if (value.isEmpty()) {
            if (containsEmpty) return false;
            containsEmpty = true;
            return true;
        }

        long low = encodeLow(value);
        long high = singleWord ? 0L : encodeHigh(value);
        return addPacked(low, high);
    }

    @Override
    public boolean contains(Object candidate) {
        if (!(candidate instanceof BitSet value)) return false;
        if (value.length() > maxBits) return false;

        if (!packed) return fallback.contains(value);
        if (value.isEmpty()) return containsEmpty;

        long low = encodeLow(value);
        long high = singleWord ? 0L : encodeHigh(value);
        int mask = lowTable.length - 1;
        int slot = hash(low, high) & mask;

        while (true) {
            long storedLow = lowTable[slot];
            long storedHigh = singleWord ? 0L : highTable[slot];
            if (storedLow == 0L && storedHigh == 0L) return false;
            if (storedLow == low && storedHigh == high) return true;
            slot = (slot + 1) & mask;
        }
    }

    @Override
    public int size() {
        return packed ? size + (containsEmpty ? 1 : 0) : fallback.size();
    }

    @Override
    public void clear() {
        if (!packed) {
            fallback.clear();
            return;
        }
        Arrays.fill(lowTable, 0L);
        if (!singleWord) Arrays.fill(highTable, 0L);
        size = 0;
        containsEmpty = false;
    }

    @Override
    public Iterator<BitSet> iterator() {
        if (!packed) {
            Iterator<BitSet> delegate = fallback.iterator();
            return new Iterator<>() {
                @Override
                public boolean hasNext() {
                    return delegate.hasNext();
                }

                @Override
                public BitSet next() {
                    return (BitSet) delegate.next().clone();
                }
            };
        }

        return new Iterator<>() {
            private boolean emptyPending = containsEmpty;
            private int nextSlot = findNext(0);

            @Override
            public boolean hasNext() {
                return emptyPending || nextSlot >= 0;
            }

            @Override
            public BitSet next() {
                if (emptyPending) {
                    emptyPending = false;
                    return new BitSet();
                }
                if (nextSlot < 0) throw new NoSuchElementException();
                int current = nextSlot;
                nextSlot = findNext(current + 1);
                return decode(lowTable[current], singleWord ? 0L : highTable[current]);
            }

            private int findNext(int start) {
                for (int slot = start; slot < lowTable.length; slot++) {
                    long low = lowTable[slot];
                    long high = singleWord ? 0L : highTable[slot];
                    if (low != 0L || high != 0L) return slot;
                }
                return -1;
            }
        };
    }

    private boolean addPacked(long low, long high) {
        int mask = lowTable.length - 1;
        int slot = hash(low, high) & mask;

        while (true) {
            long storedLow = lowTable[slot];
            long storedHigh = singleWord ? 0L : highTable[slot];
            if (storedLow == 0L && storedHigh == 0L) break;
            if (storedLow == low && storedHigh == high) return false;
            slot = (slot + 1) & mask;
        }

        if (size + 1 > resizeThreshold) {
            resize();
            return addPacked(low, high);
        }

        lowTable[slot] = low;
        if (!singleWord) highTable[slot] = high;
        size++;
        return true;
    }

    private void resize() {
        int oldCapacity = lowTable.length;
        if (oldCapacity >= (1 << 30)) {
            throw new IllegalStateException("CompactBitSetSet capacity exhausted");
        }

        long[] oldLow = lowTable;
        long[] oldHigh = highTable;
        allocateTables(oldCapacity << 1);
        int previousSize = size;
        size = 0;

        for (int slot = 0; slot < oldCapacity; slot++) {
            long low = oldLow[slot];
            long high = singleWord ? 0L : oldHigh[slot];
            if (low != 0L || high != 0L) insertRehashed(low, high);
        }

        if (size != previousSize) {
            throw new IllegalStateException("CompactBitSetSet lost entries while resizing");
        }
    }

    private void insertRehashed(long low, long high) {
        int mask = lowTable.length - 1;
        int slot = hash(low, high) & mask;
        while (lowTable[slot] != 0L
                || (!singleWord && highTable[slot] != 0L)) {
            slot = (slot + 1) & mask;
        }
        lowTable[slot] = low;
        if (!singleWord) highTable[slot] = high;
        size++;
    }

    private void allocateTables(int capacity) {
        lowTable = new long[capacity];
        highTable = singleWord ? null : new long[capacity];
        resizeThreshold = (int) ((long) capacity * 13L / 16L);
    }

    private static long encodeLow(BitSet value) {
        long encoded = 0L;
        for (int bit = value.nextSetBit(0);
             bit >= 0 && bit < Long.SIZE;
             bit = value.nextSetBit(bit + 1)) {
            encoded |= 1L << bit;
        }
        return encoded;
    }

    private static long encodeHigh(BitSet value) {
        long encoded = 0L;
        for (int bit = value.nextSetBit(Long.SIZE);
             bit >= 0 && bit < MAX_PACKED_BITS;
             bit = value.nextSetBit(bit + 1)) {
            encoded |= 1L << (bit - Long.SIZE);
        }
        return encoded;
    }

    private int hash(long low, long high) {
        long mixedLow = mix64(low + 0x9E3779B97F4A7C15L);
        if (singleWord) {
            return (int) (mixedLow ^ (mixedLow >>> 32));
        }
        long mixedHigh = mix64(high + 0xD1B54A32D192ED03L);
        long combined = mixedLow ^ Long.rotateLeft(mixedHigh, 29);
        return (int) (combined ^ (combined >>> 32));
    }

    private static long mix64(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    private static BitSet decode(long low, long high) {
        return high == 0L
                ? BitSet.valueOf(new long[]{low})
                : BitSet.valueOf(new long[]{low, high});
    }
}
