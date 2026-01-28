package com.krystalmonolith.jslideshow;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Creates a video slideshow from JPG images with dissolve transitions.
 * Pure Java implementation using JCodec.
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
        if (duration < 0) throw new IllegalArgumentException("duration must be >= 0");
        if (transition < 0) throw new IllegalArgumentException("transition must be >= 0");
        if (duration == 0 && transition == 0)
            throw new IllegalArgumentException("duration and transition cannot both be zero");
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
        // Record start time
        var startTime = System.currentTimeMillis();

        // Get all .JPG and .jpg files in the specified directory
        var imageFiles = findImageFiles(directoryPath);

        if (imageFiles.length == 0) {
            throw new IllegalStateException("No .JPG or .jpg files found in: " + directoryPath);
        }

        // Generate timestamped output filename
        var outputFile = generateOutputFilename();

        System.out.printf("Processing directory: %s%n", directoryPath.toAbsolutePath());
        System.out.printf("Found %d images%n", imageFiles.length);
        System.out.printf("Output file: %s\n%n", outputFile);

        // Create encoder
//        var encoder = SequenceEncoder.createSequenceEncoder(new File(outputFile), frameRate);

        try {
            BufferedImage initialImage = ImageIO.read(imageFiles[0]);
            var blackImage = createBlackImage(initialImage.getWidth(), initialImage.getHeight());

            List<BufferedImage> imageList =
                    IntStream.range(0, imageFiles.length)
                            .mapToObj((int i) -> { // Emit two File(s) in an array: [ currentFile, nameFile ]
                                File currentFile = null;
                                File nextFile = null;
                                if (i == 0) {
                                    nextFile = imageFiles[i];
                                } else {
                                    if (i < imageFiles.length - 1) {
                                        currentFile = imageFiles[i - 1];
                                        nextFile = imageFiles[i];
                                    } else {
                                        currentFile = imageFiles[i];
                                    }
                                }
                                return new File[]{currentFile, nextFile};
                            })
                            .map((File[] files) -> {
                                try {
                                    File currentFile = files[0];
                                    File nextFile = files[1];
                                    BufferedImage currentImage = null;
                                    BufferedImage nextImage = null;
                                    if (currentFile == null) {
                                        nextImage = ImageIO.read(nextFile);
                                        currentImage = createBlackImage(nextImage.getWidth(), nextImage.getHeight());
                                    } else {
                                        if (nextFile == null) {
                                            currentImage = ImageIO.read(currentFile);
                                            nextImage = createBlackImage(currentImage.getWidth(), currentImage.getHeight());
                                        } else {
                                            currentImage = ImageIO.read(currentFile);
                                            nextImage = ImageIO.read(nextFile);
                                        }
                                    }
                                    return new BufferedImage[]{currentImage, nextImage};
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }

                            })
                            .map(bufferedImages -> bufferedImages[0])
                            .toList();

            JCodecParallelEncoder encoder = new JCodecParallelEncoder();
            encoder.encode(imageList, new File(outputFile));

            // Calculate elapsed time
            var endTime = System.currentTimeMillis();
            var elapsedSeconds = (endTime - startTime) / 1000.0;

            System.out.println();
            System.out.printf("Success! Created %s%n", outputFile);
            System.out.printf("Total processing time: %.2f seconds%n", elapsedSeconds);

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
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

    /**
     * Create a black image with the specified dimensions.
     * TYPE_INT_RGB initializes all pixels to black (0,0,0) by default.
     *
     * @param width  Width of the image in pixels
     * @param height Height of the image in pixels
     * @return A new BufferedImage filled with black
     */
    private BufferedImage createBlackImage(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    }

}
