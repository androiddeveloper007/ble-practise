package com.zzp.blepractise.prototype

import android.app.Activity
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.util.*

/**
 * author zp.zhu
 * 一次请求所有权限
 */
class PermissionsTool(private val act: Activity) {
    //获取所有权限
    private fun retrievePermissions(): Array<String> {
        return try {
            val requestedPermissions =act.packageManager.getPackageInfo(act.packageName, PackageManager.GET_PERMISSIONS
                ).requestedPermissions
            Log.d(TAG,Arrays.toString(requestedPermissions))
            requestedPermissions
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException("This should have never happened.", e)
        }
    }

    //一次请求所有权限
    fun requestPermissions() {
        val permissions = retrievePermissions()
        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(act, permission)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(act, permissions, 0)
            }
        }
    }

    companion object {
        var TAG = "PermissionsTool"
    }
}