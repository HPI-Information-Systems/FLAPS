package diversity;

import diversity.data.DataLoader;
import diversity.sampler.ChunkSamplerFull;
import diversity.sampler.Sampler;
import diversity.util.BitMap;
import diversity.util.MUPWriter;
import diversity.util.PatternGenerator;
import diversity.util.PatternHandler;
import diversity.validator.TopDownValidator;
import diversity.validator.Validator;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class Main {
    private static final Logger log = LoggerFactory.getLogger(Main.class);
    private static final java.nio.file.Path DEFAULT_MUPS_OUTPUT_DIR =
            Paths.get("results");

    public static void main(String[] args) {
        String csv = null;
        String uninteresting = null;
        String interesting = null;
        int mincov = 1;
        int chunkSize = 1;
        int chunksPerRow = 1;
        boolean outputMups = false;
        String mupsOutput = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--csv":
                    csv = args[++i];
                    break;
                case "--uninteresting-attributes":
                    uninteresting = args[++i];
                    break;
                case "--interesting-attributes":
                    interesting = args[++i];
                    break;
                case "--mincov":
                    mincov = Integer.parseInt(args[++i]);
                    break;
                case "--chunk-size":
                    chunkSize = Integer.parseInt(args[++i]);
                    break;
                case "--chunks-per-row":
                    chunksPerRow = Integer.parseInt(args[++i]);
                    break;
                case "--output-mups":
                    outputMups = true;
                    if (i + 1 < args.length && !args[i + 1].startsWith("--")) {
                        String requestedOutput = args[++i];
                        if (!requestedOutput.isBlank()) mupsOutput = requestedOutput;
                    }
                    break;
            }
        }

        if (csv == null) {
            log.error("You must provide --csv path");
            System.exit(1);
        }

        DataLoader loader = new DataLoader();
        List<Map<String,String>> df = loader.loadCsv(csv,
                uninteresting == null ? List.of() : Arrays.asList(uninteresting.split(",")),
                interesting == null ? List.of() : Arrays.asList(interesting.split(",")));

        log.info("Loaded {} rows and {} columns", df.size(), df.getFirst().size());

        int[][] encoded = loader.encodeFeatures(df);
        log.info("Encoded shape: {} x {}", encoded.length, encoded[0].length);

        BitMap bitMap = loader.getBitMap();

        PatternHandler ph = new PatternHandler(encoded);
        List<BitSet> dataset = ph.getDataset();

        long start = System.nanoTime();

        PatternGenerator gen = new PatternGenerator(bitMap);
        List<BitSet> A = gen.initializeLevelOne();

        log.info("Start Sampler");
        Sampler sampler = new ChunkSamplerFull(mincov, bitMap.getTotalBits(), chunkSize, chunksPerRow);

        sampler.fit(dataset);
        Set<BitSet> coveredSet = sampler.getCoveredPatterns();

//        log.info("Seeder disabled: running Validator only");
//        Set<BitSet> coveredSet = Set.of();

        log.info("Sampler produced {} patterns.\n", coveredSet.size());
        long end_sampling = System.nanoTime();
        double samplingSeconds = (end_sampling - start) / 1_000_000_000.0;
        System.out.println("Sampling Runtime: " + samplingSeconds + "s");

        log.info("Start Validator");
        Validator validator = new TopDownValidator(dataset, bitMap, mincov);
        List<BitSet> mups = validator.validate(A, coveredSet);
        long coverageThresholdEvaluations = validator.getCoverageThresholdEvaluations();

        long end = System.nanoTime();
        double seconds = (end - start) / 1_000_000_000.0;

        System.out.println("Found " + mups.size() + " MUPs");
        System.out.println("Coverage-threshold evaluations: " + coverageThresholdEvaluations);
        System.out.println("Sampling Runtime: " + samplingSeconds + "s");
        System.out.println("Runtime: " + seconds + "s");

        String datasetName = Paths.get(csv).getFileName().toString();
        java.io.File resultsDir = DEFAULT_MUPS_OUTPUT_DIR.toFile();
        if (!resultsDir.exists()) {
            resultsDir.mkdirs();
        }

        if (outputMups) {
            java.nio.file.Path outputPath = mupsOutput == null
                    ? DEFAULT_MUPS_OUTPUT_DIR.resolve(
                            "mups_" + datasetName + "_mincov_" + mincov + ".csv")
                    : Paths.get(mupsOutput);
            try {
                MUPWriter.write(outputPath, mups, dataset, df, bitMap);
                System.out.println("Wrote MUPs to " + outputPath);
            } catch (IOException e) {
                log.error("Failed to write MUPs to {}", outputPath, e);
                System.exit(1);
            }
        }

        String resultsFile = new java.io.File(resultsDir, "results_" + datasetName).getPath();

        String resultsHeader = "dataset,mincov,chunkSize,chunksPerRow,numSamples,numMUPs,samplingSeconds,totalSeconds,numCoverageThresholdEvaluations";

        try (RandomAccessFile results = new RandomAccessFile(resultsFile, "rw");
             FileChannel channel = results.getChannel();
             FileLock lock = channel.lock()) {

            if (results.length() == 0) {
                results.write((resultsHeader + "\n").getBytes(StandardCharsets.UTF_8));
            }

            String row = String.format(Locale.US,
                    "%s,%d,%d,%d,%d,%d,%.4f,%.4f,%d%n",
                    datasetName, mincov, chunkSize, chunksPerRow,
                    coveredSet.size(), mups.size(), samplingSeconds, seconds,
                    coverageThresholdEvaluations);

            results.seek(results.length());
            results.write(row.getBytes(StandardCharsets.UTF_8));

        } catch (IOException e) {
            log.error("Failed to write results", e);
        }

        System.exit(0);
    }

}
