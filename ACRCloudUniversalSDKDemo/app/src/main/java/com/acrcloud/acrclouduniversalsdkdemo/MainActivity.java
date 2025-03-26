package com.acrcloud.acrclouduniversalsdkdemo;

import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import com.acrcloud.acrclouduniversalsdkdemo.databinding.ActivityMainBinding;
import com.acrcloud.rec.ACRCloudClient;
import com.acrcloud.rec.ACRCloudConfig;
import com.acrcloud.rec.ACRCloudResult;
import com.acrcloud.rec.IACRCloudListener;
import com.acrcloud.rec.IACRCloudPartnerDeviceInfo;
import com.acrcloud.rec.IACRCloudRadioMetadataListener;
import com.acrcloud.rec.utils.ACRCloudLogger;
import com.acrcloud.rec.utils.ACRCloudUtils;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.gson.Gson;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity implements IACRCloudListener, IACRCloudRadioMetadataListener {

    private final static String TAG = "MainActivity";

    private boolean mProcessing = false;
    private boolean mAutoRecognizing = false;
    private boolean initState = false;
    private String path = "";
    private long startTime = 0;

    private ACRCloudConfig mConfig = null;
    private ACRCloudClient mClient = null;

    private androidx.media3.ui.PlayerView playerView = null;
    private ExoPlayer exoPlayer = null;

    private ActivityMainBinding binding;
    private MainViewModel viewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        EdgeToEdge.enable(this);
        super.onCreate(savedInstanceState);
        //setContentView(R.layout.activity_main);
        binding = ActivityMainBinding.inflate(LayoutInflater.from(this));
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        path = getExternalCacheDir().toString()
                + "/acrcloud";
        Log.d(TAG, "path: [" + path + "]");

        File file = new File(path);
        if (!file.exists()) {
            file.mkdirs();
        }

        ACRCloudLogger.setLog(true);

        initPlayer();
        initAudioEngineSelector();
        initCtlView();
        verifyPermissions();
        bindVM();
        viewModel.setEngineType(AudioEngineType.LineInAudio);

        ViewCompat.setOnApplyWindowInsetsListener(binding.container, (v, insets) -> {
            Insets bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars()
                            | WindowInsetsCompat.Type.displayCutout()
            );
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void bindVM() {
        viewModel.engineTypeLiveData.observe(this, this::initAudioClient);
        viewModel.isStartRecognizing.observe(this, isRecognizing -> {
            if (isRecognizing) {
                start();
                binding.cancel.setEnabled(true);
                binding.start.setEnabled(false);
            } else {
                cancel();
                binding.cancel.setEnabled(false);
                binding.start.setEnabled(true);
            }
        });
    }

    private void initAudioEngineSelector() {
        binding.audioEngineToggleGroup.addOnButtonCheckedListener(new MaterialButtonToggleGroup.OnButtonCheckedListener() {
            @Override
            public void onButtonChecked(MaterialButtonToggleGroup group, int checkedId, boolean isChecked) {
                if (isChecked) {
                    switch (checkedId) {
                        case R.id.record_audio_button:
                            initAudioClient(AudioEngineType.RecordAudio);
                            break;
                        case R.id.line_in_audio_button:
                            initAudioClient(AudioEngineType.LineInAudio);
                            break;
                    }
                }
            }
        });
    }

    private void initCtlView() {
        binding.start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                mTres = "\n";
                viewModel.setRecognizing(true);
            }
        });

        binding.cancel.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        viewModel.setRecognizing(false);
                    }
                });

        findViewById(R.id.request_radio_meta).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View arg0) {
                requestRadioMetadata();
            }
        });

        binding.autoSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                if (isChecked) {
                    openAutoRecognize();
                } else {
                    closeAutoRecognize();
                }
            }
        });
    }

    private void initAudioClient(AudioEngineType type) {
        this.mConfig = new ACRCloudConfig();

        this.mConfig.acrcloudListener = this;
        this.mConfig.context = this;

        // Please create project in "http://console.acrcloud.cn/service/avr".
        this.mConfig.host = "identify-ap-southeast-1.acrcloud.com";
        this.mConfig.accessKey = type.accessKey;
        this.mConfig.accessSecret = type.accessSecret;

        // auto recognize access key
        this.mConfig.hostAuto = "";
        this.mConfig.accessKeyAuto = "";
        this.mConfig.accessSecretAuto = "";

        this.mConfig.recorderConfig.rate = 8000;
        this.mConfig.recorderConfig.channels = 1;
        this.mConfig.sessionAutoCancel = false;
        //this.mConfig.recorderType = ACRCloudConfig.RecorderType.LINEIN;

        this.mConfig.acrcloudPartnerDeviceInfo = new IACRCloudPartnerDeviceInfo() {
            @Override
            public String getGPS() {
                return null;
            }

            @Override
            public String getRadioFrequency() {
                return null;
            }

            @Override
            public String getDeviceId() {
                return "";
            }

            @Override
            public String getDeviceModel() {
                return null;
            }
        };

        // If you do not need volume callback, you set it false.
        this.mConfig.recorderConfig.isVolumeCallback = true;

        this.mClient = new ACRCloudClient();
        ACRCloudLogger.setLog(true);

        this.initState = this.mClient.initWithConfig(this.mConfig);
    }

    private void initPlayer() {
        playerView = binding.videoView;
        exoPlayer = new ExoPlayer.Builder(this).build();

        AudioAttributes attributes = new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                //.setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build();
        exoPlayer.setAudioAttributes(attributes, false);

        playerView.setPlayer(exoPlayer);
    }

    public void playMedia() {
        try {
            //Uri videoUri = Uri.parse("android.resource://com.acrcloud.acrclouduniversalsdkdemo/raw/txvideo_20250304_114359");
            //Uri videoUri = Uri.parse("android.resource://com.acrcloud.acrclouduniversalsdkdemo/raw/txvideo_wasai_20250304_212056");
            //Uri videoUri = Uri.parse("https://video.wasai-app-dev.com/0d498fea-a67c-4371-ab28-fa7ad77856d1/hls/TXVideo_20250306_091718_b13954ad-9e33-498c-8d20-26febb4524f3_1080x1920p_qvbr.m3u8");
            Uri videoUri = Uri.parse("https://video.wasaiapp.com/0cf5ce7c-bfb6-4c3f-b813-e62cc70d7324/hls/TXVideo_20250322_131127_9b79f795-21ad-4e3f-8e35-4fe0f257b44e_1080x1920p_qvbr.m3u8");
            MediaItem mediaItem = new MediaItem.Builder().setUri(videoUri)
                    //.setMimeType(MimeTypes.APPLICATION_M3U8)
                    .build();
            exoPlayer.setMediaItem(mediaItem);
            exoPlayer.addListener(new Player.Listener() {
                @Override
                public void onPlayerError(PlaybackException error) {
                    Log.d("myDebug", "play error " + error.errorCode + " " + error.toString());
                }
            });
            exoPlayer.prepare();
            exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
            exoPlayer.play();

        } catch (Exception e) {
            Log.d("myDebug", "exception: " + e.toString());
        }

    }

    public void stopMedia() {
        try {
            Log.d("myDebug", "will stopMedia");
            exoPlayer.stop();
        } catch (Exception e) {
            Log.d("myDebug", "exception: " + e.toString());
        }
    }

    public void start() {
        playMedia();

        if (!this.initState) {
            Toast.makeText(this, "init error", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!mProcessing) {
            mProcessing = true;
            binding.volume.setText("");
            binding.result.setText("");
            if (this.mClient == null || !this.mClient.startRecognize()) {
                mProcessing = false;
                binding.result.setText("start error!");
            }
            startTime = System.currentTimeMillis();
        }
    }

    public void cancel() {
        stopMedia();
        if (mProcessing && this.mClient != null) {
            this.mClient.cancel();
        }

        this.reset();
    }

    public void openAutoRecognize() {
        String str = this.getString(R.string.suss);
        if (!mAutoRecognizing) {
            mAutoRecognizing = true;
            if (this.mClient == null || !this.mClient.runAutoRecognize()) {
                mAutoRecognizing = true;
                str = this.getString(R.string.error);
            }
        }
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    public void closeAutoRecognize() {
        String str = this.getString(R.string.suss);
        if (mAutoRecognizing) {
            mAutoRecognizing = false;
            this.mClient.cancelAutoRecognize();
            str = this.getString(R.string.error);
        }
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }

    // callback IACRCloudRadioMetadataListener
    public void requestRadioMetadata() {
        String lat = "39.98";
        String lng = "116.29";
        List<String> freq = new ArrayList<>();
        freq.add("88.7");
        if (!this.mClient.requestRadioMetadataAsyn(lat, lng, freq,
                ACRCloudConfig.RadioType.FM, this)) {
            String str = this.getString(R.string.error);
            Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
        }
    }

    public void reset() {
        binding.time.setText("");
        binding.result.setText("");
        mProcessing = false;
    }

    String mTres = "\n";
    int writeCount = 0;
    @Override
    public void onResult(ACRCloudResult results) {
        this.reset();

        // If you want to save the record audio data, you can refer to the following codes.
	    byte[] recordPcm = results.getRecordDataPCM();
        if (recordPcm != null && writeCount == 0) {
            byte[] recordWav = ACRCloudUtils.pcm2Wav(recordPcm, this.mConfig.recorderConfig.rate, this.mConfig.recorderConfig.channels);
            ACRCloudUtils.createFileWithByte(recordWav, path + "/" + "record_" + writeCount + ".wav");
            writeCount++;
        }

        String result = results.getResult();

        try {
//            JSONObject j = new JSONObject(result);
//            JSONObject j1 = j.getJSONObject("status");
//            int j2 = j1.getInt("code");
//            if (j2 == 0) {
//                JSONObject metadata = j.getJSONObject("metadata");
//                //
//                if (metadata.has("music")) {
//                    JSONArray musics = metadata.getJSONArray("music");
//                    for (int i = 0; i < musics.length(); i++) {
//                        JSONObject tt = (JSONObject) musics.get(i);
//                        String title = tt.getString("title");
//                        JSONArray artistt = tt.getJSONArray("artists");
//                        JSONObject art = (JSONObject) artistt.get(0);
//                        String artist = art.getString("name");
//                        tres = tres + (i + 1) + ".  Title: " + title + "    Artist: " + artist + "\n";
//                    }
//                }
//
//                tres = tres + "\n\n" + result;
//            } else {
//                tres = result;
//            }
            Gson gson = new Gson();
            MetaResponse response = gson.fromJson(result, MetaResponse.class);
            for(int count = 0; count < response.metadata.music.size(); count++){
                Music music = response.metadata.music.get(count);
                String item = music.album.name + "\n"
                        + "     play_offset_ms\t\t\t\t\t\t\t\t" + music.play_offset_ms + "\n"
                        + "     sample_begin_time_offset_ms\t" + music.sample_begin_time_offset_ms + "\n"
                        + "     sample_end_time_offset_ms\t\t" + music.sample_end_time_offset_ms + "\n"
                        + "     db_begin_time_offset_ms\t\t\t" + music.db_begin_time_offset_ms + "\n"
                        + "     db_end_time_offset_ms\t\t\t\t" + music.db_end_time_offset_ms + "\n";
                mTres = mTres + item;
            }
        } catch (Exception e) {
            mTres = mTres + "\n" + result;
            e.printStackTrace();
        }

        mTres = mTres + "\n" +"--------------------" + "\n";
        binding.result.setText(mTres);
        startTime = System.currentTimeMillis();
    }

    @Override
    public void onVolumeChanged(double volume) {
        long time = (System.currentTimeMillis() - startTime) / 1000;
        binding.volume.setText(getResources().getString(R.string.volume) + volume + "\n\nTime: " + time + " s");
    }

    private static final int REQUEST_PERMISSION = 1;
    private static String[] PERMISSIONS = {
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
    };

    public void verifyPermissions() {
        for (int i = 0; i < PERMISSIONS.length; i++) {
            int permission = ActivityCompat.checkSelfPermission(this, PERMISSIONS[i]);
            if (permission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS,
                        REQUEST_PERMISSION);
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        int count = permissions.length;
        for (int i = 0; i < count; i++){
            int grantResult = 99999;
            if (grantResults.length > i){
                grantResult = grantResults[i];
            }
            Log.d("MainActivity", "permission: " + permissions[i] + " grant result: " + grantResult);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        Log.e("MainActivity", "release");
        if (this.mClient != null) {
            this.mClient.release();
            this.initState = false;
            this.mClient = null;
        }
    }

    @Override
    public void onRadioMetadataResult(String s) {
        binding.result.setText(s);
    }

    public enum AudioEngineType {
        RecordAudio("c605448bdb08b68dcc538f4d721b3dbd", "UwhygsaZhu72tyvQKSkGUxd7tfZKsZznz5uXWsJm"),
        LineInAudio("", "");

        final String accessKey;
        final String accessSecret;

        AudioEngineType(String accessKey, String accessSecret) {
            this.accessKey = accessKey;
            this.accessSecret = accessSecret;
        }
    }
}
