// SPDX-License-Identifier: MPL-2.0
// Copyright © 2020 Skyline Team and Contributors (https://github.com/skyline-emu/)

#include "jvm.h"
#include <applet/swkbd/software_keyboard_config.h>

namespace skyline {
    std::string JniString::GetJString(JNIEnv *env, jstring jString) {
        auto utf{env->GetStringUTFChars(jString, nullptr)};
        std::string string{utf};
        env->ReleaseStringUTFChars(jString, utf);
        return string;
    }

    /*
     * @brief A thread-local wrapper over JNIEnv and JavaVM which automatically handles attaching and detaching threads
     */
    struct JniEnvironment {
        JNIEnv *env{};
        static inline JavaVM *vm{};
        bool attached{};

        void Initialize(JNIEnv *environment) {
            env = environment;
            if (env->GetJavaVM(&vm) < 0)
                throw exception("Cannot get JavaVM from environment");
            attached = true;
        }

        JniEnvironment() {
            if (vm && !attached) {
                vm->AttachCurrentThread(&env, nullptr);
                attached = true;
            }
        }

        ~JniEnvironment() {
            if (vm && attached)
                vm->DetachCurrentThread();
        }

        operator JNIEnv *() {
            if (!attached)
                throw exception("Not attached");
            return env;
        }

        JNIEnv *operator->() {
            if (!attached)
                throw exception("Not attached");
            return env;
        }
    };

    thread_local inline JniEnvironment env;

    JvmManager::JvmManager(JNIEnv *environ, jobject instance)
        : instance(environ->NewGlobalRef(instance)),
          instanceClass(reinterpret_cast<jclass>(environ->NewGlobalRef(environ->GetObjectClass(instance)))),
          initializeControllersId(environ->GetMethodID(instanceClass, "initializeControllers", "()V")),
          vibrateDeviceId(environ->GetMethodID(instanceClass, "vibrateDevice", "(I[J[I)V")),
          clearVibrationDeviceId(environ->GetMethodID(instanceClass, "clearVibrationDevice", "(I)V")),
          showKeyboardId(environ->GetMethodID(instanceClass, "showKeyboard", "(Ljava/nio/ByteBuffer;Ljava/lang/String;)Lemu/skyline/applet/swkbd/SoftwareKeyboardDialog;")),
          getKeyboardTextId(environ->GetMethodID(instanceClass, "getKeyboardText", "(Lemu/skyline/applet/swkbd/SoftwareKeyboardDialog;)Ljava/lang/String;")),
          hideKeyboardId(environ->GetMethodID(instanceClass, "hideKeyboard", "(Lemu/skyline/applet/swkbd/SoftwareKeyboardDialog;)V")),
          getVersionCodeId(environ->GetMethodID(instanceClass, "getVersionCode", "()I")) {
        env.Initialize(environ);
    }

    JvmManager::~JvmManager() {
        env->DeleteGlobalRef(instanceClass);
        env->DeleteGlobalRef(instance);
    }

    JNIEnv *JvmManager::GetEnv() {
        return env;
    }

    jobject JvmManager::GetField(const char *key, const char *signature) {
        return env->GetObjectField(instance, env->GetFieldID(instanceClass, key, signature));
    }

    bool JvmManager::CheckNull(const char *key, const char *signature) {
        return env->IsSameObject(env->GetObjectField(instance, env->GetFieldID(instanceClass, key, signature)), nullptr);
    }

    bool JvmManager::CheckNull(jobject &object) {
        return env->IsSameObject(object, nullptr);
    }

    void JvmManager::InitializeControllers() {
        env->CallVoidMethod(instance, initializeControllersId);
    }

    void JvmManager::VibrateDevice(jint index, const span<jlong> &timings, const span<jint> &amplitudes) {
        auto jTimings{env->NewLongArray(static_cast<jsize>(timings.size()))};
        env->SetLongArrayRegion(jTimings, 0, static_cast<jsize>(timings.size()), timings.data());
        auto jAmplitudes{env->NewIntArray(static_cast<jsize>(amplitudes.size()))};
        env->SetIntArrayRegion(jAmplitudes, 0, static_cast<jsize>(amplitudes.size()), amplitudes.data());

        env->CallVoidMethod(instance, vibrateDeviceId, index, jTimings, jAmplitudes);

        env->DeleteLocalRef(jTimings);
        env->DeleteLocalRef(jAmplitudes);
    }

    void JvmManager::ClearVibrationDevice(jint index) {
        env->CallVoidMethod(instance, clearVibrationDeviceId, index);
    }


    jobject JvmManager::ShowKeyboard(service::applet::swkbd::KeyboardConfigVB &config, std::u16string initial_text) {
        auto buff = env->NewDirectByteBuffer(&config, sizeof(service::applet::swkbd::KeyboardConfigVB));
        auto str = env->NewString(reinterpret_cast<const jchar *>(initial_text.data()), static_cast<int>(initial_text.length()));
        jobject localKeyboardDialog = env->CallObjectMethod(instance, showKeyboardId, buff, str);
        env->DeleteLocalRef(buff);
        env->DeleteLocalRef(str);
        auto keyboardDialog = env->NewGlobalRef(localKeyboardDialog);
        env->DeleteLocalRef(localKeyboardDialog);
        return keyboardDialog;
    }

    std::u16string JvmManager::GetKeyboardText(jobject keyboardDialog) {
        auto jString{reinterpret_cast<jstring>(env->CallObjectMethod(instance, getKeyboardTextId, keyboardDialog))};
        auto jChars{env->GetStringChars(jString, nullptr)};
        std::u16string input{jChars, jChars + env->GetStringLength(jString)};
        env->ReleaseStringChars(jString, jChars);

        return input;
    }

    void JvmManager::HideKeyboard(jobject keyboardDialog) {
        env->CallVoidMethod(instance, hideKeyboardId, keyboardDialog);
        env->DeleteGlobalRef(keyboardDialog);
    }

    i32 JvmManager::GetVersionCode() {
        return env->CallIntMethod(instance, getVersionCodeId);
    }
}
