package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.repository.AnalysisResult
import com.example.repository.Decision
import com.example.ui.screens.AnalysisResultCard
import com.example.ui.theme.MyApplicationTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AnalysisCardTest {
    @get:Rule val compose = createComposeRule()
    private fun fixture(decision: Decision) = AnalysisResult("테스트 용기", "PP", 58, 0.91, decision,
        if (decision == Decision.UNKNOWN) emptyList() else listOf("내용물을 비우세요", "물로 헹구세요"),
        if (decision == Decision.UNKNOWN) "다시 촬영해 주세요" else "지역 배출 기준을 확인하세요",
        "테스트: 내부에 음식물 관찰", listOf("지자체 기준이 다릅니다"), "ui-test-fixture")
    @Test fun resultContainsActionEvidenceAndWarning() {
        compose.setContent { MyApplicationTheme { Column(Modifier.verticalScroll(rememberScrollState())) { AnalysisResultCard(fixture(Decision.WASH_THEN_RECYCLE)) } } }
        compose.onNodeWithText("씻은 뒤 분리배출").assertExists()
        listOf("테스트 용기 · PP", "세척 방법", "1. 내용물을 비우세요", "배출 방법", "판단 근거", "주의사항", "• 지자체 기준이 다릅니다").forEach { compose.onNodeWithText(it).assertExists() }
    }
    @Test fun unknownDoesNotDisplayFalseContaminationCertainty() {
        compose.setContent { MyApplicationTheme { Column(Modifier.verticalScroll(rememberScrollState())) { AnalysisResultCard(fixture(Decision.UNKNOWN)) } } }
        compose.onNodeWithText("판단 보류 · 재촬영 필요").assertExists()
        compose.onNodeWithText("다시 촬영해 주세요").assertExists()
        compose.onNodeWithText("시각적 오염 추정", substring = true).assertDoesNotExist()
    }
}
