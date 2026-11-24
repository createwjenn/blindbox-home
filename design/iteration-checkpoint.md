# Room and Shop checkpoint — November 24, 2026

- Expanded reference furniture catalogue and models; Shop tiles use white at 10% opacity. Frames cost 10 coins and other furniture costs 15–30 in multiples of 5.
- Updated room navigation, door transitions, proximity interactions, and HUD visibility. Removed the world Walk/Jump/Turn/Back to my room control cluster.
- Chill Guy collectibles use lotus flowers.
- The existing per-launch testing balance now starts at 10 coins; returning from a world preserves the current session's balance. Verified the initialized balance on the PICO emulator.
- Added a denser San Francisco exterior and shared atmosphere shaders. Native GPU renderer implementation is included, but final four-phase visual and lifecycle verification is unfinished. City rendering work is paused at the user's request; do not treat this checkpoint as completed renderer validation.
- Menu pointer interaction issues still need follow-up. Temporary emulator captures and device preview state remain local.

The canonical Android project is this repository. Build with JDK 21 using `./gradlew assembleDebug`; run existing tests with `./gradlew testDebugUnitTest`.
