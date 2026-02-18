package com.smartmedicine.mqtt.aliyun

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.SimpleDateFormat
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 阿里云IoT MQTT配置工具
 * 
 * 阿里云物联网平台MQTT连接需要特殊的认证方式：
 * - Broker地址: ${ProductKey}.iot-as-mqtt.${Region}.aliyuncs.com:1883
 * - ClientID: ${DeviceName}|securemode=3,signmethod=hmacsha1|
 * - 用户名: ${DeviceName}&${ProductKey}
 * - 密码: HMAC-SHA1签名
 */
class AliyunMqttConfig(
    val productKey: String,
    val deviceName: String,
    val deviceSecret: String,
    val region: String = "cn-shanghai"
) {
    companion object {
        // 阿里云MQTT服务器域名后缀
        private const val ALIYUN_MQTT_SUFFIX = "iot-as-mqtt"
        private const val ALIYUN_DOMAIN = "aliyuncs.com"
        
        // 默认端口
        const val DEFAULT_PORT = 1883
        const val DEFAULT_SSL_PORT = 443
        
        // 支持的地域列表
        val SUPPORTED_REGIONS = listOf(
            "cn-shanghai",      // 华东2（上海）
            "cn-beijing",       // 华北2（北京）
            "cn-shenzhen",      // 华南1（深圳）
            "cn-hangzhou",      // 华东1（杭州）
            "cn-zhangjiakou",   // 华北3（张家口）
            "cn-hongkong",      // 香港
            "ap-southeast-1",   // 新加坡
            "ap-northeast-1",   // 日本（东京）
            "eu-central-1",     // 德国（法兰克福）
            "us-west-1",        // 美国（硅谷）
            "us-east-1"         // 美国（弗吉尼亚）
        )
    }
    
    /**
     * 获取MQTT Broker地址
     * @param useSSL 是否使用SSL加密
     */
    fun getBrokerUrl(useSSL: Boolean = false): String {
        val protocol = if (useSSL) "ssl://" else "tcp://"
        val port = if (useSSL) DEFAULT_SSL_PORT else DEFAULT_PORT
        return "$protocol$productKey.$ALIYUN_MQTT_SUFFIX.$region.$ALIYUN_DOMAIN:$port"
    }
    
    /**
     * 获取ClientID
     * securemode:
     * - 2: SSL/TLS加密
     * - 3: TCP明文（推荐内网使用）
     * - 4: SSL/TLS加密 + X.509证书认证
     */
    fun getClientId(secureMode: Int = 3): String {
        return "$deviceName|securemode=$secureMode,signmethod=hmacsha1,timestamp=${getTimestamp()}|"
    }
    
    /**
     * 获取用户名
     */
    fun getUsername(): String {
        return "$deviceName&$productKey"
    }
    
    /**
     * 计算密码（HMAC-SHA1签名）
     * 
     * 签名原始数据格式:
     * clientId${clientId}deviceName${deviceName}productKey${productKey}timestamp${timestamp}
     */
    fun getPassword(timestamp: String = getTimestamp()): String {
        val clientId = deviceName
        val signContent = buildString {
            append("clientId").append(clientId)
            append("deviceName").append(deviceName)
            append("productKey").append(productKey)
            append("timestamp").append(timestamp)
        }
        
        return hmacSha1(deviceSecret, signContent)
    }
    
    /**
     * 获取时间戳
     */
    private fun getTimestamp(): String {
        return SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
    }
    
    /**
     * HMAC-SHA1加密
     */
    private fun hmacSha1(key: String, content: String): String {
        try {
            val mac = Mac.getInstance("HmacSHA1")
            val secretKey = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1")
            mac.init(secretKey)
            val bytes = mac.doFinal(content.toByteArray(Charsets.UTF_8))
            return bytesToHex(bytes)
        } catch (e: Exception) {
            throw RuntimeException("HMAC-SHA1签名失败", e)
        }
    }
    
    /**
     * 字节数组转十六进制字符串
     */
    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
    
    /**
     * 获取MqttConnectOptions配置
     */
    fun getMqttConnectOptions(secureMode: Int = 3): org.eclipse.paho.client.mqttv3.MqttConnectOptions {
        return org.eclipse.paho.client.mqttv3.MqttConnectOptions().apply {
            userName = getUsername()
            password = getPassword().toCharArray()
            isAutomaticReconnect = true
            isCleanSession = true
            connectionTimeout = 30
            keepAliveInterval = 60
        }
    }
}

/**
 * MQTT主题构造器（阿里云格式）
 */
class AliyunMqttTopics(private val productKey: String, private val deviceName: String) {
    
    /**
     * 属性上报主题（设备→云端）
     * /sys/${productKey}/${deviceName}/thing/event/property/post
     */
    fun getPropertyPostTopic(): String {
        return "/sys/$productKey/$deviceName/thing/event/property/post"
    }
    
    /**
     * 属性设置响应主题（设备→云端）
     */
    fun getPropertySetReplyTopic(): String {
        return "/sys/$productKey/$deviceName/thing/service/property/set_reply"
    }
    
     /**
     * 自定义数据上报主题（设备→云端）
     */
    fun getCustomUploadTopic(): String {
        return "/$productKey/$deviceName/user/update"
    }
    
    /**
     * 自定义数据订阅主题（云端→设备）
     */
    fun getCustomSubscribeTopic(): String {
        return "/$productKey/$deviceName/user/get"
    }
    
    /**
     * 与接口文档兼容的主题格式
     * 如果使用阿里云作为MQTT Broker但保持原有主题格式
     */
    fun getLegacySensorsTopic(deviceId: String): String {
        return "medicine/$deviceId/sensors"
    }
    
    fun getLegacyStatusTopic(deviceId: String): String {
        return "medicine/$deviceId/status"
    }
    
    fun getLegacyControlTopic(deviceId: String): String {
        return "medicine/$deviceId/control"
    }
}

/**
 * 阿里云设备认证信息数据类
 */
data class AliyunDeviceAuth(
    val productKey: String,
    val deviceName: String,
    val deviceSecret: String,
    val region: String = "cn-shanghai"
)
