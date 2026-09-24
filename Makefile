.PHONY: help install uninstall pair status restart logs unlock unlock-remove dev \
        apk apk-release install-apk test test-host test-app lint clean

SHELL := /bin/bash
.DEFAULT_GOAL := help

# The Android build needs Java 17; a newer default JDK fails.
JAVA17 := $(firstword $(wildcard /usr/lib/jvm/java-17-openjdk))
GRADLE := $(if $(JAVA17),JAVA_HOME=$(JAVA17)) ./gradlew -q
VENV := host/.venv
DEVICE ?=

help: ## Show this help
	@echo "Omarchy Remote"
	@echo
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | \
		awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'
	@echo
	@echo "  DEVICE=<serial> make install-apk   picks a phone when several are connected"

# ---------------------------------------------------------------- the PC

install: ## Set up the PC: service, Omarchy menu and bar icon, Wake-on-LAN, pairing QR
	@host/omarchy-remote-setup

uninstall: ## Undo the setup (keeps the pairing code and the list of phones)
	@host/omarchy-remote-remove

pair: ## Show the pairing QR code for the app
	@host/omarchy-remote pair

status: ## Is a phone connected?
	@host/omarchy-remote status

restart: ## Restart the PC service (after an update)
	@systemctl --user restart omarchy-remote-host.service && echo "Restarted."

logs: ## Follow the PC service's log
	@journalctl --user -u omarchy-remote-host.service -f

unlock: ## Turn on unlocking the PC with the phone's fingerprint (asks for sudo)
	@sudo host/omarchy-remote-unlock-setup

unlock-remove: ## Turn the fingerprint unlock off again (asks for sudo)
	@sudo host/omarchy-remote-unlock-setup --remove

dev: ## Run the service for testing: 127.0.0.1, phone through `adb reverse tcp:8765 tcp:8765`
	@host/run.sh --dev-loopback

# ---------------------------------------------------------------- the phone app

apk: ## Build the debug APK
	@$(GRADLE) :app:assembleDebug
	@echo "app/build/outputs/apk/debug/app-debug.apk"

apk-release: ## Build the signed release APK into dist/ (needs the signing key)
	@scripts/release-apk.sh

install-apk: apk ## Build and install the debug APK on the phone connected by adb
	@adb $(if $(DEVICE),-s $(DEVICE)) install -r app/build/outputs/apk/debug/app-debug.apk

# ---------------------------------------------------------------- checks

$(VENV)/bin/pytest:
	@python3 -m venv $(VENV)
	@$(VENV)/bin/pip install -q -r host/requirements.txt pytest

test: test-host test-app ## Run every test (PC service and app)

test-host: $(VENV)/bin/pytest ## Test the PC service
	@cd host && .venv/bin/python -m pytest -q

test-app: ## Test the app (unit tests)
	@$(GRADLE) :app:testDebugUnitTest && echo "App tests passed."

lint: ## Android lint
	@$(GRADLE) :app:lintDebug && echo "Lint passed."

clean: ## Remove build outputs
	@$(GRADLE) clean
	@rm -rf dist
	@find host -name __pycache__ -type d -prune -exec rm -rf {} +
