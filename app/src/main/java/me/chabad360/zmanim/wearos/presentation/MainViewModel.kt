package me.chabad360.zmanim.wearos.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalTime

/**
 * Interim ViewModel exposing UI state needed by the main Wear screen.
 * Data is stubbed for now and will be wired to the real repository/use cases later.
 */
class MainViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun refreshLocation() {
        viewModelScope.launch {
            // simple fake refresh spinner for now
            _uiState.value = _uiState.value.copy(isRefreshing = true, error = null)
            _uiState.value = _uiState.value.copy(
                isRefreshing = false,
                location = _uiState.value.location
            )
        }
    }

    fun toggleEdit() {
        _uiState.value = _uiState.value.copy(showingEdit = !_uiState.value.showingEdit)
    }

    fun toggleZman(id: String) {
        _uiState.value = _uiState.value.copy(
            zmanim = _uiState.value.zmanim.map { item ->
                if (item.id == id) item.copy(enabled = !item.enabled) else item
            }
        )
    }
}

data class UiState(
    val location: String? = "Jerusalem",
    val isRefreshing: Boolean = false,
    val isStale: Boolean = false,
    val error: String? = null,
    val zmanim: List<ZmanItem> = listOf(
        ZmanItem(id = "alos", name = "Alos", time = LocalTime.of(5, 12), enabled = true),
        ZmanItem(id = "netz", name = "Hanetz", time = LocalTime.of(6, 37), enabled = true),
        ZmanItem(id = "shema", name = "Sof Zman Shema", time = LocalTime.of(9, 12), enabled = true),
        ZmanItem(id = "tefila", name = "Sof Zman Tefila", time = LocalTime.of(10, 16), enabled = true),
        ZmanItem(id = "mincha", name = "Mincha Gedola", time = LocalTime.of(13, 22), enabled = true),
        ZmanItem(id = "shkia", name = "Shkia", time = LocalTime.of(19, 45), enabled = true)
    ),
    val showingEdit: Boolean = false
)

data class ZmanItem(
    val id: String,
    val name: String,
    val time: LocalTime?,
    val enabled: Boolean
)

