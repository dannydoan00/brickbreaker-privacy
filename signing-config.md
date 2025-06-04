# 🔐 App Signing Configuration for Release

## Step 1: Generate Release Keystore

Run this command in your project root directory:

```bash
keytool -genkey -v -keystore brickbreaker-release.keystore -alias brickbreaker-key -keyalg RSA -keysize 2048 -validity 10000
```

**Important**: 
- Store the keystore file safely - you'll need it for all future updates
- Remember the passwords - losing them means you can't update your app
- The keystore should be valid for 25+ years

## Step 2: Update build.gradle with Signing Config

Add this to your `app/build.gradle`:

```gradle
android {
    signingConfigs {
        release {
            storeFile file('path/to/brickbreaker-release.keystore')
            storePassword 'your_store_password'
            keyAlias 'brickbreaker-key'
            keyPassword 'your_key_password'
        }
    }
    
    buildTypes {
        release {
            signingConfig signingConfigs.release
            minifyEnabled true
            shrinkResources true
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
}
```

## Step 3: Create keystore.properties (Secure Method)

Create a `keystore.properties` file in your project root:

```properties
storePassword=your_store_password
keyPassword=your_key_password
keyAlias=brickbreaker-key
storeFile=path/to/brickbreaker-release.keystore
```

Then modify `app/build.gradle`:

```gradle
// Load keystore properties
def keystorePropertiesFile = rootProject.file("keystore.properties")
def keystoreProperties = new Properties()
keystoreProperties.load(new FileInputStream(keystorePropertiesFile))

android {
    signingConfigs {
        release {
            keyAlias keystoreProperties['keyAlias']
            keyPassword keystoreProperties['keyPassword']
            storeFile file(keystoreProperties['storeFile'])
            storePassword keystoreProperties['storePassword']
        }
    }
}
```

**Security Note**: Add `keystore.properties` to your `.gitignore` file!

## Step 4: Build Release APK/AAB

```bash
# For APK
./gradlew assembleRelease

# For App Bundle (recommended)
./gradlew bundleRelease
```

Your signed release files will be in:
- APK: `app/build/outputs/apk/release/app-release.apk`
- AAB: `app/build/outputs/bundle/release/app-release.aab` 