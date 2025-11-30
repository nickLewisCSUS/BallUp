package com.nicklewis.ballup.ui.map

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.android.gms.maps.model.LatLng

class MapCameraVM : ViewModel() {
    var center = mutableStateOf<LatLng?>(null)
    var zoom   = mutableStateOf<Float?>(null)
}