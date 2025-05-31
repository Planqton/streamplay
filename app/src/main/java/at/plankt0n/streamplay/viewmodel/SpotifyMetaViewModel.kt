package at.plankt0n.streamplay.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import at.plankt0n.streamplay.data.ExtendedMetaInfo

class SpotifyMetaViewModel : ViewModel() {

    private val _spotifyMetaInfo = MutableLiveData<ExtendedMetaInfo>()
    val spotifyMetaInfo: LiveData<ExtendedMetaInfo> get() = _spotifyMetaInfo

    fun updateMetaInfo(extendedInfo: ExtendedMetaInfo) {
        _spotifyMetaInfo.postValue(extendedInfo)
    }
}
