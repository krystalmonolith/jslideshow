package com.krystalmonolith.jslideshow;

import java.nio.file.Path;

/**
 * Command-line entry point for JSlideshow.
 */
public class Main {

    /**
     * Program Entry Point
     * @param args Array of zero or more command line arguments.
     */
    public static void main(String[] args) {
        try {
            if (args.length < 1) {
                System.err.println("Usage: java com.krystalmonolith.jslideshow.Main <directory> [duration] [transition] [frameRate]");
                System.err.println("  directory  - path to folder containing JPG images");
                System.err.println("  duration   - seconds per image (default: 3.0)");
                System.err.println("  transition - dissolve duration in seconds (default: 0.75)");
                System.err.println("  frameRate  - frames per second (default: 30)");
                System.err.println();
                System.err.println("Example: java com.krystalmonolith.jslideshow.Main /path/to/images 2.0 0.5 24");
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

            // Parse optional parameters with defaults
            double duration = args.length > 1 ? Double.parseDouble(args[1]) : SlideshowCreator.DEFAULT_DURATION;
            double transition = args.length > 2 ? Double.parseDouble(args[2]) : SlideshowCreator.DEFAULT_TRANSITION;
            int frameRate = args.length > 3 ? Integer.parseInt(args[3]) : SlideshowCreator.DEFAULT_FRAME_RATE;

            // Print parameters
            System.out.println("Parameters:");
            System.out.printf("  Duration:   %.2f seconds%n", duration);
            System.out.printf("  Transition: %.2f seconds%n", transition);
            System.out.printf("  Frame rate: %d fps%n%n", frameRate);

            var creator = new SlideshowCreator(duration, transition, frameRate);
            creator.createSlideshow(directoryPath);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
