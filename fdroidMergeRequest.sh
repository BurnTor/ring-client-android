#!/usr/bin/env bash

# This script pull/clone a fdroiddata repository

set -x # Get a more verbose output for Jenkins

if [ $# -lt 3 ] ; then
    echo "Usage:"
    echo "1: commit"
    echo "2: versionName"
    echo "3: versionCode"
    echo "4: private gitlab.com access token (optional)"
    echo "e.g. ./fdroidMergeRequest.sh e5d0d7d2e625e2455fce3d041dd563c45ab9d4a9 20190123 146 djlhsdKH345456DDGfo7"
    echo "NOTE: you need to set \$ANDROID_NDK"
    exit
fi

commit=$1
versionName="$2"
versionCode=$3
gitlabToken=$4

if [ -d "fdroiddata" ]
then
    echo "fdroiddata repository exists"
    git -C fdroiddata checkout master
    git -C fdroiddata pull --rebase
else
    echo "fdroiddata repository does not exists"
    git clone https://gitlab.com/savoirfairelinux/fdroiddata.git
fi

git -C fdroiddata remote add upstream https://gitlab.com/fdroid/fdroiddata.git
git -C fdroiddata fetch upstream || exit
git -C fdroiddata status
git -C fdroiddata checkout upstream/master
git -C fdroiddata config user.name "savoirfairelinux"
git -C fdroiddata config user.email mobile@savoirfairelinux.com

METADATA_FOLDER=fdroiddata/metadata

cp ${METADATA_FOLDER}/cx.ring.txt ${METADATA_FOLDER}/cx.ring.txt_

head -n -12 ${METADATA_FOLDER}/cx.ring.txt_ > ${METADATA_FOLDER}/cx.ring.txt

source_properties="${ANDROID_NDK}/source.properties"

if [ ! -s "${source_properties}" ]; then
    echo "No NDK found. Abort!"
    exit 1
fi

major_version=$(sed -En -e 's/^Pkg.Revision[ \t]*=[ \t]*([0-9a-f]+).*/\1/p' ${source_properties})
numerical_minor_version=$(sed -En -e 's/^Pkg.Revision[ \t]*=[ \t]*[0-9a-f]+\.([0-9]+).*/\1/p' ${source_properties})
minor_version=$(echo ${numerical_minor_version} | tr 0123456789 abcdefghij)
ndk_version=r${major_version}${minor_version}

echo "Build:${versionName},${versionCode}
    commit=${commit}
    timeout=10800
    subdir=client-android/ring-android/app
    submodules=yes
    sudo=apt-get update && \\
        apt-get install --yes swig
    gradle=noPush
    rm=client-electron,client-gnome,client-ios,client-macosx,client-uwp,client-windows,docker,docs,lrc,packaging,scripts
    build=cd ../.. && \\
        export ANDROID_NDK_ROOT=\"\$ANDROID_NDK\" && \\
        export ANDROID_ABI=\"armeabi-v7a arm64-v8a x86\" && \\
        ./compile.sh --release --no-gradle
    ndk=${ndk_version}" >> ${METADATA_FOLDER}/cx.ring.txt

tail -n 13 ${METADATA_FOLDER}/cx.ring.txt_ | head -n -2 >> ${METADATA_FOLDER}/cx.ring.txt

echo "Current Version:${versionName}" >> ${METADATA_FOLDER}/cx.ring.txt
echo "Current Version Code:${versionCode}" >> ${METADATA_FOLDER}/cx.ring.txt

rm ${METADATA_FOLDER}/cx.ring.txt_

git -C fdroiddata add metadata/cx.ring.txt
git -C fdroiddata commit -s -m "Updates Jami to $versionName"
git -C fdroiddata status
git -C fdroiddata push origin HEAD:master

FDROID_METADATA_PROJECT_ID=36528
SFL_METADATA_PROJECT_ID=10540147

if [ $# -ge 4 ] ; then
    curl --request POST \
    --url https://gitlab.com/api/v4/projects/${SFL_METADATA_PROJECT_ID}/merge_requests \
    --header 'content-type: application/json' \
    --header "private-token: ${gitlabToken}" \
    --data "{
    \"id\": 1,
    \"title\": \"New Jami revision\",
    \"target_branch\": \"master\",
    \"source_branch\": \"master\",
    \"target_project_id\": ${FDROID_METADATA_PROJECT_ID}
    }"
fi
