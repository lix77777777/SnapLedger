package com.codefield.snapledger.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.codefield.snapledger.AppContainer
import com.codefield.snapledger.LedgerApplication
import com.codefield.snapledger.logic.RateTable
import com.codefield.snapledger.model.Currency
import com.codefield.snapledger.model.Entry
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 只显示一次的提示。删除的提示带着那一笔，方便撤销。 */
sealed interface LedgerMessage {
    data object Saved : LedgerMessage
    data class Deleted(val entry: Entry) : LedgerMessage
}

/** 界面和数据之间的桥梁，所有页面共用一个。 */
class LedgerViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.repository

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

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LedgerApplication
                LedgerViewModel(app.container)
            }
        }
    }
}
