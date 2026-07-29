package com.bedside.log

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 대화를 평문으로 남긴다(결정 36). 앱 내부 저장(암호화 Room DB, 결정 15)과 별개로,
 * 프롬프트·대화를 사람이 바로 읽고 기능 개선에 쓰기 위한 로그다. 기기 외부 파일
 * 디렉터리 Transcripts/<날짜>.md — adb pull로 꺼낼 수 있다.
 *
 * 세션당 파일 하나. 헤더에 그날의 시스템 프롬프트(프로필·브리핑 포함)를 박아,
 * "AI가 무엇을 보고 그렇게 답했는지"를 나중에 그대로 재구성할 수 있게 한다.
 */
object TranscriptLog {

    private val stampFmt = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

    private fun dir(context: Context): File =
        File(context.getExternalFilesDir(null), "Transcripts").apply { mkdirs() }

    private fun fileFor(context: Context, date: String): File = File(dir(context), "$date.md")

    /** 세션 파일이 없을 때만 시스템 프롬프트 헤더를 쓴다. 이어가기(resume)면 건드리지 않는다. */
    fun startIfNew(context: Context, date: String, systemPrompt: String) {
        val f = fileFor(context, date)
        if (f.exists()) return
        f.writeText(
            buildString {
                append("# 대화 로그 ").append(date).append("\n\n")
                append("기능 개선용 평문 로그(결정 36).\n\n")
                append("## 시스템 프롬프트 (이 세션에서 AI가 받은 것)\n\n")
                append("```\n").append(systemPrompt).append("\n```\n\n")
                append("## 대화\n\n")
            },
        )
    }

    /** 한 턴을 이어붙인다. 새 턴이 생기는 순간에만 호출 — resume 로드 시엔 호출하지 않는다. */
    fun append(context: Context, date: String, role: String, text: String) {
        val who = if (role == "user") "나" else "인터뷰어"
        runCatching {
            fileFor(context, date).appendText("**$who** (${stampFmt.format(Instant.now())})\n$text\n\n")
        }
    }

    /** 일기 생성 등 이벤트 표시. */
    fun note(context: Context, date: String, message: String) {
        runCatching {
            fileFor(context, date).appendText("> [$message] (${stampFmt.format(Instant.now())})\n\n")
        }
    }
}
