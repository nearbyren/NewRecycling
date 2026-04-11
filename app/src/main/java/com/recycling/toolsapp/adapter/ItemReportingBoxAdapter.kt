package com.recycling.toolsapp.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.recycling.toolsapp.databinding.ItemReportingBoxBinding


/**
 * @author: lr
 * @created on: 2024/8/30 12:34 AM
 * @description:
 */
class ItemReportingBoxAdapter(private var items: List<String>) : RecyclerView.Adapter<ItemReportingBoxAdapter.ItemViewHolder>() {

    inner class ItemViewHolder(val binding: ItemReportingBoxBinding) : RecyclerView.ViewHolder(binding.root)


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val binding =
                ItemReportingBoxBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        with(holder.binding) {
            tvFileName.text = "$item"
            tvDel.setOnClickListener {

            }
        }
        holder.itemView.setOnClickListener {
            itemReportingBoxClickListener?.itemClick(item)
        }

    }

    fun updateItems(newItems: List<String>) {
        items = newItems
        notifyDataSetChanged() // 刷新整个列表
    }

    var itemReportingBoxClickListener: ItemReportingBoxClickListener? = null

    fun addItemReportingBoxClickListener(itemReportingBoxClickListener: ItemReportingBoxClickListener) {
        this.itemReportingBoxClickListener = itemReportingBoxClickListener
    }

    override fun getItemCount(): Int = items.size
}
