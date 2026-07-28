package com.bedside.ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 프롬프트 자산이 실제로 번들되고(copyPrompts) 코드펜스 추출이 되는지 실기기에서 확인.
 */
@RunWith(AndroidJUnit4::class)
class PromptAssetsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun interviewerPromptLoadsAndAddressesRising() {
        val prompt = PromptLoader.interviewer(context)
        assertTrue("인터뷰어 프롬프트 비어 있음", prompt.isNotBlank())
        assertTrue("호칭 '라이징' 없음", prompt.contains("라이징"))
    }

    @Test
    fun diaryWriterPromptLoads() {
        val prompt = PromptLoader.diaryWriter(context)
        assertTrue("일기 프롬프트 비어 있음", prompt.isNotBlank())
        assertTrue("1인칭 지침 없음", prompt.contains("1인칭"))
    }
}
