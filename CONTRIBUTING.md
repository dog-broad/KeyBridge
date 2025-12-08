# Contributing to KeyBridge Android App

Thank you for your interest in contributing to the KeyBridge Android App! This document provides guidelines and instructions for contributing.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for all contributors.

## How to Contribute

### Reporting Bugs

1. Check if the bug has already been reported in the [Issues](../../issues) section
2. If not, create a new issue with:
   - A clear, descriptive title
   - Steps to reproduce the issue
   - Expected vs. actual behavior
   - Device information (Android version, device model)
   - Screenshots or screen recordings if applicable
   - Relevant logs (use `adb logcat`)

### Suggesting Features

1. Check if the feature has already been suggested
2. Create a new issue with:
   - A clear description of the feature
   - Use cases and benefits
   - UI/UX mockups if applicable (optional)

### Pull Requests

1. **Fork the repository** and clone your fork
2. **Create a feature branch** from `develop`:
   ```bash
   git checkout develop
   git pull origin develop
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes**:
   - Follow Kotlin coding conventions
   - Use meaningful variable and function names
   - Add KDoc comments for public APIs
   - Follow Material Design 3 guidelines for UI changes
   - Update documentation if needed

4. **Test your changes**:
   - Test on multiple Android versions if possible
   - Test on different screen sizes
   - Verify dark/light theme compatibility
   - Test connection scenarios (success, failure, reconnection)

5. **Commit your changes**:
   ```bash
   git add .
   git commit -m "feat: Description of your changes"
   ```
   
   Use conventional commit messages:
   - `feat:` for new features
   - `fix:` for bug fixes
   - `docs:` for documentation changes
   - `refactor:` for code refactoring
   - `ui:` for UI/UX changes
   - `test:` for test additions/changes
   - `chore:` for maintenance tasks

6. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**:
   - Target the `develop` branch
   - Provide a clear description of changes
   - Include screenshots for UI changes
   - Reference any related issues

## Development Setup

1. Clone the repository:
   ```bash
   git clone <repository-url>
   cd KeyBridge
   ```

2. Open the project in Android Studio (Arctic Fox or later recommended)

3. Sync Gradle files and wait for dependencies to download

4. Create a `local.properties` file if needed (Android Studio usually does this automatically):
   ```properties
   sdk.dir=/path/to/android/sdk
   ```

5. Build and run the app:
   ```bash
   ./gradlew assembleDebug
   ```

## Code Style

- Follow [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add KDoc comments for public functions and classes
- Keep functions focused and single-purpose
- Use Material Design 3 components and theming
- Follow MVVM architecture pattern

## Architecture

- **MVVM (Model-View-ViewModel)** architecture
- **Jetpack Compose** for UI
- **StateFlow** for reactive state management
- **ViewModel** for business logic
- Keep UI and business logic separated

## UI Guidelines

- Follow Material Design 3 principles
- Support both light and dark themes
- Ensure accessibility (content descriptions, proper contrast)
- Test on different screen sizes and orientations
- Use appropriate animations and transitions

## License Compliance

- All contributions must be compatible with Apache License 2.0
- New source files should include the Apache License header (see existing files for template)
- Update NOTICE file if adding third-party components
- Ensure any dependencies are compatible with Apache 2.0

## Security

- **Never commit sensitive data** (API keys, tokens, keystores)
- Use `local.properties` for local configuration (already in .gitignore)
- Review security implications of new features
- Report security vulnerabilities privately to maintainers

## Testing

- Test on multiple Android versions (API 24+)
- Test connection scenarios thoroughly
- Verify encryption/decryption works correctly
- Test edge cases and error conditions
- Use Android Studio's built-in testing tools

## Documentation

- Update README.md for user-facing changes
- Add KDoc comments for new public APIs
- Update UI documentation if applicable

## Questions?

Feel free to open an issue for questions or reach out to the maintainers.

Thank you for contributing! 🎉

