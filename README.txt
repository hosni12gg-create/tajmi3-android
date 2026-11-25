tajmi3 for Galaxy (Live Update via Now Bar)

No Android Studio required — GitHub will build the APK for you automatically:

1. Create a free account at github.com, then click "New repository", name it "tajmi3", and set it to Private.
2. Unzip the project folder. In your repository, go to: Add file -> Upload files. Drag all the extracted contents inside (the app folder, gradle files, settings.gradle.kts, etc.) and click Commit.
   *Note: On macOS, the hidden .github folder may not upload via drag-and-drop. Follow the next step to add it manually.*
3. Go to: Add file -> Create new file. Enter this exact file path as the name:
   .github/workflows/build.yml
   Paste the entire contents of workflow-build.yml inside, then click Commit.
4. Open the "Actions" tab. You will see the "Build APK" workflow running (this takes about 5 minutes).
5. Once it finishes with a green checkmark, click on the run and download "tajmi3-apk" from the Artifacts section at the bottom. Unzip it to find your APK.
6. Transfer the APK to your Galaxy phone and install it (allow "Install from unknown sources" if prompted).

If the build fails (red X): Click the failed step, copy the error log, and send it to me.

Phone Setup:
- Grant notification permissions when the app first launches.
- Now Bar requires Android 16 / One UI 8. If it doesn't appear automatically, go to: Settings -> Notifications -> Advanced settings -> Live notifications -> toggle on for tajmi3.
- The live notification updates automatically whenever you add or delete an entry in the app.
