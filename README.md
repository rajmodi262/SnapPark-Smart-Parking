<div align="center">

  <h1>🚗 SnapPark - Smart Parking Management</h1>
  
  <p><b>A premium, mobile-first, QR-based secure parking ecosystem.</b></p>
  
  [![Java](https://img.shields.io/badge/Java-21-orange.svg?style=flat-square&logo=java)](https://jdk.java.net/21/)
  [![JavaFX](https://img.shields.io/badge/JavaFX-Desktop-blue.svg?style=flat-square&logo=openjdk)](https://openjfx.io/)
  [![UI](https://img.shields.io/badge/UI-Vanilla_JS-yellow.svg?style=flat-square&logo=javascript)](https://developer.mozilla.org/en-US/docs/Web/JavaScript)
  [![Database](https://img.shields.io/badge/Database-SQLite+%7C+PostgreSQL-informational.svg?style=flat-square&logo=sqlite)](#)
  [![License](https://img.shields.io/badge/License-MIT-success.svg?style=flat-square)](LICENSE)

</div>

<br/>

## 🌟 Overview
Traditional parking systems are plagued by paper waste, mandatory app tracking, and static pricing models. **SnapPark** revolutionizes this flow by providing a **zero-install, frictionless mobile experience**. 

Powered by a JavaFX terminal embedded with a custom HTTP server, users simply scan a dynamically generated QR code to access a beautifully crafted web UI right on their mobile browser—**no downloads required**.

---

## ✨ Key Features
*   **📱 Zero-Install Mobile Flow:** Users interact natively via phone browsers. No app dependency.
*   **🔐 Cryptographic QR Ticketing:** Utilizes ZXing to generate tamper-proof entry/exit sessions.
*   **📈 Dynamic Surge Pricing:** Automatically detects peak occupancy and adjusts rates to balance load.
*   **⚖️ Automated Fine Enforcement:** Penalizes users for overstaying or unauthorized parking behavior.
*   **🎨 Premium UI/UX:** Built with glassmorphism, fluid micro-animations, and responsive design.
*   **🌐 Dual Network Connectivity:** Run offline exclusively on a **Local LAN**, or expose globally via an integrated **ngrok Tunnel** automation script.

<br/>

## 🛠️ Technology Stack
| Component | Technologies Used |
|-----------|-------------------|
| **Frontend Mobile UI** | `HTML5`, `CSS3 (Glassmorphism)`, `Vanilla JS (ES6)` |
| **Backend & Kiosk** | `Java 21`, `JavaFX`, `Custom Java HTTP Server` |
| **Database Engine** | `SQLite (Local Mode)`, `PostgreSQL (Cloud Mode)` |
| **Routing & Tooling** | `Apache Maven`, `ngrok API` |

<br/>

## 🚀 Getting Started

Follow these instructions to get a copy of the project running instantly on your local machine.

### Prerequisites
Make sure you have the following installed and mapped to your system `PATH`:
*   [Java Development Kit (JDK) 21](https://jdk.java.net/21/)
*   [Apache Maven](https://maven.apache.org/)
*   *(Optional)* `ngrok` (`winget install ngrok.ngrok`) - Used for public mode only.

### Installation & Launch
1. Clone the repository and navigate to the project directory:
   ```bash
   git clone https://github.com/yourusername/SnapPark.git
   cd SnapPark/snappark
   ```

2. **Mode 1: Offline / Local Network (LAN) Mode**  
   Execute this for local testing. Setup requires both the host computer and phones to be on the **same Wi-Fi router**.
   ```bash
   # Execute on Windows
   START_SNAPPARK.bat
   ```

3. **Mode 2: Global Internet Mode (ngrok Tunneling)**  
   Execute this if you want the parking terminal to be accessible to anyone globally using cellular networks (5G/4G).
   ```bash
   # Execute on Windows
   START_PUBLIC.bat
   ```

*(Note: The system requires working internet on its first execution to resolve dependencies via Maven)*

<br/>

## 📸 Interface Previews
*(Replace these placeholder images with your actual application screenshots before publishing)*

<p align="center">
  <img src="https://via.placeholder.com/700x350.png?text=JavaFX+Desktop+Terminal" alt="Kiosk View" width="100%" style="border-radius:10px; margin-bottom: 10px;"/>
</p>
<p align="center">
  <img src="https://via.placeholder.com/300x550.png?text=Mobile+Entry+UI" width="48%" style="border-radius:10px;"/>
  &nbsp;
  <img src="https://via.placeholder.com/300x550.png?text=Mobile+Billing/Checkout+UI" width="48%" style="border-radius:10px;"/>
</p>

<br/>

## 👨‍💻 Development Architecture
This academic project was divided into a robust full-stack separation of concerns:
*   **Web Frontend Interface:** Mobile-first design, animations, DOM state management.
*   **Core Backend & API:** Embedded custom HTTP server handling state and ZXing QR cryptography.
*   **Desktop App:** JavaFX layout integration for physical interactive kiosk access.
*   **System Integrity:** Relational database schemas, automated fines, and surge algorithms.

## 📄 License
This project is provided as is. License dictates under standard academic and open-source practices.
