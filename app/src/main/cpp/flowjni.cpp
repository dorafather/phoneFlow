#include <jni.h>
#include <pthread.h>
#include <unistd.h>
#include "JniApp.h"

using namespace nsUtil;

JavaVM * g_jvm = NULL;
jobject g_callbackObj = NULL;
jmethodID g_onFlowEventMethod = NULL;

static JniApp * s_pApp = NULL;

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM * vm, void * /*reserved*/)
{
	g_jvm = vm;
	return JNI_VERSION_1_6;
}

// RUNFLOW()는 내부적으로 bootStrap() 이후 MainP::m_fnRun()(getppid()==1을
// 확인하며 sleep(1)을 도는 무한 루프)에서 영원히 리턴하지 않는다 - 원래
// Unix 데몬 프로세스의 "메인 스레드"를 위해 설계된 동작이라, JNI 호출
// 스레드에서 그대로 부르면 그 스레드가 영원히 멈춘다. 그래서 전용
// 네이티브 스레드를 하나 띄워 그 안에서만 호출한다.
//
// argc=0/argv=NULL을 그대로 넘기면 안 된다 - MainP::m_fnSetProcName()
// (libUtil/flow/MAINP.cpp)이 argc를 확인하지 않고 항상 argv[0]을 바로
// 역참조해 프로세스 이름을 뽑으므로, argv가 NULL이면 그 자리에서 즉시
// SIGSEGV가 난다(실기기 네이티브 크래시로 실측 확인). libUtil은 항상
// OS가 넘겨주는 진짜 argv를 받는다고 가정하고 설계됐으므로(Windows/Linux
// 데몬 둘 다 마찬가지), 이건 libUtil 쪽 버그가 아니라 이 JNI 호출부가
// 그 전제를 깬 것 - 그래서 고치는 지점도 libUtil이 아니라 여기, 더미
// argv를 만들어 넘기는 쪽이다.
static void * s_fnRunFlowThread(void * /*arg*/)
{
	char procName[] = "phoneflow";
	char * fakeArgv[] = { procName, NULL };
	s_pApp->RUNFLOW(1, fakeArgv);
	return NULL;
}

extern "C" JNIEXPORT void JNICALL
Java_com_notebookflow_engine_FlowBridge_nativeInit(JNIEnv * env, jobject /*thiz*/, jstring _baseDir)
{
	if(s_pApp != NULL) return; // 이미 초기화됨 - 재호출은 무시

	const char * baseDir = env->GetStringUTFChars(_baseDir, NULL);
	// RUNFLOW()가 "./addr.ini"/"./rest.sce"처럼 현재 작업 디렉터리 기준
	// 상대경로를 하드코딩해서 읽으므로, 그 디렉터리로 먼저 이동해야 한다.
	chdir(baseDir);
	env->ReleaseStringUTFChars(_baseDir, baseDir);

	s_pApp = new JniApp();

	pthread_t tid;
	pthread_create(&tid, NULL, s_fnRunFlowThread, NULL);
	pthread_detach(tid);
}

extern "C" JNIEXPORT void JNICALL
Java_com_notebookflow_engine_FlowBridge_nativePushEvent(JNIEnv * env, jobject /*thiz*/, jstring _json)
{
	if(s_pApp == NULL) return;
	const char * json = env->GetStringUTFChars(_json, NULL);
	s_pApp->PUT("", json);
	env->ReleaseStringUTFChars(_json, json);
}

extern "C" JNIEXPORT void JNICALL
Java_com_notebookflow_engine_FlowBridge_nativeSetCallback(JNIEnv * env, jobject /*thiz*/, jobject _callback)
{
	if(g_callbackObj != NULL) env->DeleteGlobalRef(g_callbackObj);
	g_callbackObj = env->NewGlobalRef(_callback);
	jclass cls = env->GetObjectClass(g_callbackObj);
	g_onFlowEventMethod = env->GetMethodID(cls, "onFlowEvent", "(Ljava/lang/String;)V");
}
