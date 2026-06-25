---
title: 'Fix it: my phone won''t connect'
description: The common reasons pairing or typing fails — same network, firewall, the manual address fallback — and how to fix each.
order: 5
audience: Troubleshooting
minutes: 4
updated: 2026-06-25
---

Most connection problems come down to one of a few things. Work through these in order.

## 1. Are both devices on the same Wi-Fi?

This is the number one cause. Your phone and PC must be on the **same network**.

- Check that your phone isn't on mobile data with Wi-Fi off.
- Some homes have a separate **"Guest" Wi-Fi** that blocks devices from seeing each other — make sure both are on the main network, not the guest one.
- Office and public Wi-Fi often isolate devices on purpose. KeyBridge may not work there; a home network or a personal hotspot will.

## 2. Did the firewall block it?

The first time the host runs, Windows asks whether to allow it through the firewall. If you clicked "Cancel" or missed it:

- Open **Windows Defender Firewall → Allow an app through firewall**, find KeyBridge Server, and tick the **Private** box.
- Then restart the server so it can listen for your phone again.

## 3. The QR code won't scan

- Make sure the **whole code** is visible on screen and not cut off, and that brightness is high enough.
- Clean the camera lens and hold steady for a second.
- Still stuck? Tap **Enter connection manually** in the app and type the address shown beneath the QR code — it looks like `ws://192.168.1.20:8765`. Type it exactly, including the `ws://` at the front.

## 4. It paired, but text appears slowly or out of order

- That's usually a busy or weak Wi-Fi signal. Move closer to the router.
- In the app's settings you can adjust the **typing delay**. A slightly higher delay can help on slow connections; a lower one types faster.

## 5. It was working, then stopped

- Check the server is still open on the PC. If you closed it (or the PC slept), the pairing ends and you'll need to **scan the QR code again**.
- Reopening the server gives you a fresh QR code — this is normal and expected.

## 6. The text didn't fully arrive

KeyBridge won't silently lose your text. If a send fails partway, the app **keeps your text and shows that it failed** instead of clearing the box — so you can just send again once the connection is back.

Still stuck? The project's [GitHub page](https://github.com/dog-broad/KeyBridge) is the place to report an issue with the details of what you saw.
