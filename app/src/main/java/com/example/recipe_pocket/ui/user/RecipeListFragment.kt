package com.example.recipe_pocket.ui.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.R
import com.example.recipe_pocket.RecipeAdapter
import com.example.recipe_pocket.databinding.FragmentRecipeListBinding
import com.example.recipe_pocket.repository.RecipeLoader
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

class RecipeListFragment : Fragment() {

    private var _binding: FragmentRecipeListBinding? = null
    private val binding get() = _binding!!
    private lateinit var recipeAdapter: RecipeAdapter
    private var mode: String? = null

    companion object {
        const val ARG_MODE = "fragment_mode"
        const val MODE_MY_RECIPES = "my_recipes"
        const val MODE_BOOKMARKS = "bookmarks"

        fun newInstance(mode: String): RecipeListFragment {
            val fragment = RecipeListFragment()
            val args = Bundle()
            args.putString(ARG_MODE, mode)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            mode = it.getString(ARG_MODE)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentRecipeListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupRecyclerView()
        loadData()
    }

    private fun setupRecyclerView() {
        recipeAdapter = RecipeAdapter(emptyList(), R.layout.cook_card_03)
        binding.recyclerViewRecipes.apply {
            adapter = recipeAdapter
            layoutManager = LinearLayoutManager(context)
        }
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val result = if (mode == MODE_MY_RECIPES) {
                RecipeLoader.loadRecipesByUserId(Firebase.auth.currentUser!!.uid)
            } else {
                RecipeLoader.loadBookmarkedRecipes()
            }

            binding.progressBar.visibility = View.GONE
            result.onSuccess { recipes ->
                if (recipes.isEmpty()) {
                    binding.tvEmptyMessage.text = if (mode == MODE_MY_RECIPES) "작성한 레시피가 없습니다." else "북마크한 레시피가 없습니다."
                    binding.tvEmptyMessage.visibility = View.VISIBLE
                } else {
                    recipeAdapter.updateRecipes(recipes)
                }
            }.onFailure {
                binding.tvEmptyMessage.text = "오류 발생"
                binding.tvEmptyMessage.visibility = View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}