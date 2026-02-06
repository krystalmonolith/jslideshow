package com.krystalmonolith.jslideshow;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for SlideshowCreator2.
 */
class SlideshowCreator2Test {

    // ========== Constructor validation tests ==========

    @Test
    void defaultConstructor_createsInstanceWithDefaults() {
        var creator = new SlideshowCreator2();
        assertNotNull(creator);
    }

    @Test
    void constructor_withValidParameters_createsInstance() {
        var creator = new SlideshowCreator2(5.0, 1.0, 24);
        assertNotNull(creator);
    }

    @Test
    void constructor_withZeroDuration_throwsException() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SlideshowCreator2(0, 0.5, 30));
        assertEquals("duration must be > 0", ex.getMessage());
    }

    @Test
    void constructor_withNegativeDuration_throwsException() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SlideshowCreator2(-1.0, 0.5, 30));
        assertEquals("duration must be > 0", ex.getMessage());
    }

    @Test
    void constructor_withNegativeTransition_throwsException() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SlideshowCreator2(3.0, -0.5, 30));
        assertEquals("transition must be >= 0", ex.getMessage());
    }

    @Test
    void constructor_withBothZero_throwsException() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SlideshowCreator2(0, 0, 30));
        assertEquals("duration must be > 0", ex.getMessage());
    }

    @Test
    void constructor_withTransitionEqualToDuration_succeeds() {
        var creator = new SlideshowCreator2(3.0, 3.0, 30);
        assertNotNull(creator);
    }

    @Test
    void constructor_withTransitionGreaterThanDuration_succeeds() {
        var creator = new SlideshowCreator2(2.0, 3.0, 30);
        assertNotNull(creator);
    }

    @Test
    void constructor_withZeroTransition_succeeds() {
        var creator = new SlideshowCreator2(3.0, 0, 30);
        assertNotNull(creator);
    }

    @Test
    void constructor_withZeroFrameRate_throwsException() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SlideshowCreator2(3.0, 0.5, 0));
        assertEquals("frameRate must be positive", ex.getMessage());
    }

    @Test
    void constructor_withNegativeFrameRate_throwsException() {
        var ex = assertThrows(IllegalArgumentException.class,
                () -> new SlideshowCreator2(3.0, 0.5, -1));
        assertEquals("frameRate must be positive", ex.getMessage());
    }

    @Test
    void constructor_withDurationLessThanTransition_printsInfoMessage() {
        var originalOut = System.out;
        var outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        try {
            new SlideshowCreator2(1.0, 2.0, 30);
            var output = outputStream.toString();
            assertTrue(output.contains("transition is longer than duration"));
        } finally {
            System.setOut(originalOut);
        }
    }

    @Test
    void constructor_withDurationGreaterThanTransition_noInfoMessage() {
        var originalOut = System.out;
        var outputStream = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStream));

        try {
            new SlideshowCreator2(3.0, 1.0, 30);
            var output = outputStream.toString();
            assertFalse(output.contains("transition is longer than duration"));
        } finally {
            System.setOut(originalOut);
        }
    }

    // ========== Default constant tests ==========

    @Test
    void defaultDuration_isThreeSeconds() {
        assertEquals(3.0, SlideshowCreator2.DEFAULT_DURATION);
    }

    @Test
    void defaultTransition_isPointSevenFiveSeconds() {
        assertEquals(0.75, SlideshowCreator2.DEFAULT_TRANSITION);
    }

    @Test
    void defaultFrameRate_isThirtyFps() {
        assertEquals(30, SlideshowCreator2.DEFAULT_FRAME_RATE);
    }

    // ========== createSlideshow tests ==========

    @Test
    void createSlideshow_withEmptyDirectory_throwsException(@TempDir Path tempDir) {
        var creator = new SlideshowCreator2();
        var ex = assertThrows(IllegalStateException.class,
                () -> creator.createSlideshow(tempDir));
        assertTrue(ex.getMessage().contains("No .JPG or .jpg files found"));
    }

    @Test
    void createSlideshow_withNonImageFiles_throwsException(@TempDir Path tempDir) throws IOException {
        // Create non-image files
        Files.createFile(tempDir.resolve("readme.txt"));
        Files.createFile(tempDir.resolve("image.png"));

        var creator = new SlideshowCreator2();
        var ex = assertThrows(IllegalStateException.class,
                () -> creator.createSlideshow(tempDir));
        assertTrue(ex.getMessage().contains("No .JPG or .jpg files found"));
    }
}
