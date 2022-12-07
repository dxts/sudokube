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
 * Method:     saveTrie0
 * Signature:  (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_backend_TrieStoreCBackend_saveTrie0
  (JNIEnv *, jobject, jstring);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     loadTrie0
 * Signature:  (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_backend_TrieStoreCBackend_loadTrie0
  (JNIEnv *, jobject, jstring);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     initTrie0
 * Signature:  (J)V
 */
JNIEXPORT void JNICALL Java_backend_TrieStoreCBackend_initTrie0
  (JNIEnv *, jobject, jlong);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     setPrimaryMoments0
 * Signature:  ([D)V
 */
JNIEXPORT void JNICALL Java_backend_TrieStoreCBackend_setPrimaryMoments0
  (JNIEnv *, jobject, jdoubleArray);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     prepareFromTrie0
 * Signature:  ([I[I)[D
 */
JNIEXPORT jdoubleArray JNICALL Java_backend_TrieStoreCBackend_prepareFromTrie0
  (JNIEnv *, jobject, jintArray, jintArray);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     saveCuboid0
 * Signature:  ([II)V
 */
JNIEXPORT jboolean JNICALL Java_backend_TrieStoreCBackend_saveCuboid0
  (JNIEnv *, jobject, jintArray, jint);

/*
 * Class:      backend_TrieStoreCBackend
 * Method:     readMultiCuboid0
 * Signature:  (Ljava/lang/String;[Z[I[I)[I
 */
JNIEXPORT jintArray JNICALL Java_backend_TrieStoreCBackend_readMultiCuboid0
  (JNIEnv *, jobject, jstring, jbooleanArray, jintArray, jintArray);

#ifdef __cplusplus
}
#endif
#endif
