# AirShare

A modern Android file sharing app that turns your phone into a local HTTP server. Share files over Wi-Fi with any device — no cables, no cloud, no internet required.

## Features

- **Local HTTP server** — Access files from any browser on your Wi-Fi network
- **Upload & Download** — Drag-and-drop upload, one-click download
- **ZIP Download** — Download all files as a ZIP archive
- **Password Protection** — Optional HTTP Basic Auth
- **QR Code Sharing** — Share server address via QR code
- **Clipboard Paste** — Paste text from clipboard as a file
- **Delete Files** — Manage files from browser or app
- **Share Intent** — Receive files from other Android apps
- **Modern UI** — Jetpack Compose + Material 3

## Tech Stack

- **Language**: Kotlin 2.1
- **UI**: Jetpack Compose with Material 3
- **Architecture**: MVVM + Hilt DI
- **Server**: Ktor CIO with 1MB I/O buffers
- **Storage**: Android Storage Access Framework (scoped storage)
- **Async**: Kotlin Coroutines + Flow
- **Settings**: DataStore Preferences
- **Logging**: Timber

## Performance Optimizations

- **Batch file queries** — Uses `ContentResolver.query()` instead of `DocumentFile.listFiles()`, reducing IPC calls from N to 1
- **1MB transfer buffers** — Optimized upload/download buffer sizes for maximum throughput
- **Direct I/O streaming** — No unnecessary buffer copying between channels
- **Coroutine-based server** — Non-blocking I/O on Ktor CIO engine

## Requirements

- Android 8.0 (API 26) or higher
- Wi-Fi network connection

## Building

```bash
./gradlew assembleDebug
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/ping` | Health check |
| GET | `/api/files` | List all files (JSON) |
| GET | `/api/download/{name}` | Download a file |
| PUT | `/{name}` | Upload a file (curl-friendly) |
| POST | `/api/upload` | Upload files (multipart) |
| DELETE | `/{name}` | Delete a file |
| GET | `/api/zip` | Download all as ZIP |
| POST | `/api/zip` | Download selected as ZIP |
| PUT | `/api/clipboard` | Save text as file |

## curl Examples

```bash
# Upload a file
curl -T photo.jpg http://192.168.1.100:8080/photo.jpg

# Download a file
curl -O http://192.168.1.100:8080/api/download/photo.jpg

# List files
curl http://192.168.1.100:8080/api/files

# Delete a file
curl -X DELETE http://192.168.1.100:8080/api/download/photo.jpg
```

## License

MIT License
