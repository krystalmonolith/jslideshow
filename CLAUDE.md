# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JSlideshow is a pure Java command-line application that creates MP4 video slideshows from JPG images with smooth dissolve transitions. It uses JCodec (pure Java video encoding library) with no native dependencies.

## Build Commands

```bash
# Build the project (creates fat JAR with dependencies)
# Uses IntelliJ's bundled Maven with Java 24 from .jdks
JAVA_HOME="C:/Users/mdeaz/.jdks/openjdk-24" "C:/Program Files/JetBrains/IntelliJ IDEA 2025.1/plugins/maven/lib/maven3/bin/mvn.cmd" clean package

# Run the application
"C:/Users/mdeaz/.jdks/openjdk-24/bin/java.exe" -jar target/jslideshow-1.2.1-jar-with-dependencies.jar <directory> [duration] [transition] [frameRate]
```

## Architecture

**Two-class design:**

- **`Main.java`** - CLI entry point, validates args, creates SlideshowCreator instance
- **`SlideshowCreator.java`** - Core slideshow creation logic:
  - `createSlideshow(Path)` - main processing method
  - `findImageFiles()` - discovers .JPG/.jpg files, sorts alphabetically
  - `processImage()` - encodes hold frames for (DURATION - TRANSITION) seconds
  - `processDissolve()` - creates alpha-blended transition frames between images
  - `blendImages()` - uses `AlphaComposite` to blend two images
  - `encodeFrame()` - converts `BufferedImage` to JCodec `Picture` and encodes

**Default configuration constants** at top of `SlideshowCreator.java`:
```java
public static final double DEFAULT_DURATION = 3.0;      // seconds per image
public static final double DEFAULT_TRANSITION = 0.75;   // transition duration
public static final int DEFAULT_FRAME_RATE = 30;        // frames per second
```

These can be overridden via optional command line arguments: `[duration] [transition] [frameRate]`

## Dependencies

- **JCodec 0.2.5** - Pure Java H.264/MP4 video encoding
- **JCodec JavaSE 0.2.5** - AWT integration for BufferedImage conversion

## Requirements

- Java 24+
- Maven 3.9+

## Output Format

- Container: MP4
- Codec: H.264
- Filename pattern: `YYYYMMDD'T'HHmmss-output.mp4`

## Fast Test

```bash
"C:/Users/mdeaz/.jdks/openjdk-24/bin/java.exe" -jar target/jslideshow-1.2.1-jar-with-dependencies.jar images 5.0 2.5 30
```

## Bump Version

When asked to "bump the version" or "bump":
1. Read the current `<version>` tag from pom.xml
2. Increment the patch number (e.g., 1.2.1 → 1.2.2)
3. Update the `<version>` tag in pom.xml
4. Report the change (e.g., "Version bumped: 1.2.1 → 1.2.2")
