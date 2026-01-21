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
 * Pure Java implementation using JCodec.
 */
public class SlideshowCreator {

    /**
     * Default Constructor to placate JavaDoc
     */
    public SlideshowCreator() {
    }

    /**
     * seconds per image
     */
    private static final double DURATION = 3.0;
    /**
     * transition duration in seconds
     */
    private static final double TRANSITION = 0.75;
    /**
     * frames per second
     */
    private static final int FRAME_RATE = 30;

    /**
     * Generate output filename with timestamp in format: YYYYMMDD'T'hhmmss-output.mp4
     * Example: 20240119T143052-output.mp4
     * @return time stamped output file name string
     */
    private static String generateOutputFilename() {
        var formatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
        var timestamp = LocalDateTime.now().format(formatter);
        return "%s-output.mp4".formatted(timestamp);
    }

    /**
     * Make a slide show video!
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
        var encoder = SequenceEncoder.createSequenceEncoder(new File(outputFile), FRAME_RATE);

        try {
            BufferedImage currentImage = null;
            BufferedImage lastImage = null;

            for (int i = 0; i < imageFiles.length; i++) {
                // Calculate percentage
                int percentage = (int) ((i + 1) * 100.0 / imageFiles.length);

                System.out.printf(
                        "\r[%3d%%] Processing image %d/%d: %s%n", percentage, i + 1, imageFiles.length, imageFiles[i].getName());

                if (currentImage == null) {
                    System.out.print('R');
                    currentImage = ImageIO.read(imageFiles[i]);

                    // Fade in from black for the first image
                    System.out.print(" [fade-in]");
                    var blackImage = createBlackImage(currentImage.getWidth(), currentImage.getHeight());
                    processDissolve(encoder, blackImage, currentImage);
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
                    lastImage = currentImage;
                }
            }

            // Fade out to black after the last image
            if (lastImage != null) {
                System.out.print(" [fade-out]");
                var blackImage = createBlackImage(lastImage.getWidth(), lastImage.getHeight());
                processDissolve(encoder, lastImage, blackImage);
            }

            // Calculate elapsed time
            var endTime = System.currentTimeMillis();
            var elapsedSeconds = (endTime - startTime) / 1000.0;

            System.out.println();
            System.out.printf("Success! Created %s%n", outputFile);
            System.out.printf("Total processing time: %.2f seconds%n", elapsedSeconds);

        } finally {
            // Finish encoding and close the file
            encoder.finish();
        }
    }

    /**
     * Encode all the frames to show a given image.
     * @param encoder a {@link SequenceEncoder} instance
     * @param image the image to encode
     * @throws IOException on error encoding frame(s) from the image
     */
    private void processImage(SequenceEncoder encoder, BufferedImage image) throws IOException {
        // Show current image for (DURATION - TRANSITION) seconds
        int holdFrames = (int) ((DURATION - TRANSITION) * FRAME_RATE);
        for (int f = 0; f < holdFrames; f++) {
            System.out.print('.');
            encodeFrame(encoder, image);
        }
    }

    /**
     * Locates all the image file(s) to be used as input.
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
     * Generate a dissolve transition from the {@code currentImage} to the {@code nextImage}.
     * @param encoder a {@link SequenceEncoder} instance
     * @param currentImage The image that is shown at the beginning of the dissolve.
     * @param nextImage The image that is shown at the end of the dissolve.
     * @throws IOException on error encoding frame(s) for the dissolve
     */
    private void processDissolve(SequenceEncoder encoder,
                                 BufferedImage currentImage,
                                 BufferedImage nextImage) throws IOException {

        int transitionFrames = (int) (TRANSITION * FRAME_RATE);

        for (int f = 0; f < transitionFrames; f++) {
            float alpha = (float) (f+1) / transitionFrames;  // 0.0 > alpha <=tes 1.0
            System.out.print('+');
            var blended = blendImages(currentImage, nextImage, alpha);
            System.out.print('.');
            encodeFrame(encoder, blended);
        }
    }

    /**
     * Encode a BufferedImage as a video frame.
     *
     * @param encoder a SequenceEncoder instance
     * @param image   image buffer to send to the encoder
     * @throws IOException on error writing output video file
     */
    private void encodeFrame(SequenceEncoder encoder, BufferedImage image) throws IOException {
        // Convert BufferedImage to JCodec Picture
        Picture picture = AWTUtil.fromBufferedImage(image, ColorSpace.RGB);
        encoder.encodeNativeFrame(picture);
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
