package com.mobvoi.wear.firefighter.tool

import android.os.Build
import androidx.annotation.RequiresApi
import java.util.*

/**
 * 蓝牙常亮类
 *
 * Created by zp.zhu on 2020/11/20
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
object BleConst {
    val UUID_SERVICE = UUID.fromString("10000000-0000-0000-0000-000000000000")
    val UUID_READ_NOTIFY = UUID.fromString("11000000-0000-0000-0000-000000000000")
    val UUID_WRITE = UUID.fromString("12000000-0000-0000-0000-000000000000")
    val UUID_DESCRIBE = UUID.fromString("12000000-0000-0000-0000-000000000000")

    const val BLE_ACTION = "com.mobvoi.firefighter.ble" //蓝牙进出标记的广播action


}