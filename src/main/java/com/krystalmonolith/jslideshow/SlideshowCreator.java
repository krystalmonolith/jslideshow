package com.krystalmonolith.jslideshow;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.ColorSpace;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Creates a video slideshow from JPG images with dissolve transitions.
 * Pure Java implementation using JCodec - Java 24 compatible.
 * <p>
 * Maven dependencies:
 * <dependency>
 * <groupId>org.jcodec</groupId>
 * <artifactId>jcodec</artifactId>
 * <version>0.2.5</version>
 * </dependency>
 * <dependency>
 * <groupId>org.jcodec</groupId>
 * <artifactId>jcodec-javase</artifactId>
 * <version>0.2.5</version>
 * </dependency>
 */
public class SlideshowCreator {

    private static final double DURATION = 3.0;      // seconds per image
    private static final double TRANSITION = 0.75;    // transition duration in seconds
    private static final int FRAME_RATE = 30;        // frames per second

    /**
     * Generate output filename with timestamp in format: YYYYMMDD'T'hhmmss-output.mp4
     * Example: 20240119T143052-output.mp4
     */
    private static String generateOutputFilename() {
        var formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        var timestamp = LocalDateTime.now().format(formatter);
        return "%s-output.mp4".formatted(timestamp);
    }

    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: java com.krystalmonolith.jslideshow.SlideshowCreator <directory-path>");
                System.err.println("Example: java com.krystalmonolith.jslideshow.SlideshowCreator /path/to/images");
                System.exit(1);
            }

            var directoryPath = Path.of(args[0]);

            if (!directoryPath.toFile().exists()) {
                System.err.println("Error: Directory does not exist: " + args[0]);
                System.exit(1);
            }

            if (!directoryPath.toFile().isDirectory()) {
                System.err.println("Error: Path is not a directory: " + args[0]);
                System.exit(1);
            }

            var creator = new SlideshowCreator();
            creator.createSlideshow(directoryPath);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

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

        System.out.println("Processing directory: %s".formatted(directoryPath.toAbsolutePath()));
        System.out.println("Found %d images".formatted(imageFiles.length));
        System.out.println("Output file: %s".formatted(outputFile));
        System.out.println();

        // Create encoder
        var encoder = SequenceEncoder.createSequenceEncoder(new File(outputFile), FRAME_RATE);

        try {
            BufferedImage currentImage = null;
            for (int i = 0; i < imageFiles.length; i++) {
                // Calculate percentage
                int percentage = (int) ((i + 1) * 100.0 / imageFiles.length);

                System.out.println("\r[%3d%%] Processing image %d/%d: %s".formatted(
                        percentage, i + 1, imageFiles.length, imageFiles[i].getName()));

                if (currentImage == null) {
                    System.out.print('R');
                    currentImage = ImageIO.read(imageFiles[i]);
                }

                if (i < imageFiles.length - 1) {
                    processImage(encoder, currentImage);

                    System.out.print('r');
                    var nextImage = ImageIO.read(imageFiles[i + 1]);

                    // Dissolve transition
                    processDissolve(encoder, currentImage, nextImage);
                    currentImage = nextImage;
                } else {
                    processImage(encoder, currentImage);
                }
            }

            // Calculate elapsed time
            var endTime = System.currentTimeMillis();
            var elapsedSeconds = (endTime - startTime) / 1000.0;

            System.out.println();
            System.out.println("Success! Created %s".formatted(outputFile));
            System.out.println("Total processing time: %.2f seconds".formatted(elapsedSeconds));

        } finally {
            // Finish encoding and close the file
            encoder.finish();
        }
    }

    private void processImage(SequenceEncoder encoder, BufferedImage currentImage) throws IOException {
        // Show current image for (DURATION - TRANSITION) seconds
        int holdFrames = (int) ((DURATION - TRANSITION) * FRAME_RATE);
        for (int f = 0; f < holdFrames; f++) {
            System.out.print('.');
            encodeFrame(encoder, currentImage);
        }
    }

    private File[] findImageFiles(Path directoryPath) {
        var dir = directoryPath.toFile();
        var files = dir.listFiles((d, name) ->
                name.endsWith(".JPG") || name.endsWith(".jpg"));

        if (files == null) {
            return new File[0];
        }

        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }

    private void processDissolve(SequenceEncoder encoder,
                                 BufferedImage currentImage,
                                 BufferedImage nextImage) throws IOException {

        int transitionFrames = (int) (TRANSITION * FRAME_RATE);

        for (int f = 0; f < transitionFrames; f++) {
            float alpha = (float) f / transitionFrames;  // 0.0 to 1.0
            var blended = blendImages(currentImage, nextImage, alpha);
            System.out.print(';');
            encodeFrame(encoder, blended);
        }
    }

    /**
     * Encode a BufferedImage as a video frame.
     */
    private void encodeFrame(SequenceEncoder encoder, BufferedImage image) throws IOException {
        // Convert BufferedImage to JCodec Picture
        Picture picture = AWTUtil.fromBufferedImage(image, ColorSpace.RGB);
        encoder.encodeNativeFrame(picture);
    }

    /**
     * Blend two images together with specified alpha.
     *
     * @param img1  First image (from)
     * @param img2  Second image (to)
     * @param alpha Blend factor (0.0 = all img1, 1.0 = all img2)
     * @return Blended image
     */
    private BufferedImage blendImages(BufferedImage img1, BufferedImage img2, float alpha) {
        int width = img1.getWidth();
        int height = img1.getHeight();

        var blended = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var g = blended.createGraphics();

        try {
            // Draw first image
            g.drawImage(img1, 0, 0, null);

            // Draw second image with transparency
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.drawImage(img2, 0, 0, null);
        } finally {
            g.dispose();
        }

        return blended;
    }
}
