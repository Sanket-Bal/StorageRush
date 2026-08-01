package com.example.swipeclean.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.swipeclean.data.repository.AppStats
import com.example.swipeclean.data.repository.StatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatsViewModel(context: Context) : ViewModel() {

    private val statsRepository = StatsRepository(context)

    private val _stats = MutableStateFlow<AppStats?>(null)
    val stats: StateFlow<AppStats?> = _stats.asStateFlow()

    init {
        loadStats()
    }

    /**
     * Load stats from repository
     */
    fun loadStats() {
        viewModelScope.launch {
            try {
                statsRepository.getStats().collect { appStats ->
                    _stats.value = appStats
                }
            } catch (e: Exception) {
                // Log error if needed
                _stats.value = AppStats()
            }
        }
    }

    /**
     * Clear all stats
     */
    fun clearAllStats() {
        viewModelScope.launch {
            try {
                statsRepository.clearAllStats()
                loadStats()
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    /**
     * Get current stats synchronously (for immediate use)
     */
    fun getCurrentStats(): AppStats {
        return _stats.value ?: AppStats()
    }
}