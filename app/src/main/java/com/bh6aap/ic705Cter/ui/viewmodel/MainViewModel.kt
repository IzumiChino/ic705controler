package com.bh6aap.ic705Cter.ui.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bh6aap.ic705Cter.data.api.TleDataManager
import com.bh6aap.ic705Cter.data.api.TransmitterDataManager
import com.bh6aap.ic705Cter.data.database.DatabaseHelper
import com.bh6aap.ic705Cter.data.location.GpsManager
import com.bh6aap.ic705Cter.data.radio.BluetoothConnectionManager
import com.bh6aap.ic705Cter.data.radio.BluetoothDevicePreference
import com.bh6aap.ic705Cter.ui.state.BluetoothConnectionState
import com.bh6aap.ic705Cter.ui.state.MainEffect
import com.bh6aap.ic705Cter.ui.state.MainEvent
import com.bh6aap.ic705Cter.ui.state.MainUiState
import com.bh6aap.ic705Cter.ui.state.VfoData
import com.bh6aap.ic705Cter.ui.state.toUiState
import com.bh6aap.ic705Cter.util.LogManager
import com.bh6aap.ic705Cter.util.MaidenheadConverter
import com.bh6aap.ic705Cter.util.Result
import com.bh6aap.ic705Cter.util.safeExecuteAsync
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 主界面ViewModel
 * 管理主界面的所有业务逻辑和状态
 */
class MainViewModel(
    application: Application,
    private val bluetoothManager: BluetoothConnectionManager = BluetoothConnectionManager.getInstance(),
    private val gpsManager: GpsManager = GpsManager(application),
    private val tleDataManager: TleDataManager = TleDataManager(application),
    private val transmitterDataManager: TransmitterDataManager = TransmitterDataManager(application),
    private val dbHelper: DatabaseHelper = DatabaseHelper.getInstance(application),
    private val devicePreference: BluetoothDevicePreference = BluetoothDevicePreference.getInstance(application)
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val GPS_TIMEOUT_MS = 5000L
    }

    // UI状态
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    // 副作用通道（一次性事件）
    private val _effectChannel = Channel<MainEffect>(Channel.BUFFERED)
    val effectFlow = _effectChannel.receiveAsFlow()

    // 最后连接的设备
    private var lastConnectedDevice: BluetoothDevice? = null

    init {
        initializeData()
        observeBluetoothState()
        observeVfoData()
    }

    /**
     * 初始化数据
     */
    private fun initializeData() {
        viewModelScope.launch {
            loadStationData()
            loadUpdateTimes()
        }
    }

    /**
     * 观察蓝牙连接状态
     */
    private fun observeBluetoothState() {
        viewModelScope.launch {
            bluetoothManager.connectionState.collect { state ->
                _uiState.update {
                    it.copy(
                        connectionState = state.toUiState(),
                        isConnecting = state == com.bh6aap.ic705Cter.data.radio.BluetoothSppConnector.ConnectionState.CONNECTING
                    )
                }
            }
        }
    }

    /**
     * 观察VFO数据
     */
    private fun observeVfoData() {
        viewModelScope.launch {
            combine(
                bluetoothManager.vfoAFrequency,
                bluetoothManager.vfoAMode,
                bluetoothManager.vfoBFrequency,
                bluetoothManager.vfoBMode
            ) { vfoAFreq, vfoAMode, vfoBFreq, vfoBMode ->
                VfoData(
                    vfoAFrequency = vfoAFreq,
                    vfoAMode = vfoAMode,
                    vfoBFrequency = vfoBFreq,
                    vfoBMode = vfoBMode
                )
            }.collect { vfoData ->
                _uiState.update { it.copy(vfoData = vfoData) }
            }
        }
    }

    /**
     * 处理UI事件
     */
    fun onEvent(event: MainEvent) {
        when (event) {
            is MainEvent.OnBluetoothCardClick -> handleBluetoothCardClick()
            is MainEvent.OnBluetoothCardLongClick -> handleBluetoothCardLongClick()
            is MainEvent.OnBluetoothDialogDismiss -> hideBluetoothDialog()
            is MainEvent.OnBluetoothDeviceSelected -> connectBluetoothDevice(event.deviceAddress)
            is MainEvent.OnGpsRefreshClick -> refreshGpsLocation()
            is MainEvent.OnSettingsClick -> showSettingsDialog()
            is MainEvent.OnSettingsDialogDismiss -> hideSettingsDialog()
            is MainEvent.OnTleUpdateClick -> updateTleData()
            is MainEvent.OnTransmitterUpdateClick -> updateTransmitterData()
            is MainEvent.OnErrorDismiss -> clearError()
        }
    }

    /**
     * 处理蓝牙卡片点击
     */
    private fun handleBluetoothCardClick() {
        when (_uiState.value.connectionState) {
            BluetoothConnectionState.CONNECTED -> disconnectBluetooth()
            BluetoothConnectionState.DISCONNECTED -> connectDefaultDevice()
            else -> { /* 其他状态不处理 */ }
        }
    }

    /**
     * 处理蓝牙卡片长按
     */
    private fun handleBluetoothCardLongClick() {
        _uiState.update { it.copy(showBluetoothDialog = true) }
    }

    /**
     * 连接默认蓝牙设备
     */
    private fun connectDefaultDevice() {
        val defaultAddress = devicePreference.getDefaultDeviceAddress()
        if (defaultAddress != null) {
            connectBluetoothDevice(defaultAddress)
        } else {
            _uiState.update { it.copy(showBluetoothDialog = true) }
        }
    }

    /**
     * 连接蓝牙设备
     */
    private fun connectBluetoothDevice(deviceAddress: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isConnecting = true) }

            val result = safeExecuteAsync("蓝牙连接") {
                val bluetoothAdapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                val device = pairedDevices.find { it.address == deviceAddress }
                    ?: throw IllegalArgumentException("设备未找到")

                bluetoothManager.connectToDevice(device)
            }

            _uiState.update { it.copy(isConnecting = false) }

            when (result) {
                is Result.Success -> {
                    _effectChannel.send(MainEffect.ShowToast("蓝牙连接成功"))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                    _effectChannel.send(MainEffect.ShowToast(result.message))
                }
                else -> {}
            }
        }
    }

    /**
     * 断开蓝牙连接
     */
    private fun disconnectBluetooth() {
        viewModelScope.launch {
            bluetoothManager.disconnect()
            lastConnectedDevice = null
            _effectChannel.trySend(MainEffect.ShowToast("已断开连接"))
        }
    }

    /**
     * 隐藏蓝牙对话框
     */
    private fun hideBluetoothDialog() {
        _uiState.update { it.copy(showBluetoothDialog = false) }
    }

    /**
     * 刷新GPS位置
     */
    private fun refreshGpsLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _effectChannel.send(MainEffect.ShowToast("正在等待GPS定位..."))

            val result = safeExecuteAsync("GPS定位") {
                getLocationWithTimeout()
            }

            _uiState.update { it.copy(isLoading = false) }

            when (result) {
                is Result.Success -> {
                    val location = result.data
                    if (location != null) {
                        saveLocationToDatabase(location)
                        loadStationData()
                        _effectChannel.send(MainEffect.ShowToast("GPS定位成功"))
                    } else {
                        _uiState.update { it.copy(errorMessage = "无法获取位置信息") }
                        _effectChannel.send(MainEffect.ShowToast("GPS定位失败，请检查定位服务"))
                    }
                }
                is Result.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                    _effectChannel.send(MainEffect.ShowToast(result.message))
                }
                else -> {}
            }
        }
    }

    /**
     * 获取位置（带超时）
     */
    private suspend fun getLocationWithTimeout(): android.location.Location? {
        return try {
            withTimeoutOrNull(GPS_TIMEOUT_MS) {
                gpsManager.getCurrentLocationModern(timeoutMs = GPS_TIMEOUT_MS)
            } ?: gpsManager.getLastKnownLocation()
        } catch (e: Exception) {
            LogManager.w(TAG, "GPS定位失败: ${e.message}")
            null
        }
    }

    /**
     * 保存位置到数据库
     */
    private suspend fun saveLocationToDatabase(location: android.location.Location) {
        withContext(Dispatchers.IO) {
            val station = com.bh6aap.ic705Cter.data.database.entity.StationEntity(
                name = "当前位置",
                latitude = location.latitude,
                longitude = location.longitude,
                altitude = location.altitude,
                isDefault = true,
                notes = "精度: ${location.accuracy}米, 提供者: ${location.provider}"
            )

            dbHelper.clearDefaultStation()
            dbHelper.insertStation(station)
        }
    }

    /**
     * 加载地面站数据
     */
    private suspend fun loadStationData() {
        val result = safeExecuteAsync("加载地面站数据") {
            withContext(Dispatchers.IO) {
                dbHelper.getDefaultStation()
            }
        }

        when (result) {
            is Result.Success -> {
                val station = result.data
                val grid = if (station != null) {
                    MaidenheadConverter.latLonToMaidenhead(
                        station.latitude,
                        station.longitude,
                        precision = 8
                    )
                } else ""

                _uiState.update {
                    it.copy(
                        stationData = station,
                        maidenheadGrid = grid
                    )
                }
            }
            is Result.Error -> {
                LogManager.e(TAG, result.message)
            }
            else -> {}
        }
    }

    /**
     * 加载更新时间
     */
    private suspend fun loadUpdateTimes() {
        // 加载TLE更新时间
        val tleResult = safeExecuteAsync("加载TLE更新时间") {
            withContext(Dispatchers.IO) {
                val record = dbHelper.getLastSyncRecord("tle_satnogs")
                record?.let {
                    SimpleDateFormat("yyyy/MM/dd-HH:mm:ss", Locale.getDefault())
                        .format(Date(it.syncTime))
                } ?: "尚未更新"
            }
        }

        // 加载转发器更新时间
        val transmitterResult = safeExecuteAsync("加载转发器更新时间") {
            withContext(Dispatchers.IO) {
                transmitterDataManager.getLastUpdateTime()
            }
        }

        _uiState.update {
            it.copy(
                tleUpdateTime = (tleResult as? Result.Success)?.data ?: "尚未更新",
                transmitterUpdateTime = (transmitterResult as? Result.Success)?.data ?: "尚未更新"
            )
        }
    }

    /**
     * 更新TLE数据
     */
    private fun updateTleData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _effectChannel.send(MainEffect.ShowToast("正在更新TLE数据..."))

            val result = safeExecuteAsync("TLE数据更新") {
                tleDataManager.fetchTleData(forceRefresh = true)
            }

            _uiState.update { it.copy(isLoading = false) }

            when (result) {
                is Result.Success -> {
                    loadUpdateTimes()
                    _effectChannel.send(MainEffect.ShowToast("TLE数据更新成功"))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                    _effectChannel.send(MainEffect.ShowToast(result.message))
                }
                else -> {}
            }
        }
    }

    /**
     * 更新转发器数据
     */
    private fun updateTransmitterData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            _effectChannel.send(MainEffect.ShowToast("正在更新转发器数据..."))

            val result = safeExecuteAsync("转发器数据更新") {
                transmitterDataManager.updateTransmitters()
            }

            _uiState.update { it.copy(isLoading = false) }

            when (result) {
                is Result.Success -> {
                    loadUpdateTimes()
                    _effectChannel.send(MainEffect.ShowToast("转发器数据更新成功"))
                }
                is Result.Error -> {
                    _uiState.update { it.copy(errorMessage = result.message) }
                    _effectChannel.send(MainEffect.ShowToast(result.message))
                }
                else -> {}
            }
        }
    }

    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    /**
     * 隐藏设置对话框
     */
    private fun hideSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    /**
     * 清除错误信息
     */
    private fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    override fun onCleared() {
        super.onCleared()
        _effectChannel.close()
    }
}
