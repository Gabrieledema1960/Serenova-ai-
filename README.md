# AI Phone Assistant V3

V3 includes voice commands, natural-language command parsing, installed-app discovery, training by demonstration, separate Training/Test modes, accessibility execution, and explicit skill approval.

## Test APK

GitHub Actions builds a debug APK automatically on pushes to `main` and can also be started manually from **Actions → Build AI Phone Assistant V3 APK → Run workflow**.

The artifact is named `AIPhoneAssistantV3-debug-apk`.

## Phone setup

1. Install the APK.
2. Grant microphone permission when requested.
3. Open Accessibility settings from the app.
4. Enable AI Phone Assistant.
5. Use Voice Assistant to say things such as `Open YouTube`.
6. Use Training Mode to record supported click actions.
7. Review and test the skill in Test Mode.
8. Explicitly approve a skill before natural-language execution.

## Safety

Training is explicit and user-controlled. The assistant does not silently record the phone. Accessibility actions run only through the enabled user-authorized service.

## V3 limitations

Android apps expose different amounts of accessibility metadata. Some apps may not expose useful text or view IDs, so demonstration recording cannot guarantee every tap is captured. The current natural-language engine is local and deterministic; it is not yet a cloud LLM.
