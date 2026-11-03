First release of the rebuilt Android app, built and signed by GitHub Actions.

**Install:** download the APK below on your phone and open it. If you installed an Android Studio build of Depth, uninstall it first. Installs over the 0.1.0 APK as an update.

### Android Auto
Depth now shows up in Android Auto as a media app (same path as Navic/Tempus). Enable *Unknown sources* in Android Auto's developer settings, open Depth from the app grid, and pick a table.
- Title: phase and time left (`Hold · 1:25`)
- Subtitle: `CO2 · Round 3 of 8`, or `Breath hold in 7s` during the 10s lead-in
- Progress bar: the current phase
- Play/pause runs the timer · Next skips a phase · Previous resets
- Never requests audio focus — your music keeps playing

### Phone
- Redesigned UI: phase color gradients, glowing countdown ring, round progress, tabular digits
- Steppers in table setup with a live summary of rounds and total time
- Lock-screen/notification controls; countdown keeps running with the screen off
- All existing behavior kept: CO2/O2/Square/Custom tables, 10s lead-in cues, triple pulses and flashes, long buzz on phase change, landscape and upside-down layouts, keep-awake

Signing cert SHA-256: `92:83:38:CC:B9:E3:05:F4:01:F1:E2:C1:65:23:8E:8F:FA:4C:D7:07:C7:D1:55:8E:7B:79:0B:86:B7:87:61:A5`
