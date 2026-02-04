#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")"

# defaults
ABI="arm64-v8a"
DEBUG=false
SIGN=false
CLEAN=false
DOCKER=false

sign_apk() {
    local u="$1" s="${1%.apk}-signed.apk"
    [[ -f local.properties ]] || { echo "local.properties not found"; return 1; }
    KP=$(grep "^keystore.path=" local.properties | cut -d= -f2-)
    KPP=$(grep "^keystore.password=" local.properties | cut -d= -f2-)
    KA=$(grep "^keystore.alias=" local.properties | cut -d= -f2-)
    KYP=$(grep "^keystore.keyPassword=" local.properties | cut -d= -f2-)
    apksigner sign --ks "$KP" --ks-pass "pass:$KPP" --ks-key-alias "$KA" --key-pass "pass:$KYP" --out "$s" "$u"
    [[ $? -eq 0 ]] && { echo "✓ Signed APK created: $s"; rm "$u"; return 0; } || { echo "Error: Failed to sign APK"; return 1; }
}

# parse arguments
for arg in "$@"; do
    case $arg in
        --abi=*) ABI="${arg#*=}";;
        --debug) DEBUG=true;;
        --sign) SIGN=true;;
        --clean) CLEAN=true;;
        --docker) DOCKER=true;;
        --help)
            echo "usage: $0 [--abi=<abi|all>] [--debug] [--sign] [--no-clean] [--docker]"
            echo "  --abi=<abi>  arm64-v8a, armeabi-v7a, x86_64, x86, or all (default: arm64-v8a)"
            echo "  --debug      build debug variant (default: release)"
            echo "  --sign       sign release APK"
            echo "  --no-clean   skip cleaning build artifacts"
            echo "  --docker     use docker for build"
            exit 0;;
        *) echo "unknown option: $arg (try --help)"; exit 1;;
    esac
done

# validate abi
[[ "$ABI" =~ ^(arm64-v8a|armeabi-v7a|x86_64|x86|all)$ ]] || { echo "Invalid ABI: $ABI"; exit 1; }

if $DOCKER; then
    GREEN='\033[0;32m'
    BLUE='\033[0;34m'
    YELLOW='\033[1;33m'
    NC='\033[0m'

    if [[ "$ABI" == "all" ]]; then
        echo "Docker build does not support ABI=all"
        exit 1
    fi

    if $CLEAN; then
        echo -e "${YELLOW}Cleaning up orphan containers and volumes...${NC}"
        docker-compose down --remove-orphans -v
        rm -rf apks 2>/dev/null || true
    fi

    mkdir -p apks

    echo -e "${YELLOW}cloning/updating repository...${NC}"
    docker-compose run --rm dejpeg bash -c '
        if [ -d .git ]; then
            echo "Repository exists, pulling latest changes..."
            git fetch origin
            git reset --hard origin/${GIT_BRANCH:-main}
            git clean -fdx
        else
            echo "Cloning repository..."
            git clone ${GIT_REPO:-https://github.com/deyerlint/dejpeg.git} .
            git checkout ${GIT_BRANCH:-main}
        fi
    '

    echo -e "${BLUE}Current commit:${NC}"
    docker-compose run --rm dejpeg git log -1 --oneline

    echo -e "${YELLOW}Building APK...${NC}"
    VARIANT=$($DEBUG && echo "Debug" || echo "Release")
    CMD="./gradlew assemble$VARIANT -PtargetAbi=$ABI"

    ! $DEBUG && CMD="$CMD --no-daemon"
    docker-compose run --rm -e SOURCE_DATE_EPOCH=0 -e TZ=UTC -e LANG=C.UTF-8 dejpeg bash -c "$CMD"

    echo -e "${YELLOW}moving apk${NC}"
    variant=$(echo $VARIANT | tr '[:upper:]' '[:lower:]')
    docker-compose run --rm dejpeg bash -c "cp -v app/build/outputs/apk/$variant/*.apk /apks/"

    if $SIGN; then
        for apk in apks/*.apk; do
            if [[ -f "$apk" ]]; then
                sign_apk "$apk"
            fi
        done
    fi

    echo -e "${GREEN}✓ Build complete! APK is available in apks/${NC}"
    ls -lh apks/*.apk
else
    # clean
    if $CLEAN; then
        echo "cleaning"
        rm -rf opencv/build_android_* app/{.cxx,build,src/main/jniLibs} {build,buildtemp,apks} 2>/dev/null || true
    fi

    # build opencv
    OPENCV_TASK="buildOpencv${ABI:+_$ABI}"
    [[ "$ABI" == "all" ]] && OPENCV_TASK="buildOpencv"
    echo "running: ./gradlew $OPENCV_TASK --no-daemon"
    ./gradlew "$OPENCV_TASK" --no-daemon

    # build apk
    VARIANT=$($DEBUG && echo "Debug" || echo "Release")
    ARGS=("assemble$VARIANT")
    $CLEAN && ARGS=("clean" "${ARGS[@]}")
    [[ "$ABI" != "all" ]] && ARGS+=("-PtargetAbi=$ABI")
    $SIGN && ARGS+=("-PsignApk=true")
    ! $DEBUG && ARGS+=("--no-daemon")

    echo "running: ./gradlew ${ARGS[*]}"
    ./gradlew "${ARGS[@]}"

    # report results
    APK_DIR=$($SIGN && echo "apks" || echo "app/build/outputs/apk")
    if [[ -d "$APK_DIR" ]]; then
        APKS=($(find "$APK_DIR" -name "*.apk" 2>/dev/null))
        [[ ${#APKS[@]} -gt 0 ]] && {
            count=${#APKS[@]}
            plural=$([[ $count -eq 1 ]] && echo "apk" || echo "apks")
            echo "built $count $plural"
            ls -lh "${APKS[@]}"
        }
    fi
fi
