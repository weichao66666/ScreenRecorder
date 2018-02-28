package net.yrom.screenrecorder.rtmp;

/**
 * Created by lake on 16-3-16.
 * Modified by raomengyang on 17-3-12
 */
public class ResFlvData {
    public static final int VIDEO_WIDTH = 1280;
    public static final int VIDEO_HEIGHT = 720;
    public static final int VIDEO_BITRATE = 500000; // 500Kbps
    public static final int FPS = 20;
    public static final int AAC_SAMPLE_RATE = 44100;
    public static final int AAC_BITRATE = 32 * 1024;

    /**
     * 和 rtmp.h 常量对应
     */
    public static final int FLV_TAGTYPE_AUDIO = 8;
    public static final int FLV_TAGTYPE_VIDEO = 9;
    public static final int FLV_TAGTYPE_SCRIPT_DATA = 18;

    public static final int AVC_NALU_TYPE_IDR = 5;

    public boolean droppable;

    public int dts;//解码时间戳

    public byte[] byteBuffer; //数据

    public int size; //字节长度

    public int flvTagType; //视频和音频的分类

    public int videoFrameType;

    public boolean isKeyframe() {
        return videoFrameType == AVC_NALU_TYPE_IDR;
    }
}
