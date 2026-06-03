# PixelPlayer Extension System

The Extension System allows PixelPlayer to dynamically load and interact with external plugins, expanding its capabilities beyond local media playback. This system is designed to be modular, secure, and user-friendly.

## Overview

PixelPlayer now supports a multi-tier extension architecture that allows for:
- **Third-party Music Sources:** Stream music from online services.
- **Custom UI Components:** Dynamic shelves and sections in the Home screen.
- **Enhanced Search:** Aggregated search results from multiple extensions.
- **Background Downloads:** Integrated download management for extension content.

## Architecture

The system is built on three core pillars:

1.  **Extension Loader:** A dedicated module (`extension-loader/`) that handles the discovery and loading of external APKs/JARs containing extension logic. It uses a custom ClassLoader to isolate extension code from the main app.
2.  **Extension Host:** The central registry within the main app (`ExtensionRepository`) that manages the lifecycle of loaded extensions, their settings, and their communication with the core UI.
3.  **Source Scoping:** A new `SourceScope` system that allows the app to distinguish between "Local" and "Extension" content, ensuring that features like Playlists and Favorites work seamlessly across all sources.

## Key Features

- **Dynamic Discovery:** Extensions are automatically detected and listed in the new "Extensions" screen.
- **Capability System:** Each extension declares its capabilities (e.g., `SEARCH`, `STREAM`, `DOWNLOAD`), allowing the app to optimize the UI.
- **Extension Shelves:** Extensions can provide custom "Shelves" (horizontal lists of content) that appear on the Home screen.
- **Unified Search:** Search queries are broadcast to all enabled extensions, and results are merged into a single, cohesive view.
- **Integrated Download Manager:** A centralized screen to monitor and manage downloads initiated by any extension.

## How to Access

1.  **Extension Management:**
    - Open the Sidebar Drawer.
    - Navigate to the **Extensions** menu item.
    - Here you can see all installed extensions, enable/disable them, and access their specific settings.
2.  **Using Extension Content:**
    - On the **Home** screen, extension-provided content will appear as "Shelves" below your local favorites.
    - Use the **Source Selector** (icon in the Top Bar) to filter the app's view to a specific extension or "All Sources".
3.  **Searching:**
    - Perform a search as usual. Results from active extensions will be displayed in dedicated sections within the search results.
4.  **Downloads:**
    - Access the **Downloads Manager** from the Sidebar to see the progress of any offline content being fetched by extensions.

## For Developers

To create an extension, you must implement the `Extension` interface provided in the `shared` module and package it as a separate APK with a specific intent-filter. Detailed developer documentation and SDK will be provided in a future update.
