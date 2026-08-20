/*
 * CodeC PTY JNI shim — Phase 1 of the Mini-Termux plan.
 *
 * openpty (posix_openpt + grantpt + unlockpt + ptsname), fork/exec of a
 * login shell with the slave as controlling tty, and TIOCSWINSZ + SIGWINCH.
 *
 * Package: com.codeci.ide.ui.terminal.PtyNative
 */
#define _XOPEN_SOURCE 600
#define _GNU_SOURCE

#include <jni.h>
#include <errno.h>
#include <fcntl.h>
#include <signal.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>

#ifdef __ANDROID__
#include <android/log.h>
#define LOG_TAG "codec-pty"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#else
#define LOGE(...)
#endif

static int open_pty(int *master, int *slave) {
    *master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (*master < 0) {
        return -1;
    }
    if (grantpt(*master) != 0 || unlockpt(*master) != 0) {
        close(*master);
        return -1;
    }
    char *name = ptsname(*master);
    if (name == NULL) {
        close(*master);
        return -1;
    }
    *slave = open(name, O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (*slave < 0) {
        close(*master);
        return -1;
    }
    return 0;
}

static void configure_slave(int slave) {
    struct termios tio;
    if (tcgetattr(slave, &tio) != 0) {
        return;
    }
    tio.c_lflag |= (ECHO | ICANON | ISIG | IEXTEN | ECHOE | ECHOK);
    tio.c_iflag |= (ICRNL | IXON);
    tio.c_iflag &= ~(INLCR | IGNCR);
    tio.c_oflag |= (OPOST | ONLCR);
    tcsetattr(slave, TCSANOW, &tio);
}

static char **jstring_array_to_cstrs(JNIEnv *env, jobjectArray array) {
    if (array == NULL) {
        char **empty = (char **) calloc(1, sizeof(char *));
        return empty;
    }
    jsize n = (*env)->GetArrayLength(env, array);
    char **out = (char **) calloc((size_t) n + 1, sizeof(char *));
    if (out == NULL) {
        return NULL;
    }
    for (jsize i = 0; i < n; i++) {
        jstring js = (jstring) (*env)->GetObjectArrayElement(env, array, i);
        if (js == NULL) {
            out[i] = strdup("");
            continue;
        }
        const char *utf = (*env)->GetStringUTFChars(env, js, NULL);
        out[i] = utf != NULL ? strdup(utf) : strdup("");
        if (utf != NULL) {
            (*env)->ReleaseStringUTFChars(env, js, utf);
        }
        (*env)->DeleteLocalRef(env, js);
    }
    out[n] = NULL;
    return out;
}

static void free_cstrs(char **arr) {
    if (arr == NULL) {
        return;
    }
    for (char **p = arr; *p != NULL; p++) {
        free(*p);
    }
    free(arr);
}

JNIEXPORT jintArray JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeOpenPty(JNIEnv *env, jobject thiz) {
    (void) thiz;
    int master = -1;
    int slave = -1;
    if (open_pty(&master, &slave) != 0) {
        LOGE("open_pty failed: %s", strerror(errno));
        return NULL;
    }
    configure_slave(slave);
    jint fds[2] = {master, slave};
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result == NULL) {
        close(master);
        close(slave);
        return NULL;
    }
    (*env)->SetIntArrayRegion(env, result, 0, 2, fds);
    return result;
}

JNIEXPORT void JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeCloseFd(JNIEnv *env, jobject thiz, jint fd) {
    (void) env;
    (void) thiz;
    if (fd >= 0) {
        close(fd);
    }
}

JNIEXPORT jint JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeSetWindowSize(
        JNIEnv *env, jobject thiz, jint fd, jint rows, jint cols) {
    (void) env;
    (void) thiz;
    if (fd < 0 || rows <= 0 || cols <= 0) {
        errno = EINVAL;
        return -1;
    }
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_row = (unsigned short) rows;
    ws.ws_col = (unsigned short) cols;
    if (ioctl(fd, TIOCSWINSZ, &ws) != 0) {
        return -1;
    }
    return 0;
}

JNIEXPORT jint JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeSpawn(
        JNIEnv *env,
        jobject thiz,
        jint masterFd,
        jint slaveFd,
        jstring file,
        jobjectArray args,
        jobjectArray envp,
        jstring cwd) {
    (void) thiz;
    if (file == NULL || masterFd < 0 || slaveFd < 0) {
        return -1;
    }

    const char *file_c = (*env)->GetStringUTFChars(env, file, NULL);
    if (file_c == NULL) {
        return -1;
    }
    const char *cwd_c = NULL;
    if (cwd != NULL) {
        cwd_c = (*env)->GetStringUTFChars(env, cwd, NULL);
    }

    char **argv = jstring_array_to_cstrs(env, args);
    char **env_c = jstring_array_to_cstrs(env, envp);
    if (argv == NULL || env_c == NULL) {
        if (argv) free_cstrs(argv);
        if (env_c) free_cstrs(env_c);
        (*env)->ReleaseStringUTFChars(env, file, file_c);
        if (cwd_c != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
        return -1;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        free_cstrs(argv);
        free_cstrs(env_c);
        (*env)->ReleaseStringUTFChars(env, file, file_c);
        if (cwd_c != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
        return -1;
    }

    if (pid == 0) {
        /* Child: become a session leader, take the slave as controlling tty. */
        close(masterFd);
        if (setsid() < 0) {
            _exit(126);
        }
        if (ioctl(slaveFd, TIOCSCTTY, 0) < 0) {
            /* Non-fatal on some kernels; stdio still works. */
        }
        if (cwd_c != NULL && cwd_c[0] != '\0') {
            if (chdir(cwd_c) != 0) {
                /* Keep going with inherited cwd. */
            }
        }
        dup2(slaveFd, STDIN_FILENO);
        dup2(slaveFd, STDOUT_FILENO);
        dup2(slaveFd, STDERR_FILENO);
        if (slaveFd > STDERR_FILENO) {
            close(slaveFd);
        }
        /* Reset caught signals to default for the new session. */
        signal(SIGINT, SIG_DFL);
        signal(SIGQUIT, SIG_DFL);
        signal(SIGTSTP, SIG_DFL);
        signal(SIGTTIN, SIG_DFL);
        signal(SIGTTOU, SIG_DFL);
        signal(SIGCHLD, SIG_DFL);
        execve(file_c, argv, env_c);
        _exit(127);
    }

    /* Parent keeps the master, drops the slave. */
    close(slaveFd);
    free_cstrs(argv);
    free_cstrs(env_c);
    (*env)->ReleaseStringUTFChars(env, file, file_c);
    if (cwd_c != NULL) (*env)->ReleaseStringUTFChars(env, cwd, cwd_c);
    return (jint) pid;
}

JNIEXPORT jint JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeWaitPid(
        JNIEnv *env, jobject thiz, jint pid, jint options) {
    (void) env;
    (void) thiz;
    if (pid <= 0) {
        return -1;
    }
    int status = 0;
    pid_t r = waitpid((pid_t) pid, &status, options);
    if (r <= 0) {
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}

JNIEXPORT jint JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeKill(
        JNIEnv *env, jobject thiz, jint pid, jint signal) {
    (void) env;
    (void) thiz;
    if (pid <= 0) {
        return -1;
    }
    /* Negative pid: signal the whole session/process group. */
    if (kill(-pid, signal) == 0) {
        return 0;
    }
    return kill(pid, signal);
}

JNIEXPORT jint JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeRead(
        JNIEnv *env, jobject thiz, jint fd, jbyteArray buffer, jint off, jint len) {
    (void) thiz;
    if (fd < 0 || buffer == NULL || off < 0 || len <= 0) {
        return -1;
    }
    jsize cap = (*env)->GetArrayLength(env, buffer);
    if (off + len > cap) {
        return -1;
    }
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (bytes == NULL) {
        return -1;
    }
    ssize_t n = read(fd, bytes + off, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, 0);
    if (n < 0) {
        return (errno == EAGAIN || errno == EINTR) ? 0 : -1;
    }
    return (jint) n;
}

JNIEXPORT jint JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeWrite(
        JNIEnv *env, jobject thiz, jint fd, jbyteArray buffer, jint off, jint len) {
    (void) thiz;
    if (fd < 0 || buffer == NULL || off < 0 || len <= 0) {
        return -1;
    }
    jsize cap = (*env)->GetArrayLength(env, buffer);
    if (off + len > cap) {
        return -1;
    }
    jbyte *bytes = (*env)->GetByteArrayElements(env, buffer, NULL);
    if (bytes == NULL) {
        return -1;
    }
    ssize_t n = write(fd, bytes + off, (size_t) len);
    (*env)->ReleaseByteArrayElements(env, buffer, bytes, JNI_ABORT);
    if (n < 0) {
        return (errno == EAGAIN || errno == EINTR) ? 0 : -1;
    }
    return (jint) n;
}

JNIEXPORT jint JNICALL
Java_com_codeci_ide_ui_terminal_PtyNative_nativeLastErrno(JNIEnv *env, jobject thiz) {
    (void) env;
    (void) thiz;
    return errno;
}
