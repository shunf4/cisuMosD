package com.dirror.music.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.media.*
import android.net.Uri
import android.os.*
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.MutableLiveData
import com.dirror.music.MyApplication
import com.dirror.music.R
import com.dirror.music.broadcast.BecomingNoisyReceiver
import com.dirror.music.music.local.PlayHistory
import com.dirror.music.music.standard.SongPicture
import com.dirror.music.music.standard.data.*
import com.dirror.music.service.base.BaseMediaService
import com.dirror.music.ui.activity.MainActivity
import com.dirror.music.ui.activity.PlayerActivity
import com.dirror.music.util.*
import org.jetbrains.annotations.TestOnly

/**
 * 音乐服务
 * @author Moriafly
 * @since 2020/9
 */
class MusicService : BaseMediaService() {
    // 懒加载 musicBinder
    private val musicBinder by lazy { MusicController() }
    private var mode: Int = MyApplication.mmkv.decodeInt(Config.PLAY_MODE, MODE_CIRCLE)
    private var notificationManager: NotificationManager? = null // 通知管理
    private var isAudioFocus = MyApplication.mmkv.decodeBool(Config.ALLOW_AUDIO_FOCUS, true) // 是否开启音频焦点

    private var mediaSessionCallback: MediaSessionCompat.Callback? = null
    private var mediaSession: MediaSessionCompat? = null

    private var speed = 1f // 默认播放速度，0f 表示暂停
    private var pitch = 1f // 默认音高
    private var pitchLevel = 0 // 音高等级
    private val pitchUnit = 0.05f // 音高单元，每次改变的音高单位

    private lateinit var audioManager: AudioManager
    private lateinit var audioAttributes: AudioAttributes
    private lateinit var audioFocusRequest: AudioFocusRequest

    override fun onCreate() {
        super.onCreate()

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager // 要在初始化通道前
        // 初始化 MediaSession
        initMediaSession()
        // 初始化通道
        initChannel()
        // 初始化音频焦点（暂时禁用，等待测试）
        initAudioFocus()
    }

    /**
     * 初始化音频焦点
     */
    @TestOnly
    private fun initAudioFocus() {
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(audioAttributes)
                .setOnAudioFocusChangeListener { focusChange ->
                    when (focusChange) {
                        // AudioManager.AUDIOFOCUS_GAIN -> musicBinder.play()
                        // AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> musicBinder.play()
                        // AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> musicBinder.play()
                        AudioManager.AUDIOFOCUS_LOSS -> {
                            // audioManager.abandonAudioFocusRequest(audioFocusRequest)
                            musicBinder.pause()
                        }
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> musicBinder.pause()
                        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> musicBinder.pause()
                    }
                }.build()
            if (isAudioFocus) {
                audioManager.requestAudioFocus(audioFocusRequest)
            }
        }

    }

    /**
     * 初始化媒体会话 MediaSession
     */
    private fun initMediaSession() {
        val intentFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)

        var myNoisyAudioStreamReceiverTag = false
        val myNoisyAudioStreamReceiver = BecomingNoisyReceiver()
        // 媒体会话的回调，Service 控制通知这个 Callback 来控制 MediaPlayer
        mediaSessionCallback = object : MediaSessionCompat.Callback() {
            // 播放
            override fun onPlay() {
                // 注册广播
                if (!myNoisyAudioStreamReceiverTag) {
                    registerReceiver(myNoisyAudioStreamReceiver, intentFilter)
                    myNoisyAudioStreamReceiverTag = true
                }

                mediaSession?.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(
                            PlaybackStateCompat.STATE_PLAYING,
                            (MyApplication.musicController.value?.getProgress() ?: 0).toLong(),
                            1f
                        )
                        .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                        .build()
                )
            }

            // 暂停
            override fun onPause() {
                mediaSession?.setPlaybackState(
                    PlaybackStateCompat.Builder()
                        .setState(
                            PlaybackStateCompat.STATE_PAUSED,
                            (MyApplication.musicController.value?.getProgress() ?: 0).toLong(),
                            1f
                        )
                        .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                        .build()
                )
            }

            // 播放下一首
            override fun onSkipToNext() {
                musicBinder.playNext()
            }

            // 播放上一首
            override fun onSkipToPrevious() {
                // AudioPlayer.get().prev()
            }

            // 关闭
            override fun onStop() {
                // 注销广播
                if (myNoisyAudioStreamReceiverTag) {
                    unregisterReceiver(myNoisyAudioStreamReceiver)
                    myNoisyAudioStreamReceiverTag = false
                }
                // AudioPlayer.get().stopPlayer()
            }

            // 跳转
            override fun onSeekTo(pos: Long) {
                mediaPlayer?.seekTo(pos.toInt())
                if (musicBinder.isPlaying().value == true) {
                    onPlay()
                }
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent?): Boolean {
                if (mediaButtonEvent != null) {
                    val keyEvent = mediaButtonEvent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as KeyEvent?
                    when (mediaButtonEvent.action) {
                        Intent.ACTION_MEDIA_BUTTON -> {
                            if (keyEvent != null) {
                                when (keyEvent.action) {
                                    // 按键按下
                                    KeyEvent.ACTION_DOWN -> {
                                        when (keyEvent.keyCode) {
                                            KeyEvent.KEYCODE_MEDIA_PLAY -> { // 播放按钮
                                                MyApplication.musicController.value?.play()
                                            }
                                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                                                MyApplication.musicController.value?.changePlayState()
                                            }
                                            KeyEvent.KEYCODE_MEDIA_PAUSE -> {
                                                MyApplication.musicController.value?.pause()
                                            }
                                            KeyEvent.KEYCODE_MEDIA_NEXT -> {
                                                MyApplication.musicController.value?.playNext()
                                            }
                                            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                                                MyApplication.musicController.value?.playPrevious()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                return true
            }

        }
        // 初始化 MediaSession
        mediaSession = MediaSessionCompat(this, "MusicService").apply {
            // 设置 Callback
            setCallback(mediaSessionCallback, Handler(Looper.getMainLooper()))
            // 把 MediaSession 置为 active，这样才能开始接收各种信息
            if (!isActive) {
                isActive = true
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.getIntExtra("int_code", 0)) {
            CODE_PREVIOUS -> musicBinder.playPrevious()
            CODE_PLAY -> {
                loge(musicBinder.isPlaying().value.toString(), TAG)
                if (musicBinder.isPlaying().value == true) {
                    loge("按钮请求暂停音乐", TAG)
                    musicBinder.pause()
                } else {
                    loge("按钮请求继续播放音乐", TAG)
                    musicBinder.play()
                }
            }
            CODE_NEXT -> musicBinder.playNext()
        }
        return START_NOT_STICKY // 非粘性服务
    }

    /**
     * 绑定
     */
    override fun onBind(p0: Intent?): IBinder {
        return musicBinder
    }

    override fun onGetRoot(clientPackageName: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        return null
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaBrowserCompat.MediaItem>>) {

    }

    private fun initChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = "Dso Music Notification"
            val descriptionText = "Dso Music 音乐通知"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = descriptionText
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 释放 mediaSession
        mediaSession?.let {
            it.setCallback(null)
            it.release()
        }
    }

    /**
     * 内部类
     * MusicBinder
     */
    inner class MusicController : Binder(), MusicControllerInterface, MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

        private var songData = MutableLiveData<StandardSongData?>()

        private val isSongPlaying = MutableLiveData<Boolean>().also {
            it.value = mediaPlayer?.isPlaying ?: false
        }

        private var isPrepared = false // 音乐是否准备完成

        override fun setPlaylist(songListData: ArrayList<StandardSongData>) {
            PlayQueue.setNormal(songListData)
            if (mode == MODE_RANDOM) {
                PlayQueue.random()
            }
        }

        override fun getPlaylist(): ArrayList<StandardSongData>? = PlayQueue.currentQueue.value

        override fun playMusic(song: StandardSongData) {
            isPrepared = false

            songData.value = song

            // 如果 MediaPlayer 已经存在，释放
            if (mediaPlayer != null) {
                mediaPlayer?.reset()
                mediaPlayer?.release()
                mediaPlayer = null
            }

            // 初始化
            mediaPlayer = MediaPlayer().apply {
                ServiceSongUrl.getUrl(song) {
                    when (it) {
                        is String -> {
                            if (!InternetState.isWifi(MyApplication.context) && !MyApplication.mmkv.decodeBool(
                                    Config.PLAY_ON_MOBILE,
                                    false
                                )
                            ) {
                                toast("移动网络下已禁止播放，请在设置中打开选项（注意流量哦）")
                            } else {
                                setDataSource(it)
                            }
                        }
                        is Uri -> setDataSource(applicationContext, it)
                    }
                }
                setOnPreparedListener(this@MusicController) // 歌曲准备完成的监听
                setOnCompletionListener(this@MusicController) // 歌曲完成后的回调
                setOnErrorListener(this@MusicController)
                prepareAsync()
            }
        }

        private fun sendMusicBroadcast() {
            // Service 通知
            val intent = Intent("com.dirror.music.MUSIC_BROADCAST")
            intent.setPackage(packageName)
            sendBroadcast(intent)
        }

        override fun onPrepared(p0: MediaPlayer?) {
            isPrepared = true
            this.play()
            sendMusicBroadcast()
            refreshNotification()
            setPlaybackParams()
            // 添加到播放历史
            getPlayingSongData().value?.let {
                PlayHistory.addPlayHistory(it)
            }
        }

        override fun changePlayState() {
            isSongPlaying.value?.let {
                if (it) {
                    mediaPlayer?.pause()
                    mediaSessionCallback?.onPause()
                } else {
                    mediaPlayer?.start()
                    mediaSessionCallback?.onPlay()
                }
                isSongPlaying.value = mediaPlayer?.isPlaying ?: false
            }

            sendMusicBroadcast()
            refreshNotification()

        }

        override fun play() {
            if (isPrepared) {
                mediaPlayer?.start()
                isSongPlaying.value = mediaPlayer?.isPlaying ?: false
                mediaSessionCallback?.onPlay()
                sendMusicBroadcast()
                refreshNotification()
            }
        }

        override fun pause() {
            if (isPrepared) {
                mediaPlayer?.pause()
                isSongPlaying.value = mediaPlayer?.isPlaying ?: false
                mediaSessionCallback?.onPause()
                sendMusicBroadcast()
                refreshNotification()
            }
        }

        override fun addToNextPlay(standardSongData: StandardSongData) {
            if (standardSongData == songData.value) {
                return
            }
            if (PlayQueue.currentQueue.value?.contains(standardSongData) == true) {
                PlayQueue.currentQueue.value?.remove(standardSongData)
            }
            val currentPosition = PlayQueue.currentQueue.value?.indexOf(songData.value) ?: -1
            PlayQueue.currentQueue.value?.add(currentPosition + 1, standardSongData)
        }

        override fun setAudioFocus(status: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (status != isAudioFocus) {
                    if (status) {
                        audioManager.requestAudioFocus(audioFocusRequest)
                    } else {
                        audioManager.abandonAudioFocusRequest(audioFocusRequest)
                    }
                    isAudioFocus = status
                    MyApplication.mmkv.encode(Config.ALLOW_AUDIO_FOCUS, isAudioFocus)
                }
            }
        }

        override fun stopMusicService() {
            stopSelf(-1)
        }

        override fun isPlaying(): MutableLiveData<Boolean> = isSongPlaying

        override fun getDuration(): Int {
            return if (isPrepared) {
                mediaPlayer?.duration ?: 0
            } else {
                0
            }
        }

        override fun getProgress(): Int {
            return if (isPrepared) {
                mediaPlayer?.currentPosition ?: 0
            } else {
                0
            }
        }

        override fun setProgress(newProgress: Int) {
            mediaPlayer?.seekTo(newProgress)
            mediaSessionCallback?.onPlay()
            // refreshNotification()
        }

        override fun getPlayingSongData(): MutableLiveData<StandardSongData?> = songData

        override fun changePlayMode() {
            when (mode) {
                MODE_CIRCLE -> mode = MODE_REPEAT_ONE
                MODE_REPEAT_ONE -> {
                    mode = MODE_RANDOM
                    PlayQueue.random()
                }
                MODE_RANDOM -> {
                    mode = MODE_CIRCLE
                    PlayQueue.normal()
                }
            }
            // 将播放模式存储
            MyApplication.mmkv.encode(Config.PLAY_MODE, mode)
            sendMusicBroadcast()
        }

        override fun getPlayMode(): Int = mode

        override fun playPrevious() {
            when (val position = PlayQueue.currentQueue.value?.indexOf(songData.value) ?: -1) {
                -1 -> return
                0 -> {
                    PlayQueue.currentQueue.value?.get(
                        PlayQueue.currentQueue.value?.lastIndex ?: 0
                    )?.let {
                        playMusic(it)
                    }
                }
                else -> {
                    PlayQueue.currentQueue.value?.get(position - 1)?.let {
                        playMusic(it)
                    }
                }
            }
        }

        override fun playNext() {

            when (val position = PlayQueue.currentQueue.value?.indexOf(songData.value) ?: -1) {
                -1 -> return
                PlayQueue.currentQueue.value?.lastIndex -> {
                    PlayQueue.currentQueue.value?.get(0)?.let {
                        playMusic(it)
                    }
                }
                else -> {
                    PlayQueue.currentQueue.value?.get(position + 1)?.let {
                        playMusic(it)
                    }
                }
            }
        }

        override fun getNowPosition(): Int {
            return PlayQueue.currentQueue.value?.indexOf(songData.value) ?: -1
        }

        override fun getAudioSessionId(): Int {
            return mediaPlayer?.audioSessionId ?: 0
        }

        override fun sendBroadcast() {
            sendMusicBroadcast()
        }

        override fun setSpeed(speed: Float) {
            this@MusicService.speed = speed
            setPlaybackParams()
        }

        override fun getSpeed(): Float = speed

        override fun getPitchLevel(): Int = pitchLevel

        override fun increasePitchLevel() {
            pitchLevel++
            val value = pitchUnit * (pitchLevel + 1f / pitchUnit)
            if (value < 1.5f) {
                pitch = value
                setPlaybackParams()
            } else {
                decreasePitchLevel()
            }
        }

        override fun decreasePitchLevel() {
            pitchLevel--
            val value = pitchUnit * (pitchLevel + 1f / pitchUnit)
            if (value > 0.5f) {
                pitch = value
                setPlaybackParams()
            } else {
                increasePitchLevel()
            }
        }

        private fun setPlaybackParams() {
            if (isPrepared) {
                mediaPlayer?.let {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            val playbackParams = it.playbackParams
                            // playbackParams.speed = speed // 0 表示暂停
                            playbackParams.pitch = pitch
                            it.playbackParams = playbackParams
                        }
                    } catch (e: Exception) {

                    }
                }
            }
        }

        override fun onCompletion(p0: MediaPlayer?) {
            autoPlayNext()
        }

        private fun autoPlayNext() {
            if (mode == MODE_REPEAT_ONE) {
                setProgress(0)
                play()
                return
            }
            when (val position = PlayQueue.currentQueue.value?.indexOf(songData.value) ?: -1) {
                -1 -> return
                PlayQueue.currentQueue.value?.lastIndex -> {
                    PlayQueue.currentQueue.value?.get(0)?.let {
                        playMusic(it)
                    }
                }
                else -> {
                    PlayQueue.currentQueue.value?.get(position + 1)?.let {
                        playMusic(it)
                    }
                }
            }
        }

        override fun onError(p0: MediaPlayer?, p1: Int, p2: Int): Boolean {
            if (MyApplication.mmkv.decodeBool(Config.SKIP_ERROR_MUSIC, true)) {
                // 播放下一首
                toast("播放错误 (${p1},${p2}) ，开始播放下一首")
                playNext()
            } else {
                toast("播放错误")
            }
            return true
        }

    }

    private fun getPendingIntentActivity(): PendingIntent {
        val intentMain = Intent(this, MainActivity::class.java)
        val intentPlayer = Intent(this, PlayerActivity::class.java)
        val intents = arrayOf(intentMain, intentPlayer)
        return PendingIntent.getActivities(this, 1, intents, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getPendingIntentPrevious(): PendingIntent {
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("int_code", CODE_PREVIOUS)
        return PendingIntent.getService(this, 2, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getPendingIntentPlay(): PendingIntent {
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("int_code", CODE_PLAY)
        return PendingIntent.getService(this, 3, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun getPendingIntentNext(): PendingIntent {
        val intent = Intent(this, MusicService::class.java)
        intent.putExtra("int_code", CODE_NEXT)
        return PendingIntent.getService(this, 4, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * 刷新通知
     */
    private var largeBitmap: Bitmap? = null
    private fun refreshNotification() {
        val song = musicBinder.getPlayingSongData().value
        if (song != null) {
            SongPicture.getPlayerActivityCoverBitmap(this, song, 100.dp()) { bitmap ->
                largeBitmap = bitmap
                showNotification(song)
            }
        }
    }

    /**
     * 显示通知
     */
    private fun showNotification(song: StandardSongData) {
        mediaSession?.apply {
            setMetadata(
                MediaMetadataCompat.Builder().apply {
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.name)
                    putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artists?.parse())
                    putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, largeBitmap)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        putLong(
                            MediaMetadata.METADATA_KEY_DURATION,
                            (MyApplication.musicController.value?.getDuration() ?: 0).toLong()
                        )
                    }
                }.build()
            )

            setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        (MyApplication.musicController.value?.getProgress() ?: 0).toLong(),
                        1f
                    )
                    .setActions(PlaybackStateCompat.ACTION_SEEK_TO)
                    .build()
            )
            setCallback(mediaSessionCallback)
            isActive = true
        }
        if (musicBinder.isPlaying().value != true) {
            mediaSessionCallback?.onPause()
        }

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(mediaSession?.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_launcher_foreground)
            .setLargeIcon(largeBitmap)
            .setContentTitle(song.name)
            .setContentText(song.artists?.let { it1 -> parseArtist(it1) })
            .setContentIntent(getPendingIntentActivity())
            .addAction(R.drawable.ic_baseline_skip_previous_24, "Previous", getPendingIntentPrevious())
            .addAction(getPlayIcon(), "play", getPendingIntentPlay())
            .addAction(R.drawable.ic_baseline_skip_next_24, "next", getPendingIntentNext())
            .setStyle(mediaStyle)
            .setOngoing(true)
            // .setTicker(tag) // 魅族状态栏歌词的实现方法
            // .setAutoCancel(true)
            .build()
        // 更新通知
        startForeground(START_FOREGROUND_ID, notification)
    }

    /**
     * 获取通知栏播放的图标
     */
    private fun getPlayIcon(): Int {
        return if (musicBinder.isPlaying().value == true) {
            R.drawable.ic_baseline_pause_24
        } else {
            R.drawable.ic_baseline_play_arrow_24
        }
    }
}