# Virtual Keyboard Android App

A mobile application that allows users to send text from their Android device to a PC in real-time, acting as a virtual keyboard.

## Features

- Real-time text input and transmission
- WebSocket communication with PC server
- Material 3 design with Jetpack Compose
- Dark/Light theme support
- Connection management
- Settings configuration

## Requirements

- Android Studio Hedgehog | 2023.1.1 or higher
- Android SDK 34 or higher
- Kotlin 1.9.0 or higher
- Gradle 8.2 or higher

## Setup

1. Clone the repository:
```bash
git clone https://github.com/yourusername/VirtualKeyboard.git
```

2. Open the project in Android Studio:
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned repository and select it

3. Build and run:
   - Connect an Android device or start an emulator
   - Click the "Run" button (green play icon) or press Shift+F10

## Project Structure

```
VirtualKeyboard/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/virtualkeyboard/
│   │   │   │   ├── ui/
│   │   │   │   │   ├── screens/
│   │   │   │   │   │   ├── ConnectScreen.kt
│   │   │   │   │   │   ├── KeyboardScreen.kt
│   │   │   │   │   │   └── SettingsScreen.kt
│   │   │   │   │   └── theme/
│   │   │   │   ├── navigation/
│   │   │   │   │   └── AppNavigation.kt
│   │   │   │   └── MainActivity.kt
│   │   │   └── res/
│   │   └── test/
│   └── build.gradle.kts
└── build.gradle.kts
```

## Development

### Git Workflow

1. Create a new branch for features:
```bash
git checkout -b feature/your-feature-name
```

2. Make your changes and commit:
```bash
git add .
git commit -m "Description of changes"
```

3. Push changes:
```bash
git push origin feature/your-feature-name
```

### Versioning

This project follows [Semantic Versioning](https://semver.org/).

## Architecture

The app follows MVVM (Model-View-ViewModel) architecture pattern and uses:
- Jetpack Compose for UI
- Navigation Compose for navigation
- Material 3 for theming
- WebSocket for real-time communication

## License

MIT License 