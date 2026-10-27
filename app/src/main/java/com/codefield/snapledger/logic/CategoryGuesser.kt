package com.codefield.snapledger.logic

import com.codefield.snapledger.model.Category

/**
 * 按商家名里的关键词猜类别。按顺序找，先找到的算：
 * 「쿠팡이츠」（外卖）要排在「쿠팡」（购物）前面。猜不出来时返回 null，由用户自己选。
 */
object CategoryGuesser {

    private val KEYWORDS: List<Pair<Category, List<String>>> = listOf(
        Category.CAFE to listOf(
            "스타벅스", "starbucks", "투썸", "twosome", "이디야", "ediya", "메가커피", "메가mgc", "컴포즈커피", "빽다방",
            "할리스", "폴바셋", "커피", "카페", "coffee", "cafe", "瑞幸", "luckin", "星巴克", "咖啡", "奶茶", "喜茶", "蜜雪",
            "茶百道", "霸王茶姬",
        ),
        Category.FOOD to listOf(
            "쿠팡이츠", "배달의민족", "배민", "요기요", "맥도날드", "mcdonald", "버거킹", "burger", "롯데리아", "맘스터치",
            "식당", "국밥", "치킨", "피자", "pizza", "김밥", "분식", "美团外卖", "饿了么", "外卖", "餐厅", "饭店", "肯德基",
            "kfc", "麦当劳", "火锅", "面馆", "restaurant",
        ),
        Category.GROCERY to listOf(
            "편의점", "gs25", "cu", "세븐일레븐", "7-eleven", "7eleven", "이마트24", "emart24", "이마트", "emart",
            "홈플러스", "롯데마트", "다이소", "daiso", "便利店", "超市", "盒马", "全家", "familymart", "罗森", "lawson",
        ),
        Category.TRANSPORT to listOf(
            "택시", "카카오t", "티머니", "t-money", "tmoney", "지하철", "버스", "코레일", "korail", "srt", "ktx", "주유",
            "地铁", "公交", "滴滴", "打车", "高铁", "12306", "加油", "uber", "taxi",
        ),
        Category.ENTERTAINMENT to listOf(
            "cgv", "롯데시네마", "메가박스", "megabox", "넷플릭스", "netflix", "유튜브", "youtube", "멜론", "melon",
            "스팀", "steam", "노래방", "pc방", "电影", "爱奇艺", "腾讯视频", "bilibili", "网易云", "ktv",
        ),
        Category.HEALTH to listOf(
            "병원", "의원", "약국", "치과", "한의원", "医院", "药店", "药房", "诊所", "pharmacy", "clinic", "hospital",
        ),
        Category.EDUCATION to listOf("교보문고", "영풍문고", "서점", "학원", "등록금", "书店", "学费", "培训", "bookstore"),
        Category.TRAVEL to listOf(
            "호텔", "hotel", "숙박", "야놀자", "여기어때", "에어비앤비", "airbnb", "대한항공", "아시아나", "항공",
            "酒店", "机票", "携程", "航空", "agoda",
        ),
        Category.HOUSING to listOf(
            "관리비", "월세", "전기요금", "가스요금", "수도요금", "통신요금", "房租", "物业", "电费", "水费", "燃气", "话费", "宽带",
        ),
        Category.SHOPPING to listOf(
            "쿠팡", "coupang", "올리브영", "무신사", "musinsa", "지마켓", "gmarket", "11번가", "淘宝", "taobao", "天猫",
            "tmall", "京东", "拼多多", "唯品会", "amazon", "아마존", "유니클로", "uniqlo", "zara",
        ),
    )

    private val SEPARATORS = Regex("""[^\p{L}\p{N}]+""")

    fun guess(text: String): Category? {
        val lower = text.lowercase()
        val tokens = lower.split(SEPARATORS).filter { it.isNotEmpty() }.toSet()
        return KEYWORDS.firstOrNull { (_, words) -> words.any { matches(it, lower, tokens) } }?.first
    }

    /** 「cu」「kfc」这种很短的英文词只按整个词匹配，不然「cute」也会被当成便利店。 */
    private fun matches(word: String, lower: String, tokens: Set<String>): Boolean =
        if (word.length <= 3 && word.all { it.code < 128 }) word in tokens else word in lower
}
