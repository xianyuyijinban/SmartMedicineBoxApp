/**
  ******************************************************************************
  * @file    esp8266.c
  * @brief   ESP8266 ESP-01S WiFi模块驱动实现
  *          使用USART3接口 (PB10-TX, PB11-RX)
  ******************************************************************************
  */
#include "esp8266.h"
#include "esp8266_command_rx_policy.h"
#include "esp8266_delay_policy.h"
#include "esp8266_mqtt_transport.h"
#include "esp8266_rx_policy.h"
#include <string.h>
#include <stdio.h>
#include <stdlib.h>
#include <ctype.h>

/* FreeRTOS头文件 */
#include "FreeRTOS.h"
#include "task.h"

extern UART_HandleTypeDef huart3;  // USART3用于ESP-01S
extern volatile uint32_t g_uart3_last_rx_tick;
extern uint8_t g_esp_rx_byte;

/* 接收缓冲区 */
static uint8_t rx_buffer[ESP8266_RX_BUF_SIZE];
static volatile uint16_t rx_write_idx = 0;
static volatile uint16_t rx_read_idx = 0;

/* 当前状态 */
static ESP8266_State_t esp_state = ESP8266_STATE_RESET;
static ESP8266_MQTT_MessageCallback_t mqtt_message_callback = NULL;
static const uint32_t esp_probe_baud_list[] = {115200U, 9600U, 57600U, 74880U, 38400U};
static ESP8266_MQTT_Config_t mqtt_cfg = {
    "broker.emqx.io",
    1883U,
    "SmartBox",
    "emqx",
    "public",
    120U
};
static uint8_t mqtt_scheme_active = 1U;
static uint16_t mqtt_requested_port = 0U;
static char mqtt_path_active[32] = "";
static char mqtt_diag[24] = "INIT";
/* 0xFF=未知, 0=不支持MQTT AT, 1=支持MQTT AT */
static uint8_t mqtt_cmd_support = 0xFFU;
/* 0=AT MQTT模式, 1=Raw MQTT(TCP/SSL socket)模式 */
static uint8_t mqtt_raw_mode = 0U;
static uint8_t mqtt_socket_open = 0U;
static uint16_t mqtt_packet_id = 1U;

static void ESP8266_CopyRxSnapshot(char *out, uint16_t out_size);
static uint8_t ESP8266_ProbeMqttCommandSupport(void);
static uint8_t ESP8266_WaitForToken(const char *token, uint32_t timeout_ms);
static uint8_t ESP8266_OpenRawSocket(const char *host, uint16_t port);
static void ESP8266_CloseRawSocket(void);
static uint8_t ESP8266_SendRawPacket(const uint8_t *packet, uint16_t packet_len);
static uint8_t ESP8266_TryConnectCurrentBroker(void);
static uint8_t ESP8266_BuildConnectPacket(uint8_t *out, uint16_t out_size, uint16_t *out_len);
static uint8_t ESP8266_BuildPublishPacket(const char *topic,
                                          const char *payload,
                                          uint8_t retain,
                                          uint8_t *out,
                                          uint16_t out_size,
                                          uint16_t *out_len);
static uint8_t ESP8266_BuildSubscribePacket(const char *topic,
                                            uint8_t qos,
                                            uint8_t *out,
                                            uint16_t out_size,
                                            uint16_t *out_len);
static void ESP8266_CopyRxSnapshotBytes(uint8_t *out, uint16_t out_size, uint16_t *out_len);
static uint8_t ESP8266_BufferContainsToken(const uint8_t *buf, uint16_t len, const char *token);
static uint8_t ESP8266_DecodeRemainingLength(const uint8_t *buf,
                                             uint16_t len,
                                             uint32_t *out_value,
                                             uint8_t *out_used);
static void ESP8266_DispatchRawMqttPublish(const uint8_t *packet, uint16_t packet_len);
static uint8_t ESP8266_ProcessRawIpdData(const uint8_t *buf, uint16_t len);
static uint8_t ESP8266_IsCloudPort(uint16_t port);
static uint8_t ESP8266_SelectCloudAtConfig(uint16_t requested_port);
static uint8_t ESP8266_TryCloudAtConnect(uint16_t requested_port);
static void ESP8266_RefreshUartRxState(void);
static HAL_StatusTypeDef ESP8266_PollRxByte(uint8_t *out);
static uint8_t ESP8266_MapPollStatus(HAL_StatusTypeDef status);
static uint8_t ESP8266_IsInterruptRxActive(void);
static uint8_t ESP8266_BeginCommandSession(void);
static void ESP8266_EndCommandSession(uint8_t interrupt_rx_was_active);

static void ESP8266_CopyString(char *dst, uint16_t dst_size, const char *src)
{
    if ((dst == NULL) || (dst_size == 0U)) {
        return;
    }

    if (src == NULL) {
        dst[0] = '\0';
        return;
    }

    (void)snprintf(dst, dst_size, "%s", src);
}

static void ESP8266_SetMqttDiag(const char *diag)
{
    ESP8266_CopyString(mqtt_diag, (uint16_t)sizeof(mqtt_diag), (diag != NULL) ? diag : "N/A");
}

static uint8_t ESP8266_IsCloudPort(uint16_t port)
{
    return (uint8_t)((port == 8883U) || (port == 8084U));
}

static uint8_t ESP8266_GetMqttSchemeByPort(uint16_t port)
{
    /* ESP8266 MQTT AT 仅支持 scheme=1(TCP) 与 scheme=6(WS/TCP)。 */
    if (port == 8083U) {
        return 6U;
    }
    return 1U;
}

static uint8_t ESP8266_IsWebSocketScheme(uint8_t scheme)
{
    return (uint8_t)((scheme == 6U) || (scheme == 7U) || (scheme == 8U) || (scheme == 9U) || (scheme == 10U));
}

static uint8_t ESP8266_ApplyMqttConfig(uint8_t scheme, const char *path)
{
    char cmd[320];
    char legacy_cmd[320];
    char lwt_topic[96];
    char diag_buf[24];
    const char *cfg_path = (path != NULL) ? path : "";

    if ((scheme == 0U) || (scheme > 10U)) {
        (void)snprintf(diag_buf, sizeof(diag_buf), "SCM_NS%u", (unsigned int)scheme);
        ESP8266_SetMqttDiag(diag_buf);
        return 1U;
    }

    snprintf(cmd, sizeof(cmd), "AT+MQTTUSERCFG=0,%u,\"%s\",\"%s\",\"%s\",0,0,\"%s\"",
             scheme, mqtt_cfg.client_id, mqtt_cfg.username, mqtt_cfg.password, cfg_path);
    if (ESP8266_SendATCommand(cmd, "OK", 5000) != 0) {
        /* 兼容部分老AT固件：scheme=1 且 path 为空时，尝试不带 path 的旧参数格式。 */
        if ((scheme == 1U) && (cfg_path[0] == '\0')) {
            snprintf(legacy_cmd, sizeof(legacy_cmd), "AT+MQTTUSERCFG=0,1,\"%s\",\"%s\",\"%s\",0,0",
                     mqtt_cfg.client_id, mqtt_cfg.username, mqtt_cfg.password);
            if (ESP8266_SendATCommand(legacy_cmd, "OK", 5000) == 0) {
                mqtt_scheme_active = 1U;
                mqtt_path_active[0] = '\0';
                ESP8266_SetMqttDiag("CFG1_OLD");
                goto mqtt_conncfg_step;
            }
        }

        (void)snprintf(diag_buf, sizeof(diag_buf), "UCFG%u", (unsigned int)scheme);
        ESP8266_SetMqttDiag(diag_buf);
        return 1U;
    }

    if ((scheme == 2U) || (scheme == 3U) || (scheme == 4U) || (scheme == 5U) ||
        (scheme == 7U) || (scheme == 8U) || (scheme == 9U) || (scheme == 10U)) {
        snprintf(cmd, sizeof(cmd), "AT+MQTTSNI=0,\"%s\"", mqtt_cfg.broker_ip);
        (void)ESP8266_SendATCommand(cmd, "OK", 3000);
    }

mqtt_conncfg_step:
    snprintf(lwt_topic, sizeof(lwt_topic), "%s/status", mqtt_cfg.client_id);
    snprintf(cmd, sizeof(cmd), "AT+MQTTCONNCFG=0,%u,0,\"%s\",\"%s\",0,0",
             mqtt_cfg.keepalive, lwt_topic, "offline");
    if (ESP8266_SendATCommand(cmd, "OK", 5000) != 0) {
        (void)snprintf(diag_buf, sizeof(diag_buf), "CCFG%u", (unsigned int)scheme);
        ESP8266_SetMqttDiag(diag_buf);
        return 1U;
    }

    mqtt_scheme_active = scheme;
    ESP8266_CopyString(mqtt_path_active, (uint16_t)sizeof(mqtt_path_active), cfg_path);
    (void)snprintf(diag_buf, sizeof(diag_buf), "CFG%uOK", (unsigned int)scheme);
    ESP8266_SetMqttDiag(diag_buf);
    return 0U;
}

static uint8_t ESP8266_ProbeMqttCommandSupport(void)
{
    if (mqtt_cmd_support != 0xFFU) {
        return mqtt_cmd_support;
    }

    /* 优先尝试新语法(带path)，失败再尝试旧语法(不带path)。 */
    if ((ESP8266_SendATCommand("AT+MQTTUSERCFG=0,1,\"probe\",\"\",\"\",0,0,\"\"", "OK", 3000) == 0U) ||
        (ESP8266_SendATCommand("AT+MQTTUSERCFG=0,1,\"probe\",\"\",\"\",0,0", "OK", 3000) == 0U)) {
        mqtt_cmd_support = 1U;
        return 1U;
    }

    mqtt_cmd_support = 0U;
    ESP8266_SetMqttDiag("NO_MQTT");
    return 0U;
}

static uint8_t ESP8266_SelectCloudAtConfig(uint16_t requested_port)
{
    ESP8266_MqttTransportCandidate_t candidate;
    uint8_t attempt = 0U;

    while (ESP8266_MQTT_GetCloudAtCandidate(requested_port, attempt, &candidate) == 0U) {
        mqtt_cfg.broker_port = candidate.port;
        if (ESP8266_ApplyMqttConfig(candidate.scheme, candidate.path) == 0U) {
            return 0U;
        }
        attempt++;
    }

    ESP8266_SetMqttDiag("AT_CFG");
    return 1U;
}

static uint8_t ESP8266_TryCloudAtConnect(uint16_t requested_port)
{
    ESP8266_MqttTransportCandidate_t candidate;
    uint8_t attempt = 0U;
    char diag_buf[24];

    while (ESP8266_MQTT_GetCloudAtCandidate(requested_port, attempt, &candidate) == 0U) {
        mqtt_cfg.broker_port = candidate.port;
        if (ESP8266_ApplyMqttConfig(candidate.scheme, candidate.path) == 0U) {
            if (ESP8266_TryConnectCurrentBroker() == 0U) {
                return 0U;
            }
            (void)snprintf(diag_buf, sizeof(diag_buf), "CONN%u", (unsigned int)candidate.scheme);
            ESP8266_SetMqttDiag(diag_buf);
        }
        attempt++;
    }

    return 1U;
}

static uint8_t ESP8266_EncodeRemainingLength(uint32_t value, uint8_t *out, uint8_t *out_len)
{
    uint8_t idx = 0U;

    if ((out == NULL) || (out_len == NULL)) {
        return 1U;
    }

    do {
        uint8_t encoded = (uint8_t)(value % 128U);
        value /= 128U;
        if (value > 0U) {
            encoded |= 0x80U;
        }
        if (idx >= 4U) {
            return 1U;
        }
        out[idx++] = encoded;
    } while (value > 0U);

    *out_len = idx;
    return 0U;
}

static uint8_t ESP8266_AppendMqttString(const char *text, uint8_t *out, uint16_t out_size, uint16_t *offset)
{
    uint16_t text_len;

    if ((text == NULL) || (out == NULL) || (offset == NULL)) {
        return 1U;
    }

    text_len = (uint16_t)strlen(text);
    if ((uint32_t)(*offset) + 2U + text_len > (uint32_t)out_size) {
        return 1U;
    }

    out[(*offset)++] = (uint8_t)((text_len >> 8) & 0xFFU);
    out[(*offset)++] = (uint8_t)(text_len & 0xFFU);
    if (text_len > 0U) {
        memcpy(&out[*offset], text, text_len);
        *offset = (uint16_t)(*offset + text_len);
    }

    return 0U;
}

static uint8_t ESP8266_BuildConnectPacket(uint8_t *out, uint16_t out_size, uint16_t *out_len)
{
    uint8_t flags = 0x02U; /* clean session */
    uint8_t rl_bytes[4];
    uint8_t rl_len = 0U;
    uint16_t payload_len;
    uint16_t offset;
    const char *username = mqtt_cfg.username;
    const char *password = mqtt_cfg.password;

    if ((out == NULL) || (out_len == NULL)) {
        return 1U;
    }

    if ((username != NULL) && (username[0] != '\0')) {
        flags |= 0x80U;
    } else {
        username = "";
    }

    if ((password != NULL) && (password[0] != '\0')) {
        flags |= 0x40U;
    } else {
        password = "";
    }

    payload_len = (uint16_t)(2U + strlen(mqtt_cfg.client_id));
    if ((flags & 0x80U) != 0U) {
        payload_len = (uint16_t)(payload_len + 2U + strlen(username));
    }
    if ((flags & 0x40U) != 0U) {
        payload_len = (uint16_t)(payload_len + 2U + strlen(password));
    }

    if (ESP8266_EncodeRemainingLength((uint32_t)(10U + payload_len), rl_bytes, &rl_len) != 0U) {
        return 1U;
    }

    if ((uint32_t)1U + rl_len + 10U + payload_len > (uint32_t)out_size) {
        return 1U;
    }

    offset = 0U;
    out[offset++] = 0x10U;
    memcpy(&out[offset], rl_bytes, rl_len);
    offset = (uint16_t)(offset + rl_len);

    out[offset++] = 0x00U;
    out[offset++] = 0x04U;
    out[offset++] = 'M';
    out[offset++] = 'Q';
    out[offset++] = 'T';
    out[offset++] = 'T';
    out[offset++] = 0x04U; /* protocol level 4 (3.1.1) */
    out[offset++] = flags;
    out[offset++] = (uint8_t)((mqtt_cfg.keepalive >> 8) & 0xFFU);
    out[offset++] = (uint8_t)(mqtt_cfg.keepalive & 0xFFU);

    if (ESP8266_AppendMqttString(mqtt_cfg.client_id, out, out_size, &offset) != 0U) {
        return 1U;
    }
    if ((flags & 0x80U) != 0U) {
        if (ESP8266_AppendMqttString(username, out, out_size, &offset) != 0U) {
            return 1U;
        }
    }
    if ((flags & 0x40U) != 0U) {
        if (ESP8266_AppendMqttString(password, out, out_size, &offset) != 0U) {
            return 1U;
        }
    }

    *out_len = offset;
    return 0U;
}

static uint8_t ESP8266_BuildPublishPacket(const char *topic,
                                          const char *payload,
                                          uint8_t retain,
                                          uint8_t *out,
                                          uint16_t out_size,
                                          uint16_t *out_len)
{
    uint8_t rl_bytes[4];
    uint8_t rl_len = 0U;
    uint16_t topic_len;
    uint16_t payload_len;
    uint16_t offset;

    if ((topic == NULL) || (payload == NULL) || (out == NULL) || (out_len == NULL)) {
        return 1U;
    }

    topic_len = (uint16_t)strlen(topic);
    payload_len = (uint16_t)strlen(payload);

    if (ESP8266_EncodeRemainingLength((uint32_t)(2U + topic_len + payload_len), rl_bytes, &rl_len) != 0U) {
        return 1U;
    }
    if ((uint32_t)1U + rl_len + 2U + topic_len + payload_len > (uint32_t)out_size) {
        return 1U;
    }

    offset = 0U;
    out[offset++] = (uint8_t)(0x30U | (retain ? 0x01U : 0x00U)); /* QoS0 only */
    memcpy(&out[offset], rl_bytes, rl_len);
    offset = (uint16_t)(offset + rl_len);
    out[offset++] = (uint8_t)((topic_len >> 8) & 0xFFU);
    out[offset++] = (uint8_t)(topic_len & 0xFFU);
    memcpy(&out[offset], topic, topic_len);
    offset = (uint16_t)(offset + topic_len);
    memcpy(&out[offset], payload, payload_len);
    offset = (uint16_t)(offset + payload_len);

    *out_len = offset;
    return 0U;
}

static uint8_t ESP8266_BuildSubscribePacket(const char *topic,
                                            uint8_t qos,
                                            uint8_t *out,
                                            uint16_t out_size,
                                            uint16_t *out_len)
{
    uint8_t rl_bytes[4];
    uint8_t rl_len = 0U;
    uint16_t topic_len;
    uint16_t offset;
    uint16_t packet_id;

    if ((topic == NULL) || (out == NULL) || (out_len == NULL)) {
        return 1U;
    }

    if (qos > 1U) {
        qos = 1U;
    }

    topic_len = (uint16_t)strlen(topic);
    if (ESP8266_EncodeRemainingLength((uint32_t)(2U + 2U + topic_len + 1U), rl_bytes, &rl_len) != 0U) {
        return 1U;
    }
    if ((uint32_t)1U + rl_len + 2U + 2U + topic_len + 1U > (uint32_t)out_size) {
        return 1U;
    }

    packet_id = mqtt_packet_id++;
    if (mqtt_packet_id == 0U) {
        mqtt_packet_id = 1U;
    }

    offset = 0U;
    out[offset++] = 0x82U;
    memcpy(&out[offset], rl_bytes, rl_len);
    offset = (uint16_t)(offset + rl_len);
    out[offset++] = (uint8_t)((packet_id >> 8) & 0xFFU);
    out[offset++] = (uint8_t)(packet_id & 0xFFU);
    out[offset++] = (uint8_t)((topic_len >> 8) & 0xFFU);
    out[offset++] = (uint8_t)(topic_len & 0xFFU);
    memcpy(&out[offset], topic, topic_len);
    offset = (uint16_t)(offset + topic_len);
    out[offset++] = qos;

    *out_len = offset;
    return 0U;
}

static void ESP8266_SetMqttDiagFromSnapshot(const char *rx_snapshot)
{
    const char *p;

    if (rx_snapshot == NULL) {
        ESP8266_SetMqttDiag("CONN_TO");
        return;
    }

    if (strstr(rx_snapshot, "+MQTTCONNECTED") != NULL) {
        ESP8266_SetMqttDiag("MQ_OK");
        return;
    }

    p = strstr(rx_snapshot, "+MQTTDISCONNECTED:");
    if (p != NULL) {
        char *endptr = NULL;
        long reason = -1;
        long v = strtol(p + strlen("+MQTTDISCONNECTED:"), &endptr, 10);

        if ((endptr != NULL) && (*endptr == ',')) {
            reason = strtol(endptr + 1, &endptr, 10);
        } else if (v >= 0) {
            reason = v;
        }

        if (reason >= 0) {
            char diag_buf[24];
            (void)snprintf(diag_buf, sizeof(diag_buf), "MQ_D%ld", reason);
            ESP8266_SetMqttDiag(diag_buf);
        } else {
            ESP8266_SetMqttDiag("MQ_DISC");
        }
        return;
    }

    p = strstr(rx_snapshot, "ERR CODE:0x");
    if (p != NULL) {
        char code[9];
        uint8_t idx = 0U;
        p += strlen("ERR CODE:0x");
        while ((idx < (uint8_t)(sizeof(code) - 1U)) && isxdigit((unsigned char)p[idx])) {
            code[idx] = p[idx];
            idx++;
        }
        code[idx] = '\0';

        if (idx > 0U) {
            char diag_buf[24];
            (void)snprintf(diag_buf, sizeof(diag_buf), "E0x%s", code);
            ESP8266_SetMqttDiag(diag_buf);
            return;
        }
    }

    if (strstr(rx_snapshot, "ERROR") != NULL) {
        ESP8266_SetMqttDiag("AT_ERROR");
        return;
    }

    if (strstr(rx_snapshot, "FAIL") != NULL) {
        ESP8266_SetMqttDiag("AT_FAIL");
        return;
    }

    ESP8266_SetMqttDiag("CONN_TO");
}

static uint8_t ESP8266_TryConnectCurrentBroker(void)
{
    char cmd[160];
    char rx_snapshot[ESP8266_RX_BUF_SIZE + 1U];
    uint32_t start_tick;

    snprintf(cmd, sizeof(cmd), "AT+MQTTCONN=0,\"%s\",%u,1", mqtt_cfg.broker_ip, mqtt_cfg.broker_port);

    if (ESP8266_SendATCommand(cmd, "OK", 30000) != 0) {
        ESP8266_SetMqttDiag("CONN_CMD");
        return 1U;
    }

    start_tick = HAL_GetTick();
    while ((HAL_GetTick() - start_tick) < 30000U) {
        ESP8266_CopyRxSnapshot(rx_snapshot, sizeof(rx_snapshot));

        if ((strstr(rx_snapshot, "+MQTTCONNECTED") != NULL) ||
            (strstr(rx_snapshot, "ALREADY CONNECTED") != NULL)) {
            ESP8266_SetMqttDiag("MQ_OK");
            return 0U;
        }

        if ((strstr(rx_snapshot, "+MQTTDISCONNECTED") != NULL) ||
            (strstr(rx_snapshot, "ERROR") != NULL) ||
            (strstr(rx_snapshot, "FAIL") != NULL)) {
            ESP8266_SetMqttDiagFromSnapshot(rx_snapshot);
            return 1U;
        }

        ESP8266_TaskFriendlyDelay(100);
    }

    ESP8266_CopyRxSnapshot(rx_snapshot, sizeof(rx_snapshot));
    ESP8266_SetMqttDiagFromSnapshot(rx_snapshot);
    return 1U;
}

static void ESP8266_PushRxByte(uint8_t data)
{
    rx_buffer[rx_write_idx] = data;
    rx_write_idx = (uint16_t)((rx_write_idx + 1U) % ESP8266_RX_BUF_SIZE);
    if (rx_write_idx == rx_read_idx) {
        rx_read_idx = (uint16_t)((rx_read_idx + 1U) % ESP8266_RX_BUF_SIZE);
    }
}

static void ESP8266_ClearRxBuffer(void)
{
    taskENTER_CRITICAL();
    rx_write_idx = 0;
    rx_read_idx = 0;
    memset(rx_buffer, 0, ESP8266_RX_BUF_SIZE);
    taskEXIT_CRITICAL();
}

static void ESP8266_CopyRxSnapshot(char *out, uint16_t out_size)
{
    uint16_t local_read;
    uint16_t local_write;
    uint16_t idx;
    uint16_t out_idx = 0U;

    if (out == NULL || out_size == 0U) {
        return;
    }

    taskENTER_CRITICAL();
    local_read = rx_read_idx;
    local_write = rx_write_idx;
    idx = local_read;

    while (idx != local_write && out_idx < (uint16_t)(out_size - 1U)) {
        out[out_idx++] = (char)rx_buffer[idx];
        idx = (uint16_t)((idx + 1U) % ESP8266_RX_BUF_SIZE);
    }
    taskEXIT_CRITICAL();

    out[out_idx] = '\0';
}

static void ESP8266_CopyRxSnapshotBytes(uint8_t *out, uint16_t out_size, uint16_t *out_len)
{
    uint16_t local_read;
    uint16_t local_write;
    uint16_t idx;
    uint16_t out_idx = 0U;

    if ((out == NULL) || (out_size == 0U) || (out_len == NULL)) {
        return;
    }

    taskENTER_CRITICAL();
    local_read = rx_read_idx;
    local_write = rx_write_idx;
    idx = local_read;

    while ((idx != local_write) && (out_idx < out_size)) {
        out[out_idx++] = rx_buffer[idx];
        idx = (uint16_t)((idx + 1U) % ESP8266_RX_BUF_SIZE);
    }
    taskEXIT_CRITICAL();

    *out_len = out_idx;
}

static uint8_t ESP8266_BufferContainsToken(const uint8_t *buf, uint16_t len, const char *token)
{
    uint16_t i;
    uint16_t token_len;

    if ((buf == NULL) || (token == NULL)) {
        return 0U;
    }

    token_len = (uint16_t)strlen(token);
    if ((token_len == 0U) || (token_len > len)) {
        return 0U;
    }

    for (i = 0U; i <= (uint16_t)(len - token_len); i++) {
        if (memcmp(&buf[i], token, token_len) == 0) {
            return 1U;
        }
    }

    return 0U;
}

static uint8_t ESP8266_WaitForToken(const char *token, uint32_t timeout_ms)
{
    uint8_t rx_snapshot[ESP8266_RX_BUF_SIZE];
    uint16_t rx_len = 0U;
    uint8_t polled_byte;
    uint32_t start_tick;
    HAL_StatusTypeDef poll_status;
    uint8_t interrupt_rx_was_active;

    if (token == NULL) {
        return 1U;
    }

    interrupt_rx_was_active = ESP8266_BeginCommandSession();
    start_tick = HAL_GetTick();
    while ((HAL_GetTick() - start_tick) < timeout_ms) {
        poll_status = ESP8266_PollRxByte(&polled_byte);
        if (poll_status == HAL_OK) {
            taskENTER_CRITICAL();
            ESP8266_PushRxByte(polled_byte);
            taskEXIT_CRITICAL();
            g_uart3_last_rx_tick = HAL_GetTick();
        } else if (ESP8266_ShouldRefreshRxState(ESP8266_MapPollStatus(poll_status)) != 0U) {
            ESP8266_RefreshUartRxState();
        }

        ESP8266_CopyRxSnapshotBytes(rx_snapshot, (uint16_t)sizeof(rx_snapshot), &rx_len);
        if (ESP8266_BufferContainsToken(rx_snapshot, rx_len, token) != 0U) {
            ESP8266_EndCommandSession(interrupt_rx_was_active);
            return 0U;
        }

        if ((ESP8266_BufferContainsToken(rx_snapshot, rx_len, "ERROR") != 0U) ||
            (ESP8266_BufferContainsToken(rx_snapshot, rx_len, "FAIL") != 0U) ||
            (ESP8266_BufferContainsToken(rx_snapshot, rx_len, "CLOSED") != 0U)) {
            ESP8266_EndCommandSession(interrupt_rx_was_active);
            return 1U;
        }

        ESP8266_TaskFriendlyDelay(10U);
    }

    ESP8266_EndCommandSession(interrupt_rx_was_active);
    return 1U;
}

static uint8_t ESP8266_DecodeRemainingLength(const uint8_t *buf,
                                             uint16_t len,
                                             uint32_t *out_value,
                                             uint8_t *out_used)
{
    uint32_t multiplier = 1U;
    uint32_t value = 0U;
    uint8_t used = 0U;

    if ((buf == NULL) || (out_value == NULL) || (out_used == NULL)) {
        return 1U;
    }

    while (used < len) {
        uint8_t encoded = buf[used++];
        value += (uint32_t)(encoded & 0x7FU) * multiplier;
        if ((encoded & 0x80U) == 0U) {
            *out_value = value;
            *out_used = used;
            return 0U;
        }
        multiplier *= 128U;
        if (used >= 4U) {
            return 1U;
        }
    }

    return 1U;
}

static void ESP8266_DispatchRawMqttPublish(const uint8_t *packet, uint16_t packet_len)
{
    uint16_t offset = 0U;

    if ((packet == NULL) || (packet_len < 2U) || (mqtt_message_callback == NULL)) {
        return;
    }

    while (offset + 2U <= packet_len) {
        uint8_t header = packet[offset];
        uint8_t packet_type = (uint8_t)(header >> 4);
        uint8_t rl_used = 0U;
        uint32_t remaining_len = 0U;
        uint32_t total_len;

        if (ESP8266_DecodeRemainingLength(&packet[offset + 1U],
                                          (uint16_t)(packet_len - offset - 1U),
                                          &remaining_len,
                                          &rl_used) != 0U) {
            break;
        }

        total_len = 1U + rl_used + remaining_len;
        if ((uint32_t)offset + total_len > packet_len) {
            break;
        }

        if (packet_type == 3U) {
            uint16_t mqtt_start = (uint16_t)(offset + 1U + rl_used);
            uint16_t topic_len;
            uint8_t qos = (uint8_t)((header >> 1U) & 0x03U);
            uint16_t var_header_len;
            uint16_t payload_len;
            char topic[128];
            char payload[256];

            if (remaining_len >= 2U) {
                topic_len = (uint16_t)(((uint16_t)packet[mqtt_start] << 8U) |
                                       packet[mqtt_start + 1U]);
                var_header_len = (uint16_t)(2U + topic_len + ((qos > 0U) ? 2U : 0U));

                if ((remaining_len >= var_header_len) &&
                    (topic_len > 0U) &&
                    (topic_len < sizeof(topic))) {
                    payload_len = (uint16_t)(remaining_len - var_header_len);
                    if (payload_len < sizeof(payload)) {
                        memcpy(topic, &packet[mqtt_start + 2U], topic_len);
                        topic[topic_len] = '\0';
                        if (payload_len > 0U) {
                            memcpy(payload,
                                   &packet[mqtt_start + var_header_len],
                                   payload_len);
                        }
                        payload[payload_len] = '\0';
                        mqtt_message_callback(topic, payload);
                    }
                }
            }
        }

        offset = (uint16_t)(offset + total_len);
    }
}

static uint8_t ESP8266_ProcessRawIpdData(const uint8_t *buf, uint16_t len)
{
    const char prefix[] = "+IPD,";
    uint16_t i;

    if ((buf == NULL) || (len == 0U)) {
        return 0U;
    }

    for (i = 0U; i + (uint16_t)sizeof(prefix) - 1U < len; i++) {
        if (memcmp(&buf[i], prefix, sizeof(prefix) - 1U) == 0) {
            uint16_t cursor = (uint16_t)(i + (sizeof(prefix) - 1U));
            uint32_t ipd_len = 0U;
            uint8_t has_digit = 0U;

            while ((cursor < len) && isdigit((unsigned char)buf[cursor])) {
                has_digit = 1U;
                ipd_len = (ipd_len * 10U) + (uint32_t)(buf[cursor] - '0');
                cursor++;
            }

            if ((has_digit == 0U) || (cursor >= len) || (buf[cursor] != ':')) {
                return 0U;
            }

            cursor++;
            if ((uint32_t)(len - cursor) < ipd_len) {
                return 0U;
            }

            ESP8266_DispatchRawMqttPublish(&buf[cursor], (uint16_t)ipd_len);
            return 1U;
        }
    }

    return 0U;
}

static uint8_t ESP8266_OpenRawSocket(const char *host, uint16_t port)
{
    char cmd[192];
    char rx_snapshot[ESP8266_RX_BUF_SIZE + 1U];
    const char *link_type = "TCP";

    if ((host == NULL) || (host[0] == '\0') || (port == 0U)) {
        return 1U;
    }

    (void)ESP8266_SendATCommand("AT+CIPMUX=0", "OK", 2000);
    (void)ESP8266_SendATCommand("AT+CIPMODE=0", "OK", 2000);
    (void)ESP8266_SendATCommand("AT+CIPCLOSE", "OK", 1000);
    ESP8266_TaskFriendlyDelay(100U);

    if (port == 8883U) {
        link_type = "SSL";
        /* 参考 ESP-AT 文档：SSL 连接前设置认证模式与 SNI。 */
        (void)ESP8266_SendATCommand("AT+CIPSSLCCONF=0", "OK", 2000);
        snprintf(cmd, sizeof(cmd), "AT+CIPSSLCSNI=\"%s\"", host);
        (void)ESP8266_SendATCommand(cmd, "OK", 2000);
    } else if (port == 8084U) {
        /* WSS 需要 HTTP Upgrade，本实现不支持。 */
        return 1U;
    }

    snprintf(cmd, sizeof(cmd), "AT+CIPSTART=\"%s\",\"%s\",%u", link_type, host, (unsigned int)port);
    if (ESP8266_SendATCommand(cmd, "OK", 30000) != 0U) {
        ESP8266_CopyRxSnapshot(rx_snapshot, sizeof(rx_snapshot));
        if (strstr(rx_snapshot, "ALREADY CONNECTED") != NULL) {
            mqtt_socket_open = 1U;
            return 0U;
        }
        return 1U;
    }

    mqtt_socket_open = 1U;
    return 0U;
}

static void ESP8266_CloseRawSocket(void)
{
    (void)ESP8266_SendATCommand("AT+CIPCLOSE", "OK", 1000);
    mqtt_socket_open = 0U;
}

static uint8_t ESP8266_SendRawPacket(const uint8_t *packet, uint16_t packet_len)
{
    char cmd[32];

    if ((packet == NULL) || (packet_len == 0U)) {
        return 1U;
    }

    snprintf(cmd, sizeof(cmd), "AT+CIPSEND=%u", (unsigned int)packet_len);
    if (ESP8266_SendATCommand(cmd, ">", 5000) != 0U) {
        return 1U;
    }

    if (HAL_UART_Transmit(&huart3, (uint8_t *)packet, packet_len, 3000) != HAL_OK) {
        return 1U;
    }

    if (ESP8266_WaitForToken("SEND OK", 8000) != 0U) {
        return 1U;
    }

    return 0U;
}

static void ESP8266_RefreshUartRxState(void)
{
    /* 清除可能由ESP启动日志(74880)引起的串口错误，避免后续AT接收中断停摆。 */
    __HAL_UART_CLEAR_OREFLAG(&huart3);
    __HAL_UART_CLEAR_FEFLAG(&huart3);
    __HAL_UART_CLEAR_NEFLAG(&huart3);
    __HAL_UART_CLEAR_PEFLAG(&huart3);
    __HAL_UART_SEND_REQ(&huart3, UART_RXDATA_FLUSH_REQUEST);
}

static uint8_t ESP8266_IsInterruptRxActive(void)
{
    return (uint8_t)((huart3.RxState == HAL_UART_STATE_BUSY_RX) ||
                     (huart3.RxState == HAL_UART_STATE_BUSY_TX_RX));
}

static uint8_t ESP8266_BeginCommandSession(void)
{
    uint8_t interrupt_rx_was_active = ESP8266_IsInterruptRxActive();

    if (ESP8266_ShouldSuspendInterruptRx(interrupt_rx_was_active) != 0U) {
        (void)HAL_UART_AbortReceive_IT(&huart3);
        ESP8266_RefreshUartRxState();
    }

    return interrupt_rx_was_active;
}

static void ESP8266_EndCommandSession(uint8_t interrupt_rx_was_active)
{
    if (ESP8266_ShouldResumeInterruptRx(interrupt_rx_was_active) != 0U) {
        ESP8266_RefreshUartRxState();
        (void)HAL_UART_Receive_IT(&huart3, &g_esp_rx_byte, 1U);
    }
}

static HAL_StatusTypeDef ESP8266_PollRxByte(uint8_t *out)
{
    uint8_t interrupt_rx_active;

    if (out == NULL) {
        return HAL_ERROR;
    }

    interrupt_rx_active = ESP8266_IsInterruptRxActive();
    if (ESP8266_ShouldPollRxFallback(interrupt_rx_active) == 0U) {
        return HAL_BUSY;
    }

    return HAL_UART_Receive(&huart3, out, 1U, 5U);
}

static uint8_t ESP8266_MapPollStatus(HAL_StatusTypeDef status)
{
    switch (status) {
        case HAL_OK:
            return ESP8266_RX_POLL_OK;
        case HAL_BUSY:
            return ESP8266_RX_POLL_BUSY;
        case HAL_TIMEOUT:
            return ESP8266_RX_POLL_TIMEOUT;
        case HAL_ERROR:
        default:
            return ESP8266_RX_POLL_ERROR;
    }
}

static void ESP8266_SetUartBaud(uint32_t baud)
{
    uint32_t pclk;
    uint32_t brr;
    uint8_t interrupt_rx_was_active;

    if (baud == 0U) {
        return;
    }

    interrupt_rx_was_active = ESP8266_BeginCommandSession();
    pclk = HAL_RCC_GetPCLK1Freq();
    brr = (pclk + (baud / 2U)) / baud;

    __HAL_UART_DISABLE(&huart3);
    huart3.Instance->BRR = brr;
    __HAL_UART_ENABLE(&huart3);
    huart3.Init.BaudRate = baud;
    ESP8266_RefreshUartRxState();
    ESP8266_EndCommandSession(interrupt_rx_was_active);
}

static uint8_t ESP8266_ParseSubRecvMessage(const char *message,
                                           char *topic,
                                           uint16_t topic_size,
                                           char *payload,
                                           uint16_t payload_size)
{
    const char *prefix;
    const char *cursor;
    const char *topic_start;
    const char *topic_end;
    const char *line_end;
    char *len_end = NULL;
    long payload_len;
    size_t topic_len;
    size_t available_len;
    size_t copy_len;

    if ((message == NULL) || (topic == NULL) || (payload == NULL) ||
        (topic_size == 0U) || (payload_size == 0U)) {
        return 1U;
    }

    prefix = strstr(message, "+MQTTSUBRECV:");
    if (prefix == NULL) {
        return 1U;
    }

    cursor = prefix + strlen("+MQTTSUBRECV:");
    cursor = strchr(cursor, ',');
    if (cursor == NULL) {
        return 1U;
    }
    cursor++;

    if (*cursor == '"') {
        cursor++;
        topic_start = cursor;
        topic_end = strchr(cursor, '"');
        if (topic_end == NULL) {
            return 1U;
        }
        cursor = topic_end + 1;
        if (*cursor != ',') {
            return 1U;
        }
    } else {
        topic_start = cursor;
        topic_end = strchr(cursor, ',');
        if (topic_end == NULL) {
            return 1U;
        }
        cursor = topic_end;
    }

    if (*cursor != ',') {
        return 1U;
    }
    cursor++;

    topic_len = (size_t)(topic_end - topic_start);
    if (topic_len >= topic_size) {
        return 1U;
    }

    memcpy(topic, topic_start, topic_len);
    topic[topic_len] = '\0';

    payload_len = strtol(cursor, &len_end, 10);
    if ((len_end == cursor) || (len_end == NULL) || (*len_end != ',') || (payload_len < 0)) {
        return 1U;
    }

    cursor = len_end + 1;
    line_end = strstr(cursor, "\r\n");
    available_len = (line_end != NULL) ? (size_t)(line_end - cursor) : strlen(cursor);
    copy_len = (size_t)payload_len;
    if (copy_len > available_len) {
        copy_len = available_len;
    }
    if (copy_len >= payload_size) {
        copy_len = payload_size - 1U;
    }

    memcpy(payload, cursor, copy_len);
    payload[copy_len] = '\0';
    return 0U;
}

/**
  * @brief  初始化ESP8266 GPIO
  */
void ESP8266_Init(void)
{
    /* IO0置高，进入正常工作模式 */
    ESP8266_IO0_HIGH();
    
    /* 先拉低复位引脚 */
    ESP8266_RST_LOW();
    ESP8266_EN_LOW();
    
    ESP8266_TaskFriendlyDelay(100);
    
    /* 使能模块 */
    ESP8266_Enable();
    
    mqtt_cmd_support = 0xFFU;
    mqtt_raw_mode = 0U;
    mqtt_socket_open = 0U;
    esp_state = ESP8266_STATE_INIT;
}

/**
  * @brief  使能ESP8266
  */
void ESP8266_Enable(void)
{
    ESP8266_EN_HIGH();
    ESP8266_TaskFriendlyDelay(10);
    ESP8266_RST_HIGH();
    ESP8266_TaskFriendlyDelay(100);
}

/**
  * @brief  禁用ESP8266
  */
void ESP8266_Disable(void)
{
    ESP8266_EN_LOW();
    ESP8266_RST_LOW();
}

/**
  * @brief  复位ESP8266
  */
void ESP8266_Reset(void)
{
    ESP8266_RST_LOW();
    ESP8266_TaskFriendlyDelay(100);
    ESP8266_RST_HIGH();
    ESP8266_TaskFriendlyDelay(500);

    mqtt_cmd_support = 0xFFU;
    mqtt_raw_mode = 0U;
    mqtt_requested_port = 0U;
    mqtt_socket_open = 0U;
    ESP8266_ClearRxBuffer();
    esp_state = ESP8266_STATE_RESET;
}

/**
  * @brief  发送AT命令
  * @param  cmd: 命令字符串
  * @param  expected_resp: 期望响应
  * @param  timeout_ms: 超时时间
  * @retval 0:成功 1:失败
  * @note   使用临界区保护，防止中断修改缓冲区索引
  */
uint8_t ESP8266_SendATCommand(const char *cmd, const char *expected_resp, uint32_t timeout_ms)
{
    uint8_t cmd_buf[ESP8266_TX_BUF_SIZE];
    uint8_t rx_snapshot[ESP8266_RX_BUF_SIZE];
    uint16_t rx_len = 0U;
    uint8_t polled_byte;
    uint32_t start_tick;
    HAL_StatusTypeDef poll_status;
    uint8_t interrupt_rx_was_active;

    interrupt_rx_was_active = ESP8266_BeginCommandSession();
    ESP8266_RefreshUartRxState();
    ESP8266_ClearRxBuffer();
    
    /* 组装命令 */
    snprintf((char *)cmd_buf, ESP8266_TX_BUF_SIZE, "%s\r\n", cmd);
    
    /* 发送命令 */
    HAL_UART_Transmit(&huart3, cmd_buf, strlen((char *)cmd_buf), 1000);
    
    /* 等待响应 */
    start_tick = HAL_GetTick();
    while ((HAL_GetTick() - start_tick) < timeout_ms) {
        poll_status = ESP8266_PollRxByte(&polled_byte);
        if (poll_status == HAL_OK) {
            taskENTER_CRITICAL();
            ESP8266_PushRxByte(polled_byte);
            taskEXIT_CRITICAL();
            g_uart3_last_rx_tick = HAL_GetTick();
        } else if (ESP8266_ShouldRefreshRxState(ESP8266_MapPollStatus(poll_status)) != 0U) {
            /* 仅在真实超时/错误时刷新，避免与单字节接收中断互相抢占。 */
            ESP8266_RefreshUartRxState();
        }

        ESP8266_CopyRxSnapshotBytes(rx_snapshot, (uint16_t)sizeof(rx_snapshot), &rx_len);

        /* 检查期望响应 */
        if (expected_resp != NULL) {
            if (ESP8266_BufferContainsToken(rx_snapshot, rx_len, expected_resp) != 0U) {
                ESP8266_EndCommandSession(interrupt_rx_was_active);
                return 0;
            }
        }
        
        /* 检查错误响应 */
        if ((ESP8266_BufferContainsToken(rx_snapshot, rx_len, "ERROR") != 0U) ||
            (ESP8266_BufferContainsToken(rx_snapshot, rx_len, "FAIL") != 0U)) {
            ESP8266_EndCommandSession(interrupt_rx_was_active);
            return 1;
        }
        
        ESP8266_TaskFriendlyDelay(10);
    }
    
    ESP8266_EndCommandSession(interrupt_rx_was_active);
    return 1;  // 超时
}

/**
  * @brief  发送原始数据
  * @param  data: 数据指针
  * @param  len: 长度
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_SendData(uint8_t *data, uint16_t len)
{
    if (HAL_UART_Transmit(&huart3, data, len, 1000) == HAL_OK) {
        return 0;
    }
    return 1;
}

/**
  * @brief  初始化WiFi模块(AT测试)
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_WiFi_Init(void)
{
    uint8_t at_ok = 0U;
    uint8_t attempt = 0U;
    uint8_t baud_idx = 0U;
    uint32_t active_baud = 115200U;
    uint8_t baud_switched = 0U;
    char hostname_cmd[64];

    /* 自动探测ESP当前波特率，覆盖被改成9600/57600等场景。 */
    ESP8266_SetMqttDiag("AT_SCAN");
    for (baud_idx = 0U; baud_idx < (uint8_t)(sizeof(esp_probe_baud_list) / sizeof(esp_probe_baud_list[0])); baud_idx++) {
        active_baud = esp_probe_baud_list[baud_idx];
        ESP8266_SetUartBaud(active_baud);

        for (attempt = 0U; attempt < 3U; attempt++) {
            if (ESP8266_SendATCommand("AT", "OK", 1000) == 0U) {
                at_ok = 1U;
                break;
            }
            ESP8266_TaskFriendlyDelay(150);
        }

        if (at_ok != 0U) {
            break;
        }
    }

    if (at_ok == 0U) {
        /* 首轮失败后做一次硬复位再探测，兼容模块上电异常。 */
        ESP8266_SetMqttDiag("AT_RST");
        ESP8266_Reset();
        ESP8266_TaskFriendlyDelay(300);

        for (baud_idx = 0U; baud_idx < (uint8_t)(sizeof(esp_probe_baud_list) / sizeof(esp_probe_baud_list[0])); baud_idx++) {
            active_baud = esp_probe_baud_list[baud_idx];
            ESP8266_SetUartBaud(active_baud);

            for (attempt = 0U; attempt < 3U; attempt++) {
                if (ESP8266_SendATCommand("AT", "OK", 1000) == 0U) {
                    at_ok = 1U;
                    break;
                }
                ESP8266_TaskFriendlyDelay(150);
            }

            if (at_ok != 0U) {
                break;
            }
        }
    }

    if (at_ok == 0U) {
        ESP8266_SetMqttDiag("AT_NONE");
        return 1U;
    }

    if (active_baud != 115200U) {
        /* 将模块波特率收敛回115200，保持系统后续通信一致。 */
        if ((ESP8266_SendATCommand("AT+UART_CUR=115200,8,1,0,0", "OK", 3000) != 0U) &&
            (ESP8266_SendATCommand("AT+UART_DEF=115200,8,1,0,0", "OK", 3000) != 0U) &&
            (ESP8266_SendATCommand("AT+UART=115200,8,1,0,0", "OK", 3000) != 0U)) {
            ESP8266_SetMqttDiag("AT_115K");
            return 1U;
        }
        ESP8266_SetUartBaud(115200U);
        ESP8266_TaskFriendlyDelay(100);
        baud_switched = 1U;
    }

    if ((baud_switched != 0U) && (ESP8266_SendATCommand("AT", "OK", 1000) != 0U)) {
        ESP8266_SetMqttDiag("AT_SYNC");
        return 1U;
    }

    /* 关闭回显 */
    ESP8266_SetMqttDiag("ATE0");
    for (attempt = 0U; attempt < 3U; attempt++) {
        if (ESP8266_SendATCommand("ATE0", "OK", 1000) == 0) {
            break;
        }
        ESP8266_TaskFriendlyDelay(500);
    }

    if (attempt >= 3U) {
        ESP8266_SetMqttDiag("ATE0ERR");
        return 1;
    }
    
    /* 设置模式为STA */
    ESP8266_SetMqttDiag("CWMODE");
    if (ESP8266_SendATCommand("AT+CWMODE=1", "OK", 2000) != 0) {
        ESP8266_SetMqttDiag("CWM_ERR");
        return 1;
    }

    /* 关闭WiFi省电，减少手机热点场景下的周期性掉线。 */
    (void)ESP8266_SendATCommand("AT+SLEEP=0", "OK", 2000);

    /* 开启自动重连，避免偶发掉线后长期离线。 */
    (void)ESP8266_SendATCommand("AT+CWAUTOCONN=1", "OK", 2000);

    /* 设置设备主机名，方便路由器/热点侧识别。 */
    (void)snprintf(hostname_cmd, sizeof(hostname_cmd), "AT+CWHOSTNAME=\"%s\"", ESP8266_DEVICE_NAME);
    (void)ESP8266_SendATCommand(hostname_cmd, "OK", 2000);
    
    /* 关闭DHCP(可选) */
    ESP8266_SendATCommand("AT+CWDHCP=1,1", "OK", 2000);
    ESP8266_SetMqttDiag("WIFIINI");
    
    return 0;
}

/**
  * @brief  连接WiFi
  * @param  ssid: WiFi名称
  * @param  password: WiFi密码
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_WiFi_Connect(const char *ssid, const char *password)
{
    char cmd[128];
    uint8_t check_retry;
    
    esp_state = ESP8266_STATE_WIFI_CONNECTING;
    
    /* 组装连接命令 */
    snprintf(cmd, sizeof(cmd), "AT+CWJAP=\"%s\",\"%s\"", ssid, password);
    
    if (ESP8266_SendATCommand(cmd, "OK", 20000) != 0) {
        ESP8266_SetMqttDiag("WJAPERR");
        esp_state = ESP8266_STATE_ERROR;
        return 1;
    }

    for (check_retry = 0U; check_retry < 10U; ++check_retry) {
        if (ESP8266_WiFi_IsConnected() != 0U) {
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
            return 0;
        }
        ESP8266_TaskFriendlyDelay(200);
    }
    
    ESP8266_SetMqttDiag("WIFI_ST");
    esp_state = ESP8266_STATE_ERROR;
    return 1;
}

/**
  * @brief  断开WiFi连接
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_WiFi_Disconnect(void)
{
    if (ESP8266_SendATCommand("AT+CWQAP", "OK", 5000) == 0) {
        mqtt_socket_open = 0U;
        esp_state = ESP8266_STATE_INIT;
        return 0;
    }
    return 1;
}

/**
  * @brief  检查WiFi连接状态
  * @retval 1:已连接 0:未连接
  */
uint8_t ESP8266_WiFi_IsConnected(void)
{
    char rx_snapshot[ESP8266_RX_BUF_SIZE + 1U];
    char *resp;
    
    if (ESP8266_SendATCommand("AT+CIPSTATUS", "OK", 2000) != 0) {
        return 0;
    }

    ESP8266_CopyRxSnapshot(rx_snapshot, sizeof(rx_snapshot));
    resp = strstr(rx_snapshot, "STATUS:");
    if (resp != NULL) {
        int status = atoi(resp + 7);
        /* STATUS:2 获得IP, STATUS:3 已连接, STATUS:4 断开 */
        return (status == 2 || status == 3) ? 1 : 0;
    }
    
    return 0;
}

/**
  * @brief  初始化MQTT
  * @param  broker_ip: MQTT服务器IP
  * @param  port: 端口号
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_MQTT_Init(const char *broker_ip, uint16_t port)
{
    uint8_t mqtt_scheme;
    const char *mqtt_path;

    if ((broker_ip == NULL) || (broker_ip[0] == '\0') || (port == 0U)) {
        ESP8266_SetMqttDiag("PARAM_ERR");
        return 1U;
    }

    ESP8266_CopyString(mqtt_cfg.broker_ip, (uint16_t)sizeof(mqtt_cfg.broker_ip), broker_ip);
    mqtt_requested_port = port;
    mqtt_cfg.broker_port = port;
    mqtt_socket_open = 0U;
    if (mqtt_cfg.keepalive == 0U) {
        mqtt_cfg.keepalive = 120U;
    }

    if (ESP8266_IsCloudPort(mqtt_cfg.broker_port) != 0U) {
        mqtt_raw_mode = 0U;
        if (ESP8266_ProbeMqttCommandSupport() == 0U) {
            ESP8266_SetMqttDiag("NO_MQTT");
            return 1U;
        }
        return ESP8266_SelectCloudAtConfig(mqtt_requested_port);
    }

    /* 若固件不支持 MQTT AT 命令集，也走 raw 模式。 */
    if (ESP8266_ProbeMqttCommandSupport() == 0U) {
        mqtt_raw_mode = 1U;
        ESP8266_SetMqttDiag("RAW_INI");
        if (mqtt_cfg.broker_port == 8084U) {
            mqtt_cfg.broker_port = 8883U;
        }
        return 0U;
    }

    mqtt_raw_mode = 0U;
    ESP8266_SetMqttDiag("MQ_INIT");

    mqtt_scheme = ESP8266_GetMqttSchemeByPort(mqtt_cfg.broker_port);
    mqtt_path = ESP8266_IsWebSocketScheme(mqtt_scheme) ? "/mqtt" : "";

    if (ESP8266_ApplyMqttConfig(mqtt_scheme, mqtt_path) == 0U) {
        return 0U;
    }

    if (mqtt_cfg.broker_port != 1883U) {
        ESP8266_SetMqttDiag("TRY_1883");
        mqtt_cfg.broker_port = 1883U;
        if (ESP8266_ApplyMqttConfig(1U, "") == 0U) {
            return 0U;
        }
    }

    return 1U;
}

/**
  * @brief  连接MQTT服务器
  * @param  client_id: 客户端ID
  * @param  username: 用户名
  * @param  password: 密码
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_MQTT_Connect(const char *client_id, const char *username, const char *password)
{
    char cmd[320];

    if ((client_id == NULL) || (client_id[0] == '\0')) {
        ESP8266_SetMqttDiag("CID_ERR");
        return 1U;
    }
    
    esp_state = ESP8266_STATE_MQTT_CONNECTING;

    ESP8266_CopyString(mqtt_cfg.client_id, (uint16_t)sizeof(mqtt_cfg.client_id), client_id);
    ESP8266_CopyString(mqtt_cfg.username, (uint16_t)sizeof(mqtt_cfg.username), username);
    ESP8266_CopyString(mqtt_cfg.password, (uint16_t)sizeof(mqtt_cfg.password), password);

    if (mqtt_raw_mode != 0U) {
        ESP8266_SetMqttDiag("RAW_AUTH");
        esp_state = ESP8266_STATE_WIFI_CONNECTED;
        return 0;
    }

    /* 使用当前激活的传输模式（可能已被Init回退到8083/WS或1883/TCP）。 */
    snprintf(cmd, sizeof(cmd), "AT+MQTTUSERCFG=0,%u,\"%s\",\"%s\",\"%s\",0,0,\"%s\"",
             mqtt_scheme_active, mqtt_cfg.client_id, mqtt_cfg.username, mqtt_cfg.password, mqtt_path_active);
    
    if (ESP8266_SendATCommand(cmd, "OK", 5000) != 0) {
        ESP8266_SetMqttDiag("AUTH_CFGE");
        esp_state = ESP8266_STATE_WIFI_CONNECTED;
        return 1;
    }

    ESP8266_SetMqttDiag("AUTH_OK");
    esp_state = ESP8266_STATE_WIFI_CONNECTED;
    return 0;
}

/**
  * @brief  连接到指定MQTT服务器
  * @param  broker_ip: 服务器IP
  * @param  port: 端口
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_MQTT_ConnectToBroker(const char *broker_ip, uint16_t port)
{
    uint8_t packet[ESP8266_TX_BUF_SIZE];
    uint16_t packet_len = 0U;

    if ((broker_ip == NULL) || (broker_ip[0] == '\0')) {
        ESP8266_SetMqttDiag("HOST_ERR");
        return 1U;
    }
    if ((port == 0U) && (mqtt_cfg.broker_port == 0U)) {
        ESP8266_SetMqttDiag("PORT_ERR");
        return 1U;
    }

    ESP8266_CopyString(mqtt_cfg.broker_ip, (uint16_t)sizeof(mqtt_cfg.broker_ip), broker_ip);
    if (port != 0U) {
        mqtt_requested_port = port;
        mqtt_cfg.broker_port = port;
    }

    if (ESP8266_IsCloudPort(mqtt_requested_port) != 0U) {
        esp_state = ESP8266_STATE_MQTT_CONNECTING;
        if (ESP8266_TryCloudAtConnect(mqtt_requested_port) == 0U) {
            esp_state = ESP8266_STATE_MQTT_CONNECTED;
            return 0U;
        }
        esp_state = ESP8266_STATE_WIFI_CONNECTED;
        return 1U;
    }

    if (mqtt_raw_mode != 0U) {
        esp_state = ESP8266_STATE_MQTT_CONNECTING;

        if (mqtt_cfg.broker_port == 8084U) {
            mqtt_cfg.broker_port = 8883U;
        }
        if (mqtt_cfg.broker_port == 8083U) {
            ESP8266_SetMqttDiag("RAW_WS");
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
            return 1U;
        }

        if (ESP8266_OpenRawSocket(mqtt_cfg.broker_ip, mqtt_cfg.broker_port) != 0U) {
            ESP8266_SetMqttDiag((mqtt_cfg.broker_port == 8883U) ? "RAW_SSL" : "RAW_OPN");
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
            return 1U;
        }

        if (ESP8266_BuildConnectPacket(packet, (uint16_t)sizeof(packet), &packet_len) != 0U) {
            ESP8266_SetMqttDiag("RAW_PKT");
            ESP8266_CloseRawSocket();
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
            return 1U;
        }

        if (ESP8266_SendRawPacket(packet, packet_len) != 0U) {
            ESP8266_SetMqttDiag("RAW_CNN");
            ESP8266_CloseRawSocket();
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
            return 1U;
        }

        ESP8266_ClearRxBuffer(); /* 丢弃CONNACK二进制，避免后续字符串匹配受0x00影响 */
        ESP8266_SetMqttDiag("RAW_OK");
        esp_state = ESP8266_STATE_MQTT_CONNECTED;
        return 0U;
    }

    esp_state = ESP8266_STATE_MQTT_CONNECTING;
    if (ESP8266_TryConnectCurrentBroker() == 0U) {
        esp_state = ESP8266_STATE_MQTT_CONNECTED;
        return 0U;
    }

    /* 连接失败时执行传输回退：TLS/WSS请求先回退 8083(WS)，再回退 1883(TCP)。 */
    if ((mqtt_cfg.broker_port == 8883U) || (mqtt_cfg.broker_port == 8084U)) {
        ESP8266_SetMqttDiag("FB_8083");
        mqtt_cfg.broker_port = 8083U;
        if ((ESP8266_ApplyMqttConfig(6U, "/mqtt") == 0U) &&
            (ESP8266_TryConnectCurrentBroker() == 0U)) {
            esp_state = ESP8266_STATE_MQTT_CONNECTED;
            return 0U;
        }
    }

    if (mqtt_cfg.broker_port != 1883U) {
        ESP8266_SetMqttDiag("FB_1883");
        mqtt_cfg.broker_port = 1883U;
        if ((ESP8266_ApplyMqttConfig(1U, "") == 0U) &&
            (ESP8266_TryConnectCurrentBroker() == 0U)) {
            esp_state = ESP8266_STATE_MQTT_CONNECTED;
            return 0U;
        }
    }

    esp_state = ESP8266_STATE_WIFI_CONNECTED;
    return 1U;
}

/**
  * @brief  断开MQTT连接
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_MQTT_Disconnect(void)
{
    if (mqtt_raw_mode != 0U) {
        static const uint8_t mqtt_disconnect_packet[2] = {0xE0U, 0x00U};
        (void)ESP8266_SendRawPacket(mqtt_disconnect_packet, (uint16_t)sizeof(mqtt_disconnect_packet));
        ESP8266_CloseRawSocket();
        ESP8266_SetMqttDiag("RAW_DCN");
        if (esp_state == ESP8266_STATE_MQTT_CONNECTED) {
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
        }
        return 0U;
    }

    if (ESP8266_SendATCommand("AT+MQTTCLEAN=0", "OK", 5000) == 0) {
        if (esp_state == ESP8266_STATE_MQTT_CONNECTED) {
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
        }
        return 0;
    }
    return 1;
}

/**
  * @brief  发布MQTT消息
  * @param  topic: 主题
  * @param  payload: 消息内容
  * @param  qos: QoS等级 (0-2)
  * @param  retain: 保留标志
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_MQTT_Publish(const char *topic, const char *payload, uint8_t qos, uint8_t retain)
{
    char cmd[ESP8266_TX_BUF_SIZE];
    uint8_t packet[ESP8266_TX_BUF_SIZE];
    uint16_t packet_len = 0U;

    if (mqtt_raw_mode != 0U) {
        if (qos != 0U) {
            ESP8266_SetMqttDiag("RAW_QOS");
            return 1U;
        }
        if (mqtt_socket_open == 0U) {
            ESP8266_SetMqttDiag("RAW_SOC");
            return 1U;
        }
        if (ESP8266_BuildPublishPacket(topic, payload, retain, packet, (uint16_t)sizeof(packet), &packet_len) != 0U) {
            ESP8266_SetMqttDiag("RAW_PKT");
            return 1U;
        }
        if (ESP8266_SendRawPacket(packet, packet_len) != 0U) {
            ESP8266_SetMqttDiag("RAW_PUB");
            mqtt_socket_open = 0U;
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
            return 1U;
        }
        ESP8266_SetMqttDiag("RAW_OK");
        return 0U;
    }
    
    snprintf(cmd, sizeof(cmd), "AT+MQTTPUB=0,\"%s\",\"%s\",%d,%d",
             topic, payload, qos, retain);
    
    if (ESP8266_SendATCommand(cmd, "OK", 5000) == 0) {
        return 0;
    }
    
    return 1;
}

/**
  * @brief  订阅MQTT主题
  * @param  topic: 主题
  * @param  qos: QoS等级
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_MQTT_Subscribe(const char *topic, uint8_t qos)
{
    char cmd[128];
    uint8_t packet[ESP8266_TX_BUF_SIZE];
    uint16_t packet_len = 0U;

    if (mqtt_raw_mode != 0U) {
        if (mqtt_socket_open == 0U) {
            ESP8266_SetMqttDiag("RAW_SOC");
            return 1U;
        }
        if (ESP8266_BuildSubscribePacket(topic, qos, packet, (uint16_t)sizeof(packet), &packet_len) != 0U) {
            ESP8266_SetMqttDiag("RAW_PKT");
            return 1U;
        }
        if (ESP8266_SendRawPacket(packet, packet_len) != 0U) {
            ESP8266_SetMqttDiag("RAW_SUB");
            return 1U;
        }
        ESP8266_ClearRxBuffer(); /* 丢弃SUBACK二进制 */
        ESP8266_SetMqttDiag("RAW_OK");
        return 0U;
    }
    
    snprintf(cmd, sizeof(cmd), "AT+MQTTSUB=0,\"%s\",%d", topic, qos);
    
    if (ESP8266_SendATCommand(cmd, "OK", 5000) == 0) {
        return 0;
    }
    
    return 1;
}

/**
  * @brief  取消订阅MQTT主题
  * @param  topic: 主题
  * @retval 0:成功 1:失败
  */
uint8_t ESP8266_MQTT_Unsubscribe(const char *topic)
{
    char cmd[128];

    if (mqtt_raw_mode != 0U) {
        (void)topic;
        /* 当前raw模式未实现UNSUBSCRIBE，保留连接可用性。 */
        return 0U;
    }
    
    snprintf(cmd, sizeof(cmd), "AT+MQTTUNSUB=0,\"%s\"", topic);
    
    if (ESP8266_SendATCommand(cmd, "OK", 5000) == 0) {
        return 0;
    }
    
    return 1;
}

void ESP8266_RegisterMQTTMessageCallback(ESP8266_MQTT_MessageCallback_t callback)
{
    mqtt_message_callback = callback;
}

/**
  * @brief  获取当前状态
  * @retval 状态枚举
  */
ESP8266_State_t ESP8266_GetState(void)
{
    return esp_state;
}

const char* ESP8266_GetMqttDiag(void)
{
    return mqtt_diag;
}

uint16_t ESP8266_GetMqttActivePort(void)
{
    return mqtt_cfg.broker_port;
}

/**
  * @brief  获取状态字符串
  * @retval 状态描述
  */
const char* ESP8266_GetStateString(void)
{
    switch (esp_state) {
        case ESP8266_STATE_RESET:           return "RESET";
        case ESP8266_STATE_INIT:            return "INIT";
        case ESP8266_STATE_WIFI_CONNECTING: return "WIFI_CONNECTING";
        case ESP8266_STATE_WIFI_CONNECTED:  return "WIFI_CONNECTED";
        case ESP8266_STATE_MQTT_CONNECTING: return "MQTT_CONNECTING";
        case ESP8266_STATE_MQTT_CONNECTED:  return "MQTT_CONNECTED";
        case ESP8266_STATE_ERROR:           return "ERROR";
        default:                            return "UNKNOWN";
    }
}

/**
  * @brief  UART接收回调函数 (需要在HAL_UART_RxCpltCallback中调用)
  * @param  data: 接收到的字节
  */
void ESP8266_UART_RxCallback(uint8_t data)
{
    ESP8266_PushRxByte(data);
}

/**
  * @brief  处理接收到的数据
  * @note   可在FreeRTOS任务中调用
  */
void ESP8266_ProcessRxData(void)
{
    char rx_snapshot[ESP8266_RX_BUF_SIZE + 1U];
    uint8_t rx_snapshot_bytes[ESP8266_RX_BUF_SIZE];
    uint16_t rx_snapshot_len = 0U;
    char topic[128];
    char payload[256];

    ESP8266_CopyRxSnapshot(rx_snapshot, sizeof(rx_snapshot));

    if (mqtt_raw_mode != 0U) {
        ESP8266_CopyRxSnapshotBytes(rx_snapshot_bytes,
                                    (uint16_t)sizeof(rx_snapshot_bytes),
                                    &rx_snapshot_len);

        if (strstr(rx_snapshot, "WIFI DISCONNECT") != NULL) {
            mqtt_socket_open = 0U;
            esp_state = ESP8266_STATE_INIT;
            ESP8266_ClearRxBuffer();
            return;
        }

        if ((strstr(rx_snapshot, "CLOSED") != NULL) || (strstr(rx_snapshot, "CONNECT FAIL") != NULL)) {
            mqtt_socket_open = 0U;
            esp_state = ESP8266_STATE_WIFI_CONNECTED;
            ESP8266_SetMqttDiag("RAW_CLS");
            ESP8266_ClearRxBuffer();
            return;
        }

        if (ESP8266_ProcessRawIpdData(rx_snapshot_bytes, rx_snapshot_len) != 0U) {
            ESP8266_ClearRxBuffer();
        }

        return;
    }

    if (strstr(rx_snapshot, "+MQTTSUBRECV:") != NULL) {
        if (ESP8266_ParseSubRecvMessage(rx_snapshot, topic, sizeof(topic), payload, sizeof(payload)) == 0U) {
            if (mqtt_message_callback != NULL) {
                mqtt_message_callback(topic, payload);
            }
        }
        ESP8266_ClearRxBuffer();
    }

    if (strstr(rx_snapshot, "+MQTTCONNECTED") != NULL) {
        esp_state = ESP8266_STATE_MQTT_CONNECTED;
        ESP8266_SetMqttDiag("MQ_OK");
        ESP8266_ClearRxBuffer();
    }
    
    if (strstr(rx_snapshot, "WIFI DISCONNECT") != NULL) {
        esp_state = ESP8266_STATE_INIT;
        ESP8266_ClearRxBuffer();
    }
    
    if (strstr(rx_snapshot, "+MQTTDISCONNECTED") != NULL) {
        esp_state = ESP8266_STATE_WIFI_CONNECTED;
        ESP8266_SetMqttDiagFromSnapshot(rx_snapshot);
        ESP8266_ClearRxBuffer();
    }
}
