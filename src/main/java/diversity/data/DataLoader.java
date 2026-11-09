package diversity.data;

import diversity.util.BitMap;

import java.io.*;
import java.util.*;

public class DataLoader {

    private BitMap bitMap;

    public DataLoader() {
        this.bitMap = new BitMap();
    }

    /**
     * Load a CSV file into a list of maps (rows).
     * Each row: featureName -> value (string)
     */
    public List<Map<String, String>> loadCsv(String path,
                                             List<String> uninterestingAttributes,
                                             List<String> interestingAttributes) {
        List<Map<String, String>> df = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String headerLine = br.readLine();
            if (headerLine == null) throw new RuntimeException("Empty CSV file: " + path);

            String[] headers = headerLine.split(",");
            List<String> columns = new ArrayList<>(Arrays.asList(headers));

            if (!interestingAttributes.isEmpty()) {
                columns.retainAll(interestingAttributes);
            }
            columns.removeAll(uninterestingAttributes);

            String line;
            while ((line = br.readLine()) != null) {
                String[] values = line.split(",");
                Map<String, String> row = new LinkedHashMap<>();
                for (int i = 0; i < headers.length; i++) {
                    if (columns.contains(headers[i])) {
                        row.put(headers[i], values[i]);
                    }
                }
                df.add(row);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return df;
    }

    /**
     * One-hot encode the dataset.
     * Returns: int[][] where each row is one-hot encoded.
     */
    public int[][] encodeFeatures(List<Map<String, String>> df) {
        Map<String, Set<String>> categories = new LinkedHashMap<>();
        for (Map<String, String> row : df) {
            for (Map.Entry<String, String> entry : row.entrySet()) {
                categories.computeIfAbsent(entry.getKey(), k -> new LinkedHashSet<>())
                        .add(entry.getValue());
            }
        }

        this.bitMap = new BitMap();
        int totalBits = 0;
        Map<String, List<String>> categoryLists = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : categories.entrySet()) {
            String feature = entry.getKey();
            List<String> cats = new ArrayList<>(entry.getValue());
            categoryLists.put(feature, cats);
            bitMap.addFeature(feature, cats.size());
        }

        totalBits = bitMap.getTotalBits();

        int[][] encoded = new int[df.size()][totalBits];
        for (int rowIdx = 0; rowIdx < df.size(); rowIdx++) {
            Map<String, String> row = df.get(rowIdx);
            int colOffset = 0;
            for (Map.Entry<String, List<String>> entry : categoryLists.entrySet()) {
                String feature = entry.getKey();
                List<String> cats = entry.getValue();
                String value = row.get(feature);

                int catIndex = cats.indexOf(value);
                if (catIndex >= 0) {
                    encoded[rowIdx][colOffset + catIndex] = 1;
                }
                colOffset += cats.size();
            }
        }
        return encoded;
    }

    public BitMap getBitMap() {
        return this.bitMap;
    }
}
