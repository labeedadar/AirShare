<div align="center">

# AirShare

**Local file server for Android — share files over Wi-Fi with any device via browser.**

No cables, no cloud, no internet required.

[![Build](https://github.com/labeedadar/AirShare/actions/workflows/build.yml/badge.svg)](https://github.com/labeedadar/AirShare/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
[![API](https://img.shields.io/badge/Android-26%2B-blue.svg)](https://developer.android.com/about/versions/oreo)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-purple.svg)](https://kotlinlang.org)

</div>

---

## What is AirShare?

AirShare turns your Android phone into a local HTTP file server. Select a folder, tap start, and access your files from any device on the same Wi-Fi network — phones, laptops, tablets, anything with a browser.

## Features

- **Web UI** — Access files from any browser, works on desktop and mobile
- **Upload & Download** — Drag-and-drop upload, one-click download from browser
- **ZIP Download** — Download all or selected files as a ZIP archive
- **Password Protection** — Optional HTTP Basic Auth for secure access
- **Clipboard Paste** — Paste text from clipboard directly as a file
- **Delete Files** — Manage files from browser or the Android app
- **Share Intent** — Receive files and text from other Android apps
- **curl Support** — Upload/download files from command line
- **Material 3 UI** — Modern Jetpack Compose interface

## Screenshots

> Add screenshots of the app here after taking them from your device.

## Getting Started

### Install

Download the latest APK from [Releases](https://github.com/labeedadar/AirShare/releases), or build from source:

```bash
git clone https://github.com/labeedadar/AirShare.git
cd AirShare
./gradlew assembleDebug
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### Usage

1. Open AirShare and tap **Select Folder** to choose which folder to share
2. Tap the **blue button** to start the server
3. Open the displayed URL (e.g. `http://192.168.1.100:8080`) in any browser
4. Upload, download, and manage files from the web interface

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose + Material 3 |
| Architecture | MVVM + Hilt DI |
| Server | Ktor CIO engine |
| Storage | Android Storage Access Framework |
| Async | Kotlin Coroutines + Flow |
| Settings | DataStore Preferences |
| Logging | Timber |

## API

The server exposes a REST API alongside the web UI.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/ping` | Health check |
| GET | `/api/files` | List all files (JSON) |
| GET | `/api/download/{name}` | Download a file |
| PUT | `/{name}` | Upload a file |
| POST | `/api/upload` | Upload files (multipart) |
| DELETE | `/{name}` | Delete a file |
| GET | `/api/zip` | Download all as ZIP |
| POST | `/api/zip` | Download selected as ZIP |
| PUT | `/api/clipboard` | Save text as a file |

### curl Examples

```bash
# Upload a file (PUT)
curl -T photo.jpg http://192.168.1.100:8080/photo.jpg

# Upload a file (multipart)
curl -F "file=@document.pdf" http://192.168.1.100:8080/api/upload

# Download a file
curl -O http://192.168.1.100:8080/api/download/photo.jpg

# List files as JSON
curl http://192.168.1.100:8080/api/files

# Download all files as ZIP
curl -O http://192.168.1.100:8080/api/zip

# Delete a file
curl -X DELETE http://192.168.1.100:8080/photo.jpg

# Save text from clipboard
echo "Hello from terminal" | curl -T - http://192.168.1.100:8080/api/clipboard
```

## Performance

AirShare is optimized for speed:

- **Batch file queries** — Uses `ContentResolver.query()` instead of `DocumentFile.listFiles()`, reducing IPC calls from N to 1
- **1MB I/O buffers** — Large transfer buffers for maximum throughput on upload, download, and ZIP operations
- **Direct streaming** — No unnecessary buffer copying between channels
- **Coroutine-based server** — Non-blocking I/O on the Ktor CIO engine

## Requirements

- Android 8.0 (API 26) or higher
- Devices must be on the same Wi-Fi network

## Contributing

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
