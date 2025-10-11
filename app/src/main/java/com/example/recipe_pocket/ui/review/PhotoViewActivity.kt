package com.example.recipe_pocket.ui.review

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.example.recipe_pocket.R
import com.example.recipe_pocket.databinding.ActivityPhotoViewBinding

class PhotoViewActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPhotoViewBinding

    companion object {
        const val EXTRA_IMAGE_URL = "image_url"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPhotoViewBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val imageUrl = intent.getStringExtra(EXTRA_IMAGE_URL)
        if (imageUrl.isNullOrEmpty()) {
            finish()
            return
        }

        Glide.with(this)
            .load(imageUrl)
            .error(R.drawable.bg_no_img_gray)
            .into(binding.ivPhotoFullscreen)

        binding.btnClosePhoto.setOnClickListener { finish() }
        binding.rootContainer.setOnClickListener { finish() }
    }
}