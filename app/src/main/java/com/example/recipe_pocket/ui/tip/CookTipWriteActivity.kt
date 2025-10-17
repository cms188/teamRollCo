package com.example.recipe_pocket.ui.tip

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.recipe_pocket.data.ContentBlock
import com.example.recipe_pocket.data.CookingTip
import com.example.recipe_pocket.databinding.ActivityCookTipWriteBinding
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import utils.ToolbarUtils

class CookTipWriteActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCookTipWriteBinding
    private val viewModel: CookTipWriteViewModel by viewModels()
    private lateinit var contentAdapter: CookTipContentAdapter
    private var currentImagePickPosition = -1

    private val auth = Firebase.auth
    private val db = Firebase.firestore
    private val storage = Firebase.storage

    private var currentMode = MODE_WRITE
    private var editingTipId: String? = null

    companion object {
        const val EXTRA_MODE = "EXTRA_MODE"
        const val EXTRA_TIP_ID = "TIP_ID"
        const val MODE_WRITE = "WRITE"
        const val MODE_EDIT = "EDIT"
    }


    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                if (currentImagePickPosition != -1) {
                    viewModel.updateImageBlock(currentImagePickPosition, uri)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCookTipWriteBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 모드 및 데이터 확인
        currentMode = intent.getStringExtra(EXTRA_MODE) ?: MODE_WRITE
        if (currentMode == MODE_EDIT) {
            editingTipId = intent.getStringExtra(EXTRA_TIP_ID)
            if (editingTipId == null) {
                Toast.makeText(this, "수정할 팁 정보를 불러오지 못했습니다.", Toast.LENGTH_SHORT).show()
                finish()
                return
            }
            loadTipForEditing()
        }

        setupRecyclerView()
        setupListeners()
        observeViewModel()
        updateUIForMode()
        setupWindowInsets()
    }

    private fun setupWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = insets.top
                leftMargin = insets.left
                bottomMargin = insets.bottom
                rightMargin = insets.right
            }
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun updateUIForMode() {
        if (currentMode == MODE_EDIT) {
            ToolbarUtils.setupWriteToolbar(activity = this, title = "팁 수정", onSaveClicked = { uploadCookingTip() })
            binding.toolbar.btnSave.text = "수정 완료"
        } else {
            ToolbarUtils.setupWriteToolbar(activity = this, title = "팁 작성", onSaveClicked = { uploadCookingTip() })
            binding.toolbar.btnSave.text = "업로드"
        }
    }


    private fun loadTipForEditing() {
        lifecycleScope.launch {
            try {
                val document = db.collection("CookingTips").document(editingTipId!!).get().await()
                val tip = document.toObject(CookingTip::class.java)
                if (tip != null) {
                    viewModel.loadExistingTip(tip)
                } else {
                    Toast.makeText(this@CookTipWriteActivity, "팁 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                Toast.makeText(this@CookTipWriteActivity, "데이터 로딩 실패: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun setupRecyclerView() {
        contentAdapter = CookTipContentAdapter(
            mutableListOf(),
            onImageClick = { position ->
                currentImagePickPosition = position
                val intent = Intent(Intent.ACTION_PICK).apply { type = "image/*" }
                pickImageLauncher.launch(intent)
            },
            onRemoveClick = { position ->
                viewModel.removeContentBlock(position)
            },
            onTextChange = { position, text ->
                viewModel.updateTextBlock(position, text)
            }
        )
        binding.rvContentBlocks.apply {
            adapter = contentAdapter
            layoutManager = LinearLayoutManager(this@CookTipWriteActivity)
        }
    }

    private fun setupListeners() {
        binding.btnAddBlock.setOnClickListener { viewModel.addContentBlock() }

        binding.etTitle.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                viewModel.updateTitle(s.toString())
            }
        })
    }

    private fun observeViewModel() {
        viewModel.title.observe(this, Observer { title ->
            if (binding.etTitle.text.toString() != title) {
                binding.etTitle.setText(title)
            }
        })

        viewModel.contentBlocks.observe(this, Observer { blocks ->
            contentAdapter.updateData(blocks)
        })
    }

    private fun uploadCookingTip() {
        val title = viewModel.title.value
        val blocks = viewModel.contentBlocks.value

        if (title.isNullOrBlank()) {
            Toast.makeText(this, "제목을 입력해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        if (blocks.isNullOrEmpty() || blocks.all { it.text.isBlank() && it.imageUri == null && it.existingImageUrl.isNullOrBlank() }) {
            Toast.makeText(this, "내용을 1개 이상 작성해주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        binding.toolbar.btnSave.isEnabled = false
        binding.toolbar.btnSave.text = "업로드 중..."

        lifecycleScope.launch {
            try {
                val uploadedBlocks = uploadImagesAndCreateContentBlocks(blocks)

                if (currentMode == MODE_EDIT) {
                    // 기존 문서 업데이트
                    val updatedData = mapOf(
                        "title" to title,
                        "content" to uploadedBlocks
                    )
                    db.collection("CookingTips").document(editingTipId!!).update(updatedData).await()
                    Toast.makeText(this@CookTipWriteActivity, "요리 팁이 수정되었습니다.", Toast.LENGTH_SHORT).show()
                    setResult(Activity.RESULT_OK)
                } else {
                    // 새 문서 생성
                    val tip = CookingTip(
                        userId = auth.currentUser?.uid,
                        title = title,
                        content = uploadedBlocks,
                        createdAt = Timestamp.now(),
                        recommendationScore = 0
                    )
                    db.collection("CookingTips").add(tip).await()
                    Toast.makeText(this@CookTipWriteActivity, "요리 팁이 등록되었습니다.", Toast.LENGTH_SHORT).show()
                }
                finish()

            } catch (e: Exception) {
                Toast.makeText(this@CookTipWriteActivity, "업로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
                binding.toolbar.btnSave.isEnabled = true
                updateUIForMode()
            }
        }
    }

    private suspend fun uploadImagesAndCreateContentBlocks(blocks: List<ContentBlockData>): List<ContentBlock> {
        val tasks = blocks.map { block ->
            if (block.imageUri != null) { // 새 이미지가 선택된 경우
                val ref = storage.reference.child("tip_images/${UUID.randomUUID()}")
                ref.putFile(block.imageUri!!).continueWithTask {
                    ref.downloadUrl
                }.onSuccessTask { uri ->
                    Tasks.forResult(ContentBlock(block.text, uri.toString()))
                }
            } else { // 기존 이미지를 유지하거나 텍스트만 있는 경우
                Tasks.forResult(ContentBlock(block.text, block.existingImageUrl))
            }
        }
        return Tasks.whenAllSuccess<ContentBlock>(tasks).await()
    }
}