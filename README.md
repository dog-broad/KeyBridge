# Virtual Keyboard Android App

A modern Android application that allows you to remotely control your computer's keyboard through WebSocket communication. Built with Jetpack Compose and Material Design 3.

## Features

### 🎨 Modern UI/UX
- **Material Design 3** with dynamic color theming
- **Light/Dark theme** support with system preference detection
- **Smooth animations** and responsive design
- **Minimalist layout** with intuitive navigation
- **Accessibility support** with proper content descriptions

### ⌨️ Keyboard Control
- **Text Input**: Send any text directly to your computer
- **Special Keys**: Ctrl, Alt, Shift, Tab, Enter, Space, Backspace, Delete, Escape
- **Function Keys**: F1-F12 support
- **Common Hotkeys**: Copy (Ctrl+C), Paste (Ctrl+V), Cut (Ctrl+X), Undo (Ctrl+Z), Select All (Ctrl+A), Alt+Tab
- **Real-time feedback** with button press animations

### 📱 QR Code Connection
- **Camera-based QR scanning** using ML Kit
- **Automatic connection** to WebSocket server
- **Easy pairing** between phone and computer
- **Connection status indicators** with visual feedback

### ⚙️ Customizable Settings
- **Key repeat rate** adjustment
- **Typing delay** configuration
- **Haptic feedback** toggle
- **Auto-connect** to last server
- **User preferences** persistence

## Prerequisites

### Android Requirements
- Android 7.0 (API level 24) or higher
- Camera permission for QR code scanning
- Internet access for WebSocket connection

### Server Requirements
Make sure you have the Virtual Keyboard Server running on your computer:
- Python 3.8 or higher
- Required Python packages (see server documentation)

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
cd VirtualKeyboard
./gradlew assembleDebug
```

## Quick Start Guide

### 1. Set Up the Server
1. Start the Virtual Keyboard Server on your computer
2. The server will display a QR code containing the WebSocket URL
3. Make sure your phone and computer are on the same network

### 2. Connect the App
1. Open the Virtual Keyboard app on your Android device
2. Tap "Scan QR Code" on the home screen
3. Grant camera permission when prompted
4. Point your camera at the QR code displayed by the server
5. Tap "Connect" when the QR code is detected

### 3. Start Controlling
Once connected, you can:
- Type text in the text input field and tap "Send"
- Use special keys like Ctrl, Alt, Shift
- Press function keys F1-F12
- Use common hotkeys for copy, paste, etc.

## App Architecture

### Technology Stack
- **Kotlin** - Primary programming language
- **Jetpack Compose** - Modern UI toolkit
- **Material Design 3** - Design system
- **CameraX** - Camera functionality
- **ML Kit** - QR code detection
- **WebSocket** - Real-time communication
- **StateFlow** - Reactive state management
- **MVVM Architecture** - Clean code organization

### Project Structure
```
app/src/main/java/com/example/virtualkeyboard/
├── navigation/          # Navigation components
├── screens/            # UI screens (Home, QR Scanner, Profile)
├── ui/theme/          # Material Design theming
├── viewmodel/         # ViewModels for state management
└── MainActivity.kt    # Main activity
```

## Configuration

### WebSocket Protocol
The app communicates with the server using JSON messages:

```json
{
  "type": "text",
  "content": "Hello World"
}
```

```json
{
  "type": "key",
  "key": "ctrl",
  "action": "press"
}
```

```json
{
  "type": "hotkey",
  "keys": "ctrl+c"
}
```

### Supported Key Codes
- Special keys: `ctrl`, `alt`, `shift`, `tab`, `enter`, `space`, `backspace`, `delete`, `escape`
- Function keys: `f1`, `f2`, ..., `f12`
- Alphanumeric keys: `a-z`, `0-9`
- Common combinations: `ctrl+c`, `ctrl+v`, `alt+tab`, etc.

## Customization

### Theme Configuration
The app supports dynamic theming and follows Material Design 3 guidelines. You can customize colors in:
- `ui/theme/Color.kt` - Color definitions
- `ui/theme/Theme.kt` - Theme configuration

### Adding New Keys
To add new keyboard shortcuts:
1. Define the key in `KeyboardKey` data class
2. Add it to the appropriate key list in `HomeScreen.kt`
3. Update the server to handle the new key code

## Troubleshooting

### Connection Issues
- Ensure both devices are on the same network
- Check firewall settings on the computer
- Verify the server is running and accessible
- Try manually entering the WebSocket URL

### Camera/QR Scanner Issues
- Grant camera permission in Android settings
- Ensure good lighting when scanning
- Hold the camera steady and at appropriate distance
- Try scanning the QR code again if detection fails

### Performance Issues
- Close other apps to free up memory
- Restart the app if it becomes unresponsive
- Check network connectivity and signal strength

## Contributing

We welcome contributions! Please follow these steps:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Add tests if applicable
5. Submit a pull request

### Code Style
- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Add comments for complex logic
- Ensure proper error handling

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Support

For support and questions:
- Create an issue on GitHub
- Check the troubleshooting section
- Review the server documentation

## Changelog

### Version 1.0.0
- Initial release
- Basic keyboard control functionality
- QR code connection system
- Material Design 3 UI
- Light/Dark theme support
- Customizable settings

---

**Note**: This app requires the corresponding Virtual Keyboard Server to be running on your computer. Make sure to set up the server before using the mobile app. 