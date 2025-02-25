#! /bin/bash
# Build Jami daemon for architecture specified by ANDROID_ABI
set -e

if [ -z "$ANDROID_NDK" -o -z "$ANDROID_SDK" ]; then
   echo "You must define ANDROID_NDK, ANDROID_SDK and ANDROID_ABI before starting."
   echo "They must point to your NDK and SDK directories."
   exit 1
fi
if [ -z "$ANDROID_ABI" ]; then
   echo "Please set ANDROID_ABI to your architecture: armeabi-v7a, x86."
   exit 1
fi

platform=$(echo "`uname`" | tr '[:upper:]' '[:lower:]')
arch=`uname -m`

ANDROID_TOPLEVEL_DIR="`pwd`"
ANDROID_APP_DIR="${ANDROID_TOPLEVEL_DIR}/ring-android"

# Set up ABI variables
if [ ${ANDROID_ABI} = "x86" ] ; then
    TARGET="i686-linux-android"
    PJ_TARGET="i686-pc-linux-android"
    PLATFORM_SHORT_ARCH="x86"
elif [ ${ANDROID_ABI} = "x86_64" ] ; then
    TARGET="x86_64-linux-android"
    PJ_TARGET="x86_64-pc-linux-android"
    PLATFORM_SHORT_ARCH="x86_64"
elif [ ${ANDROID_ABI} = "arm64-v8a" ] ; then
    TARGET="aarch64-linux-android"
    PJ_TARGET="aarch64-unknown-linux-android"
    PLATFORM_SHORT_ARCH="arm64"
else
    TARGET_CC="armv7a-linux-androideabi"
    TARGET="arm-linux-androideabi"
    PJ_TARGET="arm-unknown-linux-androideabi"
    PLATFORM_SHORT_ARCH="arm"
fi
TARGET_CC=${TARGET_CC:-$TARGET}

export API=21
export ANDROID_API=android-$API
export TOOLCHAIN=$ANDROID_NDK/toolchains/llvm/prebuilt/$platform-$arch
export TARGET

if [ -z "$DAEMON_DIR" ]; then
    DAEMON_DIR="$(pwd)/../daemon"
    echo "DAEMON_DIR not provided trying to find it in $DAEMON_DIR"
fi
if [ ! -d "$DAEMON_DIR" ]; then
    echo 'Daemon not found.'
    echo 'If you cloned the daemon in a custom location override DAEMON_DIR to point to it'
    echo "You can also use our meta repo which contains both:
          https://review.jami.net/#/admin/projects/ring-project"
    exit 1
fi
export DAEMON_DIR

if [ "${RELEASE}" -eq 1 ]; then
    echo "Daemon in release mode."
    OPTS=""
else
    echo "Daemon in debug mode."
    OPTS="--enable-debug"
fi

# Make in //
MAKEFLAGS=
if which nproc >/dev/null; then
MAKEFLAGS=-j`nproc`
elif [ "$platform" == "darwin" ] && which sysctl >/dev/null; then
MAKEFLAGS=-j`sysctl -n machdep.cpu.thread_count`
fi

# Build buildsystem tools
cd $DAEMON_DIR/extras/tools
export PATH=`pwd`/build/bin:$PATH
echo "Building tools"
./bootstrap
make $MAKEFLAGS
make .pkg-config
make .gas

# Generate JNI interface
JNIDIR=$DAEMON_DIR/bin/jni
cd $JNIDIR
PACKAGEDIR=$ANDROID_APP_DIR/libringclient/src/main/java/cx/ring/daemon $JNIDIR/make-swig.sh

# Setup cross-compilation build environemnt
export AR=$TOOLCHAIN/bin/llvm-ar
export AS=$TOOLCHAIN/bin/$TARGET-as
export CC=$TOOLCHAIN/bin/$TARGET_CC$API-clang
export CXX=$TOOLCHAIN/bin/$TARGET_CC$API-clang++
export LD=$TOOLCHAIN/bin/ld
export RANLIB=$TOOLCHAIN/bin/llvm-ranlib
export STRIP=$TOOLCHAIN/bin/llvm-strip

FLAGS_COMMON="-fPIC -g"
EXTRA_CFLAGS="${EXTRA_CFLAGS} ${FLAGS_COMMON}"
EXTRA_CXXFLAGS="${EXTRA_CXXFLAGS} ${FLAGS_COMMON}"
EXTRA_LDFLAGS="${EXTRA_LDFLAGS} -g"
if [ ${ANDROID_ABI} = "armeabi-v7a" ] ; then
    EXTRA_CFLAGS="${EXTRA_CFLAGS} -march=armv7-a -mthumb -mfpu=vfpv3-d16"
    EXTRA_LDFLAGS="${EXTRA_LDFLAGS} -march=armv7-a -mthumb -mfpu=vfpv3-d16"
fi

############
# Contribs #
############
echo "Building the contribs"
CONTRIB_DIR=${DAEMON_DIR}/contrib/native-${TARGET}
CONTRIB_SYSROOT=${DAEMON_DIR}/contrib/${TARGET}
mkdir -p ${CONTRIB_DIR}
mkdir -p ${CONTRIB_SYSROOT}/lib/pkgconfig

cd ${CONTRIB_DIR}
../bootstrap --host=${TARGET} --enable-ffmpeg

make list
make fetch
export PATH="$PATH:$CONTRIB_SYSROOT/bin"
make $MAKEFLAGS

############
# Make Jami daemon #
############
DAEMON_BUILD_DIR="${DAEMON_DIR}/build-android-${TARGET}"
mkdir -p ${DAEMON_BUILD_DIR}

cd ${DAEMON_DIR}
if [ ! -f configure ]; then
    ./autogen.sh
fi

cd "${DAEMON_BUILD_DIR}"
if [ ! -f config.h ]; then
    echo "Configuring with ${OPTS}"
    CFLAGS="${EXTRA_CFLAGS}" \
    CXXFLAGS="${EXTRA_CXXFLAGS}" \
    CPPFLAGS="${CPPFLAGS} -I${DAEMON_DIR}/contrib/${TARGET}/include " \
    LDFLAGS="${EXTRA_LDFLAGS} -L${DAEMON_DIR}/contrib/${TARGET}/lib " \
    PKG_CONFIG_LIBDIR=$DAEMON_DIR/contrib/$TARGET/lib/pkgconfig \
    ${DAEMON_DIR}/configure --host=$TARGET $EXTRA_PARAMS \
                   --disable-shared --with-opensl --without-dbus --without-alsa --without-pulse --enable-accel\
                   --prefix=$DAEMON_DIR/install-android-$TARGET \
                   ${OPTS}
fi

if [ ${ANDROID_API} = "android-21" ] ; then
    # android-21 has empty sys/shm.h headers that triggers shm detection but it
    # doesn't have any shm functions and/or symbols. */
    export ac_cv_header_sys_shm_h=no
fi

echo "Building dring ${MAKEFLAGS}"
V=99 make $MAKEFLAGS

######################
# Building JNI library
######################
cd ${ANDROID_TOPLEVEL_DIR}

STATIC_LIBS_ALL="-llog -lOpenSLES -landroid \
                -lopendht \
                -lpjsip-${PJ_TARGET} \
                -lpjsip-simple-${PJ_TARGET} \
                -lpjsip-ua-${PJ_TARGET} -lpjsua-${PJ_TARGET} \
                -lpjnath-${PJ_TARGET} \
                -lpjmedia-${PJ_TARGET} \
                -lpjlib-util-${PJ_TARGET} \
                -lpj-${PJ_TARGET} \
                -lupnp -lixml \
                -larchive \
                -lsecp256k1 \
                -lgnutls -lhogweed -lnettle -lgmp \
                -lwebrtc_audio_processing \
                -lssl -lcrypto \
                -lavformat -lavdevice -lavfilter -lavcodec -lswresample -lswscale -lavutil \
                -lyaml-cpp -ljsoncpp -lhttp_parser -lfmt\
                -luuid -lz -ldl \
                -lvpx -lopus -lspeex -lspeexdsp -lx264 \
                -largon2 \
                -lgit2 \
                -liconv"

LIBRING_JNI_DIR=${ANDROID_APP_DIR}/app/src/main/libs/${ANDROID_ABI}
LIBRING_JNI_UNSTRIPPED_DIR=${ANDROID_APP_DIR}/unstripped/${ANDROID_ABI}
LIBCPP=$ANDROID_NDK/sources/cxx-stl/llvm-libc++/libs/${ANDROID_ABI}/libc++_shared.so

echo "Building Jami JNI library for Android to ${LIBRING_JNI_DIR}"
mkdir -p ${LIBRING_JNI_DIR}

# Use a shared libc++_shared.so (shared by jami and all other plugins)
cp $LIBCPP $LIBRING_JNI_DIR

${CXX} --shared \
       -Wall -Wextra \
       -Wno-unused-variable \
       -Wno-unused-function \
       -Wno-unused-parameter \
       -Wl,-Bsymbolic \
       ${JNIDIR}/ring_wrapper.cpp \
       ${DAEMON_BUILD_DIR}/src/.libs/libring.a \
       -isystem ${DAEMON_DIR}/contrib/${TARGET}/include \
       -I${DAEMON_DIR}/src \
       -L${DAEMON_DIR}/contrib/${TARGET}/lib \
       ${STATIC_LIBS_ALL} \
       ${FLAGS_COMMON} -O3 --std=c++17 \
       -o ${LIBRING_JNI_DIR}/libring.so

if [ "${RELEASE}" -eq 1 ]; then
    mkdir -p ${LIBRING_JNI_UNSTRIPPED_DIR}
    cp ${LIBCPP} ${LIBRING_JNI_UNSTRIPPED_DIR}
    cp ${LIBRING_JNI_DIR}/libring.so ${LIBRING_JNI_UNSTRIPPED_DIR}
    ${STRIP} ${LIBRING_JNI_DIR}/libring.so
fi
