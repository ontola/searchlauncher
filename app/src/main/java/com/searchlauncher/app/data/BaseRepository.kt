package com.searchlauncher.app.data

import com.searchlauncher.app.util.SystemUtils
import kotlinx.coroutines.CancellationException

abstract class BaseRepository {

  protected suspend fun <T> safeCall(
    tag: String = "BaseRepository",
    errorMessage: String = "Error occurred",
    block: suspend () -> T,
  ): Result<T> {
    return try {
      Result.success(block())
    } catch (e: CancellationException) {
      throw e
    } catch (e: Throwable) {
      SystemUtils.logError(tag, errorMessage, e)
      Result.failure(e)
    }
  }
}
