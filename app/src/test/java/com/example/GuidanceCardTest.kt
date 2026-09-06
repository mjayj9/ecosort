package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.repository.*
import com.example.ui.screens.*
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
class GuidanceCardTest {
    @get:Rule val compose = createComposeRule()
    @Test fun showsUserSourceAndNoInventedScores() {
        val result = GuidanceResult.fromMap(guidanceFixture())
        compose.setContent { MyApplicationTheme { Column(Modifier.verticalScroll(rememberScrollState())) {
            GuidanceCard(result, ScanAnswers(), true, onChoose = { _, _ -> })
        } } }
        compose.onNodeWithText("사용 상태 · 사용자 확인").assertExists()
        compose.onNodeWithText("미개봉·미사용").assertExists()
        compose.onNodeWithText("오염 추정", substring = true).assertDoesNotExist()
        compose.onNodeWithText("자체 신뢰도", substring = true).assertDoesNotExist()
    }
    @Test fun changedAnswersHideTheOldAdvice() {
        val result = GuidanceResult.fromMap(guidanceFixture())
        compose.setContent { MyApplicationTheme { GuidanceCard(result, ScanAnswers(), true, pendingChanges = true, onChoose = { _, _ -> }) } }
        compose.onNodeWithText("답변이 바뀌었어요 · 안내를 갱신해 주세요").assertExists()
        compose.onNodeWithText(result.summary).assertDoesNotExist()
        compose.onNodeWithText("사용 상태 · 사용자 확인").assertDoesNotExist()
    }
    @Test fun contextDoesNotPreselectUnusedOrUsed() {
        compose.setContent { MyApplicationTheme { ScanContextCard(ScanAnswers(), true, onChoose = { _, _ -> }) } }
        compose.onNodeWithText("미개봉·미사용").assertIsNotSelected()
        compose.onNodeWithText("사용한 물품").assertIsNotSelected()
        compose.onNodeWithText("잘 모르겠음").assertExists()
    }
}
