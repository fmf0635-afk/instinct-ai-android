# Instinct AI — Android + secure AI backend

Instinct AI is an Android assistant starter with an animated selectable human-style avatar, Arabic speech input, spoken replies, local conversation history, quick app launching, and an optional private AI backend.

## Android app

- Android Studio / JDK 17 / Android SDK 35
- Open the repository as a Gradle project and run the `:app` configuration.
- The GitHub Actions workflow builds a debug APK on every push to `main` and uploads it as `instinct-ai-debug-apk` when the build succeeds.
- Microphone permission is requested only when voice input is used.
- The app can open a small allowlist of installed apps. Android itself controls whether an app is installed and launchable.

## AI backend (Node.js 20+)

The API key stays on the server; do not put it in the Android app.

1. Copy `.env.example` to `.env`.
2. Set `AI_API_KEY` to your provider API key.
3. Set `APP_ACCESS_TOKEN` to a long random secret.
4. Set `AI_MODEL` to a model available to your API account.
5. Run `npm start`.
6. Deploy behind HTTPS. For a public deployment, set `HOST=127.0.0.1` behind a local reverse proxy where possible; do not expose the Node port directly. If running the service in a container, bind it to the container interface and restrict inbound access at the network layer.
7. In Android Settings, enter the HTTPS base URL of the backend (for example `https://your-domain.example`) and the same access token.

The backend exposes:
- `GET /health`
- `POST /chat` with JSON `{"message":"Hello","history":[]}`

## Security and capability boundaries

- The backend authenticates requests with a bearer token when `APP_ACCESS_TOKEN` is set.
- AI provider credentials are read from server environment variables only.
- The app stores settings and the last 100 conversation turns locally in Android private preferences.
- App launching is limited to named apps in the app's allowlist. It does not read other apps' screens, send messages, or operate accounts.
- Gmail, calendars, messaging, browser automation, background wake-word listening, and full system control are not enabled. Those require separate official OAuth/API integrations, user-granted permissions, explicit confirmation flows, and additional security review.
- The avatar is a lightweight animated illustration, not a photorealistic 3D character.
- Speech recognition availability and voice quality depend on Android speech services and installed language packs.

## Local backend check

```bash
npm run check
npm start
```
