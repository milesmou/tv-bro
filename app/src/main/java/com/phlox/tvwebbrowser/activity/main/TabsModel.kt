package com.phlox.tvwebbrowser.activity.main

import android.util.Log
import android.view.View
import android.view.ViewGroup
import com.phlox.tvwebbrowser.AppContext
import com.phlox.tvwebbrowser.TVBro
import com.phlox.tvwebbrowser.model.HostConfig
import com.phlox.tvwebbrowser.model.WebTabState
import com.phlox.tvwebbrowser.singleton.AppDatabase
import com.phlox.tvwebbrowser.utils.Utils
import com.phlox.tvwebbrowser.utils.activemodel.ActiveModel
import com.phlox.tvwebbrowser.utils.observable.ObservableList
import com.phlox.tvwebbrowser.utils.observable.ObservableValue
import com.phlox.tvwebbrowser.webengine.WebEngineWindowProviderCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import com.phlox.tvwebbrowser.webengine.isGecko
import kotlinx.coroutines.withContext
import java.net.URL

class TabsModel : ActiveModel() {
    companion object {
        var TAG: String = TabsModel::class.java.simpleName
    }

    var loaded = false
    val currentTab = ObservableValue<WebTabState?>(null)
    val tabsStates = ObservableList<WebTabState>()
    private val config = AppContext.provideConfig()
    private var incognitoMode = config.incognitoMode
    private var lastOpenedTab: WebTabState? = null

    fun registerOpenedTab(tab: WebTabState) {
        lastOpenedTab = tab
    }

    fun checkpoint(retainLast: Boolean = false) {
        val tab = currentTab.value
        tab?.onPause()
        TVBro.instance.sessionSaveJob = TVBro.instance.sessionScope.launch {
            try {
                if (retainLast) retainLastOpenedTab()
                else tab?.let { saveTab(it) }
            } catch (e: Exception) {
                Log.e(TAG, "Unable to save browser session", e)
            }
        }
    }

    suspend fun retainLastOpenedTab() = TVBro.instance.sessionMutex.withLock {
        val keep = lastOpenedTab?.takeIf { candidate -> tabsStates.any { it === candidate } }
            ?: tabsStates.maxByOrNull { it.id } ?: return@withLock
        persistTab(keep)
        val others = tabsStates.filter { it !== keep }
        tabsStates.replaceAll(listOf(keep))
        val dao = AppDatabase.db.tabsDao()
        others.forEach {
            dao.delete(it)
            it.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
            if (it.wvStateFileName == keep.wvStateFileName) it.wvStateFileName = null
            if (it.thumbnailHash == keep.thumbnailHash) it.thumbnailHash = null
            withContext(Dispatchers.IO) { it.removeFiles() }
        }
        keep.selected = true
        keep.position = 0
        dao.update(keep)
        lastOpenedTab = keep
    }

    init {
        tabsStates.subscribe({
            //auto-update positions on any list change
            var positionsChanged = false
            tabsStates.forEachIndexed { index, webTabState ->
                if (webTabState.position != index) {
                    webTabState.position = index
                    positionsChanged = true
                }
            }
            if (positionsChanged) {
                val tabsListClone = listOf(*tabsStates.toTypedArray())
                modelScope.launch(Dispatchers.Main) {
                    val tabsDao = AppDatabase.db.tabsDao()
                    tabsDao.updatePositions(tabsListClone)
                }
            }
        }, false)
    }

    fun loadState() = modelScope.launch(Dispatchers.Main) {
        TVBro.instance.sessionSaveJob?.join()
        if (loaded) {
            //check is incognito mode changed
            if (incognitoMode != config.incognitoMode) {
                incognitoMode = config.incognitoMode
                loaded = false
            } else {
                return@launch
            }
        }
        val tabsDao = AppDatabase.db.tabsDao()
        val savedTabs = tabsDao.getAll(config.incognitoMode)
        // Also trim a session left behind by an OS process termination.
        val keep = savedTabs.maxByOrNull { it.id }
        savedTabs.filter { it !== keep }.forEach { tab ->
            tabsDao.delete(tab)
            if (tab.wvStateFileName == keep?.wvStateFileName) tab.wvStateFileName = null
            if (tab.thumbnailHash == keep?.thumbnailHash) tab.thumbnailHash = null
            withContext(Dispatchers.IO) { tab.removeFiles() }
        }
        keep?.let {
            it.selected = true
            it.position = 0
            tabsDao.update(it)
        }
        tabsStates.replaceAll(listOfNotNull(keep))
        lastOpenedTab = keep
        loaded = true
    }

    suspend fun saveTab(tab: WebTabState) = TVBro.instance.sessionMutex.withLock {
        if (tabsStates.none { it === tab }) return@withLock
        persistTab(tab)
    }

    private suspend fun persistTab(tab: WebTabState) {
        val snapshot = tab.copy().apply { savedState = tab.savedState }
        val isGecko = tab.webEngine.isGecko()
        val tabsDB = AppDatabase.db.tabsDao()
        if (snapshot.selected) {
            tabsDB.unselectAll(snapshot.incognito)
        }
        withContext(Dispatchers.IO) {
            snapshot.saveWebViewStateToFile(isGecko)
        }
        tab.wvStateFileName = snapshot.wvStateFileName
        if (snapshot.id != 0L) {
            tabsDB.update(snapshot)
        } else {
            tab.id = tabsDB.insert(snapshot)
        }
    }

    fun onCloseTab(tab: WebTabState) {
        tab.webEngine.onDetachFromWindow(completely = true, destroyTab = true)
        tabsStates.remove(tab)
        TVBro.instance.sessionScope.launch {
            TVBro.instance.sessionMutex.withLock {
                val tabsDB = AppDatabase.db.tabsDao()
                tabsDB.delete(tab)
                withContext(Dispatchers.IO) { tab.removeFiles() }
            }
        }
    }

    fun onCloseAllTabs() = TVBro.instance.sessionScope.launch {
        val tabsClone = ArrayList(tabsStates)
        tabsStates.clear()
        tabsClone.forEach { it.webEngine.onDetachFromWindow(completely = true, destroyTab = true) }
        TVBro.instance.sessionMutex.withLock {
            val tabsDB = AppDatabase.db.tabsDao()
            tabsDB.deleteAll(config.incognitoMode)
            withContext(Dispatchers.IO) {
                tabsClone.forEach { it.removeFiles() }
            }
        }
    }

    fun onDetachActivity() {
        for (tab in tabsStates) {
            tab.webEngine.onDetachFromWindow(completely = true, destroyTab = false)
        }
    }

    fun changeTab(
        newTab: WebTabState,
        webViewProvider: (tab: WebTabState) -> View?,
        webViewParent: ViewGroup,
        webEngineWindowProviderCallback: WebEngineWindowProviderCallback
    ) {
        if (currentTab.value == newTab && newTab.webEngine.getView() != null) return
        if (currentTab.value != newTab) {
            tabsStates.forEach {
                it.selected = false
            }
            currentTab.value?.apply {
                webEngine.onDetachFromWindow(completely = false, destroyTab = false)
                onPause()
                TVBro.instance.sessionScope.launch { saveTab(this@apply) }
            }

            newTab.selected = true
            currentTab.value = newTab
        }
        var wv = newTab.webEngine.getView()
        var needReloadUrl = false
        if (wv == null) {
            wv = webViewProvider(newTab)
            if (wv == null) {
                return
            }
            needReloadUrl = !newTab.restoreWebView()
        }
        newTab.webEngine.onAttachToWindow(webEngineWindowProviderCallback, webViewParent)
        if (needReloadUrl) {
            newTab.webEngine.loadUrl(newTab.url)
        }
        newTab.webEngine.setNetworkAvailable(Utils.isNetworkConnected(TVBro.instance))
    }

    suspend fun findHostConfig(tab: WebTabState, createIfNotFound: Boolean): HostConfig? {
        Log.d(WebTabState.TAG, "findOrCreateHostConfig")
        val currentHostName = try {
            URL(tab.url).host
        } catch (e: Exception) {
            Log.w(WebTabState.TAG, "Can not parse current url host: $e")
            return null
        }
        var hostConfig = tab.cachedHostConfig
        if (hostConfig == null || hostConfig.hostName != currentHostName) {
            val db = com.phlox.tvwebbrowser.singleton.AppDatabase.db.hostsDao()
            hostConfig = db.findByHostName(currentHostName)
            if (hostConfig == null && createIfNotFound) {
                hostConfig = HostConfig(currentHostName)
                hostConfig.id = db.insert(hostConfig)
            }
            tab.cachedHostConfig = hostConfig
        }
        return hostConfig
    }

    suspend fun changePopupBlockingLevel(newLevel: Int, tab: WebTabState) {
        val hostConfig = findHostConfig(tab,true) ?: return
        hostConfig.popupBlockLevel = newLevel
        AppDatabase.db.hostsDao().update(hostConfig)
    }
}
