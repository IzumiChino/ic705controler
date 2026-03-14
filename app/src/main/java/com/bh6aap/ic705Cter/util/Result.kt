package com.bh6aap.ic705Cter.util

/**
 * 结果封装类，用于统一处理操作结果
 * 替代直接使用try-catch和回调的方式
 */
sealed class Result<out T> {
    /**
     * 操作成功
     * @param data 返回的数据
     */
    data class Success<T>(val data: T) : Result<T>()

    /**
     * 操作失败
     * @param exception 异常信息
     * @param message 用户友好的错误消息
     * @param code 错误代码（可选）
     */
    data class Error(
        val exception: Throwable? = null,
        val message: String,
        val code: Int = -1
    ) : Result<Nothing>()

    /**
     * 操作进行中
     */
    object Loading : Result<Nothing>()

    /**
     * 检查是否为成功状态
     */
    fun isSuccess(): Boolean = this is Success

    /**
     * 检查是否为失败状态
     */
    fun isError(): Boolean = this is Error

    /**
     * 检查是否为加载状态
     */
    fun isLoading(): Boolean = this is Loading

    /**
     * 获取成功数据，如果失败则返回null
     */
    fun getOrNull(): T? = (this as? Success)?.data

    /**
     * 获取成功数据，如果失败则返回默认值
     */
    fun getOrDefault(default: @UnsafeVariance T): T = (this as? Success)?.data ?: default

    /**
     * 获取错误消息，如果成功则返回null
     */
    fun errorMessage(): String? = (this as? Error)?.message

    /**
     * 成功时执行操作
     */
    inline fun onSuccess(action: (T) -> Unit): Result<T> {
        if (this is Success) action(data)
        return this
    }

    /**
     * 失败时执行操作
     */
    inline fun onError(action: (Error) -> Unit): Result<T> {
        if (this is Error) action(this)
        return this
    }

    /**
     * 加载时执行操作
     */
    inline fun onLoading(action: () -> Unit): Result<T> {
        if (this is Loading) action()
        return this
    }

    companion object {
        /**
         * 快速创建成功结果
         */
        fun <T> success(data: T): Result<T> = Success(data)

        /**
         * 快速创建失败结果
         */
        fun error(message: String, exception: Throwable? = null, code: Int = -1): Result<Nothing> =
            Error(exception, message, code)

        /**
         * 快速创建加载结果
         */
        fun loading(): Result<Nothing> = Loading
    }
}

/**
 * 安全执行包装器，自动捕获异常并包装为Result
 * @param operation 操作名称，用于日志记录
 * @param block 要执行的代码块
 * @return Result包装的结果
 */
inline fun <T> safeExecute(
    operation: String,
    block: () -> T
): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    LogManager.e("SafeExecute", "$operation 失败", e)
    Result.Error(e, "$operation 失败: ${e.message ?: "未知错误"}")
}

/**
 * 异步安全执行包装器，用于协程环境
 * @param operation 操作名称
 * @param block 要执行的挂起函数
 * @return Result包装的结果
 */
suspend inline fun <T> safeExecuteAsync(
    operation: String,
    crossinline block: suspend () -> T
): Result<T> = try {
    Result.Success(block())
} catch (e: Exception) {
    LogManager.e("SafeExecute", "$operation 失败", e)
    Result.Error(e, "$operation 失败: ${e.message ?: "未知错误"}")
}

/**
 * 将Result转换为UI状态
 */
fun <T> Result<T>.toUiState(): UiState<T> = when (this) {
    is Result.Success -> UiState.Success(data)
    is Result.Error -> UiState.Error(message)
    is Result.Loading -> UiState.Loading
}

/**
 * UI状态封装类
 */
sealed class UiState<out T> {
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
    object Loading : UiState<Nothing>()
}
