package dev.mobile.slam

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.junit.Rule
import org.junit.Test

class MainMenuTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun startScreenShowsAllModesButDisablesUnimplementedFeatures() {
        rule.onNodeWithText("MOBILE SLAM").assertExists()
        rule.onNodeWithText("Tryb na żywo").assertExists()
        rule.onNodeWithText("Krok po kroku").assertIsNotEnabled()
        rule.onNodeWithText("Mapa 3D").assertIsNotEnabled()
        rule.onNodeWithText("Nagrywanie sesji").assertIsNotEnabled()
        rule.onNodeWithText("Porównanie z ARCore").assertIsNotEnabled()
    }

    @Test
    fun settingsAndHardwareInformationAreReachableWithoutOpeningCamera() {
        rule.onNodeWithText("Ustawienia").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Detekcja cech FAST").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithText("Parametry sprzętowe i diagnostyka").performScrollTo().performClick()
        rule.onNodeWithText("Parametry sprzętowe").assertExists()
        rule.onNodeWithText("Powrót do ustawień").performClick()
        rule.onNodeWithText("Ustawienia").assertExists()
        rule.onNodeWithText("Powrót do menu").performClick()
        rule.onNodeWithText("MOBILE SLAM").assertExists()
    }

    @Test
    fun invalidFastThresholdDisablesSave() {
        rule.onNodeWithText("Ustawienia").performClick()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("Detekcja cech FAST").fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("fastThresholdInput").performTextReplacement("999")
        rule.onNodeWithText("Zapisz i zastosuj").assertIsNotEnabled()
    }

    @Test
    fun aboutScreenReturnsToMainMenu() {
        rule.onNodeWithText("O aplikacji").performClick()
        rule.onNodeWithText("Licencja kodu: MIT.").assertExists()
        rule.onNodeWithText("Powrót do menu").performClick()
        rule.onNodeWithText("MOBILE SLAM").assertExists()
    }
}
