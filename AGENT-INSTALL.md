# Installing Omarchy Remote: guide for AI agents

You are a coding agent (Claude Code, Codex, …) asked to set up Omarchy Remote on this PC. Follow these steps in order. Each has a check; stop and tell the person when a check fails and the fix below it does not apply.

**Rules**
- Never print, log or send the pairing code (`~/.config/omarchy-remote/token`). The QR code carries it; let the person scan it from their own screen.
- Ask the person before anything that needs `sudo`, changes the network card (Wake-on-LAN) or touches `/etc`. Never edit `/etc/pam.d` yourself; only `make unlock` does, and only when they ask for the fingerprint unlock.
- Some steps can only be done by the person: signing in to Tailscale on the phone, installing the APK without a USB cable, scanning the QR code. Tell them exactly what to do and wait.
- The PC-side messages of these scripts are in Portuguese; the meaning is given below where it matters.

## 1. Check the PC

```bash
test -d ~/.local/share/omarchy && command -v hyprctl && echo "Omarchy: ok"
python3 -c "import venv" && echo "Python: ok"
tailscale status --json | python3 -c "import json,sys; d=json.load(sys.stdin); print('Tailscale:', d['BackendState'], d['Self']['DNSName'])"
```

- No Omarchy: this project is made for Omarchy (Hyprland). Stop and tell the person.
- Tailscale missing or not `Running`: ask the person to run `sudo pacman -S tailscale`, `sudo systemctl enable --now tailscaled` and `sudo tailscale up` (it opens a sign-in link). Then check again.

## 2. Get the code

```bash
git clone https://github.com/rodrigo-sntg/omarchy-remote ~/omarchy-remote
cd ~/omarchy-remote
```

Keep it there: the service runs from this folder.

## 3. Wake-on-LAN: ask first

Ask the person: *"Do you want your phone to be able to turn this PC on from sleep or off (Wake-on-LAN on the wired network card)?"* Their answer picks the flag for the next step: `--wake-on-lan` or `--no-wake-on-lan`.

## 4. Install on the PC

```bash
make install ARGS=--no-wake-on-lan      # or ARGS=--wake-on-lan
```

This checks Tailscale, starts the `omarchy-remote-host` systemd user service, adds the *Phone* menu (Super+Space › Phone), the bar icon and the theme hook, and at the end prints the pairing QR code.

Check that the service answers (it builds its Python environment on the first start; allow up to a minute):

```bash
for i in $(seq 1 30); do host/omarchy-remote status && break; sleep 2; done
```

It prints `Nenhum celular conectado.` ("no phone connected") once the service is up. If it keeps saying the service is not running, read `make logs`:
- `/dev/uinput` permission errors: the person must log out and back in, or run `sudo modprobe uinput`.
- A failure building `evdev`: ask the person to run `sudo pacman -S --needed base-devel linux-headers`, then `make restart`.

## 5. The phone

The person needs, on the phone:
1. **Tailscale**, signed in with the same account as the PC.
2. **The app:** the APK from https://github.com/rodrigo-sntg/omarchy-remote/releases/latest (Android 9 or newer).
   - If the phone is connected by USB with debugging on (`adb devices` lists it), you can install it:
     ```bash
     gh release download --repo rodrigo-sntg/omarchy-remote --pattern '*.apk' --dir /tmp/omarchy-remote && adb install -r /tmp/omarchy-remote/*.apk
     ```
   - Otherwise tell them to open that link on the phone, download the APK and open it (Android asks to allow installing from the browser).

## 6. Pair

Tell the person: *"On the PC, open Super+Space › Phone › Pair phone (QR). In the app, tap PC › Network › Scan the PC's QR code and point it at the screen."*

Check:

```bash
host/omarchy-remote status
```

`Conectado: <phone> · <battery>%` means paired and connected. Done.

## Optional

- **Fingerprint unlock** (only if the person asks for it): `make unlock` (runs `sudo`, adds one line to `/etc/pam.d/omarchy-lock-password`; `make unlock-remove` undoes it). Then, with the PC unlocked, in the app: *Settings › Unlock the PC with your fingerprint › Enroll*, and the person clicks the notification that shows up on the PC.
- **Updating later:** `git pull && make restart`, and the new APK from the releases page.
- **Removing:** `make uninstall` (and `make unlock-remove` if the unlock was on).
