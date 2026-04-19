# SnapPark Hybrid Cloud Deployment

To satisfy your faculty's requirement for a "Cloud Deployed" project while keeping your beautiful desktop Kiosk UI completely intact, we must transition from a "Monolith" to a **Client-Server Architecture**.

## The Problem
Right now, the Desktop UI, the Backend API, and the SQLite Database are tightly glued together running inside one single Java process on your laptop. If we put this on a headless cloud server, the UI code crashes. If we leave it on your laptop, it's not truly "deployed."

## The Solution: Hybrid Cloud Sync

We will slice the project into three independent parts:

### 1. The Cloud Backend (Render.com / Railway)
- We will create a `HeadlessMain` class that runs ONLY the `WebServer` and the SQLite database, completely disabling the JavaFX UI.
- We will deploy this headless Java app to a 24/7 cloud server.
- It will host your REST APIs (`/api/checkin`, `/api/slots`, etc.) on a permanent URL (e.g., `https://snappark-api.onrender.com`).

### 2. The Cloud Mobile Web App (Vercel)
- We will extract your `mobile` folder (HTML/CSS/JS) and deploy it permanently to Vercel or GitHub Pages.
- We will update the JavaScript to point to the new Cloud Backend URL instead of `localhost`.
- You get a beautiful permanent link like `https://snappark.vercel.app`.

### 3. The Desktop Kiosk UI (Your Laptop)
- Your Kiosk app remains visually 100% intact with its 3D grid and animations.
- **The Catch:** We must rewrite the Kiosk code so it no longer connects to a local `.db` file. Instead, your Kiosk will make HTTP requests over the internet to fetch the live parking data from the Cloud Backend, exactly like the mobile app does!
- The Kiosk will generate QR codes pointing to the permanent Vercel link.

---

> [!WARNING]
> **Major Code Surgery Required**
> This requires removing the local database from the JavaFX side and replacing all `ParkingService` calls with `HttpClient` REST calls. This is a complex refactoring that will take some time, but it guarantees a professional, enterprise-level architecture that will blow your faculty away.

## Step-by-Step Execution Plan

### Phase 1: Decoupling the Kiosk
1. Restructure the Java `src` folder to separate UI code from Backend code.
2. Replace local `SlotDAO` calls in the Kiosk with background HTTP polling to the backend API so the 3D grid updates based on internet data.

### Phase 2: Headless Backend 
1. Create `HeadlessMain.java` designed specifically for Linux cloud deployment.
2. Test the headless backend locally using Postman/CURL.

### Phase 3: Frontend Extraction
1. Move the Mobile web files entirely out of the Java `resources` folder into a separate `snappark-web` folder.
2. Update the `app.js` to fetch from a configurable API base URL.

### Phase 4: Cloud Deployment
1. You will need to create a free GitHub account, a free Render.com account, and a free Vercel account.
2. I will guide you on how to push the code up and link the services.

## User Action Required
Please review this plan. If you approve, we will begin the massive refactoring process immediately, starting with separating the Kiosk from the Database.
