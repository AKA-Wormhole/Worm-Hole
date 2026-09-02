package holocore.browser.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val LAST_UPDATED = "August 26, 2026"

@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    LegalDocumentScreen(title = "Privacy Policy", onBack = onBack) {
        LegalHeading("Privacy Policy")
        LegalMeta("Last updated: $LAST_UPDATED")
        LegalParagraph(
            "This policy explains what HoloCore does and does not do with your " +
                "data. HoloCore is built around a simple rule: your browsing " +
                "activity stays on your device unless a feature you turned " +
                "on requires sending something specific off-device, and " +
                "that is called out explicitly below.",
        )

        LegalSectionHeading("Information HoloCore stores on your device")
        LegalParagraph(
            "Browsing history, bookmarks, downloads, and saved passkeys are " +
                "stored in a local database on your device. HoloCore does not " +
                "upload this data anywhere. It is included in Android's " +
                "normal device-transfer flow (moving to a new phone) but " +
                "excluded from automatic cloud backup, so it is never sent " +
                "to a cloud backup service in the background.",
        )
        LegalParagraph(
            "Incognito tabs do not write to this history at all. Cookies " +
                "and site data created during an Incognito session are held " +
                "only in memory and are deleted the moment every Incognito " +
                "tab is closed, or the next time HoloCore starts if it was " +
                "closed unexpectedly.",
        )

        LegalSectionHeading("Information HoloCore does not collect")
        LegalParagraph(
            "HoloCore does not run analytics, does not use advertising " +
                "identifiers, and does not send your browsing history, " +
                "search queries, or page content to HoloCore's developers or " +
                "any third party for the purpose of building a profile " +
                "about you. There is no account, sign-in, or telemetry " +
                "system built into the app.",
        )

        LegalSectionHeading("The AI assistant (Gemini)")
        LegalParagraph(
            "If you add a Gemini API key in Settings and use the AI " +
                "assistant, summarize, or translate tools, the specific " +
                "text you ask about (a question, a page's text, or a " +
                "phrase to translate) is sent directly from your device to " +
                "Google's Gemini API using your own key. HoloCore's developers " +
                "do not see or relay this traffic -- it goes straight from " +
                "your device to Google. Your API key is stored encrypted " +
                "on your device only and is excluded from cloud backup. " +
                "Review Google's own privacy policy for how Google handles " +
                "requests made with your key. HoloCore only sends this data " +
                "when you actively use one of these AI features -- ordinary " +
                "browsing never touches this code path.",
        )

        LegalSectionHeading("Crash reports")
        LegalParagraph(
            "If HoloCore crashes, a plain-text report (what happened and " +
                "which part of the app was running) is saved locally on " +
                "your device only. It is never sent automatically to " +
                "anyone. You can view or clear it from Settings.",
        )

        LegalSectionHeading("Permissions")
        LegalParagraph(
            "Camera, microphone, and location permissions are only used " +
                "when a website you visit requests them (for example, a " +
                "video call site asking for camera access) and only after " +
                "you approve that specific request. HoloCore does not access " +
                "these on its own.",
        )

        LegalSectionHeading("Tracker and ad blocking")
        LegalParagraph(
            "HoloCore's built-in tracker and ad blockers work entirely on " +
                "your device by comparing page requests against a local " +
                "blocklist. No record of what was blocked is sent " +
                "anywhere.",
        )

        LegalSectionHeading("Changes to this policy")
        LegalParagraph(
            "If this policy changes in a way that affects what data HoloCore " +
                "collects or how it's handled, the \"Last updated\" date " +
                "above will change and, for a material change, HoloCore will " +
                "surface an in-app notice the next time you open the app.",
        )

        LegalSectionHeading("Contact")
        LegalParagraph(
            "Questions about this policy can be sent with Send feedback in Settings, " +
                "which opens the public HoloCore issue tracker.",
        )
    }
}

@Composable
fun TermsOfServiceScreen(onBack: () -> Unit) {
    LegalDocumentScreen(title = "Terms of Service", onBack = onBack) {
        LegalHeading("Terms of Service")
        LegalMeta("Last updated: $LAST_UPDATED")
        LegalParagraph(
            "These terms govern your use of HoloCore. By using the app, you " +
                "agree to them.",
        )

        LegalSectionHeading("Using HoloCore")
        LegalParagraph(
            "HoloCore is a web browser. You are responsible for the websites " +
                "you choose to visit and any content, account, or " +
                "transaction on those sites -- HoloCore is not a party to your " +
                "interactions with third-party websites and does not " +
                "review, endorse, or control the content those sites show " +
                "you.",
        )

        LegalSectionHeading("Incognito mode")
        LegalParagraph(
            "Incognito mode stops HoloCore from saving history, cookies, and " +
                "site data on your device for that session. It does not " +
                "hide your activity from the websites you visit, your " +
                "network provider, or your employer or school if you're " +
                "on a device or network they manage. You agree that you " +
                "understand this limitation each time you open an " +
                "Incognito tab, when HoloCore's Incognito notice is shown.",
        )

        LegalSectionHeading("The AI assistant")
        LegalParagraph(
            "AI features (assistant, summarize, translate) require your " +
                "own Gemini API key and are subject to Google's own terms " +
                "for that service. Responses are generated by a " +
                "third-party AI model and may be inaccurate or incomplete " +
                "-- don't rely on them for decisions where being wrong " +
                "matters (medical, legal, financial, or safety-critical " +
                "situations).",
        )

        LegalSectionHeading("Content blocking")
        LegalParagraph(
            "HoloCore's tracker, ad, and pop-up blockers change how pages " +
                "load and may affect how a site looks or functions, " +
                "including breaking site functionality that depends on a " +
                "blocked resource. You can turn any of these off per your " +
                "own preference in Settings.",
        )

        LegalSectionHeading("No warranty")
        LegalParagraph(
            "HoloCore is provided \"as is,\" without warranty of any kind, " +
                "to the extent permitted by law. The developers are not " +
                "liable for damages arising from your use of the app, " +
                "including data loss, to the extent permitted by law.",
        )

        LegalSectionHeading("Age requirement")
        LegalParagraph(
            "HoloCore is not directed at children. You must be at least 13 " +
                "years old (or the minimum age required in your country to " +
                "use online services without parental consent, if higher) " +
                "to use this app. The AI assistant in particular connects " +
                "to a third-party service (Google's Gemini) using your own " +
                "API key and is not intended for use by children.",
        )

        LegalSectionHeading("Changes to these terms")
        LegalParagraph(
            "These terms may be updated from time to time. Continued use " +
                "of HoloCore after an update means you accept the revised " +
                "terms.",
        )

        LegalSectionHeading("Contact")
        LegalParagraph(
            "Questions about these terms can be sent through the " +
                "Send feedback in Settings.",
        )
    }
}

@Composable
private fun LegalDocumentScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        SettingsHeader(title = title, onBack = onBack)
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LegalHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 12.dp),
    )
}

@Composable
private fun LegalMeta(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
    )
}

@Composable
private fun LegalSectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun LegalParagraph(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
}
