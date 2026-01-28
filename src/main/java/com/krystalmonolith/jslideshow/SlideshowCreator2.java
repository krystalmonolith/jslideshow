package com.krystalmonolith.jslideshow;

import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Creates a video slideshow from JPG images using parallel encoding
 * with dissolve transitions and fade in/out from black.
 * Pure Java implementation using JCodec with segment-based GOP encoding.
 */
public class SlideshowCreator2 {

    /**
     * Default seconds per image
     */
    public static final double DEFAULT_DURATION = 3.0;
    /**
     * Default transition duration in seconds
     */
    public static final double DEFAULT_TRANSITION = 0.75;
    /**
     * Default frames per second
     */
    public static final int DEFAULT_FRAME_RATE = 30;

    /**
     * seconds per image
     */
    private final double duration;
    /**
     * transition duration in seconds
     */
    private final double transition;
    /**
     * frames per second
     */
    private final int frameRate;

    /**
     * Default Constructor using default values.
     */
    public SlideshowCreator2() {
        this(DEFAULT_DURATION, DEFAULT_TRANSITION, DEFAULT_FRAME_RATE);
    }

    /**
     * Constructor with configurable parameters.
     *
     * @param duration   seconds per image
     * @param transition transition duration in seconds
     * @param frameRate  frames per second
     * @throws IllegalArgumentException if parameters are invalid
     */
    public SlideshowCreator2(double duration, double transition, int frameRate) {
        if (duration <= 0) throw new IllegalArgumentException("duration must be > 0");
        if (transition < 0) throw new IllegalArgumentException("transition must be >= 0");
        if (frameRate <= 0) throw new IllegalArgumentException("frameRate must be positive");
        if (duration < transition) {
            System.out.println("Note: transition is longer than duration; images will only appear during transitions");
        }
        this.duration = duration;
        this.transition = transition;
        this.frameRate = frameRate;
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
        var startTime = System.currentTimeMillis();

        var imageFiles = findImageFiles(directoryPath);

        if (imageFiles.length == 0) {
            throw new IllegalStateException("No .JPG or .jpg files found in: " + directoryPath);
        }

        var outputFile = generateOutputFilename();

        int holdFrames = (int) ((duration - transition) * frameRate);
        int transitionFrames = (int) (transition * frameRate);

        System.out.printf("Processing directory: %s%n", directoryPath.toAbsolutePath());
        System.out.printf("Found %d images%n", imageFiles.length);
        System.out.printf("Duration: %.2f seconds per image (%d hold frames @ %d fps)%n", duration, holdFrames, frameRate);
        System.out.printf("Transition: %.2f seconds (%d frames)%n", transition, transitionFrames);
        System.out.printf("Output file: %s%n%n", outputFile);

        JCodecParallelEncoder encoder = new JCodecParallelEncoder();
        encoder.encode(imageFiles, holdFrames, transitionFrames, frameRate, new File(outputFile));

        var endTime = System.currentTimeMillis();
        var elapsedSeconds = (endTime - startTime) / 1000.0;

        System.out.println();
        System.out.printf("Success! Created %s%n", outputFile);
        System.out.printf("Total processing time: %.2f seconds%n", elapsedSeconds);
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
