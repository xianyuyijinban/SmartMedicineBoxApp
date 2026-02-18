package com.smartmedicine

import android.app.Application
import timber.log.Timber

/**
 * 智能药箱应用入口
 */
class SmartMedicineApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // 初始化日志
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        
        Timber.d("SmartMedicineApp 初始化完成")
    }
}
