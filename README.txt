Parent <-> Child compatibility-fixed build

WHAT WAS FIXED
- Parent no longer starts a Child daemon locally.
- Parent pairing now connects to the actual Child daemon over LAN using Child IP + Child pairing code.
- Child exposes /api/pair and issues a persistent per-install auth token.
- Parent stores the auth token with the paired device.
- Child API endpoints require the paired auth token.
- Parent command protocol now has a matching HTTP /api/command/execute endpoint on Child.
- Parent screen/camera clients poll the Child's real JPEG frame endpoints.
- Parent camera start command is sent to the Child before frame polling.
- Child audio stream endpoint is provided for the real microphone manager.
- Parent fake seeded child devices and destructive DB seeding were removed.
- Parent no longer reports network command failure as a fake SUCCESS.
- Parent no longer uses a fake San Francisco location fallback for Child location.
- Parent geofence creation requires an actual Child location.
- Parent pairing requires an actual Child IP instead of inventing an IP address.
- Child pairing identity is persisted locally.
- Parent DB has a migration for the new authToken column.

HOW TO PAIR
1. Install/run the Child app on the Android child device.
2. Complete the Child app's required Android permissions and start its daemon.
3. On the Child app, read the displayed pairing code.
4. Put Parent and Child on the same Wi-Fi/LAN.
5. In Parent > Device Pairing, enter the Child device's LAN IP and the Child pairing code.
6. Pair. Parent then uses the returned auth token for subsequent API calls.

IMPORTANT LIMITATIONS
- This is LAN/P2P compatibility work, not a cloud relay implementation.
- Android MediaProjection screen capture requires explicit user consent on the Child device; the Parent cannot silently bypass that Android requirement.
- Camera/microphone access requires the corresponding Android permissions.
- Some existing Parent UI screens still contain demo-oriented presentation code and some advanced features are not implemented end-to-end. They must not be treated as real merely because the UI exists.
- The project was not compiled in this environment because neither project contains a complete Gradle wrapper executable/distribution and Gradle was not available here. Test on physical Android devices before release.
