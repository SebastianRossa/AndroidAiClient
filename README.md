# Bedrock Chat — Android

Native Kotlin-App für direkten Chat mit Amazon Bedrock / Claude.

## Setup

1. Projekt in **Android Studio** öffnen
2. Gradle sync abwarten
3. `Run > Run 'app'` oder `Build > Build APK(s)` für sideload auf GrapheneOS

## Sideload auf GrapheneOS

```bash
# APK bauen (debug reicht für persönliche Nutzung)
./gradlew assembleDebug

# Auf Gerät installieren
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Architektur

```
TokenFragment  →  TokenViewModel  →  TokenStore (EncryptedSharedPreferences / AES-256)
ChatFragment   →  ChatViewModel   →  BedrockRepository  →  OkHttp → AWS Bedrock
```

## Token-Sicherheit

- Token wird mit **AES-256-GCM** verschlüsselt im Android Keystore gespeichert
- Kein `file://`-Origin-Problem, kein CORS — OkHttp macht native HTTP-Calls
- KeePassDX Autofill funktioniert nativ via Android Autofill API (Feld hat `autofillHints="password"`)
- 401-Fehler: Token wird automatisch gelöscht, App navigiert zurück zum Token-Screen

## Abhängigkeiten

| Bibliothek | Zweck |
|---|---|
| OkHttp 4.12 | HTTP-Calls zu Bedrock (kein CORS) |
| kotlinx-serialization-json | JSON-Parsing |
| androidx.security:security-crypto | AES-256 Token-Speicherung |
| Markwon 4.6 | Markdown-Rendering in RecyclerView |
| Navigation Component | Fragment-Navigation |
| Material3 | UI-Komponenten, Dark/Light Mode |

## Nächste Schritte (optional)

- **Streaming**: `ConverseStream`-API via OkHttp `EventSource`
- **Biometric Lock**: `BiometricPrompt` vor Token-Zugriff
- **System Prompt**: Konfigurierbarer Prefix in `BedrockConfig`
- **Conversation Export**: Verlauf als Markdown exportieren
