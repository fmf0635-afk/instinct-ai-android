# Integration roadmap

Provider integrations are intentionally not represented as connected until OAuth is implemented and a provider confirms access.

Recommended order:
1. Google Calendar and Gmail via Google OAuth (least-privilege scopes, state + PKCE, encrypted refresh tokens).
2. Microsoft Outlook and Calendar via Microsoft identity platform OAuth.
3. Todoist / task providers through their official APIs.
4. Browser automation for approved websites only, isolated in a dedicated browser profile.

For every write action, use a two-step flow: prepare a human-readable preview -> user approves -> execute -> return provider confirmation. Never store provider secrets in the browser. Add revocation, audit history, rate limits, CSRF protection, and per-connector scopes before production.
