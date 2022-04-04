// SPDX-License-Identifier: MPL-2.0
// Copyright © 2020 Skyline Team and Contributors (https://github.com/skyline-emu/)

#pragma once

#include "common.h"
#include "applet/swkbd/software_keyboard_config.h"
#include <jni.h>

namespace skyline {
    /**
     * @brief A wrapper over std::string that supports construction using a JNI jstring
     */
    class JniString : public std::string {
      private:
        static std::string GetJString(JNIEnv *env, jstring jString);

      public:
        JniString(JNIEnv *env, jstring jString) : std::string(GetJString(env, jString)) {}
    };

    /**
     * @brief A wrapper over `Settings` kotlin class
     * @note The lifetime of the jni environment must exceed the lifetime of the instances of this class
     * @note Copy construction of this class is disallowed so that instances may only be created from valid JNIEnv and jobject 
     */
    class KtSettings {
      private:
        JNIEnv *env; //!< A pointer to the current jni environment
        jclass settingsClass; //!< The settings class
        jobject settingsInstance; //!< A reference to the settings instance

      public:
        KtSettings(JNIEnv *env, jobject settingsInstance) : env(env), settingsInstance(settingsInstance), settingsClass(env->GetObjectClass(settingsInstance)) {}

        KtSettings(const KtSettings &) = delete;
        void operator=(const KtSettings &) = delete;
        KtSettings(KtSettings &&) = default;

        template<typename T>
        requires std::is_integral_v<T> || std::is_enum_v<T>
        T GetInt(const char *key) {
            return static_cast<T>(env->GetIntField(settingsInstance, env->GetFieldID(settingsClass, key, "I")));
        }

        bool GetBool(const char *key) {
            return static_cast<bool>(env->GetBooleanField(settingsInstance, env->GetFieldID(settingsClass, key, "Z")));
        }

        JniString GetString(const char *key) {
            return {env, static_cast<jstring>(env->GetObjectField(settingsInstance, env->GetFieldID(settingsClass, key, "Ljava/lang/String;")))};
        }
    };

    /**
     * @brief The JvmManager class is used to simplify transactions with the Java component
     */
    class JvmManager {
      public:
        jobject instance; //!< A reference to the activity
        jclass instanceClass; //!< The class of the activity

        /**
         * @param env A pointer to the JNI environment
         * @param instance A reference to the activity
         */
        JvmManager(JNIEnv *env, jobject instance);

        ~JvmManager();

        /**
         * @brief Returns a pointer to the JNI environment for the current thread
         */
        static JNIEnv *GetEnv();

        /**
         * @brief Retrieves a specific field of the given type from the activity
         * @tparam objectType The type of the object in the field
         * @param key The name of the field in the activity class
         * @return The contents of the field as objectType
         */
        template<typename objectType>
        objectType GetField(const char *key) {
            JNIEnv *env{GetEnv()};
            if constexpr(std::is_same<objectType, jboolean>())
                return env->GetBooleanField(instance, env->GetFieldID(instanceClass, key, "Z"));
            else if constexpr(std::is_same<objectType, jbyte>())
                return env->GetByteField(instance, env->GetFieldID(instanceClass, key, "B"));
            else if constexpr(std::is_same<objectType, jchar>())
                return env->GetCharField(instance, env->GetFieldID(instanceClass, key, "C"));
            else if constexpr(std::is_same<objectType, jshort>())
                return env->GetShortField(instance, env->GetFieldID(instanceClass, key, "S"));
            else if constexpr(std::is_same<objectType, jint>())
                return env->GetIntField(instance, env->GetFieldID(instanceClass, key, "I"));
            else if constexpr(std::is_same<objectType, jlong>())
                return env->GetLongField(instance, env->GetFieldID(instanceClass, key, "J"));
            else if constexpr(std::is_same<objectType, jfloat>())
                return env->GetFloatField(instance, env->GetFieldID(instanceClass, key, "F"));
            else if constexpr(std::is_same<objectType, jdouble>())
                return env->GetDoubleField(instance, env->GetFieldID(instanceClass, key, "D"));
            else
                throw exception("GetField: Unhandled object type");
        }

        /**
         * @brief Retrieves a specific field from the activity as a jobject
         * @param key The name of the field in the activity class
         * @param signature The signature of the field
         * @return A jobject of the contents of the field
         */
        jobject GetField(const char *key, const char *signature);

        /**
         * @brief Checks if a specific field from the activity is null or not
         * @param key The name of the field in the activity class
         * @param signature The signature of the field
         * @return If the field is null or not
         */
        bool CheckNull(const char *key, const char *signature);

        /**
         * @brief Checks if a specific jobject is null or not
         * @param object The jobject to check
         * @return If the object is null or not
         */
        static bool CheckNull(jobject &object);

        /**
         * @brief A call to EmulationActivity.initializeControllers in Kotlin
         */
        void InitializeControllers();

        /**
         * @brief A call to EmulationActivity.vibrateDevice in Kotlin
         */
        void VibrateDevice(jint index, const span<jlong> &timings, const span<jint> &amplitudes);

        /**
         * @brief A call to EmulationActivity.clearVibrationDevice in Kotlin
         */
        void ClearVibrationDevice(jint index);

        /**

         * @brief Display an alert dialog with a text field
         * @return A pointer to the dialog
         */
        jobject ShowKeyboard(service::applet::swkbd::KeyboardConfigVB& config, std::u16string initial_text);
        std::u16string GetKeyboardText(jobject keyboardDialog);
        void HideKeyboard(jobject keyboardDialog);

        /**
         * @brief A call to EmulationActivity.getVersionCode in Kotlin
         * @return A version code in Vulkan's format with 14-bit patch + 10-bit major and minor components
         */
        i32 GetVersionCode();

      private:
        jmethodID initializeControllersId;
        jmethodID vibrateDeviceId;
        jmethodID clearVibrationDeviceId;
        jmethodID showKeyboardId;
        jmethodID getKeyboardTextId;
        jmethodID hideKeyboardId;
        jmethodID getVersionCodeId;
    };
}
