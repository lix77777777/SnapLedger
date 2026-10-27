package com.codefield.snapledger.ui

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.codefield.snapledger.AppContainer
import com.codefield.snapledger.LedgerApplication
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Category
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import com.codefield.snapledger.model.Money
import com.codefield.snapledger.model.ParsedReceipt
import com.codefield.snapledger.model.Source
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 只显示一次的提示。删除的提示带着那一笔，方便撤销。 */
sealed interface LedgerMessage {
    data object Saved : LedgerMessage
    data class Deleted(val entry: Entry) : LedgerMessage
    /** 选的或分享过来的图片里有打不开的，已经跳过。 */
    data object ImageFailed : LedgerMessage
}

/**
 * 一张等着确认的截图。draft 是 null 时还在识别；
 * draft 的金额是 0 表示截图里没找到金额，要用户自己填。
 */
data class PendingShot(
    val image: String,
    val takenAt: Long?,
    val draft: Entry? = null,
    val candidates: List<Money> = emptyList(),
)

/** 截图记账的队列：一次选了或分享了好几张时，一张一张确认。done 是这一批已经处理完的张数。 */
data class ImportQueue(val shots: List<PendingShot> = emptyList(), val done: Int = 0) {
    val current: PendingShot? get() = shots.firstOrNull()
    val position: Int get() = done + 1
    val total: Int get() = done + shots.size
}

/** 界面和数据之间的桥梁，所有页面共用一个。 */
class LedgerViewModel(
    private val container: AppContainer,
    private val savedState: SavedStateHandle,
) : ViewModel() {

    private val repository = container.repository
    private val zone = ZoneId.systemDefault()

    /** 所有账目，新的在前；还在读数据库时是 null。 */
    val entries: StateFlow<List<Entry>?> = repository.observeEntries()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val rates: StateFlow<RateTable> = repository.observeRates()
        .map { RateTable(it) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, RateTable.EMPTY)

    private val _homeCurrency = MutableStateFlow(container.settings.homeCurrency)
    val homeCurrency: StateFlow<Currency> = _homeCurrency.asStateFlow()

    private val _messages = MutableSharedFlow<LedgerMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<LedgerMessage> = _messages.asSharedFlow()

    private val _imports = MutableStateFlow(restoreImports())
    val imports: StateFlow<ImportQueue> = _imports.asStateFlow()
    private var reading: Job? = null

    init {
        // 进程被系统关掉又回来时，接着识别还没确认的截图。
        readPending()
        viewModelScope.launch {
            val saved = entries.filterNotNull().first()
            container.screenshots.removeUnused(saved.mapNotNull { it.imagePath }.toSet() + _imports.value.shots.map { it.image })
        }
    }

    /** 编号是 0 就新记一笔，否则修改原来那笔。 */
    fun save(entry: Entry) {
        viewModelScope.launch {
            if (entry.id == 0L) repository.add(entry) else repository.update(entry)
            _messages.emit(LedgerMessage.Saved)
        }
    }

    fun delete(entry: Entry) {
        viewModelScope.launch {
            repository.delete(entry.id)
            _messages.emit(LedgerMessage.Deleted(entry))
        }
    }

    fun restore(entry: Entry) {
        viewModelScope.launch { repository.restore(entry) }
    }

    fun imageFile(name: String): File = container.screenshots.file(name)

    /** 从相册选的或别的应用分享过来的截图：一张张存一份到应用里，存好一张就排进队列识别。 */
    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var failed = false
            for (uri in uris) {
                val saved = container.screenshots.save(uri)
                if (saved == null) {
                    failed = true
                    continue
                }
                updateImports { it.copy(shots = it.shots + PendingShot(saved.name, saved.takenAt)) }
                readPending()
            }
            if (failed) _messages.emit(LedgerMessage.ImageFailed)
        }
    }

    /** 确认了识别结果：存下这一笔，换下一张。 */
    fun confirmImport(entry: Entry) {
        if (_imports.value.current == null) return
        popImport()
        save(entry)
    }

    /** 跳过这一张：不记账，删掉存下的副本。 */
    fun skipImport() {
        val shot = _imports.value.current ?: return
        popImport()
        viewModelScope.launch { container.screenshots.delete(shot.image) }
    }

    /** 按顺序识别队列里还没识别的截图。用户确认第一张的时候，后面的已经在识别了。 */
    private fun readPending() {
        if (reading?.isActive == true) return
        reading = viewModelScope.launch {
            while (true) {
                val shot = _imports.value.shots.firstOrNull { it.draft == null } ?: break
                val parsed = container.screenshotReader.read(shot.image, homeCurrency.value, System.currentTimeMillis(), zone)
                val draft = draftOf(shot, parsed)
                // 识别途中用户可能已经跳过了这一张，那就什么也不改。
                updateImports { queue ->
                    queue.copy(shots = queue.shots.map { if (it.image == shot.image) it.copy(draft = draft, candidates = parsed.amountCandidates) else it })
                }
            }
        }
    }

    /** 截图里没写时间时，用截图的时间；也看不出来时用现在。 */
    private fun draftOf(shot: PendingShot, parsed: ParsedReceipt) = Entry(
        amount = parsed.amount ?: Money(0, homeCurrency.value),
        category = parsed.category ?: Category.OTHER,
        merchant = parsed.merchant.orEmpty(),
        time = parsed.time ?: shot.takenAt ?: System.currentTimeMillis(),
        source = Source.SCREENSHOT,
        imagePath = shot.image,
    )

    private fun popImport() = updateImports { queue ->
        val rest = queue.shots.drop(1)
        if (rest.isEmpty()) ImportQueue() else queue.copy(shots = rest, done = queue.done + 1)
    }

    /** 队列一变就记到 SavedStateHandle 里：进程被关掉后回来还能接着确认（识别结果不存，回来重新识别）。 */
    private fun updateImports(change: (ImportQueue) -> ImportQueue) {
        val queue = change(_imports.value)
        _imports.value = queue
        savedState[KEY_IMAGES] = ArrayList(queue.shots.map { it.image })
        savedState[KEY_TIMES] = queue.shots.map { it.takenAt ?: NO_TIME }.toLongArray()
        savedState[KEY_DONE] = queue.done
    }

    private fun restoreImports(): ImportQueue {
        val images = savedState.get<ArrayList<String>>(KEY_IMAGES) ?: return ImportQueue()
        val times = savedState.get<LongArray>(KEY_TIMES) ?: LongArray(0)
        return ImportQueue(
            shots = images.mapIndexed { i, image -> PendingShot(image, times.getOrNull(i)?.takeIf { it != NO_TIME }) },
            done = savedState.get<Int>(KEY_DONE) ?: 0,
        )
    }

    companion object {
        private const val KEY_IMAGES = "import_images"
        private const val KEY_TIMES = "import_times"
        private const val KEY_DONE = "import_done"
        private const val NO_TIME = -1L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LedgerApplication
                LedgerViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}
