package com.guideme.travel.data.auth

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingEmailLinkStore @Inject constructor() {
    private val _pendingLink = MutableStateFlow<String?>(null)
    val pendingLink: StateFlow<String?> = _pendingLink.asStateFlow()

    fun setLink(link: String) {
        _pendingLink.value = link
    }

    fun consumeLink(): String? {
        val link = _pendingLink.value
        _pendingLink.value = null
        return link
    }
}
