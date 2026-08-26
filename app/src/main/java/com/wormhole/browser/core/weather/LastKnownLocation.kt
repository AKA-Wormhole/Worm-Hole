package com.wormhole.browser.core.weather

import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.Manifest
import androidx.core.content.ContextCompat

data class SimpleLocation(val latitude: Double, val longitude: Double)

object LastKnownLocation {
    fun get(context: Context): SimpleLocation? {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null

        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )

        return runCatching {
            providers
                .mapNotNull { provider ->
                    if (manager.isProviderEnabled(provider)) manager.getLastKnownLocation(provider) else null
                }
                .maxByOrNull { it.time }
                ?.let { SimpleLocation(it.latitude, it.longitude) }
        }.getOrNull()
    }
}
