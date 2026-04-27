# Installation and Setup Guide

Follow these steps to configure and run the SnapPark system properly.

## Prerequisites
Ensure your local environment includes the following globally mapped in your system `PATH`:
1.  **Java Development Kit (JDK) 21** or higher.
    *   *Verify via CMD:* `java -version` and `javac -version`
2.  **Apache Maven 3.8+**
    *   *Verify via CMD:* `mvn -version`
3.  **(Optional) ngrok** - Required only if you're demonstrating public/internet access.
    *   *Install on Windows:* `winget install ngrok.ngrok`
    *   *Verify via CMD:* `ngrok --version`

## General Setup
The project comes pre-configured with a Maven `pom.xml` that automatically maps JavaFX, ZXing, and database driver dependencies. There is no manual setup needed beyond downloading the folder, as compiling will automatically fetch external JARS provided your PC is connected to the internet.

## Launch Configurations
Navigate to the localized `snappark` directory:
```bash
cd snappark
```

Inside you will find two primary batch (`.bat`) files to cleanly build and launch your HTTP server and Kiosk display. 

### Mode 1: Local Network Mode (Offline/WiFi deployment)
Use this mode if both your terminal Kiosk and user's phones are connected to the identical local Wi-Fi router.
*   **Run:** Double click `START_SNAPPARK.bat` or execute it in CMD.
*   **Behavior:** Compiles the app and runs the server localized to your IP (`ex: 192.168.x.x:8080`). 

### Mode 2: Global Internet Mode (Public Tunneling setup)
Use this mode if the users test scanning from a separate cellular network (e.g., 5G/4G network while your PC is on home WiFi).
*   **Run:** Double click `START_PUBLIC.bat` or execute it in CMD.
*   **Behavior:** Compiles the app, launches a background `ngrok` tunnel routing a temporary public URL out of your local `8080` port, and injects this public URL directly into the QR codes shown on the JavaFX Kiosk. 

## Stopping the Server
To quit the server, close the JavaFX window. The batch files contain automated teardowns that seamlessly terminate the `ngrok` tunnel and clear port `8080` without leaving stranded processes backgrounded.
