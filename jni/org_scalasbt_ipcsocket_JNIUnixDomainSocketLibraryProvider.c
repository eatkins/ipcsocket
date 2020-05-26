#include "errno.h"
#include "jni.h"
#include "stdio.h"
#include "stdlib.h"
#include "string.h"
#include "sys/socket.h"
#include "sys/types.h"
#include "sys/un.h"
#include "unistd.h"

#include "org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider.h"
#define UNUSED __attribute__((unused))

static void native_sockaddr(JNIEnv *env, struct sockaddr_un *native,
                            jobject array, jint len) {
  memset(native, 0, sizeof(struct sockaddr_un));
  if (array) {
    jbyte *bytes = (*env)->GetByteArrayElements(env, (jbyteArray)array, 0);
    memcpy(native->sun_path, bytes, len);
    (*env)->ReleaseByteArrayElements(env, (jbyteArray)array, bytes, JNI_ABORT);
  }
  native->sun_family = 1;
#ifdef __APPLE__
  native->sun_len = len + 2;
#endif
}

static int throwOnError(JNIEnv *env, int res) {
  int err = errno;
  errno = 0;
  if (err != 0) {
    jclass class =
        (*env)->FindClass(env, "org/scalasbt/ipcsocket/NativeErrorException");
    jmethodID cons = (*env)->GetMethodID(env, class, "<init>", "(I)V");
    jobject exc = (*env)->NewObject(env, class, cons, err);
    (*env)->Throw(env, exc);
  }
  return res;
}

jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_socket(
    JNIEnv *env, UNUSED jclass clazz, jint domain, jint type, jint protocol) {
  errno = 0;
  int res = throwOnError(env, socket(domain, type, protocol));
  return res;
}

/*
 * Class:     org_scalasbt_ipcsocket_UnixDomainSocketLibraryProvider
 * Method:    bind
 * Signature:
 * (ILorg/scalasbt/ipcsocket/UnixDomainSocketLibraryProvider/SockaddrUn;I)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_bind(
    JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray path, jint len) {
  errno = 0;
  struct sockaddr_un addr;
  const struct sockaddr *sa = (struct sockaddr *)&addr;
  native_sockaddr(env, &addr, path, len);
  int res = throwOnError(env, bind(fd, sa, sizeof(struct sockaddr_un)));
  return res;
}

/*
 * Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider
 * Method:    listen
 * Signature: (II)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_listen(
    JNIEnv *env, UNUSED jclass clazz, jint fd, jint backlog) {
  errno = 0;
  int res = throwOnError(env, listen(fd, backlog));
  return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider
* Method:    accept
* Signature:
(ILorg/scalasbt/ipcsocket/JNIUnixDomainSocketLibraryProvider/SockaddrUn { return
-1 }Lcom/sun/jna/ptr/IntByReference;)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_accept(
    JNIEnv *env, UNUSED jclass clazz, jint fd) {
  errno = 0;
  struct sockaddr_un addr;
  native_sockaddr(env, &addr, NULL, 0);
  socklen_t l = 0;
  int res = accept(fd, (struct sockaddr *)&addr, &l);
  throwOnError(env, res);
  return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider
* Method:    connect
* Signature:
(ILorg/scalasbt/ipcsocket/JNIUnixDomainSocketLibraryProvider/SockaddrUn { return
-1 }I)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_connect(
    JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray path, jint len) {
  errno = 0;
  struct sockaddr_un addr;
  native_sockaddr(env, &addr, path, len);
  int res = throwOnError(
      env, connect(fd, (struct sockaddr *)&addr, sizeof(struct sockaddr_un)));
  return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider
* Method:    read
* Signature: (ILjava/nio/ByteBuffer {
return -1
}I)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_read(
    JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray buffer, jint offset, jint len) {
  errno = 0;
  jbyte *bytes = malloc(len);
  int bytes_read = read(fd, bytes, len);
  (*env)->SetByteArrayRegion(env, buffer, offset, bytes_read, bytes);
  free(bytes);
  int res = throwOnError(env, bytes_read);
  return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider
* Method:    write
* Signature: (ILjava/nio/ByteBuffer {
return -1
}I)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_write(
    JNIEnv *env, UNUSED jclass clazz, jint fd, jbyteArray buffer, jint offset, jint len) {
  errno = 0;
  jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, 0);
  int bytes_written = write(fd, bytes + offset, len);
  (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);
  int res = throwOnError(env, bytes_written);
  return res;
}

/*
 * Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider
 * Method:    close
 * Signature: (I)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_close(
    JNIEnv *env, UNUSED jclass clazz, jint fd) {
  errno = 0;
  int res = throwOnError(env, close(fd));
  return res;
}

/*
 * Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider
 * Method:    shutdown
 * Signature: (II)I
 */
jint JNICALL
Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibraryProvider_shutdown(
    JNIEnv *env, UNUSED jclass clazz, jint fd, jint how) {
  errno = 0;
  int res = throwOnError(env, shutdown(fd, how));
  return res;
}
