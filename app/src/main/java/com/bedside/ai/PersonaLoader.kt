package com.bedside.ai

import android.content.Context

/**
 * 라이징 프로필을 앱 자산에서 읽는다. 원본 단일 출처는 gitignore된 personal/persona.md
 * (빌드 시 복사됨). 프로필은 인터뷰어 시스템 프롬프트 뒤에 배경으로 붙는다.
 *
 * 프로필이 없어도(개인 자료를 넣지 않은 빌드) 앱은 정상 동작한다 — 빈 문자열을 준다.
 */
object PersonaLoader {

    /** persona.md 전문. 코드펜스 규칙 없이 마크다운 전체를 그대로 쓴다. 없으면 "". */
    fun load(context: Context): String =
        runCatching {
            context.assets.open("personal/persona.md").bufferedReader().use { it.readText() }.trim()
        }.getOrDefault("")
}
