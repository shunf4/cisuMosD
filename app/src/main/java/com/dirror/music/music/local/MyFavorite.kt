package com.dirror.music.music.local

import com.dirror.music.MyApp
import com.dirror.music.music.standard.data.StandardSongData
import com.dirror.music.room.MyFavoriteData
import com.dirror.music.util.toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.jetbrains.annotations.TestOnly
import java.io.Reader
import java.io.Writer
import java.lang.Exception
import kotlin.concurrent.thread

/**
 * 本地我喜欢的
 */
object MyFavorite {

    private val myFavoriteDao = MyApp.appDatabase.myFavoriteDao()

    /**
     * 读取本地歌曲
     */
    @TestOnly
    fun read(success: (ArrayList<StandardSongData>) -> Unit) {
        thread {
            val data = ArrayList<StandardSongData>()
            for (myFavorite in myFavoriteDao.loadAll()) {
                data.add(0, myFavorite.songData)
            }
            success.invoke(data)
        }
    }

    /**
     * 添加一首歌
     */
    @TestOnly
    fun addSong(songData: StandardSongData) {
        thread {
            val myFavoriteData = MyFavoriteData(songData)
            if (myFavoriteData !in myFavoriteDao.loadAll()) {
                myFavoriteDao.insert(myFavoriteData)
                toast("添加成功~")
            } else {
                toast("已经添加过了哦~")
            }
        }
    }

    /**
     * 通过 id 删除一首歌
     */
    fun deleteById(id: String) {
        thread {
            myFavoriteDao.deleteById(id)
        }
    }

    /**
     * 判断歌曲是否存在数据库
     */
    fun isExist(songData: StandardSongData, exist: (Boolean) -> Unit) {
        thread {
            val myFavoriteData = MyFavoriteData(songData)
            if (myFavoriteData in myFavoriteDao.loadAll()) {
                exist.invoke(true)
            } else {
                exist.invoke(false)
            }
        }
    }

    fun clear(success: () -> Unit) {
        thread {
            myFavoriteDao.clear()
            success.invoke()
        }
    }

    fun import(reader: Reader, success: (Int) -> Unit, fail: (Exception) -> Unit) {
        thread {
            try {
                var list = Gson().fromJson<List<StandardSongData>>(reader, object : TypeToken<List<StandardSongData>>() {}.type)
                val currentAll = myFavoriteDao.loadAll().map { it.songData.id }
                list = list.filter { it.id !in currentAll }
                // myFavoriteDao.clear()
                myFavoriteDao.insertBatch(list.map { MyFavoriteData(it) })
                success.invoke(list.size)
            } catch (e: Exception) {
                fail.invoke(e)
            }
        }

    }

    fun export(writer: Writer, success: (Int) -> Unit, fail: (Exception) -> Unit) {
        thread {
            try {
                val list = myFavoriteDao.loadAll().map { it.songData }
                Gson().toJson(list, writer)
                success.invoke(list.size)
            } catch (e: Exception) {
                fail.invoke(e)
            }
        }
    }

}
