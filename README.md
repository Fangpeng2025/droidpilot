# DroidPilot

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android 11+](https://img.shields.io/badge/Android-11%2B-green.svg)](https://developer.android.com)
[![MCP Compatible](https://img.shields.io/badge/MCP-Compatible-blue.svg)](https://modelcontextprotocol.io)

**Stable Android device automation for AI agents via Accessibility Service + MCP (Model Context Protocol).**

> Control any Android device from Claude, ChatGPT, or any MCP-compatible AI — no ADB, no USB, no screen mirroring. Just WiFi.

DroidPilot uses Android's native Accessibility Service to directly access the UI tree and perform gestures through OS APIs. This is fundamentally more reliable than ADB-based or OCR-based approaches used by other mobile automation tools.

## Key Features

- **No ADB required** — communicates over WiFi via WebSocket
- **Native UI tree access** — no screenshot OCR or computer vision needed
- **Reliable gesture execution** — taps, swipes, and text input via OS APIs
- **MCP native** — works with Claude Desktop, Claude Code, and any MCP client
- **18 automation tools** — tap, swipe, type, screenshot, find element, and more
- **Low token cost** — structured UI data instead of expensive image analysis
- **Simple setup** — install APK, enable service, connect

## How It Works

```
┌──────────────┐     MCP/stdio     ┌──────────────┐    WebSocket    ┌──────────────────┐
│  AI Agent    │ ◄──────────────► │  MCP Server  │ ◄────────────► │  Android Device  │
│  (Claude,    │                   │  (Node.js)   │    WiFi/LAN    │  (Accessibility   │
│   ChatGPT)   │                   │              │                │   Service + WS)   │
└──────────────┘                   └──────────────┘                └──────────────────┘
```

## Why DroidPilot?

| Approach | Reliability | Speed | LLM Token Cost | Setup |
|----------|-------------|-------|----------------|-------|
| ADB-based (droidrun etc.) | Low — connection drops, limited UI access | Medium | High (screenshot analysis) | USB/WiFi ADB |
| Screen mirroring + OCR | Low — OCR errors, high latency | Slow | Very High | Complex |
| **DroidPilot (Accessibility Service)** | **High — native OS integration** | **Fast** | **Low (structured data)** | **Install APK** |

## Available MCP Tools

| Tool | Description |
|------|-------------|
| `connect` | Connect to Android device by IP |
| `disconnect` | Disconnect from device |
| `get_device_info` | Device manufacturer, model, screen size, Android version |
| `screenshot` | Capture screen as base64 JPEG image |
| `get_ui_tree` | Full UI hierarchy with all element properties |
| `find_element` | Search elements by text, ID, class, content description |
| `tap` | Tap at screen coordinates |
| `long_press` | Long press at coordinates |
| `swipe` | Swipe gesture from point A to B |
| `scroll` | Scroll in a direction (up/down/left/right) |
| `pinch` | Pinch zoom in/out |
| `type_text` | Append text to currently focused input |
| `set_text` | Replace text in focused input |
| `press_key` | System keys: back, home, recents, notifications, etc. |
| `click_element` | Find and click element by text/ID (more reliable than coordinates) |
| `wait_for_element` | Wait for element to appear on screen (with timeout) |
| `open_app` | Launch app by package name |
| `get_focused` | Get info about currently focused input element |

## Quick Start

### 1. Android APK

**Requirements:** Android 11+ (API 30+), WiFi (same network as PC)

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open `android/` in Android Studio and build from there.

Then on the device:
1. Open DroidPilot app
2. Tap **"Open Accessibility Settings"**
3. Enable **"Mobile MCP Pro"**
4. Return to app, tap **"Start Server"**
5. Note the **IP address** displayed

### 2. MCP Server

```bash
cd mcp-server
npm install
npm run build
```

### 3. Configure Your AI Client

**Claude Desktop** — add to `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "droidpilot": {
      "command": "node",
      "args": ["/path/to/droidpilot/mcp-server/dist/index.js"]
    }
  }
}
```

**Claude Code** — add to MCP settings:

```json
{
  "mcpServers": {
    "droidpilot": {
      "command": "node",
      "args": ["/path/to/droidpilot/mcp-server/dist/index.js"]
    }
  }
}
```

### 4. Use It

Tell the AI:

```
Connect to my Android device at 192.168.1.100
```

Then give natural language commands:

```
Take a screenshot of the current screen
Open Chrome and navigate to google.com
Find the search bar and type "hello world"
Scroll down the page
Press the back button
```

## Protocol

Communication between MCP Server and Android uses JSON over WebSocket:

**Request:**
```json
{
  "id": "req_1_1234567890",
  "command": "tap",
  "params": { "x": 500, "y": 1000 }
}
```

**Response:**
```json
{
  "id": "req_1_1234567890",
  "success": true,
  "data": { "action": "tap(500.0, 1000.0)" }
}
```

## Use Cases

- **AI-powered mobile testing** — let AI agents run QA flows on real devices
- **Mobile RPA** — automate repetitive tasks across any Android app
- **Accessibility automation** — build assistive workflows for users
- **App monitoring** — periodic screenshots and UI state checks
- **Cross-app workflows** — orchestrate actions across multiple apps

## Security

- WebSocket runs on **local network only** (no internet exposure)
- Optional **auth token** support for WebSocket connections
- No data is sent to external servers
- All communication stays between your PC and your device on your LAN

## Tech Stack

- **Android**: Kotlin, AccessibilityService, Java-WebSocket
- **MCP Server**: TypeScript, Node.js, @modelcontextprotocol/sdk
- **Communication**: WebSocket (JSON protocol)

## Contributing

Contributions are welcome! Feel free to open issues and pull requests.

## License

[MIT](LICENSE)
