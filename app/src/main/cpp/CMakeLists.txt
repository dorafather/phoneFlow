cmake_minimum_required(VERSION 3.22.1)
project(flowjni)

# ---------------------------------------------------------------------------
# libUtil(nsUtil 엔진 본체) + JNI 래퍼(flowjni.cpp/JniApp.cpp)를 libflowjni.so
# 하나로 빌드한다.
#
# 소스 목록/컴파일 정의는 libUtil/Makefile의 ANDROID_BUILD=true 분기를
# 그대로 따른다(이 조합으로 에러 0건 빌드가 실측 검증됨 - 새 조합을
# 시도하지 말고 그대로 재사용).
#   DEFLIST = -DUNIX -D_LINUX_ -DLINUX -D_REENTRANT -std=c++11
#   INC     = -I./ -I./data -I./json -I./kstring -I./thread -I./dtime
#             -I./parser -I./interpretor -I./flow -I./dfile
#   (encrypt/AES256.cpp, encrypt/SHA256.cpp는 Makefile SRCS에도 없고
#    엔진 어디에서도 참조되지 않는 미사용 코드라 여기서도 제외한다)
# ---------------------------------------------------------------------------

set(LIBUTIL_DIR ${CMAKE_CURRENT_SOURCE_DIR}/libUtil)

set(LIBUTIL_SRCS
    ${LIBUTIL_DIR}/data/ARGV.cpp
    ${LIBUTIL_DIR}/data/ID.cpp
    ${LIBUTIL_DIR}/data/LIST.cpp
    ${LIBUTIL_DIR}/data/SESSION.cpp
    ${LIBUTIL_DIR}/data/STL.cpp
    ${LIBUTIL_DIR}/data/MATRIX.cpp
    ${LIBUTIL_DIR}/dfile/DFILE.cpp
    ${LIBUTIL_DIR}/dfile/INIFILE.cpp
    ${LIBUTIL_DIR}/json/JSONK.cpp
    ${LIBUTIL_DIR}/json/JSON_ARRAYLIST.cpp
    ${LIBUTIL_DIR}/json/JSON_DEBUG.cpp
    ${LIBUTIL_DIR}/json/JSON_LINKHASH.cpp
    ${LIBUTIL_DIR}/json/JSON_OBJECT.cpp
    ${LIBUTIL_DIR}/json/JSON_PRINTBUF.cpp
    ${LIBUTIL_DIR}/json/JSON_TOKENER.cpp
    ${LIBUTIL_DIR}/json/JSON_UTIL.cpp
    ${LIBUTIL_DIR}/json/RESTMSG.cpp
    ${LIBUTIL_DIR}/kstring/COMPSTR.cpp
    ${LIBUTIL_DIR}/kstring/STRCONV.cpp
    ${LIBUTIL_DIR}/kstring/KSTRING.cpp
    ${LIBUTIL_DIR}/thread/CONDITION.cpp
    ${LIBUTIL_DIR}/thread/LOCK.cpp
    ${LIBUTIL_DIR}/thread/QUEUETHREAD.cpp
    ${LIBUTIL_DIR}/thread/THREAD.cpp
    ${LIBUTIL_DIR}/dtime/GPOLLING.cpp
    ${LIBUTIL_DIR}/dtime/DTIME.cpp
    ${LIBUTIL_DIR}/dtime/TIMER.cpp
    ${LIBUTIL_DIR}/dtime/TIMEUTIL.cpp
    ${LIBUTIL_DIR}/dtime/CRON.cpp
    ${LIBUTIL_DIR}/parser/ACTIONPARSER.cpp
    ${LIBUTIL_DIR}/parser/ASSIGNPARSER.cpp
    ${LIBUTIL_DIR}/parser/BASICPARSER.cpp
    ${LIBUTIL_DIR}/parser/CLASSPARSER.cpp
    ${LIBUTIL_DIR}/parser/CXIFPARSER.cpp
    ${LIBUTIL_DIR}/parser/FUNCPARSER.cpp
    ${LIBUTIL_DIR}/parser/IFPARSER.cpp
    ${LIBUTIL_DIR}/parser/PROCPARSER.cpp
    ${LIBUTIL_DIR}/parser/STATEPARSER.cpp
    ${LIBUTIL_DIR}/parser/STMTPARSER.cpp
    ${LIBUTIL_DIR}/parser/SCEPARSER.cpp
    ${LIBUTIL_DIR}/parser/INIPARSER.cpp
    ${LIBUTIL_DIR}/parser/LOGPARSER.cpp
    ${LIBUTIL_DIR}/parser/SBRKPARSER.cpp
    ${LIBUTIL_DIR}/interpretor/EXECORE.cpp
    ${LIBUTIL_DIR}/interpretor/EXESCEN.cpp
    ${LIBUTIL_DIR}/interpretor/EXEACTION.cpp
    ${LIBUTIL_DIR}/interpretor/EXECXIF.cpp
    ${LIBUTIL_DIR}/interpretor/EXEIF.cpp
    ${LIBUTIL_DIR}/interpretor/EXEPARAM.cpp
    ${LIBUTIL_DIR}/interpretor/EXEPROC.cpp
    ${LIBUTIL_DIR}/interpretor/EXESTATE.cpp
    ${LIBUTIL_DIR}/interpretor/EXESTMT.cpp
    ${LIBUTIL_DIR}/interpretor/EXEFUNC.cpp
    ${LIBUTIL_DIR}/interpretor/EXELOG.cpp
    ${LIBUTIL_DIR}/flow/FLOW.cpp
    ${LIBUTIL_DIR}/flow/MAINP.cpp
)

set(JNI_SRCS
    ${CMAKE_CURRENT_SOURCE_DIR}/flowjni.cpp
    ${CMAKE_CURRENT_SOURCE_DIR}/JniApp.cpp
)

add_library(flowjni SHARED
    ${LIBUTIL_SRCS}
    ${JNI_SRCS}
)

target_include_directories(flowjni PRIVATE
    ${LIBUTIL_DIR}
    ${LIBUTIL_DIR}/data
    ${LIBUTIL_DIR}/json
    ${LIBUTIL_DIR}/kstring
    ${LIBUTIL_DIR}/thread
    ${LIBUTIL_DIR}/dtime
    ${LIBUTIL_DIR}/parser
    ${LIBUTIL_DIR}/interpretor
    ${LIBUTIL_DIR}/flow
    ${LIBUTIL_DIR}/dfile
)

target_compile_definitions(flowjni PRIVATE
    UNIX
    _LINUX_
    LINUX
    _REENTRANT
)

target_compile_options(flowjni PRIVATE
    -Wall
    -Wno-deprecated
    -std=c++11
)

# bionic(Android libc)는 pthread/rt/resolv/nsl을 libc 자체에 포함하므로
# 별도 링크가 필요 없다(libUtil/Makefile의 ANDROID_BUILD COMMONLIB 주석과
# 동일한 근거). zlib는 httplib.h(미사용)에서만 걸리는 의존성이라 원래는
# 불필요하지만, NDK 표준 시스템 라이브러리라 안전하게 유지한다.
find_library(log-lib log)
find_library(z-lib z)

target_link_libraries(flowjni
    ${log-lib}
    ${z-lib}
)
