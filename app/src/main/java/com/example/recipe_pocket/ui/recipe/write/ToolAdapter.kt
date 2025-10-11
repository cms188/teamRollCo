package com.example.recipe_pocket.ui.recipe.write

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.databinding.ItemToolBinding

class ToolAdapter(
    private val tools: MutableList<String>
) : RecyclerView.Adapter<ToolAdapter.ToolViewHolder>() {

    inner class ToolViewHolder(val binding: ItemToolBinding) : RecyclerView.ViewHolder(binding.root) {
        private var nameWatcher: TextWatcher? = null

        fun bind(position: Int) {
            val toolName = tools[position]

            binding.etToolName.removeTextChangedListener(nameWatcher)
            binding.etToolName.setText(toolName)

            binding.etToolName.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus && adapterPosition == tools.size - 1) {
                    tools.add("")
                    notifyItemInserted(tools.size)
                    notifyItemChanged(adapterPosition)
                }
            }

            binding.btnDeleteTool.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    tools.removeAt(adapterPosition)
                    notifyItemRemoved(adapterPosition)
                    notifyItemRangeChanged(adapterPosition, tools.size)
                }
            }
            binding.btnDeleteTool.visibility = if (position < tools.size - 1) View.VISIBLE else View.INVISIBLE

            nameWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        tools[adapterPosition] = s.toString()
                    }
                }
            }
            binding.etToolName.addTextChangedListener(nameWatcher)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
        val binding = ItemToolBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ToolViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = tools.size
}