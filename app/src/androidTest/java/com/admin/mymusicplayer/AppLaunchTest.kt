package com.admin.mymusicplayer

import android.Manifest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppLaunchTest {
    private val permissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    private val composeRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(permissionRule).around(composeRule)

    @Test
    fun appLaunchesAndPrimaryDestinationsNavigate() {
        composeRule.onNodeWithText("Song, artist, title, or YouTube URL").assertIsDisplayed()

        composeRule.onNode(hasText("Playlists") and hasClickAction(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Filter playlists").assertIsDisplayed()

        composeRule.onNode(hasText("Queue") and hasClickAction(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Upcoming").assertIsDisplayed()

        composeRule.onNode(hasText("Now Playing") and hasClickAction(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Native background playback").assertIsDisplayed()

        composeRule.onNode(hasText("Settings") and hasClickAction(), useUnmergedTree = true).performClick()
        composeRule.onNodeWithText("Library safety").assertIsDisplayed()
    }
}
