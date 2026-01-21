# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

JSlideshow is a pure Java command-line application that creates MP4 video slideshows from JPG images with smooth dissolve transitions. It uses JCodec (pure Java video encoding library) with no native dependencies.

## Build Commands

```bash
# Build the project (creates fat JAR with dependencies)
mvn clean package

# Run the application
java -jar target/jslideshow-1.1.1-jar-with-dependencies.jar /path/to/images

# Run directly without packaging
mvn exec:java -Dexec.mainClass="com.krystalmonolith.jslideshow.SlideshowCreator" -Dexec.args="/path/to/images"

# Generate project documentation site
mvn site
```

## Architecture

**Single-class design** - The entire application is in `SlideshowCreator.java`:

- **Entry point:** `main()` validates CLI args and creates instance
- **Core API:** `createSlideshow(Path)` - processes directory of images
- **Image processing pipeline:**
  - `findImageFiles()` - discovers .JPG/.jpg files, sorts alphabetically
  - `processImage()` - encodes hold frames for (DURATION - TRANSITION) seconds
  - `processDissolve()` - creates alpha-blended transition frames between images
  - `blendImages()` - uses `AlphaComposite` to blend two images
  - `encodeFrame()` - converts `BufferedImage` to JCodec `Picture` and encodes

**Configuration constants** at top of `SlideshowCreator.java`:
```java
private static final double DURATION = 3.0;      // seconds per image
private static final double TRANSITION = 0.75;   // transition duration
private static final int FRAME_RATE = 30;        // frames per second
```

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
