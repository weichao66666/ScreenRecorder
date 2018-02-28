package net.yrom.screenrecorder.core;

import android.media.MediaFormat;

import net.yrom.screenrecorder.tools.ByteArrayTools;

import java.nio.ByteBuffer;

/**
 * Created by lake on 16-3-30.
 */
public class Packager {
    public static class AvcPackager {
        public static byte[] generateAvcDecoderConfigurationRecord(MediaFormat mediaFormat) {
            // 获取 csd-0 缓冲区的值，该值对应 SPS；csd-1 对应 PPS
            ByteBuffer spsByteBuff = mediaFormat.getByteBuffer("csd-0");
            ByteBuffer ppsByteBuff = mediaFormat.getByteBuffer("csd-1");
            // 跳过 4 个字节
            spsByteBuff.position(4);
            ppsByteBuff.position(4);
            // 获取缓冲区剩余字节数
            int spsLength = spsByteBuff.remaining();
            int ppsLength = ppsByteBuff.remaining();
            // 11 字节包括：
            // configurationVersion（1 字节）、
            // AVCProfileIndication（1 字节）、
            // profile_compatibility（1 字节）、
            // AVCLevelIndication（1 字节）、
            // 6bit的reserved + 2bit的lengthSizeMinusOne（1 字节）、
            // 3bit的reserved + 5bit的numOfSequenceParameterSets（1 字节）、
            // SPS 的长度（2 字节）、
            // numOfPictureParameterSets（1 字节）、
            // PPS 的长度（2 字节）
            int length = 11 + spsLength + ppsLength;
            byte[] result = new byte[length];
            // 从 result 数组第 9 位开始放置 spsByteBuff，一直放置完
            spsByteBuff.get(result, 8, spsLength);
            // 从 result 数组第 9+spsLength+3 位开始放置 ppsByteBuff，一直放置完
            ppsByteBuff.get(result, 8 + spsLength + 3, ppsLength);
            result[0] = (byte) 0x01;// configurationVersion，实际测试时发现总为0x01
            result[1] = result[9];// AVCProfileIndication
            result[2] = result[10];// profile_compatibility
            result[3] = result[11];// AVCLevelIndication
            result[4] = (byte) 0xFF;// 6bit的reserved + 2bit的lengthSizeMinusOne，实际测试时发现总为0xff
            result[5] = (byte) 0xE1;// 3bit的reserved + 5bit的numOfSequenceParameterSets，实际测试时发现总为0xe1
            // result 数组第 7 位放置 spsLength 的高 8 位
            // result 数组第 8 位放置 spsLength 的低 8 位
            ByteArrayTools.intToTwoByteArray(result, 6, spsLength);
            int pos = 8 + spsLength;
            result[pos] = (byte) 0x01;// numOfPictureParameterSets，实际测试时发现总为0x01
            // result 数组第 8+spsLength+1 位放置 ppsLength 的高 8 位
            // result 数组第 8+spsLength+2 位放置 ppsLength 的低 8 位
            ByteArrayTools.intToTwoByteArray(result, pos + 1, ppsLength);
            return result;
        }
    }

    public static class FlvPackager {
        public static final int FLV_TAG_HEADER_LENGTH = 11;
        public static final int FLV_VIDEO_TAG_HEADER_LENGTH = 5;
        public static final int FLV_AUDIO_TAG_HEADER_LENGTH = 2;
        public static final int FLV_TAG_FOOTER_LENGTH = 4;
        public static final int NALU_HEADER_LENGTH = 4;

        public static void setAvcTag(byte[] dst, int pos, boolean isAvcSequenceHeader, boolean isIdr, int readDataLength) {
            // 高 4 位表示 FrameType：1 为关键帧，2 为非关键帧
            // 低 4 位表示 CodecID：7 为 AVC
            dst[pos] = isIdr ? (byte) 0x17 : (byte) 0x27;// FrameType | CodecID
            // 0 为 AVCDecoderConfigurationRecord，1 为 AVC NALU
            dst[pos + 1] = isAvcSequenceHeader ? (byte) 0x00 : (byte) 0x01;// AVCPacketType
            dst[pos + 2] = (byte) 0x00;// CompositionTime
            dst[pos + 3] = (byte) 0x00;// CompositionTime
            dst[pos + 4] = (byte) 0x00;// CompositionTime
            if (!isAvcSequenceHeader) {
                // 4 个字节的 readDataLength
                ByteArrayTools.intToFourByteArray(dst, pos + 5, readDataLength);
            }
        }

        public static void setAacTag(byte[] dst, int pos, boolean isAACSequenceHeader) {
            /**
             * UB[4] 10=AAC
             * UB[2] 3=44kHz
             * UB[1] 1=16-bit
             * UB[1] 0=MonoSound
             */
            dst[pos] = (byte) 0xAE;
            dst[pos + 1] = isAACSequenceHeader ? (byte) 0x00 : (byte) 0x01;
        }
    }
}