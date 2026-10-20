package com.hdlee73.englishstudy.roleplay

/**
 * One speaking scene. [persona] and [goal] go into the prompt of Gemini Nano; [script] is the fixed script used on devices
 * without it (only some themes have one, the others need Gemini Nano). [free] is the open conversation without a topic.
 */
data class RoleplayTheme(
    val id: String,
    val emoji: String,
    val title: String,
    val group: String,
    val goal: String,
    val persona: String,
    val opener: String,
    val free: Boolean = false
) {
    /** The fixed script of this theme, if it has one. */
    val script: Scenario? get() = Scenarios.ALL.firstOrNull { it.title == title }
}

object RoleplayThemes {
    /** 프리토킹 always comes first. */
    val FREE = RoleplayTheme(
        "free", "💬", "프리토킹", "자유 대화", "주제 없이 편하게 이야기하기",
        "a warm and curious friend who chats about anything", "", free = true
    )

    private fun t(id: String, emoji: String, title: String, group: String, goal: String, persona: String, opener: String) =
        RoleplayTheme(id, emoji, title, group, goal, persona, opener)

    val SCENES: List<RoleplayTheme> = listOf(
        // 일상
        t("cafe", "☕", "카페 주문", "일상", "주문하고 사이즈·결제까지 마치기", "a friendly barista at Daily Grind Coffee", "Hi! Welcome to Daily Grind Coffee. What can I get for you today?"),
        t("restaurant", "🍽️", "식당 주문", "일상", "자리 안내부터 주문·계산까지 하기", "a polite waiter at a casual restaurant", "Good evening! Do you have a reservation?"),
        t("mart", "🛒", "마트·편의점", "일상", "물건 위치를 묻고 계산하기", "a helpful cashier at a supermarket", "Hi! Did you find everything you were looking for?"),
        t("taxi", "🚕", "택시 타기", "일상", "목적지를 말하고 요금 내기", "a talkative taxi driver", "Hi there! Where are you headed today?"),
        t("directions", "🗺️", "길 묻기", "일상", "길을 묻고 알아들었는지 확인하기", "a kind local person on the street", "Hey, you look a little lost. Can I help you find something?"),
        t("transit", "🚇", "대중교통", "일상", "표 사는 법과 환승 묻기", "a station staff member at a subway station", "Hello! How can I help you with your trip today?"),
        t("pharmacy", "💊", "약국·병원", "일상", "증상을 설명하고 안내 받기", "a caring pharmacist", "Hi, what seems to be the problem today?"),
        t("hair", "💇", "미용실", "일상", "원하는 머리 스타일 설명하기", "a friendly hairdresser", "Welcome! What are we doing with your hair today?"),
        // 여행
        t("airport", "✈️", "공항 체크인", "여행", "짐 부치고 좌석 고르고 탑승권 받기", "an airline check-in agent", "Good morning! May I see your passport and booking number, please?"),
        t("immigration", "🛂", "입국 심사", "여행", "방문 목적과 체류 기간 답하기", "an immigration officer", "Good afternoon. May I see your passport, please?"),
        t("hotel", "🏨", "호텔 프론트", "여행", "체크인하고 조식·모닝콜 요청하기", "a hotel front desk clerk", "Good evening, welcome to The Bayview. How may I help you?"),
        t("rental", "🚗", "렌터카", "여행", "차종을 고르고 보험·반납 조건 확인하기", "a car rental agent", "Hi! Do you have a reservation with us?"),
        t("tourist", "🏛️", "관광 안내소", "여행", "가볼 곳과 가는 방법 물어보기", "a tourist information desk staff member", "Welcome! How can I help you plan your day?"),
        t("refund", "🛍️", "쇼핑·환불", "여행", "사이즈 교환이나 환불을 요청하기", "a store clerk in a clothing shop", "Hi! How can I help you today?"),
        t("flight", "🛫", "기내에서", "여행", "음료·식사를 주문하고 부탁하기", "a flight attendant", "Hello! Can I get you something to drink?"),
        // 직장
        t("interview", "💼", "면접 연습", "직장", "자기소개부터 마지막 질문까지 답하기", "a hiring manager interviewing the learner", "Welcome, thanks for coming in. Could you briefly introduce yourself?"),
        t("networking", "🤝", "자기소개·네트워킹", "직장", "처음 만난 사람에게 나와 일을 소개하기", "a friendly professional at a networking event named Alex", "Hi! I don't think we've met. I'm Alex."),
        t("meeting", "🗣️", "회의 발언", "직장", "진행 상황을 말하고 질문에 답하기", "a meeting chair who asks for status updates", "Thanks, everyone. Let's start with your update. How is your part going?"),
        t("phone", "📞", "전화 응대", "직장", "전화를 받고 용건을 묻고 메모하기", "a business caller from another company named Sam", "Hello, this is Sam from Orion Tech. Is this a good time?"),
        t("report", "📊", "업무 보고", "직장", "일의 상태와 문제, 다음 계획 보고하기", "a manager who asks for a quick project report", "Hi, do you have a minute? How is the project going?"),
        t("trip", "🧳", "출장 스몰토크", "직장", "출장 동료와 가볍게 이야기하기", "a friendly colleague on a business trip", "Long flight, huh? How was your trip?"),
        // 사회
        t("friend", "👋", "새 친구 사귀기", "사회", "이름·사는 곳·취미 물어보며 친해지기", "a friendly person named Jamie meeting the learner for the first time", "Hi! I'm Jamie. Are you new here?"),
        t("date", "💬", "소개팅", "사회", "취미·여행 이야기로 스몰토크 하기", "a cheerful person on a first date", "Hi! Is this our seat number six? I'm excited to practice small talk with you!"),
        t("neighbor", "🏘️", "이웃과 대화", "사회", "새 이웃과 인사하고 동네 정보 묻기", "a friendly neighbor", "Hi! Are you the new neighbor in 302?"),
        t("roommate", "🏠", "룸메이트", "사회", "청소·소음 같은 생활 규칙 정하기", "a roommate who wants to talk about house rules", "Hey, can we talk about the apartment for a minute?"),
        t("party", "🎉", "파티·모임", "사회", "모르는 사람들과 어울리며 대화하기", "a cheerful party host", "Hey, you made it! Can I get you a drink?"),
        t("hobby", "🎨", "취미 모임", "사회", "모임에 들어온 이유와 취미 이야기하기", "a welcoming member of a hobby club", "Welcome to the club! What got you interested in joining?"),
        // 문제 해결
        t("callcenter", "🎧", "고객센터", "문제 해결", "문제를 설명하고 해결 방법 요청하기", "a patient customer service agent", "Thank you for calling. How can I help you today?"),
        t("housing", "🔑", "집 구하기", "문제 해결", "집 상태·월세·계약 조건 묻기", "a landlord showing an apartment", "Hi! Thanks for coming to see the apartment."),
        t("lost", "👜", "분실물 신고", "문제 해결", "잃어버린 물건의 특징을 설명하기", "a staff member at the lost and found desk", "Hello, how can I help you?")
    )

    /** 프리토킹 first, then the 30 scenes. */
    val ALL: List<RoleplayTheme> = listOf(FREE) + SCENES
}
