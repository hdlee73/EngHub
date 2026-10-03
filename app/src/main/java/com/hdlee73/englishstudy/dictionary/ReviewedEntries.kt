package com.hdlee73.englishstudy.dictionary

import java.util.Locale

/** Sense-based entries and original example translations. No phonetic glosses. */
internal object ReviewedEntries {
    private fun entry(word: String, korean: String, english: String, examples: String) =
        WordEntry(word = word, ipa = "", korean = if (korean.startsWith("[") || korean.contains("[형용사]")) korean else (if (word == "stuck") "[형용사]\n" else "[구동사]\n") + korean, english = english, examples = examples)
    private val entries = listOf(
        entry("catch up",
            "1. (앞서가는 사람·수준 등을) 따라잡다\n2. (밀린 일·공부 등을) 따라 하다, 밀린 일을 처리하다\n3. (오랜만에 만나) 그동안의 소식을 나누다\n4. (소식·상황 등을) 알아보다, 최신 정보를 파악하다",
            "1. To reach someone or the same level after falling behind.\n2. To do work that should have been done earlier.\n3. To talk with someone about what has happened since you last met.\n4. To learn about recent events or developments.",
            "I ran faster to catch up with the others.\t다른 사람들을 따라잡으려고 더 빨리 달렸다.\nI need to catch up on my homework this weekend.\t이번 주말에 밀린 숙제를 해야 한다.\nLet's meet for coffee and catch up.\t커피 한잔하면서 그동안 어떻게 지냈는지 이야기하자.\nI read the newspaper to catch up on the latest news.\t최근 소식을 알아보려고 신문을 읽었다."),
        entry("preliminary",
            "1. [형용사] 본격적인 일에 앞서 하는, 예비적인; 준비 단계의\n2. [형용사] 아직 확정되지 않은, 잠정적인\n3. [명사] 사전 준비나 절차; (경기 등의) 예선",
            "1. Done before the main activity as preparation.\n2. Provisional and subject to later confirmation.\n3. An initial procedure or a contest held before the final stage.",
            "These are only the preliminary results.\t이 결과는 아직 잠정적인 것이다.\nWe held a preliminary meeting to discuss the schedule.\t우리는 일정을 논의하기 위해 사전 회의를 열었다.\nShe qualified for the final after winning her preliminary race.\t그녀는 예선 경기에서 우승해 결승에 진출했다."),
        entry("struck",
            "[동사] strike의 과거형·과거분사\n1. 치다, 때리다\n2. (생각·인상이) 문득 떠오르다, 강하게 다가오다\n3. (번개·재해 등이) 덮치다",
            "1. Simple past and past participle of strike.",
            "The ball struck the window.\t공이 창문에 부딪혔다.\nAn idea struck me on the way home.\t집으로 돌아오는 길에 문득 아이디어가 떠올랐다.\nLightning struck a tree near our house.\t우리 집 근처 나무에 벼락이 떨어졌다."),
        entry("spontaneous",
            "[형용사]\n1. (계획하지 않고) 즉흥적인\n2. (강요받지 않고) 자발적인, 스스로 우러나오는\n3. (반응·과정 등이) 외부 작용 없이 저절로 일어나는, 자연 발생적인",
            "1. Done without advance planning.\n2. Arising from a person's own willingness or feelings.\n3. Occurring without an external cause.",
            "We made a spontaneous decision to spend the weekend at the beach.\t우리는 즉흥적으로 주말을 바닷가에서 보내기로 했다.\nHer kind words brought a spontaneous smile to his face.\t그녀의 다정한 말에 그의 얼굴에 저절로 미소가 번졌다.\nThe audience broke into spontaneous applause.\t관객들이 자연스럽게 박수를 터뜨렸다."),
        entry("pay off",
            "1. (빚·대출 등을) 전액 갚다, 청산하다\n2. (노력·투자 등이) 성과를 내다, 보람이 있다\n3. (누군가에게) 뇌물을 주어 매수하다\n4. (직원에게) 정산금을 주고 해고하다",
            "1. To repay a debt in full.\n2. To produce a worthwhile result.\n3. To bribe someone.\n4. To pay an employee what is owed and dismiss them.",
            "We finally paid off our mortgage.\t우리는 마침내 주택담보대출을 모두 갚았다.\nHer years of studying Spanish paid off when she traveled to Mexico.\t그녀는 멕시코로 여행을 갔을 때 수년간 스페인어를 공부한 보람을 느꼈다.\nI thought the guards would give us trouble, but apparently he had paid them off.\t경비원들이 우리를 곤란하게 할 줄 알았는데, 알고 보니 그가 이미 뇌물을 주고 매수한 모양이었다."),
        entry("give up", "1. (시도·희망 등을) 포기하다\n2. (습관·활동 등을) 그만두다\n3. (권리·자리 등을) 양보하다", "1. To stop trying.\n2. To stop a habit or activity.\n3. To surrender something.", "Don't give up on your dream.\t네 꿈을 포기하지 마.\nHe gave up smoking last year.\t그는 지난해에 담배를 끊었다."),
        entry("put off", "1. (일·약속 등을) 미루다, 연기하다\n2. ~의 흥미를 잃게 하다, 꺼리게 하다", "1. To postpone something.\n2. To discourage or repel someone.", "We put off the meeting until Friday.\t우리는 회의를 금요일로 미뤘다."),
        entry("take off", "1. (옷 등을) 벗다\n2. (비행기가) 이륙하다\n3. (사업 등이) 급성장하다\n4. (일에서) 시간을 내어 쉬다", "1. To remove clothing.\n2. To leave the ground.\n3. To become successful quickly.\n4. To take time away from work.", "The plane took off on time.\t비행기는 제시간에 이륙했다.\nPlease take off your shoes.\t신발을 벗어 주세요."),
        entry("look up", "1. (사전·자료에서) 찾아보다\n2. (상황이) 나아지다\n3. 올려다보다", "1. To search for information.\n2. To improve.\n3. To direct one's gaze upward.", "I looked up the word in a dictionary.\t나는 사전에서 그 단어를 찾아봤다."),
        entry("look after", "(사람·동물 등을) 돌보다; (물건 등을) 관리하다", "To take care of someone or something.", "Could you look after my cat this weekend?\t이번 주말에 내 고양이를 돌봐 줄 수 있니?"),
        entry("look forward to", "~을 기대하다, 고대하다", "To anticipate something with pleasure.", "I'm looking forward to seeing you again.\t너를 다시 만날 날이 기대돼."),
        entry("run out of", "(물건·돈·시간 등을) 다 써서 없어지다, 바닥나다", "To have none of something left.", "We've run out of milk.\t우유가 다 떨어졌다."),
        entry("figure out", "(생각하여) 알아내다, 이해하다; (문제를) 해결하다", "To understand or solve something by thinking.", "I finally figured out how to use this app.\t나는 마침내 이 앱의 사용법을 알아냈다."),
        entry("get along", "1. (사람과) 잘 지내다\n2. (일·생활을) 해 나가다", "1. To have a friendly relationship.\n2. To manage or make progress.", "She gets along well with her colleagues.\t그녀는 동료들과 사이좋게 지낸다."),
        entry("break down", "1. (기계 등이) 고장 나다\n2. (협상 등이) 결렬되다\n3. 감정을 억누르지 못하고 무너지다\n4. (내용을) 나누어 분석하다", "1. To stop functioning.\n2. To fail.\n3. To lose emotional control.\n4. To divide into parts for analysis.", "Our car broke down on the way home.\t집으로 돌아오는 길에 차가 고장 났다."),
        entry("stuck", "1. 끼어서 움직이지 못하는\n2. (문제·일이) 막혀 진전이 없는\n3. (어떤 상황에서) 벗어나지 못하는", "1. Unable to move.\n2. Unable to make progress.\n3. Unable to leave a situation.", "I'm stuck on this math problem.\t이 수학 문제에서 막혀 더 이상 풀지 못하고 있어.")
    ).associateBy { it.word }
    fun lookup(query: String): WordEntry? = entries[query.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")] ?: CorePhrases.lookup(query)
}

