package com.notebookflow.engine

// flowjni.cpp(app/src/main/cpp/flowjni.cpp)에 네이티브 함수 이름이
// Java_com_notebookflow_engine_FlowBridge_* 로 하드코딩되어 있어, 이
// 패키지/객체 이름은 임의로 바꾸면 안 된다(1차 JNI 프로토타입 결과보고
// 참고). 앱 자체의 패키지명(com.dorafather.phoneflow)과는 별개다.
object FlowBridge {
    init {
        System.loadLibrary("flowjni")
    }

    external fun nativeInit(baseDir: String)
    external fun nativePushEvent(json: String)
    external fun nativeSetCallback(cb: FlowCallback)
}

interface FlowCallback {
    fun onFlowEvent(json: String)
}
