package com.utaumarket.mobile.ui.screens

import com.utaumarket.mobile.data.model.NetworkStatus
import com.utaumarket.mobile.data.model.ResourceItem

/**
 * 一个声库系列：同一角色的多个分支版本。
 *
 * 有些声库分支极多（重音テト 11 个、波音リツ 9 个、欲音ルコ 9 个），
 * 全平铺在列表里会把别的东西全淹掉，所以主列表只出一条，点进去看二级版本页。
 * versions 只有一个时就是普通条目，不显示二级入口。
 */
data class SeriesGroup(
    val head: ResourceItem,
    val versions: List<ResourceItem>,
) {
    val isSeries: Boolean get() = versions.size > 1
    val key: String get() = head.series.ifBlank { head.id }

    /** 该系列在主列表里的固定位置（0 = 不固定） */
    val pin: Int get() = versions.maxOfOrNull { it.seriesPos } ?: 0

    /**
     * 系列级链接：从**所有版本**里取第一个非空的。
     *
     * 之前卡片直接用 group.head 的 detailUrl，而 head 只是排序第一的版本 ——
     * 它恰好没填官网时，整个系列就一个链接都不显示
     *（用户报的「为什么九十九紫苑没有官网」就是这种情况）。
     */
    val detailUrl: String get() = versions.firstOrNull { it.detailUrl.isNotBlank() }?.detailUrl.orEmpty()
    val moegirlUrl: String get() = versions.firstOrNull { it.moegirlUrl.isNotBlank() }?.moegirlUrl.orEmpty()
    val videoUrl: String get() = versions.firstOrNull { it.videoUrl.isNotBlank() }?.videoUrl.orEmpty()
    val xUrl: String get() = versions.firstOrNull { it.xUrl.isNotBlank() }?.xUrl.orEmpty()

    /** 系列整体网络状态：只要有一个能直连就不算全红 */
    val network: NetworkStatus
        get() {
            val all = versions.map { it.network }
            return when {
                all.any { it == NetworkStatus.GREEN } -> NetworkStatus.GREEN
                all.any { it == NetworkStatus.YELLOW } -> NetworkStatus.YELLOW
                all.isNotEmpty() -> all.first()
                else -> NetworkStatus.UNKNOWN
            }
        }

    val totalSize: Long get() = versions.sumOf { it.fileSize }
}

/** 把扁平列表按 series 归并，保持原有顺序（系列出现在它第一个成员的位置）。 */
fun List<ResourceItem>.groupBySeries(): List<SeriesGroup> {
    val bySeries = LinkedHashMap<String, MutableList<ResourceItem>>()
    for (item in this) {
        if (item.series.isNotBlank()) {
            bySeries.getOrPut(item.series) { mutableListOf() }.add(item)
        }
    }
    val out = ArrayList<SeriesGroup>(size)
    val emitted = HashSet<String>()
    for (item in this) {
        if (item.series.isBlank()) {
            out.add(SeriesGroup(item, listOf(item)))
        } else if (emitted.add(item.series)) {
            out.add(SeriesGroup(head = item, versions = bySeries[item.series].orEmpty()))
        }
    }
    return out
}

/**
 * 是否真正「仅资料」—— 没有任何下载途径。
 *
 * 【为什么不用清单里的 reference_only】
 * 有 62 条虽然有网盘 / 平台链接，但清单里被标成了 reference_only，
 * 直接按它过滤会把「能跳过去下载」的条目一起藏掉（用户反馈：
 * 隐藏仅资料后网盘条目全消失了）。
 * 判定标准改成「有没有任何可点的下载入口」才准。
 */
internal fun com.utaumarket.mobile.data.model.ResourceItem.isInfoOnly(): Boolean =
    url.isBlank() &&
        netdiskUrl.isBlank() &&
        platformOf(this) == DownloadPlatform.NONE

