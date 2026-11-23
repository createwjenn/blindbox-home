package com.jenn.blindboxworld.content

internal const val PAINTING_TIME_MS = 60_000L

internal fun worldRewardCoins(elapsedMs: Long): Int =
    if (elapsedMs <= PAINTING_TIME_MS) 10
    else (9L - (elapsedMs - PAINTING_TIME_MS - 1L) / 10_000L).coerceAtLeast(1L).toInt()
