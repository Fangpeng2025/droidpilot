# DroidPilot

Android device automation via Accessibility Service + MCP protocol. Stable, reliable, no ADB required.

Unlike ADB-based or screen-mirroring approaches, DroidPilot uses Android's native Accessibility Service for direct UI tree access and reliable gesture execution.

## Architecture

```
[AI / Claude] --> [MCP Server (PC)] <--WebSocket--> [DroidPilot APK (Android)]
```

- **Android APK**: AccessibilityService + WebSocket server running on-device
- **MCP Server**: Node.js/TypeScript, bridges MCP protocol to Android via WebSocket

## Why Accessibility Service?

| Approach | Reliability | Speed | Setup |
|----------|-------------|-------|-------|
| ADB-based (droidrun etc.) | Low - connection drops, limited UI access | Medium | USB/WiFi ADB |
| Screen mirroring + OCR | Low - OCR errors, high latency | Slow | Complex |
| **Accessibility Service (DroidPilot)** | **High - native OS integration** | **Fast** | **Install APK, enable service** |

## Available MCP Tools

| Tool | Description |
|------|-------------|
| `connect` | Connect to Android device |
| `disconnect` | Disconnect from device |
| `get_device_info` | Device manufacturer, model, screen size, Android version |
| `screenshot` | Capture screen as base64 JPEG image |
| `get_ui_tree` | Full UI hierarchy with all element properties |
| `find_element` | Search elements by text, ID, class, content description |
| `tap` | Tap at screen coordinates |
| `long_press` | Long press at coordinates |
| `swipe` | Swipe from point A to B |
| `scroll` | Scroll in a direction (up/down/left/right) |
| `pinch` | Pinch zoom in/out |
| `type_text` | Append text to focused input |
| `set_text` | Replace text in focused input |
| `press_key` | Press system keys (back, home, recents, etc) |
| `click_element` | Find and click element by text/ID (more reliable than coordinates) |
| `wait_for_element` | Wait for element to appear (with timeout) |
| `open_app` | Launch app by package name |
| `get_focused` | Get currently focused input element |

## Setup

### 1. Android APK

#### Requirements
- Android 11+ (API 30+)
- WiFi connection (same network as PC)

#### Build & Install

```bash
cd android
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

Or open the `android/` folder in Android Studio and build from there.

#### Enable Accessibility Service

1. Open the DroidPilot app
2. Tap "Open Accessibility Settings"
3. Find "Mobile MCP Pro" and enable it
4. Return to the app
5. Tap "Start Server"
6. Note the IP address shown

### 2. MCP Server

#### Install

```bash
cd mcp-server
npm install
npm run build
```

#### Configure in Claude Desktop

Add to your `claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "droidpilot": {
      "command": "node",
      "args": ["<path-to>/droidpilot/mcp-server/dist/index.js"]
    }
  }
}
```

#### Configure in Claude Code

Add to your MCP settings:

```json
{
  "mcpServers": {
    "droidpilot": {
      "command": "node",
      "args": ["<path-to>/droidpilot/mcp-server/dist/index.js"]
    }
  }
}
```

### 3. Usage

Once configured, tell the AI:

```
Connect to my Android device at 192.168.1.100
```

Then you can give natural language commands:

```
Take a screenshot
Open Chrome and navigate to google.com
Find and tap the search bar, type "hello world"
Scroll down the page
Go back to the home screen
```

## Protocol

Communication between MCP Server and Android uses JSON over WebSocket:

### Request
```json
{
  "id": "req_1_1234567890",
  "command": "tap",
  "params": { "x": 500, "y": 1000 }
}
```

### Response
```json
{
  "id": "req_1_1234567890",
  "success": true,
  "data": { "action": "tap(500.0, 1000.0)" }
}
```

## Security

- WebSocket runs on local network only (no internet exposure)
- Optional auth token support for WebSocket connection
- Network security config allows cleartext for local communication
- No data is sent to external servers

## License

MIT
