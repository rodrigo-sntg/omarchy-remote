# Omarchy Remote

Your Android phone as [Omarchy](https://omarchy.org)'s remote. Over [Tailscale](https://tailscale.com) it works from anywhere: a trackpad and keyboard, the PC's screen in your hand, your coding agents as chats, and a control center for the desktop. Near the PC it also works over plain Bluetooth, as a keyboard and mouse, with nothing installed on the PC.

## Quick start

1. **Both devices on Tailscale**, signed in with the same account.
2. **On the PC:** `git clone https://github.com/rodrigo-sntg/omarchy-remote && cd omarchy-remote && make install`
3. **On the phone:** install the APK from the [latest release](https://github.com/rodrigo-sntg/omarchy-remote/releases/latest), open it, tap **PC › Network › Scan the PC's QR code** and point it at the QR code on the PC.

**Using an AI agent?** Tell it:

> Read https://github.com/rodrigo-sntg/omarchy-remote/blob/main/AGENT-INSTALL.md and set up Omarchy Remote on this PC for me.

It checks what's needed, installs, asks you before anything that needs `sudo` or changes the network, and tells you when it's your turn on the phone.

## What it does

- **Control:** trackpad (tap, drag, two-finger scroll, three-finger workspace switch), keyboard that types in the PC's layout, shortcut row, workspace buttons.
- **Screen:** a live map of your monitors with thumbnails; see and touch the PC's screen from anywhere; follow the focused window; use the phone as an extra display.
- **AI agents:** Claude Code and Codex running in [herdr](https://herdr.dev), as chats: the whole conversation, permission requests as buttons, diffs, the `/` menu of each agent, subagents, the project's git status, model picker, voice input. Notifications when an agent needs you or finishes, answerable from the lock screen.
- **Control center:** media, volume and output, microphone, night light, do not disturb, stay awake, screen recording, power profile, wallpaper, bar, gaps, lock, suspend, restart and shut down.
- **Omarchy:** the app follows the active Omarchy theme; Omarchy's menu, keybindings and windows are one tap away; a *Phone* menu and a bar icon on the PC.
- **Files and clipboard:** send files and screenshots both ways, with progress; the clipboard syncs by itself.
- **Find the phone:** the PC makes it ring loudly, even on silent. Stop it from the full-screen alert, the volume or power button, or the PC.
- **Wake the PC:** asleep or off, the phone wakes it with Wake-on-LAN and reconnects.
- **Unlock the PC with your fingerprint:** no password typed, and the password never leaves the PC (see below).
- **Phone notifications on the PC:** opt in, pick the apps, reply from the PC.
- **PC music on the phone's lock screen,** with pause and skip.

## Install

### Phone
Download the APK from the [latest release](https://github.com/rodrigo-sntg/omarchy-remote/releases/latest) and open it (Android 9 or newer). For updates, add this repository to [Obtainium](https://github.com/ImranR98/Obtainium).

### PC (Omarchy)
Requirements: Omarchy (Hyprland), Tailscale signed in on the PC and on the phone with the same account, and Python 3. The service makes its own Python environment (`host/.venv`) on first start.

```bash
git clone https://github.com/rodrigo-sntg/omarchy-remote
cd omarchy-remote
make install
```

The setup checks Tailscale, starts the service (a systemd user unit), adds the *Phone* menu (Super+Space › Phone), the bar icon and the theme hook, offers to turn on Wake-on-LAN, and shows the pairing QR code. In the app: **PC › Network › Scan the PC's QR code**. Run it again at any time; `make uninstall` undoes it.

### Commands

Run `make` to see them all:

| Command | What it does |
|---|---|
| `make install` | Set up the PC: service, Omarchy menu and bar icon, Wake-on-LAN, pairing QR |
| `make uninstall` | Undo the setup (keeps the pairing code and the list of phones) |
| `make pair` | Show the pairing QR code |
| `make status` | Is a phone connected? |
| `make restart` | Restart the PC service (after an update) |
| `make logs` | Follow the PC service's log |
| `make unlock` / `make unlock-remove` | Turn the fingerprint unlock on / off (asks for sudo) |
| `make apk` / `make install-apk` | Build the debug APK / build and install it over adb (`DEVICE=<serial>` to pick a phone) |
| `make apk-release` | Build the signed release APK into `dist/` |
| `make test` | Run every test (`make test-host`, `make test-app`, `make lint`) |
| `make dev` | Run the service for testing on 127.0.0.1 (phone through `adb reverse`) |

An AUR package (`omarchy-remote-git`) is ready in [`packaging/aur/`](packaging/aur/) and not yet published.

### Fingerprint unlock (optional)
```bash
make unlock          # once, asks for sudo; undo with: make unlock-remove
```
Then, with the PC unlocked: **Settings › Unlock the PC with your fingerprint › Enroll** in the app, and click the notification that shows up on the PC.

How it works: the phone keeps an EC key that only its fingerprint unlocks; the PC keeps the public half. With the PC locked, the phone signs a challenge from the PC, the PC checks the signature, and types a random one-time code (valid once, for 60 seconds) into the lock screen. The setup adds one `pam_exec` line to `/etc/pam.d/omarchy-lock-password` that accepts only that code; your password keeps working as always and never leaves the PC.

## Security

- The PC service listens **only on the Tailscale address** (port 8765). A connection needs all of: the pairing code, a machine of **your own** in this tailnet (checked with `tailscale whois`; tagged machines and other tailnets are refused), and no browser `Origin`.
- Unencrypted WebSocket is allowed only to `*.ts.net` names: Tailscale already encrypts the traffic (WireGuard).
- The pairing code lives in `~/.config/omarchy-remote/token` (readable only by you) and, on the phone, encrypted with an Android Keystore key.
- `omarchy-remote devices` lists the phones that connected; `omarchy-remote devices revoke <name>` shuts one out even with the code (and removes its unlock key); `omarchy-remote new-code` changes the code for every phone. To allow only specific machines, add `--allow <machine>` with `systemctl --user edit omarchy-remote-host.service`.
- One controller at a time. Without messages for 6 seconds, the service releases every key and button and ends the session.

## On the PC

- **Bar icon:** lit while the phone is connected; the tooltip shows its battery and how many agents are waiting. Click opens the *Phone* menu; middle click sends the clipboard to the phone.
- **Super+Space › Phone:** pair (QR), send the clipboard, a file or a screenshot to the phone, show the PC's screen on the phone, use it as an extra display, open herdr on it, find the phone, list paired phones, new pairing code.
- **CLI** `omarchy-remote`: `status`, `connected`, `pair`, `send-clipboard`, `send-file`, `screenshot`, `ring [--stop]`, `devices [revoke <name>]`, `new-code`, `theme-changed`, `open screen|extra|terminal`. It talks to the service through a socket only your user can open (`$XDG_RUNTIME_DIR/omarchy-remote/control.sock`).

## Bluetooth mode

Without the network, the phone becomes a regular Bluetooth keyboard and mouse (Android's `BluetoothHidDevice`): nothing to install on the PC. In the app: **PC › Bluetooth › Make visible**, then pair from the PC (for example `bluetoothctl`: `scan on`, `pair <MAC>`, `trust <MAC>`, `connect <MAC>`). The app never removes existing pairings and only connects to the PC you choose.

## Build

Requirements: JDK 17 and the Android SDK (platform 36), from `ANDROID_HOME` or `local.properties` (`sdk.dir=...`).

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
cd host && python -m venv .venv && .venv/bin/pip install -r requirements.txt pytest && .venv/bin/python -m pytest -q
```

`scripts/release-apk.sh` builds the signed, minified release APK into `dist/`. The signing key is read from outside the repository (`~/.config/omarchy-remote-release/signing.properties`, or `OMARCHY_REMOTE_SIGNING`).

Testing without Tailscale on the phone: `host/run.sh --dev-loopback` listens on 127.0.0.1, `adb reverse tcp:8765 tcp:8765` brings the phone's port to the PC, and debug builds accept `localhost` as the address. The pairing code is still required; release builds refuse `localhost`.

## Known limitations

- Android ends the Bluetooth registration when the app leaves the screen; coming back, the app registers again and reconnects to the last PC.
- In the background only the network connection stays up, and only with *Tell me when an agent needs me* on. Video and the terminal close when you leave the app.
- Waking the PC needs the phone on the same local network, and Wake-on-LAN enabled on the PC (`host/omarchy-remote-setup`; some PCs also need it in the BIOS).
- The US keyboard layout is plain `us`; `us(intl)` is not supported.

The design documents in `design/` and the notes in `docs/` are in Portuguese.

## License

[MIT](LICENSE)
