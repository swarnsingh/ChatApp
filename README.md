# Real-Time Chat Application

A modern Android chat application that provides real-time messaging capabilities with offline support and robust error handling.

## Features

### Core Features
- Single-screen chat interface with conversation list
- Real-time message updates using PieSocket
- Offline message queuing and automatic retry
- Message preview for each conversation
- Clear error handling and user feedback
- Empty state handling for various scenarios

### Technical Features
- Real-time synchronization using PieSocket
- Offline message queueing system
- Automatic message retry mechanism
- Clean architecture implementation
- Modern Material Design UI
- Robust error handling

## Architecture

### Tech Stack
- **Language**: Kotlin
- **Architecture Pattern**: MVVM (Model-View-ViewModel)
- **Real-time Communication**: PieSocket
- **UI Framework**: Jetpack Compose
- **Dependency Injection**: Hilt
- **Coroutines**: For asynchronous operations
- **Flow**: For reactive programming

### Project Structure
```
ChatApp/
├── app/                      # Application module
│   └── src/main/java/com/swarn/chatapp/
│       ├── presentation/     # UI components and ViewModels
│       ├── MainActivity.kt   # Main entry point
│       └── ChatApplication.kt # Application class
│
├── data/                     # Data layer
│   └── src/main/java/com/swarn/chatapp/data/
│       ├── di/              # Dependency injection
│       ├── local/           # Local data sources
│       ├── remote/          # Remote data sources
│       ├── repository/      # Repository implementations
│       └── websocket/       # WebSocket implementation
│
├── domain/                   # Domain layer
│   └── src/main/java/com/swarn/chatapp/domain/
│       ├── di/              # Dependency injection
│       ├── model/           # Domain models
│       ├── repository/      # Repository interfaces
│       ├── util/            # Utility classes
│       └── websocket/       # WebSocket interfaces
│
└── design-system/           # UI components and theme
    └── src/main/java/com/swarn/chatapp/designsystem/
        ├── components/      # Reusable UI components
        ├── compose/         # Compose-specific components
        └── theme/           # Theme and styling
```

## Implementation Details

### Real-Time Communication
- Uses PieSocket for real-time message updates
- Handles connection states and reconnection logic

### Offline Support
- Implements a message queue system
- Messages are stored locally when offline
- Automatic retry mechanism when connection is restored
- Queue management for failed messages

### Error Handling
- Comprehensive error states:
  - Network connectivity issues
  - API failures
  - Message sending failures
- User-friendly error messages
- Empty state handling for:
  - No chats available
  - No internet connection
  - Failed message delivery

### UI Components
- Chat list with message previews
- Message bubbles with different styles for sent/received
- Error states and empty states
- Material Design components

## Getting Started

### Prerequisites
- Android Studio Arctic Fox or newer
- Kotlin 2.1.10 or newer
- Minimum SDK: 27
- Target SDK: 35

### Setup
1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle files
4. Run the application

### Configuration
- Update the PieSocket server URL in the configuration file
- Configure any necessary API keys or credentials
- type message in this format {"event":"message","data":"{\"event\":\"message\",\"data\":\"Hi \",\"botId\":\"1\"}"} in url 
- change bot id in the url to get the message from the different bot

## Future Enhancements (P1, P2)
- Message persistence
- User authentication
- Message encryption
- File sharing capabilities

## Contributing
1. Fork the repository
2. Create your feature branch
3. Commit your changes
4. Push to the branch
5. Create a Pull Request

## License
This project is licensed under the MIT License - see the LICENSE file for details 