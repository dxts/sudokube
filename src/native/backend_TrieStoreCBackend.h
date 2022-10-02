/* DO NOT EDIT THIS FILE - it is machine generated */
#include <jni.h>
/* Header for class backend_TrieStoreCBackend */

#ifndef _Included_backend_TrieStoreCBackend
#define _Included_backend_TrieStoreCBackend
#ifdef __cplusplus
extern "C" {
#endif
/*
 * Class:      backend_TrieStoreCBackend
 * Method:     saveAsTrie0
 * Signature:  ([Lscala/Tuple2;Ljava/lang/String;J)V
 */
JNIEXPORT void JNICALL Java_backend_TrieStoreCBackend_saveAsTrie0
  (JNIEnv *, jobject, jobjectArray, jstring, jlong);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     loadTrie0
 * Signature:  (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_backend_TrieStoreCBackend_loadTrie0
  (JNIEnv *, jobject, jstring);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     prepareFromTrie0
 * Signature:  ([I)[Lscala/Tuple2;
 */
JNIEXPORT jobjectArray JNICALL Java_backend_TrieStoreCBackend_prepareFromTrie0
  (JNIEnv *, jobject, jintArray);

#ifdef __cplusplus
}
#endif
#endif
