package com.storagerush.app.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.storagerush.app.data.repository.PlayerRepository
import com.storagerush.app.data.repository.PlayerState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Exposes player progression (level, XP, streak) as a StateFlow for the
 * Deck screen's progress card. DataStore's Flow already emits on every
 * write — including writes from TrashBinViewModel.recordCleanup() — so
 * this updates live with no manual refresh needed as long as both
 * ViewModels are reading/writing the same underlying DataStore file.
 */
class PlayerViewModel(context: Context) : ViewModel() {

    private val playerRepository = PlayerRepository(context)

    val playerState: StateFlow<PlayerState> = playerRepository.getPlayerState()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = PlayerState()
        )
}