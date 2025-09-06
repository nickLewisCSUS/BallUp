package com.nicklewis.ballup.map

import androidx.lifecycle.ViewModel
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.maps.model.LatLng

class MapCameraVM : ViewModel() {
    var center = mutableStateOf<LatLng?>(null)
    var zoom   = mutableStateOf<Float?>(null)
}