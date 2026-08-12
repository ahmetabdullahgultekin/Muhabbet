# Publishing to Play, and letting the build do it

Two halves: a one-time service-account setup that only the account owner can do, and the automation
that uses it afterwards.

## Part 1 — the service account (owner, ~10 minutes, free)

This creates a robot identity that can upload releases. It costs nothing; the only paid thing in the
whole flow is the Play Developer account itself, which already exists.

1. **Google Cloud → create the account.**
   [console.cloud.google.com](https://console.cloud.google.com) → pick or create a project →
   *IAM & Admin* → *Service Accounts* → **Create service account**.
   Name it something obvious, e.g. `muhabbet-play-publisher`. No roles needed at this step — Play
   grants the permissions, not Cloud IAM.

2. **Create a JSON key.**
   Open the new service account → *Keys* → *Add key* → *Create new key* → **JSON**. The file
   downloads once. Treat it like a password: it can publish to your listing.

3. **Enable the API.**
   Same project → *APIs & Services* → *Enable APIs* → search **Google Play Android Developer API** →
   Enable.

4. **Invite it in Play Console.**
   [play.google.com/console](https://play.google.com/console) → *Users and permissions* → **Invite
   new user** → paste the service account's email (it ends in `.iam.gserviceaccount.com`).
   Grant, scoped to the Muhabbet app:
   - *Releases* → **Release to testing tracks** (internal testing)
   - *Releases* → Release to production, only when you want the robot to be able to do that
   - *View app information*

   Nothing else. It does not need financial or account-level permissions.

5. **Put the key somewhere outside the repo** — next to the keystore is sensible:
   `C:\Users\ahabg\.keystores\muhabbet-play-publisher.json`

   Do not commit it. `.gitignore` covers `*.json` at the keystore path only because that path is
   outside the repo entirely; inside the repo there is no safe place for it.

That is the entire owner-side task. Once the file exists, releases can be published from the build.

## Part 2 — the first release still goes through the browser

A service account **cannot create the app listing**, and Play will not accept an upload to an app
that has never been configured. So the very first release is manual regardless:

1. Play Console → **Create app** → name, default language, *App*, *Free*.
2. Upload `muhabbet-0.2.0.aab` (attached to the GitHub release) to **Internal testing**.
3. Fill the required declarations before it will let you roll out:
   - **Data safety** — Muhabbet collects phone numbers, contact hashes and user-uploaded media.
     Answer honestly; a wrong answer here is a policy violation, not a formality.
   - **Privacy policy URL** — must be publicly reachable. The draft in `docs/legal/` is not
     published anywhere yet and, per its own header, contains claims that no longer match where the
     data lives. It needs correcting before it can be linked.
   - Content rating questionnaire, target audience, ads declaration.
4. Add yourself as an internal tester and roll out.

After that first rollout exists, every subsequent version can be pushed by the automation.

## Part 3 — automated uploads afterwards

With the key in place, add the publisher plugin:

```kotlin
// mobile/composeApp/build.gradle.kts
plugins {
    id("com.github.triplet.play") version "3.12.1"
}

play {
    serviceAccountCredentials.set(file(System.getenv("PLAY_SERVICE_ACCOUNT_JSON") ?: "/dev/null"))
    track.set("internal")
    defaultToAppBundles.set(true)
    // Uploads are rejected outright if the version code is not higher than the last one on the
    // track, so `verifyBuildInfoVersion` failing is cheaper than Play failing.
}
```

Then:

```bash
export PLAY_SERVICE_ACCOUNT_JSON=C:/Users/ahabg/.keystores/muhabbet-play-publisher.json
./gradlew :mobile:composeApp:publishReleaseBundle
```

The plugin is deliberately **not** added yet: without the key it would fail the build for everyone,
and there is nothing to authenticate with until Part 1 is done.

## What blocks a public (production) release

Internal testing needs none of this, which is why it is the right first track. Production does:

- End-to-end encryption is **off**. The store listing must not claim otherwise, and the privacy
  policy must describe what actually happens: transport encryption only, history on our servers.
- The two IDORs and the SSRF in `docs/PRODUCT_ROADMAP_2026-06-06.md` are open. Shipping an
  authorization hole to the public is different from shipping it to yourself.
- SMS delivery is live via Twilio Verify, but on an account with no spend controls configured.
