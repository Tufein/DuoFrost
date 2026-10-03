# Publishing DuoFrost

Repository: https://github.com/Tufein/DuoFrost
Android identity: `io.github.tufein.duofrost`

1. Increment `versionCode` for each published APK and update `versionName`.
2. Use JDK 17, run JVM tests and build the release variant.
3. Configure a **gitignored** `keystore.properties` file:

   ```properties
   storeFile=/absolute/private/path/duofrost-release.jks
   storePassword=YOUR_PRIVATE_PASSWORD
   keyAlias=duofrost
   keyPassword=YOUR_PRIVATE_PASSWORD
   ```

4. Sign every future release with the same DuoFrost key. Keep an independent,
   private backup of the keystore and its passwords. A different key cannot
   update an installed release in place. Do not commit these files.
5. Verify the APK with `apksigner verify --verbose --print-certs`.
6. Tag the exact tested source and publish the APK with a SHA-256 checksum and
   release notes. GitHub's source archive for that tag is the corresponding
   GPLv3 source; do not publish APK-only releases.

CI intentionally uses debug signing and does not receive release keys.
