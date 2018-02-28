package net.yrom.screenrecorder.tools;

/**
 * Created by lake on 16-3-30.
 * Big-endian
 */
public class ByteArrayTools {
    /**
     * 将 interger 分为 4 字节
     *
     * @param dst
     * @param pos
     * @param interger
     */
    public static void intToFourByteArray(byte[] dst, int pos, int interger) {
        dst[pos] = (byte) ((interger >> 24) & 0xFF);
        dst[pos + 1] = (byte) ((interger >> 16) & 0xFF);
        dst[pos + 2] = (byte) ((interger >> 8) & 0xFF);
        dst[pos + 3] = (byte) ((interger) & 0xFF);
    }

    /**
     * 将 interger 分为 2 字节
     *
     * @param dst
     * @param pos
     * @param interger
     */
    public static void intToTwoByteArray(byte[] dst, int pos, int interger) {
        dst[pos] = (byte) ((interger >> 8) & 0xFF);
        dst[pos + 1] = (byte) ((interger) & 0xFF);
    }
}
