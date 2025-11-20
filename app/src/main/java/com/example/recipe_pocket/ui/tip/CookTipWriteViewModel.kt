package com.example.recipe_pocket.ui.tip

import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.recipe_pocket.data.CookingTip
import java.util.UUID

// RecyclerView의 각 아이템 데이터를 표현하는 클래스
data class ContentBlockData(
    val id: String = UUID.randomUUID().toString(),
    var text: String = "",
    var imageUri: Uri? = null,
    var existingImageUrl: String? = null // 수정 시 기존 이미지 URL을 저장할 필드
)

class CookTipWriteViewModel : ViewModel() {

    private val _title = MutableLiveData<String>("")
    val title: LiveData<String> = _title

    private val _contentBlocks = MutableLiveData<MutableList<ContentBlockData>>()
    val contentBlocks: LiveData<MutableList<ContentBlockData>> = _contentBlocks

    init {
        // ViewModel이 생성될 때 첫 번째 빈 콘텐츠 블록으로 초기화
        _contentBlocks.value = mutableListOf(ContentBlockData())
    }

    fun addContentBlock() {
        // 기존 리스트의 복사본을 만들어 아이템을 추가한 뒤, LiveData에 새로 할당합니다.
        val newList = _contentBlocks.value?.toMutableList() ?: mutableListOf()
        newList.add(ContentBlockData())
        _contentBlocks.value = newList
    }

    fun removeContentBlock(position: Int) {
        val currentList = _contentBlocks.value ?: return
        if (currentList.size > 1 && position >= 0 && position < currentList.size) {
            // 아이템이 제거된 새로운 리스트를 생성하여 LiveData에 할당합니다.
            val newList = currentList.toMutableList()
            newList.removeAt(position)
            _contentBlocks.value = newList
        }
    }

    fun updateTitle(newTitle: String) {
        if (_title.value != newTitle) {
            _title.value = newTitle
        }
    }

    fun updateTextBlock(position: Int, text: String) {
        val list = _contentBlocks.value
        if (list != null && position >= 0 && position < list.size) {
            if (list[position].text != text) {
                list[position].text = text
            }
        }
    }

    fun updateImageBlock(position: Int, uri: Uri) {
        val currentList = _contentBlocks.value
        if (currentList != null && position >= 0 && position < currentList.size) {
            val newList = currentList.toMutableList()
            // 리스트에서 해당 위치의 아이템을 찾아 imageUri를 업데이트합니다.
            val updatedBlock = newList[position].copy(imageUri = uri)
            newList[position] = updatedBlock
            _contentBlocks.value = newList
        }
    }

    // 수정 모드일 때 기존 데이터를 ViewModel에 로드하는 함수
    fun loadExistingTip(tip: CookingTip) {
        _title.value = tip.title ?: ""

        val newContentBlockDataList = mutableListOf<ContentBlockData>()

        // tip.content가 null이 아닌 경우에만 루프를 실행하도록 지역 변수에 할당하여 명시적으로 null-safety를 보장합니다.
        val content = tip.content
        if (content != null) {
            for (contentBlock in content) {
                newContentBlockDataList.add(
                    ContentBlockData(
                        text = contentBlock.text ?: "",
                        existingImageUrl = contentBlock.imageUrl
                    )
                )
            }
        }

        if (newContentBlockDataList.isEmpty()) {
            newContentBlockDataList.add(ContentBlockData())
        }

        _contentBlocks.value = newContentBlockDataList
    }
}