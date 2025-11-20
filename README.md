# BallUp — Pickup Basketball App

BallUp is an Android application built with Kotlin, Jetpack Compose, and Firebase. It allows players to find, join, and host local pickup basketball runs. The app provides real-time run updates, profile customization, and location-based court browsing.

---

## Features

### Pickup Runs
- Browse local pickup basketball runs
- Join or leave runs instantly
- Host your own run with start/end time, mode, and capacity
- Real-time updates through Firestore listeners
- Run cancellation and stale run cleanup via Cloud Functions
- Google Maps integration for navigation

### Courts
- Browse courts in your area
- See court details (name, location, surface count)
- View runs happening at each court
- Backend supports admin-level court creation

### Player Profiles
Each user has a profile that includes:
- Username  
- Display name (from Google)  
- Skill level  
- Play style  
- Height bracket  
- Favorite courts  
- Google profile photo  

These fields appear on run detail screens so hosts and players can see who is joining.

### Authentication
- Google Sign-In (Firebase Authentication)
- Automatic user document creation on first login
- Merge behavior when updating profile fields

---

## Tech Stack

### Frontend
- Kotlin  
- Jetpack Compose  
- Material 3  
- Compose Navigation  
- Google Maps SDK  

### Backend
- Firebase Authentication  
- Firebase Firestore  
- Firebase Cloud Functions (TypeScript)  

### Build Tools
- Gradle (KTS)  
- Firebase KTX libraries  
- Jetpack Compose BOM  

---

## Firestore Structure

### `users/{uid}`
uid: string  
displayName: string  
username: string  
skillLevel: string  
playStyle: string  
heightBracket: string  
favoriteCourts: string[]  
createdAt: Timestamp  
updatedAt: Timestamp  

### `courts/{courtId}`
name: string  
geo: { lat: number, lng: number }  
surfaceCount: number  
indoor: boolean  
outdoor: boolean  

### `runs/{runId}`
name: string  
courtId: string  
hostUid: string  
playerIds: string[]  
maxPlayers: number  
playerCount: number  
mode: string  
status: "active" | "cancelled" | "inactive"  
startsAt: Timestamp  
endsAt: Timestamp  
lastHeartbeatAt: Timestamp

---

## Cloud Functions (Summary)
- `cleanupStaleRuns` — removes expired or inactive runs  
- `notifyUpcomingRuns` — sends reminders before runs start  
- `purgeOldRuns` — deletes old run documents  
- `handleHostLeaving` — ensures run integrity when host leaves  

---

## Roadmap

### Short-Term
- Profile editing screen in Settings  
- Improved player cards on Run Details  
- Tap player → view full profile  
- Court filtering improvements  

### Medium-Term
- Run discovery/recommendations  
- Court popularity metrics  
- Player trust scoring  
- Run chat  

### Long-Term
- iOS version  
- Leagues and tournament features  
- Skill-based matchmaking  

---

## Author
**Nicholas Lewis**  
GitHub: https://github.com/nickLewisCSUS
