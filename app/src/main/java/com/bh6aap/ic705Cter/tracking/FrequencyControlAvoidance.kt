package com.bh6aap.ic705Cter.tracking

import com.bh6aap.ic705Cter.util.LogManager
import kotlinx.coroutines.*

/**
 * 自动避让波轮控制器
 * 用于检测用户手动操作波轮并自动暂停/恢复频率控制
 *
 * 优化策略（参考Aapsctror项目）：
 * 1. 单次频率变化超过阈值即触发避让（无需等待多次变化）
 * 2. 快速接管机制：用户停止操作300ms后立即接管
 * 3. 最大等待时间2秒后强制接管
 * 4. 更低的频率变化阈值（4Hz）
 * 5. 更短的命令后忽略窗口（200ms）
 */
class FrequencyControlAvoidance {

    companion object {
        private const val TAG = "FrequencyControlAvoidance"

        // 命令发送后忽略窗口（ms）- 避免自己发送的命令触发避让
        private const val COMMAND_IGNORE_WINDOW_MS = 200L

        // 用户活动超时时间（ms）- 用户停止操作后多久恢复控制
        private const val USER_ACTIVITY_TIMEOUT_MS = 100L

        // 最大避让时间（ms）- 强制恢复控制
        private const val MAX_AVOIDANCE_TIME_MS = 1000L

        // 频率变化阈值（Hz）- 超过此值认为用户在操作波轮
        private const val FREQUENCY_CHANGE_THRESHOLD_HZ = 4.0

        // PTT频率跳变阈值（Hz）- 检测PTT状态切换
        private const val PTT_FREQUENCY_JUMP_THRESHOLD_HZ = 100_000_000.0
    }

    // 回调接口
    interface AvoidanceListener {
        fun onAvoidanceStarted()
        fun onAvoidanceEnded(userAdjustedFrequency: Double)
        fun onPttStateChanged(isTransmitting: Boolean)
    }

    // 状态
    private var isAvoiding = false
    private var wasInPttTransmit = false
    private var lastKnownFrequency = 0.0
    private var lastUserFrequency = 0.0
    private var lastCommandSentTime = 0L

    // 协程任务
    private var userActivityJob: Job? = null
    private var maxWaitJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // 监听器
    var listener: AvoidanceListener? = null

    /**
     * 处理频率广播
     * @param frequencyHz 当前频率（Hz）
     * @param isVfoA 是否为VFO A（接收）
     */
    fun onFrequencyBroadcast(frequencyHz: Double, isVfoA: Boolean = true) {
        if (!isVfoA) return // 只处理VFO A的频率

        val now = System.currentTimeMillis()

        // 忽略命令发送后的广播（避免自己发送的命令触发避让）
        if (now - lastCommandSentTime < COMMAND_IGNORE_WINDOW_MS) {
            lastKnownFrequency = frequencyHz
            return
        }

        // 首次初始化
        if (lastKnownFrequency == 0.0) {
            lastKnownFrequency = frequencyHz
            return
        }

        // 计算频率变化
        val frequencyDelta = kotlin.math.abs(frequencyHz - lastKnownFrequency)

        // PTT检测：频率跳变超过100MHz
        if (frequencyDelta >= PTT_FREQUENCY_JUMP_THRESHOLD_HZ) {
            handlePttFrequencyJump(frequencyHz)
            return
        }

        // PTT发射期间不检测避让
        if (wasInPttTransmit) {
            LogManager.d(TAG, "【PTT发射期间】关闭避让检测")
            lastKnownFrequency = frequencyHz
            return
        }

        // 检测用户操作
        if (frequencyDelta >= FREQUENCY_CHANGE_THRESHOLD_HZ) {
            LogManager.i(TAG, "检测到频率变化 ${frequencyDelta.toInt()}Hz，启动避让")
            lastUserFrequency = frequencyHz
            lastKnownFrequency = frequencyHz
            startAvoidance()
            return
        }

        lastKnownFrequency = frequencyHz
    }

    /**
     * 处理PTT频率跳变
     */
    private fun handlePttFrequencyJump(currentFrequency: Double) {
        if (!wasInPttTransmit) {
            // PTT开始发射
            LogManager.i(TAG, "【跳频检测】频率跳变，检测到PTT开始发射")
            wasInPttTransmit = true

            // PTT发射时立即停止避让
            if (isAvoiding) {
                manualEndAvoidance()
            }

            listener?.onPttStateChanged(true)
        } else {
            // PTT停止发射
            LogManager.i(TAG, "【跳频检测】频率跳变，检测到PTT停止发射")
            wasInPttTransmit = false
            listener?.onPttStateChanged(false)
        }

        lastKnownFrequency = currentFrequency
    }

    /**
     * 开始避让
     */
    private fun startAvoidance() {
        if (isAvoiding) {
            // 已经在避让中，重置用户活动检测和最大等待计时器
            // 这样用户持续操作时不会触发强制接管
            resetUserActivityCheck()
            resetMaxWaitTimer()
            return
        }

        isAvoiding = true
        LogManager.i(TAG, "【避让开始】暂停自动跟踪，等待用户调整完成...")
        listener?.onAvoidanceStarted()

        startUserActivityCheck()
        startMaxWaitTimer()
    }

    /**
     * 启动用户活动检测
     */
    private fun startUserActivityCheck() {
        userActivityJob?.cancel()
        userActivityJob = scope.launch {
            delay(USER_ACTIVITY_TIMEOUT_MS)
            // 用户停止操作，结束避让
            endAvoidance()
        }
    }

    /**
     * 重置用户活动检测
     */
    private fun resetUserActivityCheck() {
        userActivityJob?.cancel()
        userActivityJob = scope.launch {
            delay(USER_ACTIVITY_TIMEOUT_MS)
            endAvoidance()
        }
    }

    /**
     * 启动最大等待计时器
     */
    private fun startMaxWaitTimer() {
        maxWaitJob?.cancel()
        maxWaitJob = scope.launch {
            delay(MAX_AVOIDANCE_TIME_MS)
            // 达到最大等待时间，强制结束避让
            LogManager.w(TAG, "【避让超时】达到最大等待时间${MAX_AVOIDANCE_TIME_MS}ms，强制恢复控制")
            endAvoidance()
        }
    }

    /**
     * 重置最大等待计时器
     * 用户持续操作时重置，避免强制接管
     */
    private fun resetMaxWaitTimer() {
        maxWaitJob?.cancel()
        maxWaitJob = scope.launch {
            delay(MAX_AVOIDANCE_TIME_MS)
            // 达到最大等待时间，强制结束避让
            LogManager.w(TAG, "【避让超时】达到最大等待时间${MAX_AVOIDANCE_TIME_MS}ms，强制恢复控制")
            endAvoidance()
        }
    }

    /**
     * 结束避让
     */
    private fun endAvoidance() {
        if (!isAvoiding) return

        isAvoiding = false
        userActivityJob?.cancel()
        maxWaitJob?.cancel()

        LogManager.i(TAG, "【避让结束】恢复自动控制，用户频率: ${formatFreq(lastUserFrequency)}")
        listener?.onAvoidanceEnded(lastUserFrequency)
    }

    /**
     * 手动结束避让（用于PTT等情况）
     */
    fun manualEndAvoidance() {
        if (!isAvoiding) return

        isAvoiding = false
        userActivityJob?.cancel()
        maxWaitJob?.cancel()

        LogManager.i(TAG, "【避让手动结束】恢复自动控制")
        listener?.onAvoidanceEnded(lastUserFrequency)
    }

    /**
     * 通知命令已发送
     */
    fun notifyCommandSent() {
        lastCommandSentTime = System.currentTimeMillis()
    }

    /**
     * 检查是否正在避让
     */
    fun isAvoiding(): Boolean = isAvoiding

    /**
     * 检查是否正在PTT发射
     */
    fun isInPttTransmit(): Boolean = wasInPttTransmit

    /**
     * 获取用户调整后的频率
     */
    fun getUserAdjustedFrequency(): Double = lastUserFrequency

    /**
     * 重置状态
     */
    fun reset() {
        isAvoiding = false
        wasInPttTransmit = false
        lastKnownFrequency = 0.0
        lastUserFrequency = 0.0
        lastCommandSentTime = 0L
        userActivityJob?.cancel()
        maxWaitJob?.cancel()
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        reset()
        scope.cancel()
    }

    /**
     * 格式化频率显示
     */
    private fun formatFreq(freqHz: Double): String {
        return String.format("%.6f MHz", freqHz / 1e6)
    }
}
