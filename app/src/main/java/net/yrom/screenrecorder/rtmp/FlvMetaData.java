package net.yrom.screenrecorder.rtmp;

import net.yrom.screenrecorder.core.ResCoreParameters;
import net.yrom.screenrecorder.tools.LogTools;

import java.util.ArrayList;

/**
 * Created by tianyu on 15-12-29.
 * modified by lake on 16-4-8.
 * This class is able to generate a FLVTAG in accordance with Adobe Flash Video File Format
 * Specification v10.1 Annex E.5 with limited types available.
 */
public class FlvMetaData {
    private static final String NAME = "onMetaData";
    private static final int CODEC_ID_AAC = 7;
    private static final int CODEC_ID_AVC = 10;
    private static final int TYPE_NUMBER = 0;
    private static final int TYPE_STRING = 2;
    private static final int TYPE_ECMA_ARRAY = 8;
    private static final int EMPTY_SIZE = 21;
    private static final byte[] END_MARKER = {0x00, 0x00, 0x09};

    private int dataSize;
    private byte[] metaData;
    private ArrayList<byte[]> metaDataList;
    private int pointer;

    public FlvMetaData() {
        metaDataList = new ArrayList<>();
        dataSize = 0;
    }

    public FlvMetaData(ResCoreParameters parameters) {
        this();
        // audio
        setProperty("audiocodecid", CODEC_ID_AAC);
        switch (parameters.mediacodecAACBitRate) {
            case 32 * 1024:
                setProperty("audiodatarate", 32);
                break;
            case 48 * 1024:
                setProperty("audiodatarate", 48);
                break;
            case 64 * 1024:
                setProperty("audiodatarate", 64);
                break;
            default:
                LogTools.e("不支持的 audiodatarate: " + parameters.mediacodecAACBitRate);
                break;
        }
        switch (parameters.mediacodecAACSampleRate) {
            case 44100:
                setProperty("audiosamplerate", 44100);
                break;
            default:
                LogTools.e("不支持的 audiosamplerate: " + parameters.mediacodecAACSampleRate);
                break;
        }
        // video
        setProperty("videocodecid", CODEC_ID_AVC);
        setProperty("framerate", parameters.mediacodecAVCFrameRate);
        setProperty("width", parameters.videoWidth);
        setProperty("height", parameters.videoHeight);
    }

    private void setProperty(String key, int value) {
        addProperty(toFlvBytes(key), (byte) TYPE_NUMBER, toFlvBytes(value));
    }

    private void setProperty(String key, String value) {
        addProperty(toFlvBytes(key), (byte) TYPE_STRING, toFlvBytes(value));
    }

    private void addProperty(byte[] key, byte dataType, byte[] value) {
        int propertySize = key.length + 1 + value.length;
        byte[] property = new byte[propertySize];

        // property 数组的前 key.length 个元素是 key 数组
        System.arraycopy(key, 0, property, 0, key.length);
        // property 数组的第 key.length+1 个元素是 dataType
        property[key.length] = dataType;
        // property 数组的后 value.length 个元素是 value 数组
        System.arraycopy(value, 0, property, key.length + 1, value.length);

        metaDataList.add(property);
        dataSize += propertySize;
    }

    /**
     * 将 double 类型的值转为 8 字节的数组
     *
     * @param value
     * @return
     */
    private byte[] toFlvBytes(double value) {
        long l = Double.doubleToLongBits(value);
        return toUI(l, 8);
    }

    /**
     * 将 String 类型的值转为 值长度+2 字节的数组，其中前 2 个字节表示数组的长度，后面的是数据
     *
     * @param value
     * @return
     */
    private byte[] toFlvBytes(String value) {
        byte[] bytes = new byte[value.length() + 2];
        // bytes 数组的前 2 个元素表示 value 的长度
        System.arraycopy(toUI(value.length(), 2), 0, bytes, 0, 2);
        // bytes 数组第 3 个元素到最后一个元素是数据
        System.arraycopy(value.getBytes(), 0, bytes, 2, value.length());
        return bytes;
    }

    /**
     * 将 value 转为指定字节数的数组
     *
     * @param value
     * @param length
     * @return
     */
    private byte[] toUI(long value, int length) {
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; i++) {
            bytes[length - 1 - i] = (byte) (value >> (8 * i) & 0xff);
        }
        return bytes;
    }

    /**
     * 构建 SCRIPT TAG
     *
     * @return
     */
    public byte[] getMetaData() {
        metaData = new byte[dataSize + EMPTY_SIZE];
        pointer = 0;
        // 设置下一个内容的类型
        addByte(TYPE_STRING);// 1 个字节
        addByteArray(toFlvBytes(NAME));// 12 个字节
        // 设置下一个内容的类型
        addByte(TYPE_ECMA_ARRAY);// 1 个字节
        addByteArray(toUI(metaDataList.size(), 4));// 4 个字节
        for (byte[] property : metaDataList) {
            addByteArray(property);// property长度 个字节
        }
        addByteArray(END_MARKER);// 3 个字节
        return metaData;
    }

    private void addByte(int value) {
        metaData[pointer] = (byte) value;
        pointer++;
    }

    private void addByteArray(byte[] value) {
        System.arraycopy(value, 0, metaData, pointer, value.length);
        pointer += value.length;
    }
}