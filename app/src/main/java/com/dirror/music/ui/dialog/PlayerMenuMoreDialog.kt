package com.dirror.music.ui.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import com.dirror.music.MyApp
import com.dirror.music.R
import com.dirror.music.databinding.DialogPlayMoreBinding
import com.dirror.music.manager.User
import com.dirror.music.music.standard.data.SOURCE_NETEASE
import com.dirror.music.music.standard.data.SOURCE_QQ
import com.dirror.music.music.standard.data.StandardSongData
import com.dirror.music.startUserActivity
import com.dirror.music.ui.activity.PlayHistoryActivity
import com.dirror.music.ui.activity.SearchActivity
import com.dirror.music.ui.base.BaseBottomSheetDialog
import com.dirror.music.util.singleClick
import com.dirror.music.util.toast

class PlayerMenuMoreDialog(context: Context, menuItemCallback: (Int) -> Unit) : BaseBottomSheetDialog(context) {

    private val binding: DialogPlayMoreBinding = DialogPlayMoreBinding.inflate(layoutInflater)

    private val menuItemCallback = menuItemCallback

    init {
        setContentView(binding.root)
    }


    private var song: StandardSongData? = null

    override fun initView() {

        MyApp.musicController.value?.getPlayingSongData()?.value?.let { it ->
            binding.tvSongName.text = it.name
            song = it
        }
    }

    override fun initListener() {
        binding.apply {
            // 添加到网易云我喜欢
            itemAddNeteaseFavorite.setOnClickListener {
                if (User.cookie.isEmpty()) {
                    toast("离线模式无法收藏到在线我喜欢~")
                } else {
                    song?.let {
                        when (it.source) {
                            SOURCE_NETEASE -> {
                                MyApp.cloudMusicManager.likeSong(it.id?:"", {
                                    toast("添加到我喜欢成功")
                                    dismiss()
                                }, {
                                    toast("添加到我喜欢失败")
                                    dismiss()
                                })
                            }
                            SOURCE_QQ -> {
                                toast("暂不支持此音源")
                                dismiss()
                            }
                        }
                    }
                }
            }
            // 歌曲信息
            itemSongInfo.setOnClickListener {
                MyApp.musicController.value?.getPlayingSongData()?.value?.let { it1 ->
                    SongInfoDialog(context, it1).show()
                }
                dismiss()
            }

            // 播放历史
            itemPlayHistory.setOnClickListener {
                it.context.startActivity(Intent(it.context, PlayHistoryActivity::class.java))
                dismiss()
            }

            timeClose.setOnClickListener {
                dismiss()
                TimingOffDialog(context).show()
            }

            equalizer.setOnClickListener {
                menuItemCallback(R.id.equalizer)
                dismiss()
            }

            similarSongs.setOnClickListener {
                song?.let {
                    when (it.source) {
                        SOURCE_NETEASE -> {
                            context.startActivity(Intent(context, SearchActivity::class.java).apply {
                                putExtra("SEARCH_STRING",
                                    "@ne_similar_songs:" + song?.id
                                )
                            })
                            menuItemCallback(R.id.similarSongs)
                            dismiss()

                        }
                        else -> {
                            toast("暂不支持此音源")
                            dismiss()
                        }
                    }
                }
            }

            similarPlaylists.setOnClickListener {
                song?.let {
                    when (it.source) {
                        SOURCE_NETEASE -> {
                            context.startActivity(Intent(context, SearchActivity::class.java).apply {
                                putExtra("SEARCH_STRING",
                                    "@ne_similar_playlists:" + song?.id
                                )
                            })
                            menuItemCallback(R.id.similarPlaylists)
                            dismiss()

                        }
                        else -> {
                            toast("暂不支持此音源")
                            dismiss()
                        }
                    }
                }
            }
        }
    }

}
