# Instructions for Claude to Generate a SnapPark Presentation

Copy all the text below the divider and paste it into Claude to generate your 11-slide presentation content.

---

You are an expert presentation designer and technical communicator. I need you to create the complete content for an 11-slide academic/professional presentation for my project, **SnapPark**. 

Please provide the **Slide Title**, **Visual Recommendations**, **Bullet Points** (keep them concise and impactful), and detailed **Speaker Notes** for each of the 11 slides outlined below. The tone should be professional, innovative, and academic.

### Project Context for you to use in the slides:
*   **Project Name:** SnapPark
*   **Tagline:** A premium, mobile-first, QR-based smart parking management system.
*   **The Problem:** Traditional parking systems rely on paper tickets, require users to download bulky native apps, utilize static pricing models, and lack real-time spot tracking.
*   **The Solution:** A scalable system featuring a JavaFX desktop kiosk embedded with a custom HTTP server. Users don't install an app; they simply scan a QR code at the physical kiosk with their phone's native camera, instantly opening a localized web application.
*   **Key Features:**
    *   Zero-install mobile web interface featuring a premium UI/UX design with glassmorphism and modern micro-animations.
    *   Dynamic surge pricing based on real-time occupancy (adjusts base rates dynamically).
    *   Automated fine enforcement for overstaying/unauthorized parking.
    *   Cryptographic QR ticket generation (using the ZXing backend) for secure session validation.
    *   Dual Network Deployment: Capable of offline deployment on a Local Wi-Fi (LAN mode) or global reach via an automated `ngrok` background tunnel (Public mode).
*   **Tech Stack:**
    *   **Frontend:** HTML5, CSS3, JavaScript (ES6). Focused on high-end, responsive vanilla design.
    *   **Backend:** Java 21, JavaFX (for the Desktop Kiosk GUI), custom embedded HTTP Server.
    *   **Database:** JDBC, SQLite (local), PostgreSQL (remote cloud).
    *   **Tooling:** Maven, ngrok.

### Required 11-Slide Structure

*   **Slide 1: Title Slide** - Include project name, tagline, and presenter info.
*   **Slide 2: The Problem with Traditional Parking** - Highlight app fatigue, paper waste, static pricing, and operational bottlenecks.
*   **Slide 3: Introducing SnapPark** - The core value proposition, emphasizing the "Mobile-First, Zero-Install" philosophy.
*   **Slide 4: Key System Features** - A highlight reel of QR ticketing, surge pricing, automated fines, and the premium UI.
*   **Slide 5: System Architecture** - Explain the client-server relationship: JavaFX Kiosk -> Embedded HTTP Server -> Mobile Web App.
*   **Slide 6: The User Workflow (Entry to Exit)** - The frictionless step-by-step journey of a user interacting with the system.
*   **Slide 7: Dynamic Pricing & Occupancy Engine** - How the system tracks spots and scales prices up during peak hours.
*   **Slide 8: Robust Networking (LAN vs. Global)** - Explain how SnapPark bypasses external hosting fees using local routing and `ngrok` tunneling.
*   **Slide 9: Premium UI/UX Engineering** - Detail the modern aesthetic choices. Why it looks like a startup rather than a basic school project.
*   **Slide 10: Future Enhancements** - Scope for integration like ALPR (Automated License Plate Recognition), IoT gates, and Stripe payment gateways.
*   **Slide 11: Conclusion & Open for Q&A** - A strong wrap-up summarizing the project's impact.
