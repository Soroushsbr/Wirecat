package com.soroush.wirecat.data

import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

class NetworkStatsHelper(private val context: Context) {

    private val packageManager = context.packageManager
    private val statsManager =
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as? NetworkStatsManager
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    data class AppInfo(val packageName: String, val uid: Int, val label: String)

    fun installedApps(): List<AppInfo> {
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchablePackages = packageManager.queryIntentActivities(launcherIntent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .toHashSet()

        val apps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        val seenUids = HashSet<Int>()
        val result = ArrayList<AppInfo>()
        for (app: ApplicationInfo in apps) {
            if (app.packageName == context.packageName) continue
            val isSystemApp = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystemApp = (app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            val hasLauncherEntry = launchablePackages.contains(app.packageName)

            if (!hasLauncherEntry && isSystemApp && !isUpdatedSystemApp) continue // skip system apps with no visible UI
            if (!seenUids.add(app.uid)) continue
            val label = packageManager.getApplicationLabel(app).toString()
            result.add(AppInfo(app.packageName, app.uid, label))
        }
        return result
    }

    fun hasWorkingNetwork(): Boolean {
        val cm = connectivityManager ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun queryUsageSince(sinceMillis: Long): Map<Int, Long> {
        val manager = statsManager ?: return emptyMap()
        val now = System.currentTimeMillis()
        if (now <= sinceMillis) return emptyMap()
        val result = HashMap<Int, Long>()
        queryOneTransport(manager, ConnectivityManager.TYPE_WIFI, sinceMillis, now, result)
        queryOneTransport(manager, ConnectivityManager.TYPE_MOBILE, sinceMillis, now, result)
        return result
    }

    private fun queryOneTransport(
        manager: NetworkStatsManager,
        networkType: Int,
        start: Long,
        end: Long,
        into: MutableMap<Int, Long>
    ) {

        var stats: NetworkStats? = null
        try {
            stats = manager.querySummary(networkType, null, start, end) // requires PACKAGE_USAGE_STATS
            if (stats == null) return
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket()) {
                stats.getNextBucket(bucket)
                val uid = bucket.uid
                val bytes = bucket.rxBytes + bucket.txBytes
                into[uid] = (into[uid] ?: 0L) + bytes
            }
        } catch (e: SecurityException) {
            // usage-access permission not granted yet - expected until the user grants it
        } catch (e: Exception) {

        } finally {

            stats?.close()
        }
    }
}
