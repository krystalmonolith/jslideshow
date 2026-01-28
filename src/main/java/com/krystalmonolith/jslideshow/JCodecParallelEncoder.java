package com.krystalmonolith.jslideshow;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.common.Codec;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.VideoEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.*;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.muxer.CodecMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * Parallel H.264 encoder using segment-based GOP encoding.
 * Each image becomes an independent GOP (Group of Pictures) with one IDR frame
 * followed by P-frames, enabling true parallel encoding.
 */
public class JCodecParallelEncoder {

    /**
     * Holds encoded packets for a single segment (one image's worth of frames).
     *
     * @param segmentIndex the index of this segment in the overall sequence
     * @param packets      the encoded frame packets in Annex B format
     */
    record EncodedSegment(int segmentIndex, List<MP4Packet> packets) {}

    public JCodecParallelEncoder() {
    }

    /**
     * Encode a list of images into an MP4 video file.
     *
     * @param images         List of images to encode
     * @param framesPerImage Number of frames to hold each image
     * @param frameRate      Frame rate for the output video
     * @param output         Output MP4 file
     */
    public void encode(List<BufferedImage> images, int framesPerImage, int frameRate, File output) {
        if (images.isEmpty()) {
            throw new IllegalArgumentException("Image list cannot be empty");
        }
        if (framesPerImage <= 0) {
            throw new IllegalArgumentException("framesPerImage must be positive");
        }
        if (frameRate <= 0) {
            throw new IllegalArgumentException("frameRate must be positive");
        }

        System.out.printf("Encoding %d images, %d frames each @ %d fps%n", images.size(), framesPerImage, frameRate);

        // Phase 1: Parallel encoding - each image becomes an independent segment
        ConcurrentHashMap<Integer, EncodedSegment> segments = new ConcurrentHashMap<>();

        IntStream.range(0, images.size())
                .parallel()
                .forEach(segmentIndex -> {
                    BufferedImage img = images.get(segmentIndex);
                    EncodedSegment segment = encodeSegment(img, segmentIndex, framesPerImage, frameRate);
                    segments.put(segmentIndex, segment);
                    System.out.printf("  Encoded segment %d/%d%n", segmentIndex + 1, images.size());
                });

        // Phase 2: Sequential muxing - write segments in order
        System.out.println("Muxing segments...");
        muxSegments(segments, images.size(), frameRate, output);
    }

    /**
     * Encode a single segment (one image held for multiple frames).
     * The H264Encoder naturally produces an IDR first frame followed by P-frames.
     *
     * @param img            the image to encode
     * @param segmentIndex   the index of this segment
     * @param framesPerImage number of frames to hold this image
     * @param frameRate      frame rate (used as timescale)
     * @return the encoded segment containing all frame packets
     */
    private EncodedSegment encodeSegment(BufferedImage img, int segmentIndex, int framesPerImage, int frameRate) {
        H264Encoder encoder = H264Encoder.createH264Encoder();
        List<MP4Packet> packets = new ArrayList<>(framesPerImage);

        // Convert image to YUV once for the entire segment
        Picture yuv = AWTUtil.fromBufferedImage(img, ColorSpace.YUV420J);

        // Allocate buffer for encoded data
        int bufferSize = img.getWidth() * img.getHeight() * 3;

        for (int localFrame = 0; localFrame < framesPerImage; localFrame++) {
            ByteBuffer buffer = ByteBuffer.allocate(bufferSize);

            VideoEncoder.EncodedFrame encoded = encoder.encodeFrame(yuv, buffer);
            boolean isKeyFrame = encoded.isKeyFrame();

            // Copy encoded data into a right-sized buffer so the oversized
            // encoder buffer (~5MB) can be GC'd, keeping only the actual
            // encoded data (~150KB typical)
            ByteBuffer srcData = encoded.getData();
            ByteBuffer data = ByteBuffer.allocate(srcData.remaining());
            data.put(srcData);
            data.flip();

            // Create packet with local frame number (remapped to global during muxing)
            MP4Packet packet = new MP4Packet(
                    data,                       // data (Annex B format)
                    localFrame,                 // pts (local, remapped later)
                    frameRate,                  // timescale
                    1L,                         // duration
                    localFrame,                 // frameNo (local)
                    isKeyFrame ? Packet.FrameType.KEY : Packet.FrameType.INTER,
                    null,                       // tapeTimecode
                    localFrame,                 // displayOrder (local)
                    localFrame,                 // mediaPts (local)
                    0,                          // entryNo
                    0L,                         // fileOff
                    data.remaining(),           // size
                    isKeyFrame                  // psync
            );

            packets.add(packet);
        }

        return new EncodedSegment(segmentIndex, packets);
    }

    /**
     * Mux all segments into the final MP4 file in order.
     *
     * @param segments    map of segment index to encoded segment
     * @param numSegments total number of segments
     * @param frameRate   frame rate for the output video
     * @param output      output MP4 file
     */
    private void muxSegments(ConcurrentHashMap<Integer, EncodedSegment> segments,
                            int numSegments, int frameRate, File output) {
        try (SeekableByteChannel out = NIOUtils.writableFileChannel(output.getPath())) {
            MP4Muxer muxer = MP4Muxer.createMP4Muxer(out, Brand.MP4);

            // Initialize track - CodecMP4MuxerTrack will extract SPS/PPS
            // from the Annex B data passed via addFrame()
            Size size = new Size(1920, 1080);
            CodecMP4MuxerTrack track = (CodecMP4MuxerTrack) muxer.addVideoTrack(
                    Codec.H264,
                    VideoCodecMeta.createVideoCodecMeta("avc1", null, size, Rational.ONE)
            );

            // Write all segments in order
            long globalFrame = 0;
            for (int segmentIdx = 0; segmentIdx < numSegments; segmentIdx++) {
                EncodedSegment segment = segments.get(segmentIdx);
                if (segment == null) {
                    throw new RuntimeException("Missing segment: " + segmentIdx);
                }

                for (MP4Packet packet : segment.packets()) {
                    // Pass raw Annex B data - CodecMP4MuxerTrack handles
                    // NAL unit extraction and MP4 conversion internally
                    ByteBuffer rawData = packet.getData().duplicate();

                    MP4Packet globalPacket = new MP4Packet(
                            rawData,
                            globalFrame,                    // pts
                            frameRate,                      // timescale
                            1L,                             // duration
                            globalFrame,                    // frameNo
                            packet.getFrameType(),
                            null,                           // tapeTimecode
                            (int) globalFrame,              // displayOrder
                            globalFrame,                    // mediaPts
                            0,                              // entryNo
                            0L,                             // fileOff
                            rawData.remaining(),            // size
                            packet.getFrameType() == Packet.FrameType.KEY  // psync
                    );

                    track.addFrame(globalPacket);
                    globalFrame++;
                }
            }

            muxer.finish();
            System.out.printf("Wrote %d total frames%n", globalFrame);

        } catch (Exception e) {
            System.err.println("Error writing to \"" + output.getAbsolutePath() + "\"");
            throw new RuntimeException(e);
        }
    }
}
