{
  description = "Android";
  inputs = {
    flake-utils.url = "github:numtide/flake-utils";
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
  };
  outputs = { self, nixpkgs, flake-utils }:
    flake-utils.lib.eachSystem [ "x86_64-linux" ] (system:
      let
        pkgs = import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        };
        buildToolsVersion = "36.0.0";
        androidComposition = pkgs.androidenv.composeAndroidPackages {
          platformToolsVersion = "36.0.1";
          buildToolsVersions = [ buildToolsVersion "35.0.0" ];
          platformVersions = [ "36" "35" ];
          includeEmulator = false;
          includeSystemImages = false;
          systemImageTypes = [ "default" ];
          abiVersions = [ "arm64-v8a" ];
          includeNDK = true;
          useGoogleAPIs = false;
          extraLicenses = [
            "android-googletv-license"
            "android-sdk-arm-dbt-license"
            "android-sdk-license"
            "android-sdk-preview-license"
            "google-gdk-license"
            "intel-android-extra-license"
            "intel-android-sysimage-license"
            "mips-android-sysimage-license"
          ];
        };
        androidSdk = androidComposition.androidsdk;
      in {
        devShells.default = with pkgs;
          mkShell rec {
            ANDROID_HOME = "${androidSdk}/libexec/android-sdk";
            ANDROID_SDK_ROOT = "${androidSdk}/libexec/android-sdk";
            ANDROID_NDK_ROOT = "${ANDROID_HOME}/ndk-bundle";
            GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${ANDROID_HOME}/build-tools/${buildToolsVersion}/aapt2";

            # shellHook = ''
            #   PROPS_FILE="$(git rev-parse --show-toplevel 2>/dev/null || pwd)/gradle.properties"
            #   AAPT2_LINE="android.aapt2FromMavenOverride=${ANDROID_HOME}/build-tools/${buildToolsVersion}/aapt2"
            #   if ! grep -qF "android.aapt2FromMavenOverride" "$PROPS_FILE" 2>/dev/null; then
            #     echo "$AAPT2_LINE" >> "$PROPS_FILE"
            #     echo "Added aapt2 override to $PROPS_FILE"
            #   else
            #     sed -i "s|android.aapt2FromMavenOverride=.*|$AAPT2_LINE|" "$PROPS_FILE"
            #   fi
            # '';

            buildInputs = [
              (android-studio.withSdk androidSdk)
              androidSdk
              jdk21
              wget
              cmake
              ispc
            ];
          };
      });
}
