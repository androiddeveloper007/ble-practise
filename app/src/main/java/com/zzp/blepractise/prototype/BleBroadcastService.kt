package com.zzp.blepractise.prototype

import android.app.Service
import android.bluetooth.*
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresApi
import com.mobvoi.wear.firefighter.tool.BleConst
import com.zzp.blepractise.R

/**
 * 蓝牙广播服务，用于检测手表有没有进入消防车范围
 *
 * Created by zp.zhu on 2020/11/20
 */
@RequiresApi(Build.VERSION_CODES.O)
class BleBroadcastService : Service() {
    private val TAG = javaClass.simpleName
    private var mBluetoothGattServer: BluetoothGattServer? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var receiver: BroadcastReceiver = MyReceiver()

    override fun onBind(intent: Intent?): IBinder? {
        return null;
    }

    override fun onCreate() {
        super.onCreate()
        NotificationTool.foregroundNotify(
            this,
            false,
            getString(R.string.ble_tip_title),
            getString(R.string.ble_tip_text),
            1002
        )
        initBle()
        registerReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    //添加系统蓝牙开关的广播监听，蓝牙关闭时stopAdvertise，开启时startAdvertise
    //离腕半小时后会关闭蓝牙。戴上后重启。
    private fun registerReceiver() {
        val filter = IntentFilter()
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        filter.addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
        registerReceiver(receiver, filter)
    }

    //每过一个小时重启一下service，弄懂离腕机制后就不用定时启动了。
    private fun startAlarm(intent:Intent?) {
        var fromReceiver = false;
        if(intent!=null){
            fromReceiver = intent.getBooleanExtra("fromReceiver", false)
            Log.e(TAG, "广播启动的方式 fromReceiver： $fromReceiver")
        }
    }

    private fun initBle() {
        val blueManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = blueManager.adapter
//        val watchSn = SharedWearInfoHelper.getInstance(this).deviceSerial
//        bluetoothAdapter?.name = "111"       //11111111111 FireFighter pro4g-$watchSn
        /**
         * GAP广播数据最长只能31个字节，包含两中： 广播数据和扫描回复
         * - 广播数据是必须的，外设需要不断发送广播，让中心设备知道
         * - 扫描回复是可选的，当中心设备扫描到才会扫描回复
         * 广播间隔越长，越省电
         */
        //广播设置
        val advSetting = AdvertiseSettings.Builder()
            //低延时，高功率，不使用后台
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)            //ADVERTISE_MODE_LOW_POWER
            // 高的发送功率
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)               //ADVERTISE_TX_POWER_ULTRA_LOW
            // 可连接
            .setConnectable(true)
            //广播时限。最多180000毫秒。值为0将禁用时间限制。（不设置则为无限广播时长）
            .setTimeout(0)
            .build()
        //设置广播包，这个是必须要设置的
        val advData = AdvertiseData.Builder()
            .setIncludeDeviceName(true) //显示名字
            .setIncludeTxPowerLevel(true)//设置功率
            .addServiceUuid(ParcelUuid(BleConst.UUID_SERVICE)) //设置 UUID 服务的 uuid
            .build()
        /**
         * GATT 使用了 ATT 协议，ATT 把 service 和 characteristic 对应的数据保存在一个查询表中，
         * 依次查找每一项的索引
         * BLE 设备通过 Service 和 Characteristic 进行通信
         * 外设只能被一个中心设备连接，一旦连接，就会停止广播，断开又会重新发送
         * 但中心设备同时可以和多个外设连接
         * 他们之间需要双向通信的话，唯一的方式就是建立 GATT 连接
         * 外设作为 GATT(server)，它维持了 ATT 的查找表以及service 和 charateristic 的定义
         */
        val bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        //开启广播,这个外设就开始发送广播了
        bluetoothLeAdvertiser?.startAdvertising(advSetting,advData,advertiseCallback)
        /**
         * characteristic 是最小的逻辑单元
         * 一个 characteristic 包含一个单一 value 变量 和 0-n个用来描述 characteristic 变量的
         * Descriptor。与 service 相似，每个 characteristic 用 16bit或者32bit的uuid作为标识
         * 实际的通信中，也是通过 Characteristic 进行读写通信的
         */
        //添加读+通知的 GattCharacteristic
        val readCharacteristic = BluetoothGattCharacteristic(
            BleConst.UUID_READ_NOTIFY,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )

        //添加写的 GattCharacteristic
        val writeCharacteristic = BluetoothGattCharacteristic(
            BleConst.UUID_WRITE,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        //添加 Descriptor 描述符
        val descriptor = BluetoothGattDescriptor(BleConst.UUID_DESCRIBE, BluetoothGattDescriptor.PERMISSION_WRITE )

        //为特征值添加描述
        writeCharacteristic.addDescriptor(descriptor)

        /**
         * 添加 Gatt service 用来通信
         */
        //开启广播service，这样才能通信，包含一个或多个 characteristic ，每个service 都有一个 uuid
        val gattService = BluetoothGattService(BleConst.UUID_SERVICE, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        gattService.addCharacteristic(readCharacteristic)
        gattService.addCharacteristic(writeCharacteristic)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        //打开 GATT 服务，方便客户端连接
        try {
            mBluetoothGattServer = bluetoothManager.openGattServer(this, gattServiceCallback)
            mBluetoothGattServer?.addService(gattService)
        } catch (e:Exception){
            e.printStackTrace()
            Log.e(TAG, "蓝牙异常，请重启设备")
        }
    }

    private val gattServiceCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            super.onConnectionStateChange(device, status, newState)
            device ?: return
            Log.d(TAG, "ble onConnectionStateChange: ")
            if (status == BluetoothGatt.GATT_SUCCESS && newState == 2) {
                Log.e(TAG, "连接到中心设备: ${device.name}")
            } else {
                Log.e(TAG, "与: ${device.name} 断开连接失败！")
                //TODO 切换日常模式
//                sendContentBroadcast(UploadHbPosService.MODE_DAILY)
            }
        }

        override fun onCharacteristicReadRequest(device: BluetoothDevice?, requestId: Int, offset: Int,
            characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
            //中心设备read时，回调
            val data = "this is a test from ble server"
            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data.toByteArray())
            Log.e(TAG, "客户端读取 [characteristic ${characteristic?.uuid}] $data")
        }

        override fun onCharacteristicWriteRequest(device: BluetoothDevice?, requestId: Int,
            characteristic: BluetoothGattCharacteristic?,preparedWrite: Boolean, responseNeeded: Boolean,
            offset: Int, value: ByteArray?) {
            super.onCharacteristicWriteRequest(device, requestId,characteristic, preparedWrite,responseNeeded, offset, value)
            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS,offset, value)
            value?.let {
                Log.e(TAG, "客户端写入 [characteristic ${characteristic?.uuid}] ${String(it)}")
                //成功的回调处
                //先判断uploadService是否在线
            }
        }

        override fun onDescriptorReadRequest(
            device: BluetoothDevice?, requestId: Int, offset: Int, descriptor: BluetoothGattDescriptor?) {
            super.onDescriptorReadRequest(device, requestId, offset, descriptor)
            val data = "this is a test"
            mBluetoothGattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, data.toByteArray())
            Log.e(TAG, "客户端读取 [descriptor ${descriptor?.uuid}] $data")
        }

        override fun onDescriptorWriteRequest(device: BluetoothDevice?,requestId: Int,
            descriptor: BluetoothGattDescriptor?,preparedWrite: Boolean,
            responseNeeded: Boolean,offset: Int,value: ByteArray?) {
            super.onDescriptorWriteRequest(device,requestId,descriptor,preparedWrite,responseNeeded,offset,value)
            value?.let {
                Log.e(TAG, "客户端写入 [descriptor ${descriptor?.uuid}] ${String(it)}")
                // 简单模拟通知客户端Characteristic变化
                Log.d(TAG, "ble onDescriptorWriteRequest: $value")
            }
        }
    }

    private fun startUploadDataService() {
//        val intent = Intent(this, UploadHbPosService::class.java)
//        startForegroundService(intent)
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            super.onStartSuccess(settingsInEffect)
            Log.w(TAG, "服务准备就绪，请搜索广播")
        }

        override fun onStartFailure(errorCode: Int) {
            super.onStartFailure(errorCode)
            if (errorCode == ADVERTISE_FAILED_DATA_TOO_LARGE) {
                Log.w(TAG, "广播数据超过31个字节了 !")
            } else {
                Log.w(TAG, "服务启动失败: $errorCode")
            }
        }
    }

    /**
     * 发送广播切换战时和日常模式
     * */
    fun sendContentBroadcast(isDaily: Int) {
//        val intent = Intent()
//        intent.action = UploadHbPosService.SWITCH_MODE_ACTION
//        intent.putExtra("mode", isDaily)
//        sendBroadcast(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdvertise()
    }

    private fun stopAdvertise() {
        if(mBluetoothGattServer!=null) {
            mBluetoothGattServer?.close()
        }
        if(bluetoothAdapter!=null && bluetoothAdapter?.bluetoothLeAdvertiser!=null) {
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        }
        bluetoothAdapter=null
        mBluetoothGattServer=null
    }

    /**
     * 离腕超过30分钟时，系统会重启蓝牙开关。广播接收
     */
    private inner class MyReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothAdapter.ACTION_STATE_CHANGED == action) {
                val preState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_OFF)
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF)
                Log.e(TAG,"BT state changed, %s -> %s")
                if (state == BluetoothAdapter.STATE_ON) {
                    initBle()           //重新开启广播
                } else if (state == BluetoothAdapter.STATE_OFF) {
                    stopAdvertise()//关闭ble广播
                }
            } else if (BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED == action) {
                val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothAdapter.ERROR)
                val stateLabel =
                    BluetoothHelper.connectionStateStr(state)
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                if (device == null) {
                    Log.e(TAG,"A2DP null device found for connection state change: $stateLabel")
                    return
                }
                Log.e(TAG, "A2DP device " + device.address +"("+device.name+")" + " connect state changed: " + stateLabel)
            }
        }
    }
}