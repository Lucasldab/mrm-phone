package io.lldab.mrmphone.ui

import io.lldab.mrmphone.data.ApiException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Short lower-case message for an error line. */
fun errorText(e: Throwable): String = when (e) {
    is ApiException -> if (e.code == 401) "unauthorized · check the token" else "${e.code} ${e.message}"
    is UnknownHostException -> "hearth unreachable (dns) · tailscale on?"
    is ConnectException -> "hearth unreachable · is mrm serve running?"
    is SocketTimeoutException -> "timed out"
    else -> e.message ?: e.javaClass.simpleName
}
