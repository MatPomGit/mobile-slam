package dev.mobile.slam

import androidx.compose.ui.test.assertExists
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
    fun settingsAreReachableWithoutOpeningLiveCamera() {
        rule.onNodeWithText("Ustawienia").performClick()
        rule.onNodeWithText("Detekcja FAST: próg 20").assertExists()
        rule.onNodeWithText("Parametry sprzętowe i diagnostyka · w przygotowaniu").assertIsNotEnabled()
        rule.onNodeWithText("Powrót do menu").performClick()
        rule.onNodeWithText("MOBILE SLAM").assertExists()
    }

    @Test
    fun aboutScreenReturnsToMainMenu() {
        rule.onNodeWithText("O aplikacji").performClick()
        rule.onNodeWithText("Licencja kodu: MIT.").assertExists()
        rule.onNodeWithText("Powrót do menu").performClick()
        rule.onNodeWithText("MOBILE SLAM").assertExists()
    }
}
