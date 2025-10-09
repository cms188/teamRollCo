package com.example.recipe_pocket.ui.recipe.write

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.recipe_pocket.data.Ingredient
import com.example.recipe_pocket.databinding.ItemIngredientBinding

class IngredientAdapter(
    private val context: Context,
    private val ingredients: MutableList<Ingredient>
) : RecyclerView.Adapter<IngredientAdapter.IngredientViewHolder>() {

    inner class IngredientViewHolder(val binding: ItemIngredientBinding) : RecyclerView.ViewHolder(binding.root) {
        private var nameWatcher: TextWatcher? = null
        private var amountWatcher: TextWatcher? = null

        fun bind(position: Int) {
            val ingredient = ingredients[position]

            binding.etIngredientName.removeTextChangedListener(nameWatcher)
            binding.etIngredientAmount.removeTextChangedListener(amountWatcher)

            binding.etIngredientName.setText(ingredient.name)
            binding.etIngredientAmount.setText(ingredient.amount)

            // [핵심 수정 1] 포커스 리스너로 자동 추가 로직 변경
            binding.etIngredientName.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus && adapterPosition == ingredients.size - 1) {
                    ingredients.add(Ingredient())
                    notifyItemInserted(ingredients.size)
                    notifyItemChanged(adapterPosition) // 현재 아이템의 삭제 버튼 상태 갱신
                }
            }

            // [핵심 수정 2] 삭제는 X 버튼으로만
            binding.btnDeleteIngredient.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    ingredients.removeAt(adapterPosition)
                    notifyItemRemoved(adapterPosition)
                    notifyItemRangeChanged(adapterPosition, ingredients.size)
                }
            }
            // 마지막 빈 칸에서는 삭제 버튼 숨기기
            binding.btnDeleteIngredient.visibility = if (position < ingredients.size - 1) View.VISIBLE else View.INVISIBLE

            // TextWatcher는 데이터 업데이트 역할만 수행
            nameWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        ingredients[adapterPosition].name = s.toString()
                    }
                }
            }

            amountWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    if (adapterPosition != RecyclerView.NO_POSITION) {
                        ingredients[adapterPosition].amount = s.toString()
                    }
                }
            }

            binding.etIngredientName.addTextChangedListener(nameWatcher)
            binding.etIngredientAmount.addTextChangedListener(amountWatcher)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IngredientViewHolder {
        val binding = ItemIngredientBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return IngredientViewHolder(binding)
    }

    override fun onBindViewHolder(holder: IngredientViewHolder, position: Int) {
        holder.bind(position)
    }

    override fun getItemCount(): Int = ingredients.size
}