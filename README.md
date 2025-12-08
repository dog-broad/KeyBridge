# KeyBridge Android App

A modern Android application that allows you to remotely control your computer's keyboard through WebSocket communication. Built with Jetpack Compose and Material Design 3.

## Features

### 🎨 Modern UI/UX
- **Material Design 3** with dynamic color theming
- **Light/Dark theme** support with manual toggle
- **Smooth animations** including pulse indicators and press effects
- **Minimalist layout** with intuitive navigation
- **Accessibility support** with proper content descriptions

### ⌨️ Comprehensive Keyboard Control
- **Text Input**: Send any text directly to your computer
- **Modifier Keys**: Ctrl, Alt, Shift, Win/Cmd
- **Navigation Keys**: Arrow keys, Home, End, Page Up/Down, Insert
- **Action Keys**: Tab, Enter, Space, Backspace, Delete, Escape
- **Function Keys**: F1-F12 support
- **System Keys**: Caps Lock, Num Lock, Scroll Lock, Menu, Pause
- **Media Controls**: Play/Pause, Next/Previous Track, Volume Up/Down/Mute
- **Print Screen**: Screenshot support
- **Common Hotkeys**: Copy, Paste, Cut, Undo, Redo, Select All, Save, Find, Alt+Tab

### 🔐 Secure Connection
- **AES-256-GCM Encryption** for all communications
- **Token-based Authentication** with QR code setup
- **PBKDF2 Key Derivation** with 100,000 iterations
- **Session Management** with automatic keep-alive

**⚠️ Security Note**: The app uses a default secret key for development. Ensure your server uses the same key or implement a secure key exchange mechanism for production use.

### 📱 Easy Setup
- **QR Code Scanning** using ML Kit for instant connection
- **Manual URL Entry** as fallback option
- **Auto-Connect** to last server on app launch
- **Quick Reconnect** button when disconnected
- **Connection status** with real-time visual feedback

### ⚙️ Customizable Settings
- **Haptic Feedback** toggle for tactile response
- **Key Repeat Rate** adjustment (0.5x - 2.0x)
- **Typing Delay** configuration (0-200ms)
- **Auto-Connect** toggle for startup behavior
- **Last Server** memory with clear option
- **Reset All Settings** with confirmation

## Prerequisites

### Android Requirements
- Android 7.0 (API level 24) or higher
- Camera permission for QR code scanning
- Vibration permission for haptic feedback
- Internet/WiFi access for WebSocket connection

### Server Requirements
Make sure you have the KeyBridge Server running on your computer:
- Python 3.8 or higher
- Required Python packages: `websockets`, `pynput`, `qrcode`, `cryptography`

## Installation

### Download APK
1. Download the latest APK from the releases page
2. Enable "Install from unknown sources" in Android settings
3. Install the APK on your device

### Build from Source
1. Clone this repository
2. Open the project in Android Studio
3. Build and run the application

```bash
git clone <repository-url>
cd KeyBridge
./gradlew assembleDebug
```

## Quick Start Guide

### 1. Set Up the Server
1. Start the KeyBridge Server on your computer
2. The server will display a QR code containing connection data
3. Make sure your phone and computer are on the same network

### 2. Connect the App
1. Open the KeyBridge app on your Android device
2. Tap "Scan QR Code" on the home screen
3. Grant camera permission when prompted
4. Point your camera at the QR code displayed by the server
5. Tap "Connect" when the QR code is detected

### 3. Start Controlling
Once connected (indicated by pulsing green WiFi icon):
- Type text in the input field and tap "Send"
- Use modifier keys, navigation, and action keys
- Control media playback with media keys
- Use quick actions for common hotkeys

## App Architecture

### Technology Stack
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern declarative UI toolkit
- **Material Design 3** - Latest design system with dynamic theming
- **CameraX** - Camera functionality for QR scanning
- **ML Kit** - Google's machine learning for barcode detection
- **Java-WebSocket** - WebSocket client implementation
- **StateFlow/ViewModel** - Reactive state management
- **MVVM Architecture** - Clean separation of concerns

### Project Structure
```
app/src/main/java/com/keybridge/
├── navigation/          # Navigation graph and bottom bar
├── screens/
│   ├── HomeScreen.kt    # Main keyboard controls
│   ├── QRScannerScreen.kt # QR/manual connection
│   └── ProfileScreen.kt  # Settings and about
├── ui/theme/           # Material 3 theming
│   ├── Color.kt
│   ├── Theme.kt
│   └── Type.kt
├── viewmodel/
│   ├── WebSocketViewModel.kt    # Connection & messaging
│   └── PreferencesViewModel.kt  # User settings
└── MainActivity.kt    # App entry point
```

## Communication Protocol

### Message Format
All messages use JSON format with AES-256-GCM encryption after authentication:

```json
// Text input
{"command": "type", "text": "Hello World"}

// Key press/release
{"command": "key_press", "key": "ctrl"}
{"command": "key_release", "key": "ctrl"}

// Key combination (e.g., Ctrl+C)
{"command": "key_combo", "keys": ["ctrl", "c"]}

// Keep-alive ping
{"command": "ping", "timestamp": 1701234567890}
```

### Supported Key Codes
| Category | Keys |
|----------|------|
| Modifiers | `ctrl`, `alt`, `shift`, `cmd` |
| Navigation | `up`, `down`, `left`, `right`, `home`, `end`, `page_up`, `page_down` |
| Actions | `tab`, `enter`, `space`, `backspace`, `delete`, `esc`, `insert` |
| Function | `f1` - `f12` |
| System | `caps_lock`, `num_lock`, `scroll_lock`, `menu`, `pause`, `print_screen` |
| Media | `media_play_pause`, `media_next`, `media_previous`, `media_volume_up`, `media_volume_down`, `media_volume_mute` |

## Troubleshooting

### Connection Issues
- Ensure both devices are on the same WiFi network
- Check firewall settings on the computer (allow port 8765)
- Verify the server is running and displays the QR code
- Try using manual URL entry: `ws://YOUR_PC_IP:8765`
- Check if authentication token hasn't expired (regenerate QR code)

### QR Scanner Issues
- Grant camera permission in Android Settings > Apps
- Ensure adequate lighting when scanning
- Hold the camera steady at ~15-30cm distance
- Make sure the QR code is fully visible in the frame

### Keys Not Working
- Verify connection status shows "Ready ✓"
- Check that the server window is active/focused
- Some keys may require administrator privileges on the server
- Media keys depend on OS-level support

### Performance Issues
- Close other apps to free up memory
- Check network signal strength
- Reduce typing delay in settings for faster response
- Enable haptic feedback for better tactile confirmation

## Contributing

We welcome contributions! Please:
1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing`)
3. Make your changes with proper commit messages
4. Add tests if applicable
5. Submit a pull request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add KDoc comments for public APIs
- Ensure proper error handling

## License

Apache License 2.0

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

## Changelog

### Version 1.0.0
- Initial release with full keyboard control
- QR code and manual connection
- AES-256-GCM encrypted communication
- Media controls and system keys
- Auto-connect and quick reconnect
- Material Design 3 UI with dark/light themes
- Haptic feedback and customizable settings

---

**Note**: This app requires the corresponding KeyBridge Server to be running on your computer. Make sure to set up the server before using the mobile app.
