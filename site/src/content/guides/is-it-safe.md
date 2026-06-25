---
title: Is KeyBridge safe?
description: How pairing, encryption, and the local-network design keep your input private — explained without the jargon.
order: 4
audience: Good to know
minutes: 4
updated: 2026-06-25
---

Short answer: yes, and it's built to stay that way. Here's how it works, in plain terms.

## It never leaves your home network

KeyBridge talks **directly between your phone and your PC over Wi-Fi**. Nothing is sent to the internet, there's no server in the middle, and there's no account to sign up for. If your phone and PC can see each other on the same Wi-Fi, that's all that's used.

## Pairing has no shared password

A lot of "remote" apps ship with a built-in password that's the same for everyone — which is no protection at all. KeyBridge doesn't.

Instead, each time you start the host it creates a **fresh, random key** and puts it inside the QR code on your screen. Scanning the code is how your phone learns the key. That key **never travels across the network** — it only ever exists on your two screens and devices. Someone would have to physically see your QR code to pair.

## Everything is encrypted, per session

Once paired, your phone and PC agree on a unique key for that session and **encrypt everything they send** with AES-256-GCM — the same family of encryption used to protect web traffic and banking apps.

Two things follow from that:

- A message that doesn't decrypt correctly is **thrown away**, not trusted. There's no "fall back to plain text" weak spot.
- Each session uses its own key, so capturing one session tells an attacker nothing about the next.

## You're in control of pairing

Pairing lasts only while the host is running. **Close the server and the pairing is gone** — the key it generated is discarded. Start it again and you get a brand-new key and a new QR code to scan. If you ever want to cut everything off, just quit the server.

## What KeyBridge does *not* do

- It doesn't read your screen or your files.
- It doesn't run in the background phoning home — there's no telemetry.
- It doesn't need cloud permissions or a login.

It does exactly one job: carry the keys you press on your phone to your PC, safely, and confirm they arrived.

## The honest caveat

Anyone with access to your PC's screen can scan the QR code while the server is open, just like anyone holding a real keyboard can type on your PC. KeyBridge protects the **connection**, not the room. On your own home Wi-Fi, that's exactly the boundary you want.

New to it all? Start with [what is KeyBridge?](/KeyBridge/guides/what-is-keybridge)
