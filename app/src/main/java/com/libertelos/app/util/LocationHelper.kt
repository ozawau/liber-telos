package com.libertelos.app.util

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.coroutines.resume

data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val address: String = ""
)

object LocationHelper {
    fun hasPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    suspend fun currentOrLast(context: Context): GeoPoint? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )
        val last = providers
            .filter { manager.isProviderEnabled(it) || it == LocationManager.PASSIVE_PROVIDER }
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }

        val fresh = withTimeoutOrNull(2_500L) {
            currentOnce(manager)
        }
        val loc = fresh ?: last ?: return null
        val address = reverseGeocode(context, loc.latitude, loc.longitude).orEmpty()
        return GeoPoint(loc.latitude, loc.longitude, address)
    }

    suspend fun reverseGeocode(context: Context, latitude: Double, longitude: Double): String? {
        if (!Geocoder.isPresent()) return null
        return withContext(Dispatchers.IO) {
            runCatching {
                val geocoder = Geocoder(context.applicationContext, Locale.CHINA)
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { cont ->
                        geocoder.getFromLocation(latitude, longitude, 1) { list ->
                            if (cont.isActive) cont.resume(list.firstOrNull())
                        }
                    }
                } else {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                }
                address?.let { formatAddress(it) }?.takeIf { it.isNotBlank() }
            }.getOrNull()
        }
    }

    private fun formatAddress(address: Address): String {
        address.getAddressLine(0)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        return listOfNotNull(
            address.adminArea,
            address.locality ?: address.subAdminArea,
            address.subLocality,
            address.thoroughfare,
            address.subThoroughfare,
            address.featureName
        )
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString("")
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentOnce(manager: LocationManager): Location? {
        val provider = when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            else -> return null
        }
        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    provider,
                    signal,
                    Executors.newSingleThreadExecutor()
                ) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } else {
                @Suppress("DEPRECATION")
                val listener = object : android.location.LocationListener {
                    override fun onLocationChanged(location: Location) {
                        manager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                }
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(provider, listener, contextLooper())
            }
        }
    }

    private fun contextLooper() = android.os.Looper.getMainLooper()
}
