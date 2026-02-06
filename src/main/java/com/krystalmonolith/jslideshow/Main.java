package com.krystalmonolith.jslideshow;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.concurrent.Callable;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * Command-line entry point for JSlideshow.
 */
@Command(
        name = "jslideshow",
        description = "Creates MP4 video slideshows from JPG images with smooth dissolve transitions.",
        mixinStandardHelpOptions = true,
        versionProvider = Main.ManifestVersionProvider.class
)
public class Main implements Callable<Integer> {

    @Parameters(index = "0", description = "Path to folder containing JPG images.")
    private Path directory;

    @Option(names = {"-d", "--duration"},
            description = "Seconds per image (default: ${DEFAULT-VALUE}).",
            defaultValue = "3.0")
    private double duration;

    @Option(names = {"-t", "--transition"},
            description = "Dissolve transition duration in seconds (default: ${DEFAULT-VALUE}).",
            defaultValue = "0.75")
    private double transition;

    @Option(names = {"-f", "--frame-rate"},
            description = "Frames per second (default: ${DEFAULT-VALUE}).",
            defaultValue = "30")
    private int frameRate;

    @Option(names = {"-o", "--output"},
            description = "Output MP4 file path (default: timestamped filename).")
    private String output;

    @Option(names = {"-b", "--batchsize"},
            description = "Parallel encoding batch size (default: number of available processors).")
    private Integer batchSize;

    @Override
    public Integer call() throws Exception {
        if (!directory.toFile().exists()) {
            System.err.println("Error: Directory does not exist: " + directory);
            return 1;
        }

        if (!directory.toFile().isDirectory()) {
            System.err.println("Error: Path is not a directory: " + directory);
            return 1;
        }

        int maxBatchSize = Runtime.getRuntime().availableProcessors();
        int effectiveBatchSize = maxBatchSize;

        if (batchSize != null) {
            if (batchSize < 1 || batchSize > maxBatchSize) {
                System.err.printf("Error: Invalid batch size %d. " +
                        "On this system, -b/--batchsize must be between 1 and %d " +
                        "(%d available processors).%n", batchSize, maxBatchSize, maxBatchSize);
                return 1;
            }
            effectiveBatchSize = batchSize;
        }

        System.out.println("Parameters:");
        System.out.printf("  Duration:   %.2f seconds%n", duration);
        System.out.printf("  Transition: %.2f seconds%n", transition);
        System.out.printf("  Frame rate: %d fps%n", frameRate);
        System.out.printf("  Batch size: %d%n%n", effectiveBatchSize);

        var creator = new SlideshowCreator2(duration, transition, frameRate);
        if (output != null) {
            creator.createSlideshow(directory, new File(output), effectiveBatchSize);
        } else {
            creator.createSlideshow(directory, effectiveBatchSize);
        }
        return 0;
    }

    /**
     * Program Entry Point
     * @param args Array of zero or more command line arguments.
     */
    public static void main(String[] args) {
        int exitCode = new CommandLine(new Main()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Reads Implementation-Version from the JAR manifest for --version support.
     */
    static class ManifestVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            Enumeration<URL> resources = Main.class.getClassLoader()
                    .getResources("META-INF/MANIFEST.MF");
            while (resources.hasMoreElements()) {
                URL url = resources.nextElement();
                try {
                    Manifest manifest = new Manifest(url.openStream());
                    Attributes attr = manifest.getMainAttributes();
                    String mainClass = attr.getValue("Main-Class");
                    if ("com.krystalmonolith.jslideshow.Main".equals(mainClass)) {
                        String version = attr.getValue("Implementation-Version");
                        if (version != null) {
                            return new String[]{"jslideshow " + version};
                        }
                    }
                } catch (IOException ignored) {
                }
            }
            return new String[]{"jslideshow (unknown version)"};
        }
    }
}
