package fyi.goodbye.fridgy.ui.shared

/**
 * A generic sealed interface for representing common UI states across the application.
 * 
 * This provides a standardized pattern for handling loading, success, and error states
 * in ViewModels, reducing code duplication across the codebase.
 * 
 * Usage:
 * ```kotlin
 * private val _uiState = MutableStateFlow<UiState<MyData>>(UiState.Loading)
 * val uiState: StateFlow<UiState<MyData>> = _uiState.asStateFlow()
 * 
 * // Set success:
 * _uiState.value = UiState.Success(myData)
 * 
 * // Set error:
 * _uiState.value = UiState.Error("Something went wrong")
 * 
 * // In Compose:
 * when (val state = uiState) {
 *     is UiState.Loading -> LoadingIndicator()
 *     is UiState.Success -> ContentView(state.data)
 *     is UiState.Error -> ErrorView(state.message)
 * }
 * ```
 * 
 * @param T The type of data held in the Success state.
 */
sealed interface UiState<out T> {
    /**
     * Represents a loading state where data is being fetched or processed.
     */
    data object Loading : UiState<Nothing>
    
    /**
     * Represents a successful state containing the loaded data.
     * 
     * @property data The successfully loaded data of type T.
     */
    data class Success<T>(val data: T) : UiState<T>
    
    /**
     * Represents an error state with an error message.
     * 
     * @property message A human-readable error message to display to the user.
     */
    data class Error(val message: String) : UiState<Nothing>
}

/**
 * Extension function to get the data from a Success state, or null otherwise.
 * 
 * @return The data if in Success state, null otherwise.
 */
fun <T> UiState<T>.dataOrNull(): T? = when (this) {
    is UiState.Success -> data
    else -> null
}

/**
 * Extension function to check if the state is loading.
 */
val <T> UiState<T>.isLoading: Boolean
    get() = this is UiState.Loading

/**
 * Extension function to check if the state is an error.
 */
val <T> UiState<T>.isError: Boolean
    get() = this is UiState.Error

/**
 * Extension function to check if the state is successful.
 */
val <T> UiState<T>.isSuccess: Boolean
    get() = this is UiState.Success

/**
 * Transform the data in a Success state using the provided mapping function.
 * Returns the same Loading or Error state if not in Success state.
 * 
 * @param transform The transformation function to apply to the data.
 * @return A new UiState with the transformed data, or the original Loading/Error state.
 */
inline fun <T, R> UiState<T>.map(transform: (T) -> R): UiState<R> = when (this) {
    is UiState.Loading -> UiState.Loading
    is UiState.Success -> UiState.Success(transform(data))
    is UiState.Error -> UiState.Error(message)
}
