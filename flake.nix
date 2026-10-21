{
  description = "Android SDK";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";
    systems.url = "github:nix-systems/default";
  };

  outputs = { self, nixpkgs, systems }:
    let
      forEachSystem = nixpkgs.lib.genAttrs (import systems);
      pkgsFor = forEachSystem (system:
        import nixpkgs {
          inherit system;
          config.allowUnfree = true;
          config.android_sdk.accept_license = true;
        });
    in
    {
      devShells = forEachSystem (system:
        let
          pkgs = pkgsFor.${system};
          buildToolsVersion = "36.0.0";
          androidComposition = pkgs.androidenv.composeAndroidPackages {
            platformToolsVersion = "37.0.1";
            buildToolsVersions = [ buildToolsVersion "35.0.0" ];
            platformVersions = [ "36" "37" ];
            includeNDK = true;
            ndkVersions = [ "29.0.14206865" ];
            useGoogleAPIs = false;
          };
          androidSdk = androidComposition.androidsdk;
          androidSdkDir = "${androidSdk}/libexec/android-sdk";
          cmakeWithNinja = pkgs.symlinkJoin {
            name = "cmake-with-ninja";
            paths = [ pkgs.cmake pkgs.ninja ];
          };
        in
        {
          default = pkgs.mkShell {
            packages = [
              (pkgs.android-studio.withSdk androidSdk)
              androidSdk
              pkgs.jdk21
              pkgs.git-lfs
              pkgs.wget
              pkgs.cmake
              pkgs.ninja
              pkgs.zip
              pkgs.jq
              pkgs.ispc # oidn
            ];
            env = {
              ANDROID_HOME = androidSdkDir;
              ANDROID_SDK_ROOT = androidSdkDir;
              ANDROID_NDK_ROOT = "${androidSdkDir}/ndk/29.0.14206865";
              JAVA_HOME = pkgs.jdk21.home;
              GRADLE_OPTS = "-Dorg.gradle.project.android.aapt2FromMavenOverride=${androidSdkDir}/build-tools/${buildToolsVersion}/aapt2";
            };
            # shellHook = ''
            #   ROOT="$(git rev-parse --show-toplevel 2>/dev/null || pwd)"
            #   PROPS_FILE="$ROOT/local.properties"
            #   CMAKE_HOME="${cmakeWithNinja}"
            #   if grep -qF "cmake.dir=" "$PROPS_FILE" 2>/dev/null; then
            #     sed -i "s|^cmake.dir=.*|cmake.dir=$CMAKE_HOME|" "$PROPS_FILE"
            #   else
            #     echo "cmake.dir=$CMAKE_HOME" >> "$PROPS_FILE"
            #   fi
            # '';
          };
        });
    };
}
