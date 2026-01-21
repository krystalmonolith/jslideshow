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
                System.err.println("Usage: java com.krystalmonolith.jslideshow.Main <directory-path>");
                System.err.println("Example: java com.krystalmonolith.jslideshow.Main /path/to/images");
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
}
