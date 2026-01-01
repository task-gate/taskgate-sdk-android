# Publishing TaskGate SDK to Maven Central

This guide explains how to publish the TaskGate SDK to Maven Central.

## Prerequisites Completed ✓

- [x] Namespace `co.taskgate` registered and verified on Maven Central
- [x] Build configuration updated with Maven Central publishing setup
- [x] POM metadata configured (name, description, license, etc.)

## Next Steps to Publish

### 1. Generate GPG Key (if you don't have one)

```bash
# Generate a new GPG key
gpg --gen-key

# Follow the prompts:
# - Use your name and email (e.g., dev@taskgate.co)
# - Set a strong passphrase

# List your keys to get the Key ID
gpg --list-secret-keys --keyid-format=short

# Example output:
# sec   rsa3072/12345678 2024-01-01 [SC]
#       ^^^^^^^^ - This is your Key ID (last 8 chars)

# Upload your public key to key servers
gpg --keyserver keyserver.ubuntu.com --send-keys YOUR_KEY_ID
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

### 2. Configure Credentials

Copy the template and fill in your credentials:

```bash
cd android
cp gradle.properties.template gradle.properties
```

Edit `gradle.properties` with your actual values:

```properties
# Your Maven Central credentials
ossrhUsername=your-username-from-central.sonatype.com
ossrhPassword=your-password-or-token

# Your GPG key information
signing.keyId=YOUR_KEY_ID
signing.password=YOUR_GPG_PASSPHRASE
signing.gnupg.keyName=dev@taskgate.co
```

**IMPORTANT:** `gradle.properties` is gitignored to protect your credentials!

### 3. Build and Publish

```bash
cd android

# Clean build
./gradlew clean

# Build all artifacts (AAR, sources, javadoc)
./gradlew assembleRelease

# Publish to Maven Central
./gradlew publishReleasePublicationToSonatypeRepository

# Sign and publish in one command
./gradlew publish
```

### 4. Deploy on Maven Central Portal

1. Go to https://central.sonatype.com/
2. Log in with your credentials
3. Navigate to "Deployments"
4. Find your deployment bundle
5. Review the contents
6. Click "Publish" to make it available publicly

### 5. Verify Publication

After publishing, your SDK will be available at:

```gradle
dependencies {
    implementation("co.taskgate:sdk:1.0.14")
}
```

It may take a few hours to appear in search and sync to all mirrors.

## Troubleshooting

### GPG Signing Issues

If you get "gpg: signing failed: No secret key":
```bash
# Export your key
gpg --export-secret-keys YOUR_KEY_ID > secring.gpg

# Update gradle.properties
signing.secretKeyRingFile=/path/to/secring.gpg
```

### Authentication Failed

- Verify your credentials at https://central.sonatype.com/
- Consider using a User Token instead of password (more secure)
- Check that `ossrhUsername` and `ossrhPassword` are correct

### Missing Artifacts

Ensure all required artifacts are generated:
- AAR file (main library)
- Sources JAR
- Javadoc JAR
- POM file with metadata
- All files must be signed (.asc files)

## Publishing Checklist

Before publishing a new version:

- [ ] Update version number in `build.gradle.kts`
- [ ] Test the SDK thoroughly
- [ ] Update CHANGELOG/release notes
- [ ] Ensure all tests pass
- [ ] Build succeeds: `./gradlew build`
- [ ] Verify POM metadata is correct
- [ ] GPG key is valid and uploaded to key servers
- [ ] Credentials are configured in `gradle.properties`
- [ ] Run `./gradlew publish`
- [ ] Deploy on Maven Central portal
- [ ] Verify SDK is accessible after publication

## Version Management

Current version: **1.0.14**

To release a new version:
1. Update `version` in `build.gradle.kts` (line 58)
2. Commit the change
3. Tag the release: `git tag v1.0.14`
4. Follow the publishing steps above

## Links

- Maven Central Portal: https://central.sonatype.com/
- Documentation: https://central.sonatype.org/publish/
- Your namespace: https://central.sonatype.com/namespace/co.taskgate
