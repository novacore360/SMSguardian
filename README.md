# SecureSMS Guardian

A professional Android SMS security application that protects users from phishing, fraud, and malicious links in SMS messages.

---

## Features

- **Real-time SMS Analysis** — Intercepts and analyzes every incoming SMS for threats
- **Link & Domain Extraction** — Detects URLs, IP-based links, shortened URLs, and obfuscated domains
- **3-Level Threat Classification** — RED FLAG / SUSPICIOUS / SAFE with instant notifications
- **Auto-Blocking Quarantine** — Automatically blocks confirmed RED FLAG messages
- **Community Threat Database** — Shared flagged domain database powered by Supabase
- **Contact Recognition** — Identifies known vs. unknown senders
- **Flagged Database Screen** — Search, browse, and manually check any URL
- **Threat Reporting** — Report messages, URLs, and phone numbers
- **Email Authentication** — Sign up / sign in via Supabase Auth
- **Onboarding Flow** — Permission requests with clear explanations
- **Privacy Policy** — Full in-app privacy policy screen
- **Dark Mode** — Full system theme support

---

## Architecture

```
MVVM + Clean Architecture
├── presentation/   — Compose UI, ViewModels
├── domain/         — Models, use-case logic
├── data/
│   ├── local/      — Room database (cache)
│   └── remote/     — Supabase data source
├── service/        — SMS Foreground Service
├── receiver/       — SMS BroadcastReceiver, BootReceiver
├── worker/         — WorkManager (cleanup & sync)
├── util/           — ThreatAnalyzer, LinkExtractor, HashUtil
└── di/             — Hilt modules
```

---

## Setup Instructions

### 1. Clone and open in Android Studio

```bash
git clone <repo>
cd SecureSMSGuardian
```

Open in Android Studio Hedgehog (2023.1.1) or later.

### 2. Configure Supabase

Run the migration SQL on your Supabase project:

```bash
# In Supabase Dashboard > SQL Editor, paste and run:
supabase/migrations/001_initial_schema.sql
```

### 3. Environment Variables

For local development, create a `local.env` file (never commit this):

```env
SUPABASE_URL=https://lrnvnbnnxuynaalggdtr.supabase.co
SUPABASE_ANON_KEY=your_anon_key_here
```

These are injected at build time via `BuildConfig`.

### 4. Codemagic CI/CD Setup

1. Connect your repository to [Codemagic](https://codemagic.io)
2. Go to **Environment Variables** and create a group named `supabase_credentials`
3. Add these variables to the group:
   - `SUPABASE_URL` = `https://lrnvnbnnxuynaalggdtr.supabase.co`
   - `SUPABASE_ANON_KEY` = your Supabase anon/publishable key
4. Under **Android code signing**, upload your keystore and name it `secureguardian_keystore`
5. Add signing environment variables:
   - `CM_KEYSTORE_PATH`
   - `CM_KEYSTORE_PASSWORD`
   - `CM_KEY_ALIAS`
   - `CM_KEY_PASSWORD`
6. Trigger a build using the `android-release` workflow

### 5. Enable Supabase pg_cron (Optional)

To enable automatic 24-hour message deletion:

1. Go to Supabase Dashboard → Database → Extensions
2. Enable `pg_cron`
3. Run in SQL Editor:
```sql
SELECT cron.schedule(
  'cleanup-expired-data',
  '0 0 * * *',
  'SELECT cleanup_expired_data();'
);
```

---

## Permissions

| Permission | Purpose |
|---|---|
| `RECEIVE_SMS` | Intercept incoming messages for analysis |
| `READ_SMS` | Read SMS history for bulk scanning |
| `READ_CONTACTS` | Identify known senders |
| `INTERNET` | Sync with Supabase cloud database |
| `POST_NOTIFICATIONS` | Show threat alerts |
| `FOREGROUND_SERVICE` | Background SMS monitoring |
| `RECEIVE_BOOT_COMPLETED` | Restart service after reboot |
| `USE_BIOMETRIC` | Biometric app lock |
| `CAMERA` | QR code URL scanning |

---

## Data Storage

| Data Type | Storage | Retention |
|---|---|---|
| SMS Messages | Local (Room) + Supabase | 24 hours (auto-deleted) |
| Contacts | Local + Supabase | Permanent |
| Flagged Domains | Local + Supabase | Permanent |
| Threat Reports | Supabase | Permanent |

---

## Build Requirements

- Android Studio Hedgehog+
- Java 17
- Android SDK 34
- Minimum SDK: Android 8.0 (API 26)
- Kotlin 1.9.23
- Gradle 8.6

---

## Tech Stack

- **UI**: Jetpack Compose + Material Design 3
- **Architecture**: MVVM + Hilt DI
- **Database**: Room (local) + Supabase Postgres (cloud)
- **Auth**: Supabase Auth (email/password)
- **Background**: WorkManager + Foreground Service
- **Async**: Kotlin Coroutines + Flow
- **Networking**: Ktor client (via Supabase SDK)

---

## Security Notes

- All network traffic uses TLS 1.3
- Supabase Row Level Security (RLS) enforces per-user data isolation
- Phone numbers are SHA-256 hashed before community sharing
- No plain-text sensitive data in community database
- ProGuard enabled for release builds
