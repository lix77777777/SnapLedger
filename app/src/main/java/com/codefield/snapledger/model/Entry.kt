package com.codefield.snapledger.model

/** 消费类别。名字和颜色在界面的资源里，这里只有编号。 */
enum class Category {
    FOOD,
    CAFE,
    GROCERY,
    TRANSPORT,
    SHOPPING,
    HOUSING,
    ENTERTAINMENT,
    HEALTH,
    EDUCATION,
    TRAVEL,
    OTHER,
}

/** 这笔账是怎么记的。 */
enum class Source { MANUAL, SCREENSHOT }

data class GeoPoint(val latitude: Double, val longitude: Double)

/**
 * 一笔消费。金额保存付款时的原始货币，统计时再按那天的汇率换算成主货币，
 * 这样改了主货币也不用改数据。
 */
data class Entry(
    val id: Long = 0,
    val amount: Money,
    val category: Category,
    val merchant: String,
    /** 付款时间（毫秒时间戳）。 */
    val time: Long,
    val note: String = "",
    val location: GeoPoint? = null,
    val source: Source = Source.MANUAL,
    /** 截图记账时保存的截图副本（应用自己的文件夹里的路径）。 */
    val imagePath: String? = null,
)
