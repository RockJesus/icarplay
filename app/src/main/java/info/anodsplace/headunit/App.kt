package info.anodsplace.headunit

import android.app.Application
import androidx.core.content.ContextCompat
import android.content.Context
import info.anodsplace.headunit.utils.AppLog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import java.io.File

open class App : Application() {

    private val component: AppComponent by lazy {
        AppComponent(this)
    }

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            if (nm != null) {
                nm.createNotificationChannel(
                    NotificationChannel(defaultChannel, "Android Auto", NotificationManager.IMPORTANCE_LOW)
                )
            }
        }

        ContextCompat.registerReceiver(
            this,
            AapBroadcastReceiver(),
            AapBroadcastReceiver.filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    companion object {
        const val defaultChannel = "default"

        @JvmStatic
        fun get(context: Context): App {
            return context.applicationContext as App
        }
        @JvmStatic
        fun provide(context: Context): AppComponent {
            return get(context).component
        }
    }
}
