package com.acrcloud.acrclouduniversalsdkdemo;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class MainViewModel extends ViewModel {
    private final MutableLiveData<MainActivity.AudioEngineType> _engineTypeMutableLiveData = new MutableLiveData<>();
    LiveData<MainActivity.AudioEngineType> engineTypeLiveData = _engineTypeMutableLiveData;

    private final MutableLiveData<Boolean> _isStartRecognizing = new MutableLiveData<>();
    LiveData<Boolean> isStartRecognizing = _isStartRecognizing;

    public void setEngineType(MainActivity.AudioEngineType type) {
        _engineTypeMutableLiveData.setValue(type);
    }

    public void setRecognizing(Boolean isRecognizing) {
        _isStartRecognizing.setValue(isRecognizing);
    }
}
