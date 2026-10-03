package com.hdlee73.englishstudy.dictionary

import java.util.Locale

/** Curated high-frequency multi-word entries. They take priority over network-only definitions. */
internal object CorePhrases {
    private fun entry(word: String, korean: String, english: String, examples: String) = WordEntry(
        word = word, ipa = "", korean = "[구동사]\n$korean", english = english, examples = examples,
        source = "의미별 자체 검토 · 학습용 한영 예문")

    private val entries = listOf(
        entry("keep away", "1. (사람·동물 등이) 가까이 오지 않게 하다, 멀리 두다\n2. ~에 가까이 가지 않다, 거리를 두다", "1. To prevent someone or something from coming near.\n2. To stay at a distance from someone or something.", "Keep your children away from the construction site.\t아이들이 공사장 근처에 가지 않게 하세요.\nI'm keeping away from Joe until he calms down.\t조가 진정할 때까지 그와 거리를 두고 있다."),
        entry("keep up", "1. (속도·수준을) 따라가다\n2. 계속 유지하다\n3. 최신 정보나 변화에 뒤처지지 않다", "1. To move or progress at the same speed or level.\n2. To continue or maintain something.\n3. To remain informed about recent developments.", "It is hard to keep up with the fast pace of the class.\t수업 진행 속도가 빨라 따라가기 어렵다."),
        entry("come across", "1. 우연히 발견하다\n2. 우연히 마주치다\n3. (인상을) 주다", "1. To find something by chance.\n2. To meet someone unexpectedly.\n3. To appear or seem in a particular way.", "I came across an old photo while cleaning my desk.\t책상을 정리하다가 오래된 사진을 우연히 발견했다."),
        entry("come up with", "(생각·답·계획 등을) 생각해 내다, 찾아내다", "To think of an idea, answer, or plan.", "We need to come up with a better solution.\t우리는 더 나은 해결책을 생각해 내야 한다."),
        entry("get over", "1. (병·충격·실패 등을) 극복하다, 회복하다\n2. ~을 넘다, 건너다", "1. To recover from or overcome something.\n2. To get across something.", "It took her a long time to get over the disappointment.\t그녀는 그 실망을 극복하는 데 오랜 시간이 걸렸다."),
        entry("get through", "1. (시험·어려운 시기 등을) 끝내다, 무사히 넘기다\n2. (전화로) 연결되다\n3. (일을) 끝마치다", "1. To finish or survive something difficult.\n2. To make contact by phone.\n3. To complete work.", "I finally got through to the customer service line.\t마침내 고객센터 전화가 연결됐다."),
        entry("make up", "1. 지어내다, 꾸며내다\n2. 화해하다\n3. 구성하다\n4. 화장하다", "1. To invent a story.\n2. To become friendly again after an argument.\n3. To form a whole.\n4. To apply cosmetics.", "They argued yesterday but made up this morning.\t그들은 어제 다퉜지만 오늘 아침 화해했다."),
        entry("turn down", "1. (제안·요청 등을) 거절하다\n2. (소리·온도 등을) 낮추다", "1. To refuse an offer or request.\n2. To reduce the level of sound or heat.", "She turned down the job offer.\t그녀는 그 일자리 제안을 거절했다."),
        entry("turn up", "1. 나타나다, 도착하다\n2. (소리·온도 등을) 높이다\n3. 우연히 발견되다", "1. To arrive or appear.\n2. To increase the level of sound or heat.\n3. To be found unexpectedly.", "My missing keys turned up under the sofa.\t없어진 줄 알았던 열쇠가 소파 밑에서 나왔다."),
        entry("call off", "(행사·계획 등을) 취소하다", "To cancel an event or arrangement.", "They called off the game because of the rain.\t비 때문에 경기를 취소했다."),
        entry("carry on", "1. 계속하다\n2. (어려움 속에서도) 일을 계속해 나가다", "1. To continue.\n2. To keep doing something despite difficulty.", "Please carry on with your presentation.\t발표를 계속해 주세요."),
        entry("bring up", "1. (화제·문제를) 꺼내다\n2. (아이를) 기르다", "1. To introduce a topic.\n2. To raise a child.", "She brought up an important question at the meeting.\t그녀는 회의에서 중요한 문제를 꺼냈다."),
        entry("break up", "1. 헤어지다\n2. (모임·학교 등이) 끝나다\n3. 부수어 작은 조각으로 나누다", "1. To end a relationship.\n2. To end a meeting or school term.\n3. To divide into smaller pieces.", "They decided to break up after five years together.\t그들은 5년간 사귄 뒤 헤어지기로 했다."),
        entry("work out", "1. 잘 풀리다\n2. 해결하다, 계산해 내다\n3. 운동하다", "1. To develop successfully.\n2. To solve or calculate.\n3. To exercise.", "Everything worked out better than we expected.\t모든 일이 예상보다 잘 풀렸다."),
        entry("set up", "1. 설치하다, 마련하다\n2. 설립하다\n3. ~이 ~하도록 준비시키다", "1. To arrange or install something.\n2. To establish an organization.\n3. To prepare something for a purpose.", "They set up a new company last year.\t그들은 지난해 새 회사를 설립했다."),
        entry("check out", "1. 확인하다, 살펴보다\n2. 호텔에서 퇴실하다\n3. (도서관에서) 대출하다", "1. To examine something.\n2. To leave a hotel.\n3. To borrow an item.", "Check out this useful English-learning app.\t이 유용한 영어 학습 앱을 한번 확인해 봐."),
        entry("find out", "(사실·정보 등을) 알아내다, 발견하다", "To discover information or learn a fact.", "I found out that the store closes at six.\t그 가게가 6시에 문을 닫는다는 것을 알게 됐다."),
        entry("point out", "(문제·사실 등을) 지적하다, 알려 주다", "To draw attention to a fact or problem.", "The teacher pointed out a mistake in my sentence.\t선생님이 내 문장의 실수를 지적해 주셨다."),
        entry("deal with", "1. 다루다, 처리하다\n2. (문제·상황에) 대처하다\n3. ~와 거래하다", "1. To handle something.\n2. To take action about a problem.\n3. To do business with someone.", "We need to deal with this problem quickly.\t우리는 이 문제를 빨리 처리해야 한다."),
        entry("go through", "1. 겪다, 경험하다\n2. 자세히 살펴보다\n3. 통과하다", "1. To experience something.\n2. To examine something carefully.\n3. To pass through something.", "She went through a difficult time last year.\t그녀는 지난해 힘든 시기를 겪었다."),
        entry("hold on", "1. 잠깐 기다리다\n2. 꼭 잡다, 버티다", "1. To wait for a short time.\n2. To hold tightly or continue to survive.", "Hold on a moment while I check the schedule.\t일정을 확인하는 동안 잠깐만 기다려 주세요."),
        entry("put up with", "(불쾌한 사람·상황을) 참고 견디다", "To tolerate someone or something unpleasant.", "I cannot put up with this noise any longer.\t나는 더 이상 이 소음을 참을 수 없다."),
        entry("take care of", "(사람·일·문제 등을) 돌보다, 맡아서 처리하다", "To look after someone or handle something.", "Can you take care of the children this afternoon?\t오늘 오후에 아이들을 돌봐 줄 수 있니?")
    ).associateBy { it.word }

    fun lookup(query: String): WordEntry? = entries[query.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")]
}
