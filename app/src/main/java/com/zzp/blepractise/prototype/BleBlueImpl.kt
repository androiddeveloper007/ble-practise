package com.zzp.blepractise.prototype

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*

/**
 * describe：专门给低功耗蓝牙的
 */
data class BleData(val dev: BluetoothDevice, val scanRecord: String? = null)
typealias BleDevListener = (BleData) -> Unit

//我没有意识到用于服务和特性的UUID，仔细观察后发现，我只使用了特性（读取和写入）的UUID。
//将服务的UUID从2b10 / 2b11更改为2b00解决了此问题。
@RequiresApi(Build.VERSION_CODES.M)
object BleBlueImpl {
    val UUID_SERVICE = UUID.fromString("10000000-0000-0000-0000-000000000000")
    val UUID_READ_NOTIFY = UUID.fromString("11000000-0000-0000-0000-000000000000")
    val UUID_WRITE = UUID.fromString("12000000-0000-0000-0000-000000000000")
    private var isScanning = false
    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var devCallback: BleDevListener? = null

    fun scanDev(callback: BleDevListener) {
        devCallback = callback
        if (isScanning) {
            return
        }
        //扫描设置
        val builder = ScanSettings.Builder()
            /**
             * 三种模式
             * - SCAN_MODE_LOW_POWER : 低功耗模式，默认此模式，如果应用不在前台，则强制此模式
             * - SCAN_MODE_BALANCED ： 平衡模式，一定频率下返回结果
             * - SCAN_MODE_LOW_LATENCY 高功耗模式，建议应用在前台才使用此模式
             */
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)//高功耗，应用在前台
        /**
         * 三种回调模式
         * - CALLBACK_TYPE_ALL_MATCHED : 寻找符合过滤条件的广播，如果没有，则返回全部广播
         * - CALLBACK_TYPE_FIRST_MATCH : 仅筛选匹配第一个广播包出发结果回调的
         * - CALLBACK_TYPE_MATCH_LOST : 这个看英文文档吧，不满足第一个条件的时候，不好解释
         */
        builder.setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
        //判断手机蓝牙芯片是否支持皮批处理扫描
        if (bluetoothAdapter.isOffloadedFilteringSupported) {
            builder.setReportDelay(0L)
        }
        isScanning = true
        //扫描是很耗电的，所以，我们不能持续扫描
//        handler.postDelayed({
//            bluetoothAdapter.bluetoothLeScanner?.stopScan(scanListener)
//            isScanning = false;
//        }, 5000)
        bluetoothAdapter.bluetoothLeScanner?.startScan(null, builder.build(),
            scanListener
        )
    }

    private val scanListener = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            //不断回调，所以不建议做复杂的动作
            result ?: return
            result.device.name ?: return
            val bean = BleData(
                result.device,
                result.scanRecord.toString()
            )
            devCallback?.let {
                it(bean)
            }
        }
    }

    fun stopScan(){
        bluetoothAdapter.bluetoothLeScanner?.stopScan(
            scanListener
        )
    }
}