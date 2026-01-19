# JSlideshow - Pure Java Slideshow Creator

Creates video slideshows from JPG images with smooth dissolve transitions using JCodec.

## Features

- **Pure Java** - No native dependencies required
- **Dissolve transitions** - Smooth alpha-blended transitions between images
- **Java 24 compatible** - Uses modern Java features
- **Customizable** - Configure duration, transition time, and frame rate
- **Platform independent** - Runs on any OS with Java 24+
- **Command line interface** - Specify image directory as parameter
- **Timestamped output** - Each run creates uniquely named output file (YYYYMMDD'T'hhmmss-output.mp4)
- **Flexible input** - Processes all .JPG and .jpg files in specified directory
- **Progress tracking** - Shows percentage completion and elapsed time

## Requirements

- Java 24 or later
- Maven 3.9+
- Directory containing JPG or jpg image files

## Quick Start

### Build and Run

```bash
# Clone or download the project
cd jslideshow

# Build the project
mvn clean package

# Run with path to directory containing JPG files
java -jar target/jslideshow-1.0.0-jar-with-dependencies.jar /path/to/images

# Example with current directory
java -jar target/jslideshow-1.0.0-jar-with-dependencies.jar .

# Example with absolute path
java -jar target/jslideshow-1.0.0-jar-with-dependencies.jar /home/user/photos/vacation
```

### Usage

```
java -jar target/jslideshow-1.0.0-jar-with-dependencies.jar <directory-path>
```

**Parameters:**
- `<directory-path>` - Path to directory containing JPG/jpg files (required)

**Example:**
```bash
java -jar target/jslideshow-1.0.0-jar-with-dependencies.jar ~/Pictures/vacation-2024
```

### Directory Structure

```
jslideshow/
├── src/
│   └── main/
│       └── java/
│           └── com/
│               └── krystalmonolith/
│                   └── jslideshow/
│                       └── SlideshowCreator.java
├── pom.xml
└── README.md
```

Place your `DSC_*.JPG` files in the directory where you run the JAR.

## Configuration

Edit the constants in `SlideshowCreator.java`:

```java
private static final double DURATION = 1.0;      // seconds per image
private static final double TRANSITION = 0.5;    // transition duration in seconds
private static final int FRAME_RATE = 30;        // frames per second
```

The output filename is automatically generated with a timestamp in the format: `YYYYMMDD'T'hhmmss-output.mp4`

## How It Works

The slideshow creator processes images sequentially:

1. **Hold Phase**: Each image is displayed for `(DURATION - TRANSITION)` seconds
2. **Dissolve Phase**: Images blend together over `TRANSITION` seconds using alpha compositing
   - Formula: `output = alpha × nextImage + (1-alpha) × currentImage`
   - Alpha increases linearly from 0.0 to 1.0
3. **Final Image**: Last image displays for full `DURATION`

### Performance

**For 144 images at 5568×3712 pixels:**
- Processing time: 3-8 minutes (CPU-dependent)
- Memory usage: 1-2GB peak
- Output size: ~50-100MB (H.264 in MP4 container)
- Total frames: ~4,320 frames
- Duration: ~144 seconds (2 minutes 24 seconds)

The program displays progress percentage for each image processed and shows the total elapsed time at completion.

## Building from Source

```bash
# Compile only
mvn compile

# Compile and package
mvn clean package

# Run without building JAR
mvn exec:java -Dexec.mainClass="com.krystalmonolith.jslideshow.SlideshowCreator" -Dexec.args="/path/to/images"
```

## Running the Application

### Option 1: Fat JAR (Recommended)
```bash
java -jar target/jslideshow-1.0.0-jar-with-dependencies.jar /path/to/images
```

### Option 2: With classpath
```bash
java -cp target/jslideshow-1.0.0.jar:target/dependency/* \
  com.krystalmonolith.jslideshow.SlideshowCreator /path/to/images
```

### Option 3: Direct compilation
```bash
# Ensure JCodec jars are in lib/
javac -cp "lib/*" -d target/classes \
  src/main/java/com/krystalmonolith/jslideshow/SlideshowCreator.java

java -cp "target/classes:lib/*" \
  com.krystalmonolith.jslideshow.SlideshowCreator /path/to/images
```

## Example Output

```
Processing directory: /home/user/photos/vacation
Found 144 images
Output file: 20260119T143052-output.mp4

[  1%] Processing image 1/144: DSC_7141.JPG
[  1%] Processing image 2/144: DSC_7145.JPG
[  2%] Processing image 3/144: DSC_7151.JPG
[  3%] Processing image 4/144: DSC_7155.JPG
...
[ 99%] Processing image 143/144: DSC_7709.JPG
[100%] Processing image 144/144: DSC_7713.JPG

Success! Created 20260119T143052-output.mp4
Total processing time: 287.45 seconds
```

## Troubleshooting

### Usage Error / Missing Directory Parameter
If you see: `Usage: java com.krystalmonolith.jslideshow.SlideshowCreator <directory-path>`
- You must provide a directory path as the first argument
- Example: `java -jar jslideshow-1.0.0-jar-with-dependencies.jar /path/to/images`

### Directory Does Not Exist Error
- Verify the path you provided exists
- Use absolute paths or correct relative paths
- Check spelling and case sensitivity

### Out of Memory Error
Increase heap size:
```bash
java -Xmx4g -jar target/jslideshow-1.0.0-jar-with-dependencies.jar /path/to/images
```

### No Images Found
- Ensure directory contains files with .JPG or .jpg extensions (both cases supported)
- Check file permissions
- Verify you specified the correct directory path

### Compilation Errors
- Verify Java 24 is installed: `java -version`
- Ensure Maven 3.9+ is installed: `mvn -version`
- Clean and rebuild: `mvn clean install`

### Unsupported class file major version
You're not using Java 24. Download and install Java 24 or modify `pom.xml` to use an earlier version (minimum Java 17 for text blocks and records).

## Technical Details

### Dependencies
- **JCodec 0.2.5** - Pure Java video encoding library
- **JCodec JavaSE 0.2.5** - AWT integration for JCodec

### Algorithm
Uses alpha compositing via Java's `AlphaComposite` class to blend consecutive images. Each blended frame is converted to a JCodec `Picture` object and encoded to H.264.

### Output Format
- Container: MP4
- Video codec: H.264
- Color space: RGB
- Frame rate: 30 fps (configurable)

## Customization Examples

### Change output filename pattern
Modify the `generateOutputFilename()` method:
```java
private static String generateOutputFilename() {
    var formatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    var timestamp = LocalDateTime.now().format(formatter);
    return "slideshow-%s.mp4".formatted(timestamp);
}
```

### Longer display time per image
```java
private static final double DURATION = 3.0;      // 3 seconds per image
private static final double TRANSITION = 1.0;    // 1 second transition
```

### Higher quality (60 fps)
```java
private static final int FRAME_RATE = 60;
```

### Filter for specific file patterns
Modify the `findImageFiles()` method to filter by prefix or other criteria:
```java
private File[] findImageFiles(Path directoryPath) {
    var dir = directoryPath.toFile();
    var files = dir.listFiles((d, name) -> 
        name.startsWith("IMG_") && (name.endsWith(".JPG") || name.endsWith(".jpg")));
    
    if (files == null) {
        return new File[0];
    }
    
    Arrays.sort(files, Comparator.comparing(File::getName));
    return files;
}
```

## License

This is example code for educational purposes. JCodec is licensed under FreeBSD License.

## Author

krystalmonolith
