package com.distractionfree.app

import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

/**
 * Shared bounded thread pool for TCP/UDP relay connections. Threads are
 * expensive (each one is real OS/pthread state); a raw thread-per-connection
 * design crashed the app on-device with pthread_create OOM once the phone's
 * normal background traffic volume exceeded what a quiet test session ever
 * produced. Reusing a capped pool keeps thread count bounded regardless of
 * how many flows TcpNat/UdpNat try to open.
 */
object RelayExecutor {
    val shared: ThreadPoolExecutor = Executors.newFixedThreadPool(48) as ThreadPoolExecutor
}
