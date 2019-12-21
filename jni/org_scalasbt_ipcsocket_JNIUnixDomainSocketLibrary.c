#include "jni.h"
#include "sys/socket.h"
#include "sys/types.h"
#include "sys/un.h"
#include "unistd.h"
#include "string.h"
#include "stdlib.h"
#include "errno.h"

#include "org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary.h"
#define FAMILY_SIG "()B"
#define FAMILY_LEN_SIG "()B"
#define ENTER
#define EXIT
#define LINE
/*#define ENTER fprintf(stderr, "Enter %s\n", __func__);*/
/*#define EXIT fprintf(stderr, "Exit %s\n", __func__);*/
/*#define LINE fprintf(stderr, "at line %s:%d\n", __func__, __LINE__);*/

static void native_sockaddr(JNIEnv *env, struct sockaddr_un *native, jobject array, jint len) {
    jbyte *bytes = (*env)->GetByteArrayElements(env, (jbyteArray) array, 0);
    memset(native, 0, sizeof(struct sockaddr_un));
    memcpy(native->sun_path, bytes, len);
    (*env)->ReleaseByteArrayElements(env, (jbyteArray) array, bytes, JNI_ABORT);
    native->sun_len = len + 2;
    native->sun_family = 1;
}

static int throwOnError(JNIEnv *env, int res) {
    ENTER
    int err = errno;
    errno = 0;
    if (err != 0) {
        jclass class = (*env)->FindClass(env, "org/scalasbt/ipcsocket/NativeErrorException");
        jmethodID cons = (*env)->GetMethodID(env, class, "<init>", "(I)V");
        jobject exc = (*env)->NewObject(env, class, cons, err);
        EXIT
        (*env)->Throw(env, exc);
    }
    EXIT
    return res;
}

jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_socket
(JNIEnv *env, jclass clazz, jint domain, jint type, jint protocol) {
    ENTER
    errno = 0;
    int res = throwOnError(env, socket(domain, type, protocol));
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_UnixDomainSocketLibrary
* Method:    bind
* Signature: (ILorg/scalasbt/ipcsocket/UnixDomainSocketLibrary/SockaddrUn;I)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_bind
(JNIEnv *env, jclass clazz, jint fd, jbyteArray path, jint len) {
    ENTER
    errno = 0;
    struct sockaddr_un addr;
    const struct sockaddr *sa = &addr;
    native_sockaddr(env, &addr, path, len);
    int res = throwOnError(env, bind(fd, sa, sizeof(struct sockaddr_un)));
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary
* Method:    listen
* Signature: (II)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_listen
(JNIEnv *env, jclass clazz, jint fd, jint backlog) {
    ENTER
    errno = 0;
    int res = throwOnError(env, listen(fd, backlog));
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary
* Method:    accept
* Signature: (ILorg/scalasbt/ipcsocket/JNIUnixDomainSocketLibrary/SockaddrUn {
return -1
}Lcom/sun/jna/ptr/IntByReference;)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_accept
(JNIEnv *env, jclass clazz, jint fd, jbyteArray path, jint len) {
    ENTER
    errno = 0;
    struct sockaddr_un addr;
    native_sockaddr(env, &addr, path, len);
    socklen_t l;
    int res = accept(fd, (struct sockaddr*) &addr, &l);
    throwOnError(env, res);
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary
* Method:    connect
* Signature: (ILorg/scalasbt/ipcsocket/JNIUnixDomainSocketLibrary/SockaddrUn {
return -1
}I)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_connect
(JNIEnv *env, jclass clazz, jint fd, jbyteArray path, jint len) {
    ENTER
    errno = 0;
    struct sockaddr_un addr;
    native_sockaddr(env, &addr, path, len);
    int res = throwOnError(env, connect(fd, (struct sockaddr *)&addr, sizeof(struct sockaddr_un)));
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary
* Method:    read
* Signature: (ILjava/nio/ByteBuffer {
return -1
}I)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_read
(JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer, jint len) {
    ENTER
    errno = 0;
    LINE
    jclass buf_class = (*env)->GetObjectClass(env, buffer);
    LINE
    jboolean isCopy;
    jbyte *bytes = malloc(len);
    LINE
    int bytes_read = read(fd, bytes, len);
    LINE
    (*env)->SetByteArrayRegion(env, buffer, 0, bytes_read, bytes);
    LINE
    free(bytes);
    int res = throwOnError(env, bytes_read);
    LINE
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary
* Method:    write
* Signature: (ILjava/nio/ByteBuffer {
return -1
}I)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_write
(JNIEnv *env, jclass clazz, jint fd, jbyteArray buffer, jint len) {
    ENTER
    errno = 0;
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, 0);
    LINE
    int bytes_written = write(fd, bytes, len);
    LINE
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);
    LINE
    int res =  throwOnError(env, bytes_written);
    LINE
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary
* Method:    close
* Signature: (I)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_close
(JNIEnv *env, jclass clazz, jint fd) {
    ENTER
    errno = 0;
    int res = throwOnError(env, close(fd));
    EXIT
    return res;
}

/*
* Class:     org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary
* Method:    shutdown
* Signature: (II)I
 */
jint JNICALL Java_org_scalasbt_ipcsocket_JNIUnixDomainSocketLibrary_shutdown
(JNIEnv *env, jclass clazz, jint fd, jint how) {
    ENTER
    errno = 0;
    int res = throwOnError(env, shutdown(fd, how));
    EXIT
    return res;
}
