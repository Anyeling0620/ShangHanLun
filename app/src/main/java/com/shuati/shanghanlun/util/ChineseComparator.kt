package com.shuati.shanghanlun.util

import kotlin.text.iterator

class ChineseStringComparator : Comparator<String> {
    private val chineseNumberMap = mapOf(
        '一' to 1, '二' to 2, '三' to 3, '四' to 4, '五' to 5,
        '六' to 6, '七' to 7, '八' to 8, '九' to 9, '十' to 10,
        '零' to 0
    )

    override fun compare(s1: String?, s2: String?): Int {
        if (s1 == null || s2 == null) return 0
        val n1 = extractNumber(s1)
        val n2 = extractNumber(s2)
        if (n1 != -1 && n2 != -1) return n1.compareTo(n2)
        return s1.compareTo(s2)
    }

    private fun extractNumber(s: String): Int {
        val digitMatch = Regex("\\d+").find(s)
        if (digitMatch != null) return digitMatch.value.toInt()
        val cnMatch = Regex("[一二三四五六七八九十]+").find(s)
        if (cnMatch != null) return parseChineseNumber(cnMatch.value)
        return -1
    }

    private fun parseChineseNumber(s: String): Int {
        var result = 0
        var temp = 0
        for (char in s) {
            val value = chineseNumberMap[char] ?: continue
            if (value == 10) {
                if (temp == 0) temp = 1
                result += temp * 10
                temp = 0
            } else {
                temp = value
            }
        }
        result += temp
        return if (result == 0) -1 else result
    }
}