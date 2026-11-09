package diversity.util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Writes reverse-mapped MUPs and their exact coverage on explicit request. */
public final class MUPWriter {

    private MUPWriter() {}

    public static void write(
            Path output,
            List<BitSet> mups,
            List<BitSet> dataset,
            List<Map<String, String>> dataFrame,
            BitMap bitMap
    ) throws IOException {
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        List<String> features = new ArrayList<>(bitMap.getFeatures());
        Map<String, List<String>> categories = collectCategories(features, dataFrame);
        BitmapIndex index = new BitmapIndex(dataset, bitMap.getTotalBits());
        long[] scratch = new long[index.getNumIndexWords()];

        try (BufferedWriter writer = Files.newBufferedWriter(
                output, StandardCharsets.UTF_8)) {
            writePatternFields(writer, features);
            writer.write(";Coverage");
            writer.newLine();

            for (BitSet mup : mups) {
                writePattern(writer, mup, features, categories, bitMap);
                writer.write(';');
                writer.write(Integer.toString(index.countSupport(mup, scratch)));
                writer.newLine();
            }
        }
    }

    private static Map<String, List<String>> collectCategories(
            List<String> features,
            List<Map<String, String>> dataFrame
    ) {
        Map<String, Set<String>> uniqueValues = new LinkedHashMap<>();
        for (String feature : features) {
            uniqueValues.put(feature, new LinkedHashSet<>());
        }
        for (Map<String, String> row : dataFrame) {
            for (String feature : features) {
                uniqueValues.get(feature).add(row.get(feature));
            }
        }

        Map<String, List<String>> categories = new LinkedHashMap<>();
        for (String feature : features) {
            categories.put(feature, new ArrayList<>(uniqueValues.get(feature)));
        }
        return categories;
    }

    private static void writePatternFields(
            BufferedWriter writer,
            List<String> fields
    ) throws IOException {
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) writer.write(',');
            writer.write(escape(fields.get(i)));
        }
    }

    private static void writePattern(
            BufferedWriter writer,
            BitSet pattern,
            List<String> features,
            Map<String, List<String>> categories,
            BitMap bitMap
    ) throws IOException {
        for (int i = 0; i < features.size(); i++) {
            if (i > 0) writer.write(',');
            String feature = features.get(i);
            int[] range = bitMap.getRange(feature);
            int selectedBit = pattern.nextSetBit(range[0]);

            if (selectedBit < 0 || selectedBit > range[1]) {
                writer.write('*');
                continue;
            }
            int additionalBit = pattern.nextSetBit(selectedBit + 1);
            if (additionalBit >= 0 && additionalBit <= range[1]) {
                throw new IllegalArgumentException(
                        "Pattern contains multiple values for attribute " + feature);
            }

            int categoryIndex = selectedBit - range[0];
            List<String> values = categories.get(feature);
            if (categoryIndex >= values.size()) {
                throw new IllegalStateException(
                        "Missing reverse mapping for attribute " + feature);
            }
            writer.write(escape(values.get(categoryIndex)));
        }
    }

    private static String escape(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf(';') < 0
                && value.indexOf('"') < 0 && value.indexOf('\n') < 0
                && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
