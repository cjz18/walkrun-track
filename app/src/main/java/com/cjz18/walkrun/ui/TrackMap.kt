package com.cjz18.walkrun.ui

import android.os.Bundle
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.MapView
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.LatLngBounds
import com.amap.api.maps.model.Polyline
import com.amap.api.maps.model.PolylineOptions
import com.cjz18.walkrun.tracking.TrackPoint

enum class MapMode {
    Idle,
    Follow,
    Fit,
}

@Composable
fun TrackMap(
    points: List<TrackPoint>,
    mode: MapMode,
    sessionKey: Long,
    logoBottomDp: Int,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val mapView = remember {
        MapView(context).apply { onCreate(Bundle()) }
    }
    var polyline by remember { mutableStateOf<Polyline?>(null) }
    val userPanned = remember(sessionKey) { mutableStateOf(false) }

    AndroidView(
        modifier = modifier,
        factory = { mapView },
    )

    DisposableEffect(mapView, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            mapView.onPause()
            mapView.onDestroy()
            polyline = null
        }
    }

    LaunchedEffect(mapView, sessionKey) {
        mapView.map?.setOnMapTouchListener { userPanned.value = true }
    }

    LaunchedEffect(mapView, points, mode, sessionKey, logoBottomDp) {
        val map = mapView.map ?: return@LaunchedEffect
        val density = mapView.resources.displayMetrics.density
        map.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            setLogoBottomMargin((logoBottomDp * density).toInt())
        }
        val latLngs = points.map { LatLng(it.latitude, it.longitude) }
        polyline = updatePolyline(map, polyline, latLngs)
        moveCamera(map, mapView, latLngs, mode, userPanned.value)
    }
}

private fun updatePolyline(map: AMap, existing: Polyline?, latLngs: List<LatLng>): Polyline? {
    if (latLngs.size < 2) {
        existing?.remove()
        return null
    }
    if (existing == null) {
        return map.addPolyline(
            PolylineOptions()
                .addAll(latLngs)
                .width(16f)
                .color(android.graphics.Color.parseColor("#1B7F3A"))
                .geodesic(true),
        )
    }
    existing.points = latLngs
    return existing
}

private fun moveCamera(
    map: AMap,
    view: MapView,
    latLngs: List<LatLng>,
    mode: MapMode,
    userPanned: Boolean,
) {
    if (latLngs.isEmpty()) return
    try {
        when (mode) {
            MapMode.Idle -> Unit
            MapMode.Follow -> {
                if (!userPanned) {
                    map.animateCamera(CameraUpdateFactory.newLatLngZoom(latLngs.last(), 17f))
                }
            }
            MapMode.Fit -> {
                if (latLngs.size == 1) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLngs.first(), 16f))
                } else {
                    val bounds = LatLngBounds.builder().apply {
                        latLngs.forEach { include(it) }
                    }.build()
                    val metrics = view.resources.displayMetrics
                    val padding = (48 * metrics.density).toInt()
                    map.moveCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            bounds,
                            metrics.widthPixels,
                            metrics.heightPixels,
                            padding,
                        ),
                    )
                }
            }
        }
    } catch (error: Exception) {
        Log.w(TAG, "camera update failed", error)
    }
}

private const val TAG = "TrackMap"
