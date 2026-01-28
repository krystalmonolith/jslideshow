package com.krystalmonolith.jslideshow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Creates a video slideshow from JPG images using parallel encoding.
 * Pure Java implementation using JCodec with segment-based GOP encoding.
 */
public class SlideshowCreator2 {

    /**
     * Default seconds per image
     */
    public static final double DEFAULT_DURATION = 3.0;
    /**
     * Default transition duration in seconds (currently unused - transitions disabled)
     */
    public static final double DEFAULT_TRANSITION = 0.0;
    /**
     * Default frames per second
     */
    public static final int DEFAULT_FRAME_RATE = 30;

    /**
     * seconds per image
     */
    private final double duration;
    /**
     * frames per second
     */
    private final int frameRate;

    /**
     * Default Constructor using default values.
     */
    public SlideshowCreator2() {
        this(DEFAULT_DURATION, DEFAULT_FRAME_RATE);
    }

    /**
     * Constructor with configurable parameters.
     *
     * @param duration   seconds per image
     * @param frameRate  frames per second
     * @throws IllegalArgumentException if parameters are invalid
     */
    public SlideshowCreator2(double duration, int frameRate) {
        if (duration <= 0) throw new IllegalArgumentException("duration must be > 0");
        if (frameRate <= 0) throw new IllegalArgumentException("frameRate must be positive");
        this.duration = duration;
        this.frameRate = frameRate;
    }

    /**
     * Legacy constructor for backward compatibility (transition parameter ignored).
     *
     * @param duration   seconds per image
     * @param transition ignored (transitions not yet supported in parallel encoder)
     * @param frameRate  frames per second
     */
    public SlideshowCreator2(double duration, double transition, int frameRate) {
        this(duration, frameRate);
        if (transition > 0) {
            System.out.println("Note: Dissolve transitions not yet supported in parallel encoder; using hard cuts");
        }
    }

    /**
     * Generate output filename with timestamp in format: YYYYMMDD'T'hhmmss-output.mp4
     * Example: 20240119T143052-output.mp4
     *
     * @return time stamped output file name string
     */
    private static String generateOutputFilename() {
        var formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        var timestamp = LocalDateTime.now().format(formatter);
        return "%s-output.mp4".formatted(timestamp);
    }

    /**
     * Make a slide show video!
     *
     * @param directoryPath Path of a directory containing one or more *.JPG or *.jpg files.
     * @throws Exception on error creating the video
     */
    public void createSlideshow(Path directoryPath) throws Exception {
        // Record start time
        var startTime = System.currentTimeMillis();

        // Get all .JPG and .jpg files in the specified directory
        var imageFiles = findImageFiles(directoryPath);

        if (imageFiles.length == 0) {
            throw new IllegalStateException("No .JPG or .jpg files found in: " + directoryPath);
        }

        // Generate timestamped output filename
        var outputFile = generateOutputFilename();

        // Calculate frames per image
        int framesPerImage = (int) (duration * frameRate);

        System.out.printf("Processing directory: %s%n", directoryPath.toAbsolutePath());
        System.out.printf("Found %d images%n", imageFiles.length);
        System.out.printf("Duration: %.2f seconds per image (%d frames @ %d fps)%n", duration, framesPerImage, frameRate);
        System.out.printf("Output file: %s%n%n", outputFile);

        // Load all images
        List<BufferedImage> images = loadImages(imageFiles);

        // Encode using parallel segment-based encoder
        JCodecParallelEncoder encoder = new JCodecParallelEncoder();
        encoder.encode(images, framesPerImage, frameRate, new File(outputFile));

        // Calculate elapsed time
        var endTime = System.currentTimeMillis();
        var elapsedSeconds = (endTime - startTime) / 1000.0;

        System.out.println();
        System.out.printf("Success! Created %s%n", outputFile);
        System.out.printf("Total processing time: %.2f seconds%n", elapsedSeconds);
    }

    /**
     * Load all image files into memory.
     *
     * @param imageFiles Array of image files to load
     * @return List of BufferedImage objects
     */
    private List<BufferedImage> loadImages(File[] imageFiles) {
        List<BufferedImage> images = new ArrayList<>(imageFiles.length);
        for (File file : imageFiles) {
            try {
                BufferedImage img = ImageIO.read(file);
                if (img != null) {
                    images.add(img);
                    System.out.printf("  Loaded: %s (%dx%d)%n", file.getName(), img.getWidth(), img.getHeight());
                } else {
                    System.err.printf("  Warning: Could not read %s%n", file.getName());
                }
            } catch (IOException e) {
                System.err.printf("  Error loading %s: %s%n", file.getName(), e.getMessage());
            }
        }
        return images;
    }

    /**
     * Locates all the image file(s) to be used as input.
     *
     * @param directoryPath Path of a directory containing one or more *.JPG or *.jpg files.
     * @return an array of zero or more {@link File}
     */
    private File[] findImageFiles(Path directoryPath) {
        var dir = directoryPath.toFile();
        var files = dir.listFiles((_, name) ->
                name.endsWith(".JPG") || name.endsWith(".jpg"));

        if (files == null) {
            return new File[0];
        }

        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }
}
