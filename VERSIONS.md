# AI Phone Assistant — V1 → V3

This repository contains one cumulative Android application. V2 and V3 build on the V1 foundation rather than being separate apps.

## V1 — Training/Test Foundation

- Separate Training Mode and Test Mode.
- Local skill storage.
- Create, edit, delete and review skills.
- Preview trained action sequences.
- Accessibility-based execution foundation.
- Actions: launch app, click text, click view ID, Back, Home, Wait.

## V2 — Real Android Execution

- Android AccessibilityService.
- User-authorized cross-app execution.
- Launch installed applications by package name.
- Click exposed accessibility text and view IDs.
- Test execution and failure reporting.
- Explicit Accessibility permission screen.
- Skills remain local and user-controlled.

## V3 — Voice + AI-style Commands + Discovery + Demonstration

- Voice commands using Android SpeechRecognizer.
- Natural-language command parser.
- Automatic discovery of launchable installed apps.
- App label → package-name matching.
- Explicit training-by-demonstration mode.
- Records supported accessibility click events while training is explicitly active.
- Review recorded actions before saving.
- Test Mode remains separate from Training Mode.
- Explicit approval before a trained skill can be invoked through natural-language commands.

## Cumulative flow

Voice or typed command → command parser → app discovery OR trained skill → approval/test gate → AccessibilityService → Android action.

Training flow: Start Training → user performs actions → Stop → Review → Save → Test → Approve → use by voice/natural language.

## Safety boundaries

The app does not silently start recording. Training is explicitly started and stopped. It does not provide password theft, spyware, hidden surveillance, lock bypassing, or unauthorized access functionality.

## Current AI status

V3's natural-language layer is a local deterministic parser. It is an AI-assistant architecture foundation, not a remote/cloud LLM. A future release can connect a chosen model/API for richer intent planning while keeping the same approval and execution architecture.
