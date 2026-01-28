package com.krystalmonolith.jslideshow;

import org.jcodec.codecs.h264.H264Encoder;
import org.jcodec.codecs.h264.H264Utils;
import org.jcodec.common.Codec;
import org.jcodec.common.VideoCodecMeta;
import org.jcodec.common.VideoEncoder;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.*;
import org.jcodec.containers.mp4.Brand;
import org.jcodec.containers.mp4.MP4Packet;
import org.jcodec.containers.mp4.boxes.SampleEntry;
import org.jcodec.containers.mp4.muxer.CodecMP4MuxerTrack;
import org.jcodec.containers.mp4.muxer.MP4Muxer;
import org.jcodec.scale.AWTUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class JCodecParallelEncoder {

    public JCodecParallelEncoder() {
    }

    private static final MP4Packet POISON_PILL = getNullMp4Packet();

    public void encode(List<BufferedImage> images, File output) throws RuntimeException {
        BlockingQueue<MP4Packet> queue = new LinkedBlockingQueue<>(50);
        try (SeekableByteChannel out = NIOUtils.writableFileChannel(output.getPath())) {

            // 1. Initialize Muxer and Track (0.2.5 API style)
            MP4Muxer muxer = MP4Muxer.createMP4Muxer(out, Brand.MP4);
            Size size = new Size(images.getFirst().getWidth(), images.getFirst().getHeight());

            // In 0.2.5, addVideoTrack is the preferred way to set up the codec metadata
            CodecMP4MuxerTrack track = (CodecMP4MuxerTrack) muxer.addVideoTrack(Codec.H264,
                    VideoCodecMeta.createVideoCodecMeta("avc1", null, size, Rational.ONE));

            // 2. Consumer Thread (Sequential Writing)
            Thread writerThread = startWriterThread(queue, track, muxer);

            // 3. Producer (Parallel Encoding)
            AtomicLong frameIdx = new AtomicLong(0);
            images.parallelStream().forEachOrdered(img -> {
                try {
                    // Create encoder instance per thread
                    H264Encoder encoder = H264Encoder.createH264Encoder();

                    // Allocate buffer: width * height * 3
                    ByteBuffer buffer = ByteBuffer.allocate(img.getWidth() * img.getHeight() * 3);
                    Picture yuv = AWTUtil.fromBufferedImage(img, ColorSpace.YUV420J);

                    // In 0.2.5, encodeFrame returns an EncodedFrame object
                    VideoEncoder.EncodedFrame encoded = encoder.encodeFrame(yuv, buffer);
                    ByteBuffer data = encoded.getData();
                    boolean isKey = encoded.isKeyFrame();

                    MP4Packet packet = getMp4Packet(frameIdx, data, isKey);

                    queue.put(packet);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            queue.put(POISON_PILL);
            writerThread.join();
            NIOUtils.closeQuietly(out);
        } catch (Exception e) {
            System.err.println("Error opening \"" + output.getAbsolutePath() + "\" for write!");
            throw new RuntimeException(e);
        }
    }

    private static Thread startWriterThread(BlockingQueue<MP4Packet> queue, CodecMP4MuxerTrack track, MP4Muxer muxer) {
        Thread writerThread = new Thread(() -> {
            try {
                boolean trackInitialized = false;
                while (true) {
                    MP4Packet packet = queue.take();
                    if (packet == POISON_PILL) break;

                    if (!trackInitialized) {
                        // 1. Prepare lists for the metadata NAL units
                        List<ByteBuffer> spsList = new ArrayList<>();
                        List<ByteBuffer> ppsList = new ArrayList<>();

                        // 2. Extract SPS and PPS from the Annex B buffer
                        // H264Utils.nextNALUnit scans the buffer for start codes (00 00 00 01)
                        ByteBuffer dup = packet.getData().duplicate();
                        ByteBuffer nal;
                        while ((nal = H264Utils.nextNALUnit(dup)) != null) {
                            int type = nal.get() & 0x1f; // Get NAL unit type
                            if (type == 7) { // SPS
                                spsList.add(nal);
                            } else if (type == 8) { // PPS
                                ppsList.add(nal);
                            }
                        }

                        // 3. Create the SampleEntry using the extracted lists
                        // 4 is the standard nalLengthSize for MP4
                        SampleEntry se = H264Utils.createMOVSampleEntryFromSpsPpsList(spsList, ppsList, 4);
                        track.addSampleEntry(se);
                        trackInitialized = true;
                    }

                    // 4. Transform Annex B to MP4 (ISO BMF) format before writing
                    // MP4 requires 4-byte length prefixes instead of 00 00 00 01 start codes
                    ByteBuffer mp4Data = H264Utils.encodeMOVPacket(packet.getData());
                    MP4Packet mp4Packet = new MP4Packet(
                            mp4Data,
                            packet.getPts(),
                            packet.getTimescale(),
                            packet.getDuration(),
                            packet.getFrameNo(),
                            packet.getFrameType(),      // FrameType iframe (e.g., KEY, INTER, UNKNOWN)
                            packet.getTapeTimecode(),
                            (int) packet.getFrameNo(),  // displayOrder (usually matches frameNo for H264)
                            packet.getMediaPts(),       // mediaPts
                            0,                          // entryNo
                            0,                          // fileOff (Muxer calculates this)
                            mp4Data.remaining(),        // size
                            true                        // psync
                    );
                    track.addFrame(mp4Packet);
                }
                muxer.finish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Start consumer thread!
        writerThread.start();
        return writerThread;
    }

    private static MP4Packet getMp4Packet(AtomicLong frameIdx, ByteBuffer data, boolean isKey) {
        long idx = frameIdx.getAndIncrement();

        // Map to the 13-argument MP4Packet constructor
        return new MP4Packet(
                data,                       // 1. data
                idx,                        // 2. pts
                25,                         // 3. timescale (den)
                1L,                         // 4. duration (num)
                idx,                        // 5. frameNo
                isKey ? Packet.FrameType.KEY : Packet.FrameType.INTER, // 6. iframe (FrameType enum)
                null,                       // 7. tapeTimecode
                (int) idx,                  // 8. displayOrder
                idx,                        // 9. mediaPts
                0,                          // 10. entryNo
                0L,                         // 11. fileOff
                data.remaining(),           // 12. size
                isKey                       // 13. psync
        );
    }

    private static MP4Packet getNullMp4Packet() {
        return new MP4Packet(
                null,             // ByteBuffer data
                0L,                    // long pts
                0,                     // int timescale
                0L,                    // long duration
                0L,                    // long frameNo
                Packet.FrameType.KEY,  // FrameType iframe
                null,                  // TapeTimecode tapeTimecode
                0,                     // int displayOrder
                0L,                    // long mediaPts
                0,                     // int entryNo
                0L,                    // long fileOff
                0,                     // int size
                true                   // boolean psync
        );
    }
}
