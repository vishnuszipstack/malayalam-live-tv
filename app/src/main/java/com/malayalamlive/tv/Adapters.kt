package com.malayalamlive.tv

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.malayalamlive.tv.databinding.ItemCategoryBinding
import com.malayalamlive.tv.databinding.ItemChannelBinding

class CategoryAdapter(
    private val onFocused: (String) -> Unit,
) : RecyclerView.Adapter<CategoryAdapter.Holder>() {

    var items: List<String> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }
    var selected: String? = null
        set(value) { field = value; notifyDataSetChanged() }

    class Holder(val binding: ItemCategoryBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val category = items[position]
        holder.binding.root.text = category
        holder.binding.root.isActivated = category == selected
        holder.binding.root.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) onFocused(category) }
        holder.binding.root.setOnClickListener { onFocused(category) }
    }
}

class ChannelAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<ChannelAdapter.Holder>() {

    var items: List<Channel> = emptyList()
        set(value) { field = value; notifyDataSetChanged() }
    var favorites: Set<String> = emptySet()
        set(value) { field = value; notifyDataSetChanged() }

    class Holder(val binding: ItemChannelBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemChannelBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val channel = items[position]
        with(holder.binding) {
            name.text = if (channel.url in favorites) "★ ${channel.name}" else channel.name
            logo.load(channel.logo) {
                crossfade(true)
                placeholder(R.drawable.ic_tv)
                error(R.drawable.ic_tv)
                fallback(R.drawable.ic_tv)
            }
            root.setOnClickListener { onClick(holder.bindingAdapterPosition) }
            root.setOnLongClickListener { onLongClick(holder.bindingAdapterPosition); true }
        }
    }
}
