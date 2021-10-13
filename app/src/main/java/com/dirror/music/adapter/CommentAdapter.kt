package com.dirror.music.adapter

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.size.ViewSizeResolver
import coil.transform.CircleCropTransformation
import com.dirror.music.App
import com.dirror.music.R
import com.dirror.music.data.CommentData
import com.dirror.music.util.msTimeToFormatDate
import kotlin.math.min

/**
 * 评论 Adapter
 */
class CommentAdapter(private val commentData: CommentData, private val activity: Activity): RecyclerView.Adapter<CommentAdapter.ViewHolder>() {

    inner class ViewHolder(view: View): RecyclerView.ViewHolder(view) {
        val tvName: TextView = view.findViewById(R.id.tvName)
        val tvContent: TextView = view.findViewById(R.id.tvContent)
        val tvLikedCount: TextView = view.findViewById(R.id.tvLikedCount)
        val ivCover: ImageView = view.findViewById(R.id.ivCover)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        LayoutInflater.from(parent.context).inflate(R.layout.layout_comment, parent, false).apply {
            return ViewHolder(this)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val comment = if (position < commentData.hotComments.size) {
            commentData.hotComments[position]
        } else {
            commentData.comments[position - commentData.hotComments.size]
        }

        holder.apply {
            tvName.text = comment.user.nickname
            tvContent.text = comment.content
            tvLikedCount.text = comment.likedCount.toString()
            tvTime.text = msTimeToFormatDate(comment.time)
            ivCover.load(comment.user.avatarUrl) {
                transformations(CircleCropTransformation())
                size(ViewSizeResolver(ivCover))
                crossfade(300)
            }

            ivCover.setOnClickListener {
                App.activityManager.startUserActivity(activity , comment.user.userId)
            }

            tvName.setOnClickListener {
                App.activityManager.startUserActivity(activity , comment.user.userId)
            }
        }
    }

    override fun getItemCount(): Int {
        return commentData.hotComments.size + min(10, commentData.comments.size)
    }

}
