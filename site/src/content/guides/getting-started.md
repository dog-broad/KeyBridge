---
title: 'Getting started: pair your phone to your PC'
description: Install the two pieces, scan one QR code, and send your first words to your PC — in about two minutes.
order: 2
audience: Step by step
minutes: 4
updated: 2026-06-25
---

This guide takes you from nothing to typing on your PC from your phone. You'll need an Android phone and a Windows PC **on the same Wi-Fi network**.

## Step 1 — Install the desktop host

On your PC, download and run the **KeyBridge Server** installer from the [latest release](https://github.com/dog-broad/keybridge-server/releases/latest). It installs like any normal program — double-click, follow the prompts, done.

When you open it, a small window appears showing a **QR code**. Leave this window open.

> The first time you run it, Windows may ask whether to allow it through the firewall. Say **yes** for private networks — KeyBridge needs this to talk to your phone over Wi-Fi.

## Step 2 — Install the app

On your phone, install the **KeyBridge app** from the [latest release](https://github.com/dog-broad/KeyBridge/releases/latest). Open it, and you'll land on a screen that asks to scan a code.

## Step 3 — Scan the QR code

Point your phone's camera at the QR code on your PC screen. The app reads it and pairs automatically. That's the whole setup.

If the camera won't cooperate, tap **Enter connection manually** and type the address shown under the QR code (it looks like `ws://192.168.x.x:8765`).

## Step 4 — Send your first words

Type something into the text box in the app and send it. Watch it appear on your PC.

- For a short line, it sends instantly.
- For a long paste, a **progress bar** fills as it arrives. Your text only clears once the PC confirms it all landed — so nothing gets lost halfway.

## That's it

You're paired for as long as the server stays open. Next, try the [media remote](/KeyBridge/guides/media-remote), or if anything went wrong, see [fixing connection problems](/KeyBridge/guides/troubleshooting).
