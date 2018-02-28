#include <jni.h>
#include <screenrecorderrtmp.h>
#include <malloc.h>
#include "rtmp.h"

/*
 * Class:     net_yrom_screenrecorder_rtmp_RtmpClient
 * Method:    open
 * Signature: (Ljava/lang/String;Z)J
 */
JNIEXPORT jlong JNICALL Java_net_yrom_screenrecorder_rtmp_RtmpClient_open(JNIEnv * env, jobject thiz, jstring url_, jboolean isPublishMode) {
 	LOGD("Java_net_yrom_screenrecorder_rtmp_RtmpClient_open(%s, %b)", url_, isPublishMode);
   	const char *url = (*env)->GetStringUTFChars(env, url_, 0);

    // 创建一个 RTMP 会话的句柄
   	RTMP* rtmp = RTMP_Alloc();
   	if (rtmp == NULL) {
   		LOGD("rtmp == NULL");
   		return NULL;
   	}
   	// 初始化句柄
   	RTMP_Init(rtmp);

    // 设置参数
   	int ret = RTMP_SetupURL(rtmp, url);
   	if (!ret) {
   	    // 清理会话
   		RTMP_Free(rtmp);
   		rtmp = NULL;
   		LOGD("RTMP_SetupURL: %d", ret);
   		return NULL;
   	}

   	if (isPublishMode) {
   	    // 设置可写
   		RTMP_EnableWrite(rtmp);
   	}

    // 建立 RTMP 链接中的网络连接
   	ret = RTMP_Connect(rtmp, NULL);
   	if (!ret) {
   	    // 清理会话
   		RTMP_Free(rtmp);
   		rtmp = NULL;
   		LOGD("RTMP_Connect: %d", ret);
   		return NULL;
   	}

    // 建立 RTMP 链接中的网络流
   	ret = RTMP_ConnectStream(rtmp, 0);
   	if (!ret) {
   	    // 关闭 RTMP 链接
   		RTMP_Close(rtmp);
   	    // 清理会话
   		RTMP_Free(rtmp);
   		rtmp = NULL;
   		LOGD("RTMP_ConnectStream: %d", ret);
   		return NULL;
   	}

   	(*env)->ReleaseStringUTFChars(env, url_, url);
   	LOGD("RTMP_OPENED");
   	return rtmp;
}

/*
 * Class:     net_yrom_screenrecorder_rtmp_RtmpClient
 * Method:    read
 * Signature: (J[BII)I
 */
JNIEXPORT jint JNICALL Java_net_yrom_screenrecorder_rtmp_RtmpClient_read(JNIEnv * env, jobject thiz, jlong rtmp, jbyteArray data_, jint offset, jint size) {
 	LOGD("Java_net_yrom_screenrecorder_rtmp_RtmpClient_read(%d, %d, %d)", rtmp, offset, size);
 	char* data = malloc(size*sizeof(char));
 	int readCount = RTMP_Read((RTMP*)rtmp, data, size);
 	if (readCount > 0) {
        (*env)->SetByteArrayRegion(env, data_, offset, readCount, data);
    }
    free(data);
    return readCount;
}

/*
 * Class:     net_yrom_screenrecorder_rtmp_RtmpClient
 * Method:    write
 * Signature: (J[BIII)I
 */
JNIEXPORT jint JNICALL Java_net_yrom_screenrecorder_rtmp_RtmpClient_write(JNIEnv * env, jobject thiz, jlong rtmp, jbyteArray data, jint size, jint type, jint ts) {
 	LOGD("Java_net_yrom_screenrecorder_rtmp_RtmpClient_write(%d, %d, %d, %d)", rtmp, size, type, ts);
 	RTMPPacket *packet = (RTMPPacket*)malloc(sizeof(RTMPPacket));
 	RTMPPacket_Alloc(packet, size);
 	RTMPPacket_Reset(packet);
 	// 设置 Chunk Stream ID
    if (type == RTMP_PACKET_TYPE_INFO) {// metadata
    	packet->m_nChannel = 0x03;// 自定义 Chunk Stream ID
    } else if (type == RTMP_PACKET_TYPE_VIDEO) {// video
    	packet->m_nChannel = 0x04;// 自定义 Chunk Stream ID
    } else if (type == RTMP_PACKET_TYPE_AUDIO) {// audio
    	packet->m_nChannel = 0x05;// 自定义 Chunk Stream ID
    } else {
    	packet->m_nChannel = -1;
    }

    // 设置 Stream ID
    packet->m_nInfoField2  =  ((RTMP*)rtmp)->m_stream_id;
 	LOGD("((RTMP*)rtmp)->m_stream_id: %d", ((RTMP*)rtmp)->m_stream_id);

 	jbyte *buffer = (*env)->GetByteArrayElements(env, data, NULL);
    memcpy(packet->m_body, buffer, size);
    // 设置 Message Header 的格式和长度：0
    packet->m_headerType = RTMP_PACKET_SIZE_LARGE;
    // 设置是否使用绝对时间戳：不使用
    packet->m_hasAbsTimestamp = FALSE;
    // 设置时间戳
    packet->m_nTimeStamp = ts;
    // 设置包类型
    packet->m_packetType = type;
    // 设置消息长度
    packet->m_nBodySize  = size;

    int ret = RTMP_SendPacket((RTMP*)rtmp, packet, 0);
    if (!ret) {
    	LOGD("end write error: %d", ret);
		return ret;
    }else{
    	LOGD("end write success");
		return 0;
    }

    RTMPPacket_Free(packet);
    free(packet);
    (*env)->ReleaseByteArrayElements(env, data, buffer, 0);
}

/*
 * Class:     net_yrom_screenrecorder_rtmp_RtmpClient
 * Method:    close
 * Signature: (J)I
 */
JNIEXPORT jint JNICALL Java_net_yrom_screenrecorder_rtmp_RtmpClient_close(JNIEnv * env, jlong rtmp, jobject thiz) {
 	LOGD("Java_net_yrom_screenrecorder_rtmp_RtmpClient_close(%d)", rtmp);
 	RTMP_Close((RTMP*)rtmp);
 	RTMP_Free((RTMP*)rtmp);
 	return 0;
}

/*
 * Class:     net_yrom_screenrecorder_rtmp_RtmpClient
 * Method:    getIpAddr
 * Signature: (J)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_net_yrom_screenrecorder_rtmp_RtmpClient_getIpAddr(JNIEnv * env, jobject thiz, jlong rtmp) {
 	LOGD("Java_net_yrom_screenrecorder_rtmp_RtmpClient_getIpAddr(%d)", rtmp);
	if(rtmp!=0){
		RTMP* r= (RTMP*)rtmp;
		return (*env)->NewStringUTF(env, r->ipaddr);
	}else {
		return (*env)->NewStringUTF(env, "");
	}
}