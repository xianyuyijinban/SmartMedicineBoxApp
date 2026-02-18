package com.smartmedicine.box.data.model

/**
 * ViewModel状态封装类
 * 用于表示异步操作的状态
 */
sealed class ViewModelState<out T> {
    object Loading : ViewModelState<Nothing>()
    data class Success<T>(val data: T) : ViewModelState<T>()
    data class Error(val message: String, val code: Int? = null) : ViewModelState<Nothing>()
    object Idle : ViewModelState<Nothing>()

    fun isLoading(): Boolean = this is Loading
    fun isSuccess(): Boolean = this is Success
    fun isError(): Boolean = this is Error
    fun isIdle(): Boolean = this is Idle

    fun getOrNull(): T? = (this as? Success)?.data
    fun errorOrNull(): Error? = this as? Error
}

/**
 * 设备连接状态
 */
sealed class DeviceConnectionState {
    object Disconnected : DeviceConnectionState()
    object Connecting : DeviceConnectionState()
    object Connected : DeviceConnectionState()
    data class Error(val message: String) : DeviceConnectionState()

    fun isConnected(): Boolean = this is Connected
    fun isConnecting(): Boolean = this is Connecting
}

/**
 * 数据刷新状态
 */
data class RefreshState(
    val isRefreshing: Boolean = false,
    val lastRefreshTime: Long = 0L,
    val refreshError: String? = null
)

/**
 * 扩展函数：将ViewModelState转换为UI友好的格式
 */
fun <T> ViewModelState<T>.toDisplayMessage(): String {
    return when (this) {
        is ViewModelState.Loading -> "加载中..."
        is ViewModelState.Success -> "操作成功"
        is ViewModelState.Error -> message
        is ViewModelState.Idle -> ""
    }
}
