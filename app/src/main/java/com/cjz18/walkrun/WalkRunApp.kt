package com.cjz18.walkrun

import android.app.Application
import android.content.Context
import com.amap.api.location.AMapLocationClient
import com.amap.api.maps.MapsInitializer
import com.cjz18.walkrun.data.AppDatabase
import com.cjz18.walkrun.data.TrackRepository

class WalkRunApp : Application() {
    lateinit var repository: TrackRepository
        private set

    override fun onCreate() {
        super.onCreate()
        acknowledgeAmapPrivacy(this)
        repository = TrackRepository(
            database = AppDatabase.create(this),
            prefs = getSharedPreferences(PREFS, MODE_PRIVATE),
        )
    }

    companion object {
        private const val PREFS = "walkrun"

        /**
         * 高德要求在任何地图/定位接口之前调用隐私接口，否则底图和定位不可用。
         * v0 在进程启动时同意，这样空闲页可以直接铺地图，且不会因此弹出定位权限。
         */
        fun acknowledgeAmapPrivacy(context: Context) {
            val appContext = context.applicationContext
            MapsInitializer.updatePrivacyShow(appContext, true, true)
            MapsInitializer.updatePrivacyAgree(appContext, true)
            AMapLocationClient.updatePrivacyShow(appContext, true, true)
            AMapLocationClient.updatePrivacyAgree(appContext, true)
        }
    }
}
