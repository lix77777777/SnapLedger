package com.codefield.snapledger.ui

import android.content.Intent
import android.graphics.Bitmap
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
import java.io.IOException
import java.time.LocalDate
import java.time.YearMonth
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
    /** 列表截图一次记了好几笔。 */
    data class SavedSeveral(val count: Int) : LedgerMessage
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
    /** 列表截图（账单、流水）里的每一笔支出，至少两笔；不是列表时是空的，确认 draft。 */
    val items: List<Entry> = emptyList(),
    /** 列表里勾上的那几笔（items 的下标）。null 表示用户还没动过，这时默认勾上没记过的。 */
    val selected: Set<Int>? = null,
)

/** 汇率下载的状态，显示在设置里。FAILED 是上一次没能连上网络（已经存下的汇率照常用）。 */
enum class RateStatus { IDLE, UPDATING, FAILED }

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

    private val _rateStatus = MutableStateFlow(RateStatus.IDLE)
    val rateStatus: StateFlow<RateStatus> = _rateStatus.asStateFlow()
    private var updatingRates: Job? = null

    private val _reviewReminder = MutableStateFlow(container.settings.reviewReminder)
    val reviewReminder: StateFlow<Boolean> = _reviewReminder.asStateFlow()

    /** 点了回顾提醒的通知，等着打开的那个月；打开以后清掉。 */
    private val _openReview = MutableStateFlow<YearMonth?>(null)
    val openReview: StateFlow<YearMonth?> = _openReview.asStateFlow()

    /** 调试版才显示「测试提醒」按钮。 */
    val debuggable: Boolean get() = container.debuggable

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
            // 记了比已有汇率更早的账时，补上那几天的汇率。
            refreshRates()
        }
    }

    fun setHomeCurrency(currency: Currency) {
        container.settings.homeCurrency = currency
        _homeCurrency.value = currency
    }

    fun setReviewReminder(enabled: Boolean) {
        container.settings.reviewReminder = enabled
        _reviewReminder.value = enabled
        if (enabled) container.reviewReminder.schedule() else container.reviewReminder.cancel()
    }

    /** 调试用：马上发一条这个月份的回顾提醒。 */
    fun sendTestReminder(month: YearMonth) = container.reviewReminder.sendNow(month)

    fun openReview(month: YearMonth) {
        _openReview.value = month
    }

    fun reviewOpened() {
        _openReview.value = null
    }

    /** 把回顾的总结卡存成图片，返回系统的分享面板。 */
    suspend fun shareImage(bitmap: Bitmap, name: String, chooserTitle: String): Intent =
        container.imageSharer.shareIntent(bitmap, name, chooserTitle)

    /**
     * 补上缺少的汇率。启动、回到前台、记了账之后调用；隔 [RATE_CHECK_INTERVAL_MS] 才看一次有没有新汇率。
     * force 是在设置里点了「立即更新」：不管上次什么时候看过，都看一次。
     */
    fun refreshRates(force: Boolean = false) {
        if (updatingRates?.isActive == true) return
        updatingRates = viewModelScope.launch {
            val settings = container.settings
            val now = System.currentTimeMillis()
            val checkNewer = force || now - settings.ratesCheckedAt >= RATE_CHECK_INTERVAL_MS
            _rateStatus.value = RateStatus.UPDATING
            _rateStatus.value = try {
                // 直接查数据库，刚记下的那笔也算在里面。
                container.rateUpdater.update(repository.observeEntries().first(), LocalDate.now(zone), zone, checkNewer)
                if (checkNewer) settings.ratesCheckedAt = now
                RateStatus.IDLE
            } catch (e: IOException) {
                RateStatus.FAILED
            }
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

    /** 列表截图：勾上、取消了某几笔。 */
    fun selectImportItems(selected: Set<Int>) = updateCurrent { it.copy(selected = selected) }

    /** 列表截图：改了其中一笔。 */
    fun editImportItem(index: Int, entry: Entry) = updateCurrent { shot ->
        shot.copy(items = shot.items.mapIndexed { i, item -> if (i == index) entry else item })
    }

    /** 列表截图：记下勾上的那几笔，换下一张。 */
    fun confirmImportItems(items: List<Entry>) {
        if (_imports.value.current == null || items.isEmpty()) return
        popImport()
        viewModelScope.launch {
            repository.addAll(items)
            _messages.emit(LedgerMessage.SavedSeveral(items.size))
            refreshRates()
        }
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
                // 截图里只写了时间、没写日期时，按截图那天算；不知道截图时间时按今天。
                val takenAt = shot.takenAt ?: System.currentTimeMillis()
                val read = recognized(shot, container.screenshotReader.read(shot.image, homeCurrency.value, takenAt, zone), takenAt)
                // 识别途中用户可能已经跳过了这一张，那就什么也不改。
                updateImports { queue -> queue.copy(shots = queue.shots.map { if (it.image == shot.image) read else it }) }
            }
        }
    }

    /** 识别结果填成待确认的账。截图里没写时间时，用截图的时间（不知道时是现在）。 */
    private fun recognized(shot: PendingShot, parsed: ParsedReceipt, takenAt: Long): PendingShot {
        fun entry(amount: Money?, category: Category?, merchant: String?, time: Long?) = Entry(
            amount = amount ?: Money(0, homeCurrency.value),
            category = category ?: Category.OTHER,
            merchant = merchant.orEmpty(),
            time = time ?: takenAt,
            source = Source.SCREENSHOT,
            imagePath = shot.image,
        )
        return shot.copy(
            draft = entry(parsed.amount, parsed.category, parsed.merchant, parsed.time),
            candidates = parsed.amountCandidates,
            items = parsed.items.map { entry(it.amount, it.category, it.merchant, it.time) },
        )
    }

    private fun updateCurrent(change: (PendingShot) -> PendingShot) = updateImports { queue ->
        val shot = queue.current ?: return@updateImports queue
        queue.copy(shots = listOf(change(shot)) + queue.shots.drop(1))
    }

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

        /** 汇率每个工作日只更新一次，看得太勤没有用。 */
        private const val RATE_CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LedgerApplication
                LedgerViewModel(app.container, createSavedStateHandle())
            }
        }
    }
}
