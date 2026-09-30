package com.phlox.tvwebbrowser.activity.history

import com.phlox.tvwebbrowser.model.HistoryItem
import com.phlox.tvwebbrowser.singleton.AppDatabase
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class HistoryModel: ActiveModel() {
    val lastLoadedItems = ObservableValue<List<HistoryItem>>(ArrayList())
    private var loading = false
    private var endReached = false
    var searchQuery = ""


    fun loadItems(eraseOldResults: Boolean, offset: Long = 0) = modelScope.launch(Dispatchers.Main) {
        if (loading || (endReached && !eraseOldResults && offset > 0)) {
            return@launch
        }
        loading = true
        try {
            if (eraseOldResults || offset == 0L) endReached = false
            val items = if (searchQuery.isEmpty()) {
                AppDatabase.db.historyDao().allByLimitOffset(offset)
            } else {
                AppDatabase.db.historyDao().search(searchQuery, searchQuery)
            }
            endReached = items.size < 100
            lastLoadedItems.value = items
        } finally {
            loading = false
        }
    }
}
