#!/bin/bash
# Google Play does not yet allow MANAGE_EXTERNAL_STORAGE apps
# so we have to work around this for now

echo "==> Repackaging Shelter ($1) for Google Play"
apktool -o work d $1

echo "=> Removing MANAGE_EXTERNAL_STORAGE from manifest"
sed -i 's@<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE"/>@@g' work/AndroidManifest.xml

echo "=> Disabling File Shuttle in preferences"
sed -i 's@android:key="settings_cross_profile_file_chooser"@android:key="settings_cross_profile_file_chooser" android:enabled="false"@' work/res/xml/preferences_settings.xml

echo "=> Building new APK"
sed -i -e "s/versionName: '\(.*\)'/versionName: '\\1-google'/" work/apktool.yml
pushd work
apktool b
popd
mv work/dist/*.apk app-release-google.apk
rm -rf work

echo "=> Zipaligning the new APK"
$ANDROID_HOME/build-tools/30.0.2/zipalign -f 4 app-release-google.apk app-release-google-aligned.apk 

echo "=> Signing the new APK"
read -p "Enter keystore path: " KS_PATH
read -p "Enter key alias: " KS_ALIAS
$ANDROID_HOME/build-tools/30.0.2/apksigner sign --ks $KS_PATH --ks-key-alias $KS_ALIAS app-release-google-aligned.apk
